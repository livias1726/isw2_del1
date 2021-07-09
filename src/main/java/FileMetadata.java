package main.java;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

public class FileMetadata{
	
	private String filename;	

	private Pair<RevCommit, LocalDate> creation;	
	private Map<RevCommit, LocalDate> modifications;
	
	private long age;
	private int size;
	private List<String> releases;
	private int fixCounter;
	private List<String> authors;
	private Map<String,Integer> locAddedPerRev;
	private Map<String,Integer> locRemovedPerRev;
	
	private boolean renamed;

	public FileMetadata(String filename, RevCommit createCm, LocalDate creationDate) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
		
		this.releases = new ArrayList<>();
		this.authors = new ArrayList<>();
	}
	
	public FileMetadata(String filename, String firstRel, RevCommit createCm, LocalDate creationDate, String auth) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
		
		this.releases = new ArrayList<>();
		this.authors = new ArrayList<>();
		
		this.releases.add(firstRel);
		this.authors.add(auth);
	}
	
	public FileMetadata(FileMetadata src) {
		this.filename = src.filename;
		this.creation = new Pair<>(src.creation.getKey(),src.creation.getValue());
		this.releases = new ArrayList<>();
		
		if(src.modifications != null) {
			Iterator<RevCommit> modif = src.modifications.keySet().iterator();
			RevCommit mod;
			while(modif.hasNext()) {
				mod = modif.next();
				this.addModification(mod, null, src.modifications.get(mod), false, null);
			}
		}
		
		for(int i=0; i<src.releases.size(); i++) {
			this.addRelease(src.releases.get(i));
		}
		
		this.setSize(src.size);
		this.fixCounter = src.fixCounter;
		
		for(int i=0; i<src.authors.size(); i++) {
			this.addAuthor(src.authors.get(i));
		}
		
		this.locAddedPerRev = src.locAddedPerRev;
		this.locRemovedPerRev = src.locRemovedPerRev;
	}

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
	
	public void addLOCPerRevision(String rev, int locAdded) {
		if(locAddedPerRev == null) {
			locAddedPerRev = new LinkedHashMap<>();
		}
		
		if(locAddedPerRev.containsKey(rev)) {
			int loc = locAddedPerRev.get(rev);
			locAddedPerRev.put(rev, loc + locAdded);
		}else {
			locAddedPerRev.put(rev, locAdded);
		}
	}
	
	public void removeLOCPerRevision(String rev, int locRemoved) {
		if(locRemovedPerRev == null) {
			locRemovedPerRev = new LinkedHashMap<>();
		}
		
		if(locRemovedPerRev.containsKey(rev)) {
			int loc = locRemovedPerRev.get(rev);
			locRemovedPerRev.put(rev, loc + locRemoved);
		}else {
			locRemovedPerRev.put(rev, locRemoved);
		}
	}

	//PARAMETERS
	public int getNumberOfReleases() {
		return this.releases.size();
	}
	
	public int getNumberOfAuthors() {
		return this.authors.size();
	}
	
	public int getSize() {
		return size;
	}
	
	public long getAge() {
		return age;
	}

	public int getFixes() {
		return fixCounter;
	}
	
	public Map<String,Integer> getLOCPerRev(){
		return this.locAddedPerRev;
	}
	
	public int getAvgLOC() {
		int totLoc = 0;
		Iterator<Integer> loc = locAddedPerRev.values().iterator();
		while(loc.hasNext()) {
			totLoc += loc.next();
		}
		
		return totLoc/locAddedPerRev.size();
		
	}
	
	public Map<String,Integer> getChurnPerRev(){
		Map<String,Integer> churn = new LinkedHashMap<>();
		
		Iterator<String> relAdd = this.locAddedPerRev.keySet().iterator();
		String currAdd;
		
		while(relAdd.hasNext()) {
			currAdd = relAdd.next();
			if(this.locRemovedPerRev != null && this.locRemovedPerRev.containsKey(currAdd)) {
				churn.put(currAdd, this.locAddedPerRev.get(currAdd) + this.locRemovedPerRev.get(currAdd));
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
			if(!churn.containsKey(currRem)) {
				churn.put(currRem, this.locAddedPerRev.get(currRem));
			}
		}
		
		return churn;
	}
	
	public int getAvgChurn() {
		int totLoc = 0;
		Map<String,Integer> churn = getChurnPerRev();
		Iterator<Integer> loc = churn.values().iterator();
		
		while(loc.hasNext()) {
			Integer i = loc.next();
			if(i != null) {
				totLoc += i;
			}
			
		}
		
		return totLoc/churn.size();
		
	}
}
