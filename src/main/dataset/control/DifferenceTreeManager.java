package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
	public Map<String, List<FileMetadata>> analyzeFilesEvolution(Map<String, Map<RevCommit, LocalDate>> commits) throws GitAPIException, IOException {

		RevCommit prevCommit = null;
		String prevRelease = null;
		String currRelease;

		for (Map.Entry<String, Map<RevCommit, LocalDate>> currEntry: commits.entrySet()) { //Scan every release
			currRelease = currEntry.getKey();

			if(prevRelease != null){
				populateNewRelease(prevRelease, currRelease); 	/*save every file not deleted from the previous release
																in the new one*/
			}

			for(RevCommit currCommit: currEntry.getValue().keySet()){ //Scan every commit in the release

				computeChanges(currEntry.getKey(), prevCommit, currCommit); //Compute changes between a pair of commits

				//Manage changing set
				for(FileMetadata f: chgSet) {
					f.addChgSetCommit(currEntry.getKey(), currCommit, chgSet.size());
				}
				updateChgSet(null); //Re-initialize changing set

				prevCommit = currCommit; //Move forward
			}

			removeDeletedFiles(currRelease);

			if(prevCommit != null){
				updateFilesAge(currEntry.getKey(), prevCommit);
			}

			prevRelease = currRelease;
		}

		return getFiles();
	}

	/**
	 * Adds to the map of files, for the new release, every non-deleted file that was present in the previous release.
	 *
	 * @param prevRelease : previous release
	 * @param currRelease : current release
	 * */
	private void populateNewRelease(String prevRelease, String currRelease) {
		List<FileMetadata> toAdd = new ArrayList<>();

		for(FileMetadata prevFile: files.get(prevRelease)){
			toAdd.add(new FileMetadata(prevFile));
		}

		files.put(currRelease, toAdd);
	}

	/**
	 * Removes every file that has been deleted during the release from the files associated with the release.
	 *
	 * @param currRelease : current release
	 * */
	private void removeDeletedFiles(String currRelease) {
		List<FileMetadata> toRemove = new ArrayList<>();

		for(FileMetadata file: files.get(currRelease)){
			if(file.isDeleted()){
				toRemove.add(file);
			}
		}

		files.get(currRelease).removeAll(toRemove);
	}

	/**
	 * Scan the differences between pairs of sequential commits and manages those differences.
	 *
	 * @param release : the release in which these commits took place
	 * @param from: the source commit
	 * @param to: the destination commit
	 */
	public void computeChanges(String release, RevCommit from, RevCommit to) throws IOException, GitAPIException {
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
					manageDeletion(release, diff.getOldPath());
					break;
				case RENAME:
					manageRenaming(release, diff.getOldPath(), diff.getNewPath());
					break;
				case COPY:
					manageCopying(release, diff.getOldPath(), diff.getNewPath());
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
		FileMetadata file = getFile(release, diff.getNewPath());

		//If the file does not exist or was deleted in the past, create a new FileMetadata instance
		if(file == null) {
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
		FileMetadata file = getFile(release, diff.getNewPath());
		if(file == null) { //the file was not "seen" as added before
			return;
		}

		//Check for buggyness
		List<String> affectedVersions = manageFileBuggyness(bugs, to);

		//Modify the file if the last modification was before the date of the current commit
		PersonIdent author = to.getAuthorIdent();
		LocalDate modDate = author.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate lastMod = file.getLastModified();
		if(lastMod == null || lastMod.isBefore(modDate)) {
			file.addModification(release, to, modDate, author.getName(), affectedVersions); //manage the new modification
		}else {
			return; //invalid modification
		}

		//Manage the LOC of the file
		computeLOCChanges(file, df, diff, release, to);

		//Manage the changing set of the file to add at the end of the commit analysis
		updateChgSet(file);
	}

	/**
	 * Triggered by a deletion of a .java file in the repository.
	 * Needs to account for the presence of bug fix.
	 * Revision addition is not considered.
	 *
	 * @param release : current release
	 * @param oldPath : name of the deleted file
	 */
	public void manageDeletion(String release, String oldPath) {
		//Retrieve existing file -> return null if file does not exist, else f
		FileMetadata file = getFile(release, oldPath);
		if(file == null) {
			return;
		}

		file.setDeleted(true);
	}

	/**
	 * Triggered by a renaming of a .java file in the repository.
	 * Revision addition is not considered.
	 *
	 * @param release: name of the release in which the addition is done
	 */
	private void manageRenaming(String release, String oldName, String newName){
		FileMetadata file = getFile(release, oldName);
		if(file == null) {
			return;
		}

		file.setFilename(newName); //update the file in the release
		updateChgSet(file);
	}

	/**
	 * Triggered by a copy of a .java file in the repository.
	 *
	 * @param release: name of the release in which the addition is done
	 */
	private void manageCopying(String release, String oldName, String newName) {
		FileMetadata file = getFile(release, oldName);
		if(file == null) {
			return;
		}

		FileMetadata f = new FileMetadata(file);

		f.setFilename(newName);
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
	 * Scans the files in the project at the time of the commit.
	 *
	 * @param currentRelease : the release under analysis
	 * @param filename: considered the ID of the file within the project
	 *
	 * @return : FileMetadata instance
	 * */
	private FileMetadata getFile(String currentRelease, String filename) {
		FileMetadata fileToReturn = null;

		if(files.containsKey(currentRelease)){
			for(FileMetadata file: files.get(currentRelease)) { //Scan the files in the release
				if(filename.equals(file.getFilename())) {
					fileToReturn = file;
					break;
				}
			}
		}

		return fileToReturn;
	}

	/**
	 * Updates the global list of files divided per release.
	 *
	 * @param release: release to analyze
	 * @param file: file instance to update (add, remove, modify)
	 * */
	private void updateListOfFiles(String release, FileMetadata file) {
		files.computeIfAbsent(release, k -> new ArrayList<>()); //New release in the list: initialize
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
	private void updateFilesAge(String release, RevCommit lastCommit) {
		LocalDate lastDate = lastCommit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		for(FileMetadata file: files.get(release)){ //files added or modified in the release
			file.setAge(ChronoUnit.WEEKS.between(file.getCreation().getValue(), lastDate));	/*updates file age in terms
																							of weeks*/
		}
	}

	/**
	 * Scans the referencing commits of the bugs.
	 * If the current commit is referencing the bug, the affected versions of the bug are considered
	 * as releases in which the file modified by the commits was buggy.
	 *
	 * @param bugs : list of Bug instances
	 * @param to : current commit
	 *
	 * @return : affected versions
	 * */
	private List<String> manageFileBuggyness(List<Bug> bugs, RevCommit to) {
		Set<String> avsNotDup = new LinkedHashSet<>(); //using a Set to avoid duplicates

		for(Bug bug: bugs){
			if(bug.getFixCm().equals(to)){ //If the modification commit is related to a fix
				avsNotDup.addAll(bug.getAffectedVers());
			}

			if(bug.getReferencingCms() != null){
				for(RevCommit cm: bug.getReferencingCms()){ //check every referencing commit
					if(cm.equals(to)){
						avsNotDup.addAll(bug.getAffectedVers());
					}
				}
			}
		}

		return new ArrayList<>(avsNotDup);
	}

}
