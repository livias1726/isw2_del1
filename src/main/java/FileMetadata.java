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
	
	private boolean deleted;
	
	//Constant
	private Pair<RevCommit, LocalDate> creation;
	
	//Relative	
	private String filename;
	private LocalDate lastModified;
	private List<String> releases = new ArrayList<>();
	private List<String> authors = new ArrayList<>();
	private int fixCounter;
	private long age;
	private int size;
	
	//Relative but internally discriminated
	private Map<String,Integer> locAddedPerRev = new LinkedHashMap<>();
	private Map<String,Integer> locRemovedPerRev = new LinkedHashMap<>();
	private Map<String,Integer> locModifiedPerRev = new LinkedHashMap<>();
	private Map<String, Map<RevCommit,Integer>> chgSet;
	private Map<String,Boolean> buggyness = new LinkedHashMap<>();
	
	//------------------------------------------CONSTRUCTORS-------------------------------------------------

	public FileMetadata(String filename, String firstRel, RevCommit createCm, LocalDate creationDate, String auth) {
		setFilename(filename);
		setCreation(createCm, creationDate);
		addRelease(firstRel);
		addAuthor(auth);
	}
	
	public FileMetadata(FileMetadata src) {
		setFilename(src.getFilename());
		setCreation(src.getCreation().getKey(), src.getCreation().getValue());
		setSize(src.getSize());
		setAge(src.getAge());
		
		setLOCAddedPerRev(src.getLOCAddedPerRev());
		setLOCRemovedPerRev(src.getLOCRemovedPerRev());
		
		setReleases(src.getReleases());
		setFixCounter(src.getFixCounter());
		setAuthors(src.getAuthors());
		
		setChgSet(src.getChgSet());
		
		setBuggyness(src.getBuggyness());
	}
	
	//------------------------------------------------GETTER & SETTER---------------------------------------------

	public String getFilename() {
		return filename;
	}
	
	public void setFilename(String name) {
		this.filename = name;
	}
	
	public Pair<RevCommit, LocalDate> getCreation() {
		return this.creation;
	}
	
	public void setCreation(RevCommit key, LocalDate value) { 
		this.creation = new Pair<>(key, value);
	}
	
	public int getSize() {
		return size;
	}
	
	public void setSize(int size) {
		this.size = size;
	}
	
	public long getAge() {
		return age;
	}
	
	public void setAge(long age) {
		this.age = age;
	}
		
	public LocalDate getLastModified() {
		return this.lastModified;
	}
	
	public void setLastModified(LocalDate date) {
		this.lastModified = date;
	}
	
	public int getFixCounter() {
		return fixCounter;
	}
	
	public void setFixCounter(int fix) {
		this.fixCounter = fix;
	}
	
	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
	
	public boolean isDeleted() {
		return this.deleted;
	}
	
	public List<String> getReleases(){
		return this.releases;
	}
	
	public void setReleases(List<String> list) {
		for(int i=0; i<list.size(); i++) {
			addRelease(list.get(i));
		}
	}
	
	public List<String> getAuthors(){
		return this.authors;
	}
	
	public void setAuthors(List<String> list) {
		for(int i=0; i<list.size(); i++) {
			addAuthor(list.get(i));
		}
	}
	
	public Map<String,Integer> getLOCAddedPerRev(){
		return this.locAddedPerRev;
	}
	
	public void setLOCAddedPerRev(Map<String,Integer> map){
		this.locAddedPerRev = map;
	}
	
	public Map<String,Integer> getLOCRemovedPerRev(){
		return this.locRemovedPerRev;
	}
	
	public void setLOCRemovedPerRev(Map<String,Integer> map){
		this.locRemovedPerRev = map;
	}
	
	public Map<String, Map<RevCommit,Integer>> getChgSet(){
		return this.chgSet;
	}
	
	public void setChgSet(Map<String, Map<RevCommit,Integer>> set) {
		this.chgSet = set;
	}
	
	public Map<String,Boolean> getBuggyness() {
		return this.buggyness;
	}
	
	public void setBuggyness(Map<String,Boolean> map) {
		this.buggyness = map;
	}
	//-----------------------------------------------------UTILS--------------------------------------------------
	
	public void addModification(String release, LocalDate modDate, boolean fix, String auth) {
		setLastModified(modDate);
		setAge(ChronoUnit.WEEKS.between(getCreation().getValue(), modDate));
		
		if(release != null && !getReleases().contains(release)) {
			addRelease(release);
		}
		
		if(fix) {
			setFixCounter(getFixCounter()+1);
		}
		
		if(auth != null && !getAuthors().contains(auth)) {
			addAuthor(auth);
		}
	}
	
	private void addRelease(String r) {
		this.releases.add(r);
	}
	
	private void addBuggyness(String release, Boolean buggy) {
		this.buggyness.put(release, buggy);
	}
	
	private void addAuthor(String pi) {
		if(this.authors == null) {
			this.authors = new ArrayList<>();
		}
		this.authors.add(pi);
	}

	public void setBuggy(List<String> affectedVers) {
		for(String rel: releases) {
			addBuggyness(rel, affectedVers.contains(rel));
		}
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

	
	//LOC touched		
	public Map<String,Integer> getLOCTouchedPerRev(){
		Map<String,Integer> locTouched = getChurnPerRev();
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
	
	//NAuth
	public int getNumberOfAuthors() {
		return this.authors.size();
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
	public Map<String,Integer> getChurnPerRev(){
		Map<String, Integer> churn = new LinkedHashMap<>();
		
		Iterator<String> iterAdditions = this.locAddedPerRev.keySet().iterator();
		String currAdd;
		
		//Iterate over LOC added to this file 
		while(iterAdditions.hasNext()) {
			currAdd = iterAdditions.next();
			churn.put(currAdd, this.locAddedPerRev.get(currAdd));
		}
		
		if(this.locRemovedPerRev == null) {
			return churn;
		}
		
		Iterator<String> iterRemoved = this.locRemovedPerRev.keySet().iterator();
		String currRem;
			
		while(iterRemoved.hasNext()) {
			currRem = iterRemoved.next();
			if(churn.containsKey(currRem)) {
				int base = churn.get(currRem);
				churn.put(currRem, base+this.locRemovedPerRev.get(currRem));
			}else {
				churn.put(currRem, this.locRemovedPerRev.get(currRem));
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
}
