package main.dataset.entity;

import java.time.LocalDate;
import java.util.*;

import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

public class FileMetadata{

	//---------------------------Attributes used as features of from which a feature is derived-------------------------
	//Incremental through releases
	private int size;
	private long age;
	private int fixCounter; 							//NFix
	private List<String> authors = new ArrayList<>(); 	//NAuth

	//Per release
	private Map<String, Map<RevCommit, Integer>> locAddedPerRevision = new LinkedHashMap<>(); 			//LOCs added: number per revision and revision per release
	private Map<String, Map<RevCommit, Integer>> locDeletedPerRevision = new LinkedHashMap<>(); 		//LOCs deleted: number per revision and revision per release
	private final Map<String, Map<RevCommit, Integer>> locModifiedPerRevision = new LinkedHashMap<>(); 	//LOCs touched: number per revision and revision per release
	private Map<String, List<RevCommit>> revisions = new LinkedHashMap<>(); 							//NR
	private Map<String, Map<RevCommit, Integer>> chgSet;												//Changing set

	//--------------------------------------------------------Label-----------------------------------------------------
	private List<String> buggynessSet = new ArrayList<>(); //Releases in which the file was buggy

	//--------------------------------------------------------Utils-----------------------------------------------------
	private String filename; 						//Used as ID
	private Pair<RevCommit, LocalDate> creation; 	//Pair of creation commit and date
	private boolean isDeleted; 						//Flag to signal a deletion (used when the same file is created again)
	private LocalDate lastModified; 				//Last modification date

	//------------------------------------------CONSTRUCTORS-------------------------------------------------

	/**
	 * Basic constructor for a new FileMetadata instance.
	 *
	 * @param filename: name of the file
	 * @param firstRel: the release in which the file was created
	 * @param createCm: the commit that added the new file
	 * @param creationDate: the creation date
	 * @param auth: the first author of the file
	 * */
	public FileMetadata(String filename, String firstRel, RevCommit createCm, LocalDate creationDate, String auth) {
		setFilename(filename);
		setCreation(createCm, creationDate);
		addRevision(firstRel, createCm);
		addAuthor(auth);
	}

	/**
	 * Constructs a new FileMetadata instance from another FileMetadata instance.
	 *
	 * @param src: the source instance
	 * */
	public FileMetadata(FileMetadata src) {
		setFilename(src.getFilename()); 									   	//Filename
		setCreation(src.getCreation().getKey(), src.getCreation().getValue()); 	//Creation date
		setSize(src.getSize()); 												//Size
		setAge(src.getAge()); 													//Age
		setLOCAddedPerRev(src.getLOCAddedPerRev());								//Lines of code added per revision
		setLOCRemovedPerRev(src.getLOCRemovedPerRev());							//Lines of code deleted per revision
		setRevisionsPerRelease(src.getRevisionsPerRelease());					//Revisions
		setFixCounter(src.getFixCounter());										//Number of fixes involving the file
		setAuthors(src.getAuthors());											//Authors for the file
		setChgSet(src.getChgSet());												//Changing set
		setDeleted(src.isDeleted());											//Deleted
		setLastModified(src.getLastModified());									//Last modified
		setBuggynessSet(src.getBuggynessSet());									//Buggyness
	}
	
	//------------------------------------------------GETTERS & SETTERS-------------------------------------------------

	//Filename
	public String getFilename() {return this.filename;}
	public void setFilename(String name) {this.filename = name;}

	//Pair of creation commit and creation date
	public Pair<RevCommit, LocalDate> getCreation() {return this.creation;}
	public void setCreation(RevCommit key, LocalDate value) { this.creation = new Pair<>(key, value);}

	//Size
	public int getSize() {return this.size;}
	public void setSize(int size) {this.size = size;}

	//Age
	public long getAge() {return this.age;}
	public void setAge(long age) {this.age = age;}	/*Used to set the age of a file that is being removed or files that
													are in a release by the end of it*/

	//Last modification date
	public LocalDate getLastModified() {return this.lastModified;}
	public void setLastModified(LocalDate date) {this.lastModified = date;}

	//Number of fixes involving the file
	public int getFixCounter() {return this.fixCounter;}
	public void setFixCounter(int fix) {this.fixCounter = fix;}

	//Flag that indicates whether the file was deleted
	public boolean isDeleted() {return this.isDeleted;}
	public void setDeleted(boolean deleted) {this.isDeleted = deleted;}

	//Revisions
	public Map<String, List<RevCommit>> getRevisionsPerRelease() {return revisions;}
	public void setRevisionsPerRelease(Map<String, List<RevCommit>> revisions) {this.revisions = revisions;}

	//List of authors that touched the file
	public List<String> getAuthors(){
		return this.authors;
	}
	public void setAuthors(List<String> list) { this.authors = new ArrayList<>(list);}

	//Lines of code added per revision
	public Map<String, Map<RevCommit, Integer>> getLOCAddedPerRev(){return this.locAddedPerRevision;}
	public void setLOCAddedPerRev(Map<String, Map<RevCommit, Integer>> map){this.locAddedPerRevision = map;}

