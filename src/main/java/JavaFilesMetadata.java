package main.java;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class JavaFilesMetadata {
	
	private String filename;
	private List<String> oldNames = new ArrayList<>();

	private LocalDate creation;
	private LocalDate lastModified;
	private LocalDate deletion;
	
	private long age;
	private int size;
	
	public JavaFilesMetadata(String filename, LocalDate creation) {
		this.filename = filename;
		this.creation = creation;
		this.lastModified = creation;
	}

	public String getFilename() {
		return filename;
	}
	
	public void setFilename(String filename) {
		this.filename = filename;
	}

	public LocalDate getCreation() {
		return creation;
	}

	public LocalDate getLastModified() {
		return lastModified;
	}

	public void setLastModified(LocalDate lastModified) {
		this.lastModified = lastModified;
		this.age = ChronoUnit.WEEKS.between(creation, lastModified);
	}
	
	public LocalDate getDeletion() {
		return deletion;
	}

	public void setDeletion(LocalDate deletion) {
		this.deletion = deletion;
		this.age = ChronoUnit.WEEKS.between(creation, deletion);
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
	
	public List<String> getOldNames() {
		return oldNames;
	}

	public void addOldName(String oldNames) {
		this.oldNames.add(oldNames);
	}
}
