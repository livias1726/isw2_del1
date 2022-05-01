package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import javafx.util.Pair;

/**
 * Controller class.
 *
 * Analyzes the commit log to report every addition, deletion and changing to java files in the project.
 */
public class DifferenceTreeManager {
	
	private final String project;
	private final List<Bug> bugs;
	private final Map<String, List<FileMetadata>> files; //map of releases and list of files
	private final List<FileMetadata> chgSet; //set of files committed together

	//Instantiation
	private static DifferenceTreeManager instance = null;

	private DifferenceTreeManager(String projectName, List<Bug> bugs) {
		this.project = projectName;
		this.bugs = bugs;
		this.files = new LinkedHashMap<>();
		this.chgSet = new ArrayList<>();
	}

	public static DifferenceTreeManager getInstance(String projectName, List<Bug> bugs) {
		if(instance == null) {
			instance = new DifferenceTreeManager(projectName, bugs);
		}
		return instance;
	}

	//--------------------------------------------Getters and Setters---------------------------------------------------
	public Map<String, List<FileMetadata>> getFiles(){ return this.files; }

	//----------------------------------------------Functionalities-----------------------------------------------------
	/**
	 * Scan the commits per release and select pairs of subsequent commits
	 * to compute changes and update java files information (per release).
	 *
	 * @param commits : every commit for the project divided per release (ordered from first to last)
	 *
	 * @return : list of files per release
	 */
	public Map<String, List<FileMetadata>> analyzeFilesEvolution(Map<String, Map<RevCommit, LocalDate>> commits)
			throws GitAPIException, IOException {

		RevCommit cmId1 = null;
		for (Map.Entry<String, Map<RevCommit, LocalDate>> currEntry: commits.entrySet()) { //Scan every release
			for(RevCommit cmId2: currEntry.getValue().keySet()){ //Scan every commit in the release

				computeChanges(currEntry.getKey(), cmId1, cmId2); //Compute changes between a pair of commits

				//Manage changing set
				for(FileMetadata f: chgSet) {
					f.addChgSetCommit(currEntry.getKey(), cmId2, chgSet.size());
				}
				updateChgSet(null); //Re-initialize changing set

				cmId1 = cmId2; //Move forward
			}

			if(cmId1 != null){
				updateFilesAge(currEntry.getKey(), cmId1);
			}
		}

		return getFiles();
	}

	/**
	 * Scan the differences between pairs of sequential commits and manages those differences.
	 *
	 * @param release : the release in which these commits took place
	 * @param from: the source commit
	 * @param to: the destination commit
	 */
	private void computeChanges(String release, RevCommit from, RevCommit to) throws IOException, GitAPIException {
		DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);

		GitManager git = GitManager.getInstance(project); //Uses Git API to compute the difference tree
		List<DiffEntry> diffs = git.retrieveDifferences(diffFormatter, from, to);