	//Lines of code deleted per revision
	public Map<String, Map<RevCommit, Integer>> getLOCRemovedPerRev(){return this.locDeletedPerRevision;}
	public void setLOCRemovedPerRev(Map<String, Map<RevCommit, Integer>> map){this.locDeletedPerRevision = map;}

	//Set of files commit together with the file
	public Map<String, Map<RevCommit,Integer>> getChgSet(){ return this.chgSet; }
	public void setChgSet(Map<String, Map<RevCommit,Integer>> set) { this.chgSet = set; }

	//Releases in which the file was buggy
	public List<String> getBuggynessSet() {return this.buggynessSet;}
	public void setBuggynessSet(List<String> bugSet) {this.buggynessSet = bugSet;}

	//---------------------------------------------DATASET--------------------------------------------------------------
	
	//LOC_touched: sum of added, deleted and modified over revisions per release
	public int getLOCTouchedOverRevision(String release){
		int added = getLOCAddedOverRevision(release);
		int deleted = getLOCDeletedOverRevision(release);
		int modified = getLOCModifiedOverRevision(release);

		return added+deleted+modified;
	}

	//NR: number of revisions given a certain release
	public int getNumberOfRevisionsPerRelease(String release){
		if(revisions.containsKey(release)){
			return revisions.get(release).size();
		}

		return 0;
	}
	
	//NAuth
	public int getNumberOfAuthors() {
		return authors.size();
	}

	//LOC_added
	public int getLOCAddedOverRevision(String release) {
		int totAdd = 0;

		if(locAddedPerRevision.containsKey(release)){
			for(RevCommit rev: locAddedPerRevision.get(release).keySet()){
				totAdd += locAddedPerRevision.get(release).get(rev);
			}
		}

		return totAdd;
	}

	//MAX_LOC_added
	public int getMaxLOCAddedPerRelease(String release) {
		int maxAdded = 0;

		if(locAddedPerRevision.containsKey(release)){
			for(RevCommit rev: locAddedPerRevision.get(release).keySet()){
				if(locAddedPerRevision.get(release).get(rev) > maxAdded){
					maxAdded = locAddedPerRevision.get(release).get(rev);
				}
			}
		}

		return maxAdded;
	}

	//AVG_LOC_added
	public double getAvgLOCAddedPerRelease(String release) {
		double avgAdded = 0;

		if(locAddedPerRevision.containsKey(release)){
			int numRev = locAddedPerRevision.get(release).size();
			int totAdded = getLOCAddedOverRevision(release);

			avgAdded = (double)totAdded/numRev;
		}
		
		return avgAdded;
	}

	//Churn
	public int getChurnOverRevision(String release){
		int added = getLOCAddedOverRevision(release);
		int deleted = getLOCDeletedOverRevision(release);

		return Math.abs(added-deleted);
	}

	//MAX_Churn
	public int getMaxChurnPerRelease(String release) {
		int maxChurn = 0;
		int added;
		int deleted = 0;
		int churn;

		if(locAddedPerRevision.containsKey(release)){
			for(RevCommit rev: locAddedPerRevision.get(release).keySet()){
				added = locAddedPerRevision.get(release).get(rev);

				if(locDeletedPerRevision.containsKey(release) && locDeletedPerRevision.get(release).containsKey(rev)){
					deleted = locDeletedPerRevision.get(release).get(rev);
				}

				churn = Math.abs(added-deleted);
				if(churn > maxChurn){
					maxChurn = churn;
				}
			}
		}

		return maxChurn;
	}

	//AVG_Churn
	public double getAvgChurnPerRelease(String release) {
		int numTotRev = getRevisionNumberForAvgChurn(release);
		int churn = getChurnOverRevision(release);

		if(numTotRev == 0){
			return numTotRev;
		}

		return (double) churn/numTotRev;
	}

	//ChgSetSize
	public int getChgSetSizeOverRevisions(String release) {

		int totChgSet = 0;
		if(chgSet.containsKey(release)){
			for(RevCommit rev: chgSet.get(release).keySet()){
				totChgSet += chgSet.get(release).get(rev);
			}
		}

		return totChgSet;
	}

	//MAX_ChgSet
	public int getMaxChgSetSizePerRelease(String release) {

		int maxChgSet = 0;
		if(chgSet.containsKey(release)){
			for(RevCommit rev: chgSet.get(release).keySet()){
				if(chgSet.get(release).get(rev) > maxChgSet){
					maxChgSet = chgSet.get(release).get(rev);
				}
			}
		}

		return maxChgSet;
	}

	//AVG_ChgSet
	public double getAvgChgSetSizePerRelease(String release) {
		double avgChgSet = 0;
		int numRev;
		int tot = 0;

		if(chgSet.containsKey(release)){
			numRev = chgSet.get(release).size();

			for(RevCommit rev: chgSet.get(release).keySet()){
				tot += chgSet.get(release).get(rev);
			}

			avgChgSet = (double)tot/numRev;
		}

		return avgChgSet;
	}

