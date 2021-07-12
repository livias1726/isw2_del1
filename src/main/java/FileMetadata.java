package main.java;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

public class FileMetadata{
	
	private String filename;	

	private Pair<RevCommit, LocalDate> creation;	
	private Map<RevCommit, LocalDate> modifications = new LinkedHashMap<>();
	
	private long age;
	private int size;
	private List<String> releases = new ArrayList<>();
	private int fixCounter;
	private List<String> authors = new ArrayList<>();
	
	private Map<String,Integer> locAddedPerRev = new LinkedHashMap<>();
	private Map<String,Integer> locRemovedPerRev = new LinkedHashMap<>();
	private Map<String,Integer> locModifiedPerRev = new LinkedHashMap<>();
	
	private Map<String, Map<RevCommit,Integer>> chgSet;
	
	private boolean renamed;

	public FileMetadata(String filename, RevCommit createCm, LocalDate creationDate) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
	}
	
	public FileMetadata(String filename, String firstRel, RevCommit createCm, LocalDate creationDate, String auth) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
		
		this.releases.add(firstRel);
		this.authors.add(auth);
	}
	
	public FileMetadata(FileMetadata src) {
		this.filename = src.filename;
		this.creation = new Pair<>(src.creation.getKey(),src.creation.getValue());
		
		//Size
		this.setSize(src.size);
		
		//LOC touched + LOC added + AVG LOC added + Churn + AVG Churn
		this.locAddedPerRev = src.locAddedPerRev;
		this.locRemovedPerRev = src.locRemovedPerRev;
		
		//NR
		for(int i=0; i<src.releases.size(); i++) {
			this.addRelease(src.releases.get(i));
		}
		
		//NFix
		this.setFixNumber(src.fixCounter);
		
		//NAuth
		for(int i=0; i<src.authors.size(); i++) {
			this.addAuthor(src.authors.get(i));
		}
				
		//ChgSetSize + AVG ChgSet -> fixed over same release/same file -> pass by reference
		this.chgSet = src.chgSet;
		
		//WeightedAge
		if(src.modifications != null) {
			Iterator<RevCommit> modif = src.modifications.keySet().iterator();
			RevCommit mod;
			while(modif.hasNext()) {
				mod = modif.next();
				this.addModification(mod, null, src.modifications.get(mod), false, null);
			}
		}
		
	}
	
	//-----------------------------------------------------UTILS--------------------------------------------------

	//FILENAME
	public String getFilename() {
		return filename;
	}
	
	public void setNewFilename(String filename) {
		if(this.renamed) {
			this.filename = filename;
		}else {
			this.filename = filename;
			this.renamed = true;
		}
	}
	
	//CREATION
	public LocalDate getCreationDate() {
		return this.creation.getValue();
	}
	
	public RevCommit getCreationCommit() {
		return this.creation.getKey();
	}
	
	//MODIFICATIONS
	public void addModification(RevCommit modCm, String release, LocalDate modDate, boolean fix, String auth) {
		if(this.modifications == null) {
			this.modifications = new LinkedHashMap<>();
		}
		
		this.modifications.put(modCm, modDate);		
		this.age = ChronoUnit.WEEKS.between(this.creation.getValue(), modDate);
		
		if(release != null && !this.releases.contains(release)) {
			this.releases.add(release);
		}
		
		if(fix) {
			fixCounter++;
		}
		
		if(auth != null && !this.authors.contains(auth)) {
			this.authors.add(auth);
		}
	}

	public LocalDate getLastModified() {
		if(modifications == null) {
			return null;
		}
		
		Iterator<RevCommit> iter = modifications.keySet().iterator();
		RevCommit curr = null;
		while(iter.hasNext()) {
			curr = iter.next();
		}
		
		return this.modifications.get(curr);
	}
	
	public void setSize(int size) {
		this.size = size;
	}
	
	public void setFixNumber(int fix) {
		this.fixCounter = fix;
	}

	public void addCopy(String filename) {
		this.filename = filename;
		this.renamed = true;
	}
	
	private void addRelease(String r) {
		this.releases.add(r);
	}
	
	private void addAuthor(String pi) {
		if(this.authors == null) {
			this.authors = new ArrayList<>();
		}
		this.authors.add(pi);
	}
	
	public void setLOCPerRevision(String rev, int add, int map) {
		Map<String,Integer> toSet;
		switch(map) {
			case 0:
				toSet = this.locAddedPerRev;
				break;
			case 1:
				toSet = this.locRemovedPerRev;
				break;
			case 2:
				toSet = this.locModifiedPerRev;
				break;
			default:
				return;
		}
		
		if(toSet.containsKey(rev)) {
			int loc = toSet.get(rev);
			toSet.put(rev, loc + add);
		}else {
			toSet.put(rev, add);
		}
	}
	
	public void addChgSetCommit(String release, RevCommit cm, int numFiles) {
		if(this.chgSet == null) {
			this.chgSet = new HashMap<>();
		}else if(this.chgSet.containsKey(release)){
			this.chgSet.get(release).put(cm, numFiles-1); //-1 to exclude the file itself
			return;
		}
		
		this.chgSet.put(release, new HashMap<>());
		this.chgSet.get(release).put(cm, numFiles-1);
	}

	//----------------------------------------------FEATURES-----------------------------------------------------
	
	//Size
	public int getSize() {
		return size;
	}
			
	//LOC touched		
	public Map<String,Integer> getLOCTouchedPerRev(){
		Map<String,Integer> locTouched = getChurnPerRev(1);
		if(this.locModifiedPerRev == null) {
			return locTouched;
		}
		
		Iterator<String> rel = this.locModifiedPerRev.keySet().iterator();
		String curr;
		int base;
			
		while(rel.hasNext()) {
			curr = rel.next();
			
			if(locTouched.containsKey(curr)) {
				base = locTouched.get(curr);
				locTouched.put(curr, base + (this.locModifiedPerRev.get(curr)));
			}else {
				locTouched.put(curr, this.locModifiedPerRev.get(curr));
			}	
		}
		
		return locTouched;
	}
	
	//NR
	public int getNumberOfReleases() {
		return this.releases.size();
	}

	//NFix
	public int getFixes() {
		return fixCounter;
	}
			
	//NAuth
	public int getNumberOfAuthors() {
		return this.authors.size();
	}
			
	//LOC added + AVG LOC added
	public Map<String,Integer> getLOCAddedPerRev(){
		return this.locAddedPerRev;
	}
	
	public int getAvgLOCAdded() {
		int totLoc = 0;
		Iterator<Integer> loc = locAddedPerRev.values().iterator();
		while(loc.hasNext()) {
			totLoc += loc.next();
		}
		
		return totLoc/locAddedPerRev.size();
		
	}
			
	//Churn + AVG Churn
	public Map<String,Integer> getChurnPerRev(int i){
		Map<String,Integer> churn = new LinkedHashMap<>();
		
		Iterator<String> relAdd = this.locAddedPerRev.keySet().iterator();
		String currAdd;
		
		while(relAdd.hasNext()) {
			currAdd = relAdd.next();
			if(this.locRemovedPerRev != null && this.locRemovedPerRev.containsKey(currAdd)) {
				churn.put(currAdd, this.locAddedPerRev.get(currAdd) + i*(this.locRemovedPerRev.get(currAdd)));
			}else {
				churn.put(currAdd, this.locAddedPerRev.get(currAdd));
			}
		}
		
		if(this.locRemovedPerRev == null) {
			return churn;
		}
		
		Iterator<String> relRem = this.locRemovedPerRev.keySet().iterator();
		String currRem;
			
		while(relRem.hasNext()) {
			currRem = relRem.next();
			
			final String rel = currRem;
			churn.computeIfAbsent(rel, 
					f -> churn.put(rel, i*(this.locRemovedPerRev.get(rel))));
				
		}
		
		return churn;
	}
	
	public int getAvgChurn() {
		int totLoc = 0;
		Map<String,Integer> churn = getChurnPerRev(-1);
		Iterator<Integer> loc = churn.values().iterator();
		
		while(loc.hasNext()) {
			Integer i = loc.next();
			if(i != null) {
				totLoc += i;
			}
			
		}
		
		return totLoc/churn.size();
		
	}
					
	//ChgSetSize + AVG ChgSet
	public int getChgSetSize(String rel) {
		int avgPerRel = 0;
		int totCm = 0;
		
		if(!this.chgSet.containsKey(rel)) {
			return 0;
		}
		
		Iterator<RevCommit> iter = this.chgSet.get(rel).keySet().iterator();
		while(iter.hasNext()) {
			totCm++;
			avgPerRel += this.chgSet.get(rel).get(iter.next());
		}
		
		if(totCm != 0) {
			return avgPerRel/totCm;
		}else {
			return 0;
		}
	}
	
	public int getAvgChgSetSize() {
		int avg = 0;
		int tot = 0;
		
		Iterator<String> iterRel = this.chgSet.keySet().iterator();
		Iterator<RevCommit> iterCm;
		String rel;
		while(iterRel.hasNext()) {
			rel = iterRel.next();
			iterCm = this.chgSet.get(rel).keySet().iterator();
			while(iterCm.hasNext()) {
				tot++;
				avg += this.chgSet.get(rel).get(iterCm.next());
			}
		}
		
		if(tot != 0) {
			return avg/tot;
		}else {
			return 0;
		}
	}
			
	//WeightedAge
	public long getAge() {
		return age;
	}
}