		for (DiffEntry diff : diffs) {
			switch(diff.getChangeType()) {
				case ADD:
					manageAddition(release, to, diffFormatter, diff);
					break;
				case MODIFY:
					manageModified(release, to, diffFormatter, diff);
					break;
				case DELETE:
					manageDeletion(to, diff.getOldPath());
					break;
				case RENAME:
					manageRenaming(release, diff);
					break;
				case COPY:
					manageCopying(release, diff);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Triggered by a detected addition of a new .java file to the repository.
	 *
	 * @param release: name of the release in which the addition is done
	 * @param to: reference to the commit that added the file
	 * @param df: instance of the DiffFormatter
	 * @param diff: instance of the DiffEntry
	 */
	private void manageAddition(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		PersonIdent creator = to.getAuthorIdent();
		LocalDate additionDate = creator.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		//Check if the file already exists
		Pair<String, Integer> file = getLatestKnownFileVersion(diff.getNewPath());

		//If the file does not exist or was deleted in the past, create a new FileMetadata instance
		if(file.getKey() == null || files.get(file.getKey()).get(file.getValue()).isDeleted()) {
			FileMetadata f = new FileMetadata(diff.getNewPath(), release, to, additionDate, creator.getName());

			//Manage the LOC modifications
			computeLOCChanges(f, df, diff, release, to);
			//Add the file to a global list related to the release
			updateListOfFiles(release, f);
			//Manages the chgSet of the file to add at the end of the commit analysis
			updateChgSet(f);
		}
	}

	/**
	 * Triggered by a change to a .java file in the repository.
	 * Needs to manage the presence of bug fix.
	 * 
	 * @param release: name of the release in which the addition is done
	 * @param to: reference to the commit that added the file
	 * @param df: instance of the DiffFormatter
	 * @param diff: instance of the DiffEntry
	 */
	private void manageModified(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		//Check if the file exists
		Pair<String,Integer> cm = getLatestKnownFileVersion(diff.getNewPath());
		if(cm.getKey() == null) { //the file was not "seen" as added before
			return;
		}

		FileMetadata f = files.get(cm.getKey()).get(cm.getValue()); //get file from list

		//Check if the file was found in the release that is being analyzed
		boolean isIn = cm.getKey().equals(release);
		if(isIn) {
			f = new FileMetadata(f); //create a new instance from the old one
		}

		//Check for buggyness
		List<String> affectedVersions = null;
		for(Bug bug: bugs){
			if(bug.getFixCm().equals(to)){ //If the modification commit is related to a fix
				affectedVersions = bug.getAffectedVers();
				break;
			}
		}

		//Modify the file if the last modification was before the date of the current commit
		PersonIdent author = to.getAuthorIdent();
		LocalDate modDate = author.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate lastMod = f.getLastModified();
		if(lastMod == null || lastMod.isBefore(modDate)) {
			f.addModification(release, to, modDate, author.getName(), affectedVersions); //manage the new modification
		}else {
			return; //invalid modification
		}

		//Manage the LOC of the file
		computeLOCChanges(f, df, diff, release, to);

		//Add the file to a global list related to the release
		if(isIn) {
			updateListOfFiles(release, f); //overwrites the file if already in for the same release
		}

		//Manage the changing set of the file to add at the end of the commit analysis
		updateChgSet(f);
	}

	/**
	 * Triggered by a deletion of a .java file in the repository.
	 * Needs to account for the presence of bug fix.
	 * Revision addition is not considered.
	 *
	 * @param to : commit that deletes the file
	 * @param oldPath : name of the deleted file
	 */
	public void manageDeletion(RevCommit to, String oldPath) {
		//Retrieve existing file -> return null if file does not exist, else f
		Pair<String,Integer> cm = getLatestKnownFileVersion(oldPath);
		if(cm.getKey() == null) {
			return;
		}
		FileMetadata f = files.get(cm.getKey()).get(cm.getValue());

		LocalDate delDate = to.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		f.setAge(ChronoUnit.WEEKS.between(f.getCreation().getValue(), delDate)); //Sets the age at the time of deletion

		f.setDeleted(true);
	}

	/**
	 * Triggered by a renaming of a .java file in the repository.
	 * Revision addition is not considered.
	 *
	 * @param release: name of the release in which the addition is done
	 * @param diff: instance of the DiffEntry
	 */
	private void manageRenaming(String release, DiffEntry diff){
		Pair<String,Integer> cm = getLatestKnownFileVersion(diff.getOldPath());
		if(cm.getKey() == null) {
			return;
		}

		FileMetadata f = files.get(cm.getKey()).get(cm.getValue());
		
		if(!cm.getKey().equals(release)) {	/*if the renaming takes place in another release than the latest one the
											file was in*/
			//add the new file in the new release
			FileMetadata newF = new FileMetadata(f);
			newF.setFilename(diff.getNewPath());
			updateListOfFiles(release, newF);

		}else {
			f.setFilename(diff.getNewPath()); //update the file in the release
		}

		updateChgSet(f);
	}

	/**
	 * Triggered by a copy of a .java file in the repository.
	 *
	 * @param release: name of the release in which the addition is done
	 * @param diff: instance of the DiffEntry
	 */
	private void manageCopying(String release, DiffEntry diff) {
		Pair<String,Integer> cm = getLatestKnownFileVersion(diff.getOldPath());
		if(cm.getKey() == null) {
			return;
		}
		FileMetadata f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));

		f.setFilename(diff.getNewPath());
		updateListOfFiles(release, f);
		updateChgSet(f);
	}

	/**
	 * Adds a new file to the changing set of the commit.
	 *
	 * @param f: the file to add
	 * */
	private void updateChgSet(FileMetadata f) {
		if(f == null) {
			this.chgSet.clear();
		}else{
			this.chgSet.add(f);
		}
	}