	//-----------------------------------------------------UTILS--------------------------------------------------------

	//-----------------------------------------------------Internal-----------------------------------------------------

	//Used in 'addModification' and constructor
	private void addRevision(String release, RevCommit commit) {
		if(!revisions.containsKey(release)){
			revisions.put(release, new ArrayList<>());
		}

		if(!revisions.get(release).contains(commit)){
			revisions.get(release).add(commit);
		}
	}

	//Used in 'addModification' and constructor
	private void addAuthor(String author) {
		if(authors == null) {
			authors = new ArrayList<>();
		}
		authors.add(author);
	}

	//LOC_deleted: sum over revisions (per release) of LOCs deleted from the file
	private int getLOCDeletedOverRevision(String release) {
		int totDel = 0;
		if(locDeletedPerRevision.containsKey(release)){
			for(RevCommit rev: locDeletedPerRevision.get(release).keySet()){
				totDel += locDeletedPerRevision.get(release).get(rev);
			}
		}

		return totDel;
	}

	//LOC_modified: sum over revisions (per release) of LOCs replaced in the file
	private int getLOCModifiedOverRevision(String release) {
		int totMod = 0;
		if(locModifiedPerRevision.containsKey(release)){
			for(RevCommit rev: locModifiedPerRevision.get(release).keySet()){
				totMod += locModifiedPerRevision.get(release).get(rev);
			}
		}

		return totMod;
	}

	private int getRevisionNumberForAvgChurn(String release) {
		//Need to count every revision in both added and deleted list + every revision in only one of them
		List<RevCommit> revAdd = new ArrayList<>();
		List<RevCommit> revDel = new ArrayList<>();

		if(locAddedPerRevision.containsKey(release)){
			revAdd.addAll(locAddedPerRevision.get(release).keySet());
		}

		if(locDeletedPerRevision.containsKey(release)){
			revDel.addAll(locDeletedPerRevision.get(release).keySet());
		}

		Set<RevCommit> merged = new LinkedHashSet<>(revAdd);
		merged.addAll(revDel);

		return merged.size();
	}


	//----------------------------------------------------External------------------------------------------------------

	/**
	 * Manages a modification and eventual buggyness for a file.
	 *
	 * @param release : the release in which the modification takes place
	 * @param to : the commit that adds the modification
	 * @param modDate : the date of the modification
	 * @param auth : the author of the modification
	 * @param affectedVersions
	 *        list of affected versions related to a bug if the file was modified by a fix commit.
	 *        (If null, @param to is not a fix commit)
	 * */
	public void addModification(String release, RevCommit to, LocalDate modDate, String auth, List<String> affectedVersions) {
		setLastModified(modDate); //updates the last modification date

		//Manage new revision
		if(release != null){
			addRevision(release, to);
		}

		//Manage new author
		if(auth != null && !getAuthors().contains(auth)) {
			addAuthor(auth);
		}

		//Manage buggyness
		if(affectedVersions != null){
			for(String av: affectedVersions){
				if(revisions.containsKey(av)){ //the file was in the affected version
					fixCounter++;

					if(!buggynessSet.contains(av)){ //the class was still snoring in the affected version
						buggynessSet.add(av);
					}
				}
			}
		}
	}

	/**
	 * Manages the lists of LOCs added, deleted and modified
	 *
	 * @param release: release in which the revision takes place
	 * @param rev: commit ID
	 * @param newLoc: number of LOCs that were touched in the operation
	 * @param map: index of the map to update based on the operation (0: INSERT, 1: DELETE, 2: REPLACE)
	 * */
	public void setLOCPerRevision(String release, RevCommit rev, int newLoc, int map) {
		Map<String, Map<RevCommit, Integer>> toSet; //Map to update with new information

		switch(map) {
			case 0: //LOCs were added
				toSet = locAddedPerRevision;
				break;
			case 1: //LOCs were deleted
				toSet = locDeletedPerRevision;
				break;
			case 2: //LOCs were modified
				toSet = locModifiedPerRevision;
				break;
			default:
				return;
		}

		if(!toSet.containsKey(release)){
			toSet.put(release, new LinkedHashMap<>());
		}else if(toSet.get(release).containsKey(rev)) {
			newLoc += toSet.get(release).get(rev);
		}

		toSet.get(release).put(rev, newLoc);
	}

	/**
	 * Sets the changing set of the file per commit and per release.
	 *
	 * @param release: release of the commit
	 * @param cm: commit related to the computed changing set
	 * @param numFiles: number of files in the changing set (including the file itself)
	 * */
	public void addChgSetCommit(String release, RevCommit cm, int numFiles) {
		if(chgSet == null) {
			chgSet = new HashMap<>(); //Initialize changing set

		}else if(chgSet.containsKey(release)){
			chgSet.get(release).put(cm, numFiles-1); //-1 to exclude the file itself
			return;
		}

		chgSet.put(release, new LinkedHashMap<>()); //Initialize release's list
		chgSet.get(release).put(cm, numFiles-1);
	}
}
