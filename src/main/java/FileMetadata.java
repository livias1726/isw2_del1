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
	
	private boolean renamed;

	public FileMetadata(String filename, RevCommit createCm, LocalDate creationDate) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
		
		this.releases = new ArrayList<>();
	}
	
	public FileMetadata(String filename, String firstRel, RevCommit createCm, LocalDate creationDate) {
		this.filename = filename;
		this.creation = new Pair<>(createCm, creationDate);
		
		this.releases = new ArrayList<>();
		this.releases.add(firstRel);
	}
	
	public FileMetadata(FileMetadata src) {
		FileMetadata newF = new FileMetadata(src.filename, src.creation.getKey(), src.creation.getValue());
		
		if(src.modifications != null) {
			Iterator<RevCommit> modif = src.modifications.keySet().iterator();
			RevCommit mod;
			while(modif.hasNext()) {
				mod = modif.next();
				newF.addModification(mod, null, src.modifications.get(mod));
			}
		}
		
		for(int i=0; i<src.releases.size(); i++) {
			newF.addRelease(src.releases.get(i));
		}
		
		newF.setSize(src.size);
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
	public void addModification(RevCommit modCm, String release, LocalDate modDate) {
		if(this.modifications == null) {
			this.modifications = new LinkedHashMap<>();
		}
		
		this.modifications.put(modCm, modDate);		
		this.age = ChronoUnit.WEEKS.between(this.creation.getValue(), modDate);
		
		if(!this.releases.contains(release) && release != null) {
			releases.add(release);
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

	//PARAMETERS
	public int getNumberOfReleases() {
		return this.releases.size();
	}
	
	public int getSize() {
		return size;
	}
	
	public long getAge() {
		return age;
	}

	private void addRelease(String r) {
		this.releases.add(r);
	}

}