	/**
	 * Scans the map of files (release, list of files) to retrieve
	 * the latest release the file was in and its index in the list
	 *
	 * @param filename: considered the ID of the file within the project
	 *
	 * @return : pair (latest release, index)
	 * */
	private Pair<String, Integer> getLatestKnownFileVersion(String filename) {
		Integer idx = null;
		String rel = null;

		int i = 0; //Index
		for(Map.Entry<String, List<FileMetadata>> currEntry: files.entrySet()){ //Scan the releases
			for(FileMetadata file: currEntry.getValue()) { //Scan the files in the release
				if(filename.equals(file.getFilename())) {
					rel = currEntry.getKey();
					idx = i;
				}
				i++;
			}
			i = 0;
		}

		return new Pair<>(rel, idx);
	}

	/**
	 * Updates the global list of files divided per release.
	 *
	 * @param release: release to analyze
	 * @param file: file instance to update (add, remove, modify)
	 * */
	private void updateListOfFiles(String release, FileMetadata file) {
		if(!files.containsKey(release)) { //New release in the list: initialize
			files.put(release, new ArrayList<>());

		}else{
			for(FileMetadata f: files.get(release)) { //Scan files in the release
				if(f.getFilename().equals(file.getFilename())) { //File already in the release
					files.get(release).remove(f); //Remove file to add the updated instance
					break;
				}
			}
		}
		
		files.get(release).add(file); //Add the new file instance
	}

	/**
	 * Manages the LOC updates for the file and consequently its size.
	 *
	 * @param f: file to analyze
	 * @param df: differences formatter
	 * @param diff: type of difference
	 * @param release: release of the commit
	 * @param revision: commit related to the changes
	 */
	private void computeLOCChanges(FileMetadata f, DiffFormatter df, DiffEntry diff, String release, RevCommit revision)
			throws IOException {

		//Get original file size (0 if new)
		int size = f.getSize();
		
		FileHeader fileHeader = df.toFileHeader(diff);
		for(Edit edit: fileHeader.toEditList()) {

			if (edit.getType() == Type.INSERT) { //LOCs were added
				//increment the size of the file and update the list for the "LOC added"
				size = size + edit.getLengthB();
				f.setLOCPerRevision(release, revision, edit.getLengthB(), 0);
				
			} else if (edit.getType() == Type.DELETE) { //LOCs were deleted
				//decrement the size and update the list for the "LOC deleted"
				size = size - edit.getLengthA();
				f.setLOCPerRevision(release, revision, edit.getLengthA(), 1);
				
			} else if (edit.getType() == Type.REPLACE) { //LOCs were modified
				//update the size and perform an addition or deletion and an update for "LOC touched"
				size = size + edit.getLengthB() - edit.getLengthA();

				if(edit.getLengthB() > edit.getLengthA()) {
					//Save LOCs added: the modification added some LOC
					f.setLOCPerRevision(release, revision, edit.getLengthB()-edit.getLengthA(), 0);
					//Save LOCs modified
					f.setLOCPerRevision(release, revision, edit.getLengthA(), 2);
					
				}else if(edit.getLengthB() < edit.getLengthA()) {
					//Save LOCs deleted: the modification deleted some LOC
					f.setLOCPerRevision(release, revision, edit.getLengthA()-edit.getLengthB(), 1);
					//Save LOCs modified
					f.setLOCPerRevision(release, revision, edit.getLengthB(), 2);
					
				}else { //edit.getLengthB() == edit.getLengthA()
					//Save LOCs modified
					f.setLOCPerRevision(release, revision, edit.getLengthA(), 2);
				}
			}
		}
		
		f.setSize(size);
	}

	/**
	 * Updates the ages of the files related to a given release by the end of said release.
	 * Only files that are present within the tree of the last commit in the release are considered:
	 * deleted files are managed in the deletion method.
	 *
	 * @param release : given release
	 * @param lastCommit : last commit in the release
	 * */
	private void updateFilesAge(String release, RevCommit lastCommit) throws GitAPIException, IOException {
		if(!files.containsKey(release)){
			return;
		}

		LocalDate lastDate = lastCommit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		GitManager git = GitManager.getInstance(project); //Uses Git API to compute the walk tree
		List<String> filenames = git.retrieveFilesInTree(lastCommit);

		for(FileMetadata file: files.get(release)){ //files added or modified in the release
			for(String filename: filenames){
				if(file.getFilename().equals(filename)){
					file.setAge(ChronoUnit.WEEKS.between(file.getCreation().getValue(), lastDate));	/*updates file age
																									in terms of weeks*/
				}
			}
		}
	}

}
