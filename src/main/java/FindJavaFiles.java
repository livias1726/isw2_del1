package main.java;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import javafx.util.Pair;

/*
 * Get releases list
 * 	For every release create a new list: file java in every commit for that release
 * */
public class FindJavaFiles {
	
	private String project;
	private Map<String, List<FileMetadata>> files = new LinkedHashMap<>();
	private List<RevCommit> fixCm;
	private boolean fix;
	
	public FindJavaFiles(String projName, List<RevCommit> fixes) { 
	    this.project = projName;
	    this.fixCm = fixes;
	    this.fix = false;
	}
	
	public void getClassesFromCommits(Map<String, Map<RevCommit, LocalDate>> commitsByRelease) throws GitAPIException, IOException {
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		
		Iterator<String> releases = commitsByRelease.keySet().iterator();
		Iterator<RevCommit> commits;
		
		RevCommit cmId1 = null;
		RevCommit cmId2 = null;
		String currRel;
		while(releases.hasNext()) {
			currRel = releases.next();
			commits = commitsByRelease.get(currRel).keySet().iterator();
			
			while(commits.hasNext()) {
				cmId2 = commits.next();			
				fix = markFixCommit(cmId2);
				
				getChangesFromCommit(git, currRel, cmId1, cmId2);
				
				fix = false;			
				cmId1 = cmId2;
			}
		}
	}

	private boolean markFixCommit(RevCommit cmId2) {
		for(RevCommit c: fixCm) {
			if(c.equals(cmId2)) {
				return true;
			}
		}
		
		return false;
	}

	private void getChangesFromCommit(Git git, String release, RevCommit from, RevCommit to) throws IOException {
		DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
		diffFormatter.setRepository(git.getRepository());
		diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
		diffFormatter.setPathFilter(PathSuffixFilter.create(".java"));
		diffFormatter.setDetectRenames(true);
		
	    List<DiffEntry> diffs;
		
		if(from == null) {
			AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
			ObjectReader reader = git.getRepository().newObjectReader();
			AbstractTreeIterator newTreeIter = new CanonicalTreeParser(null, reader, to.getTree());
			
			diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
		}else {
			diffs = diffFormatter.scan(from.getTree(), to.getTree());
		}
		
	    for (DiffEntry diff : diffs) {
	    	switch(diff.getChangeType()) {
	    		case ADD:
		    		manageAddition(release, to, diffFormatter, diff);
		    		break;
		    	case MODIFY:
		    		manageModified(release, to, diffFormatter, diff);
		    		break;
		    	case RENAME:
		    		removeAndAdd(release, diff);
		    		break;
		    	case COPY:
		    		copyAndAdd(release, diff);
		    		break;
	    		default:
	    			break;
		    }
	    }
	}
	
	private void manageAddition(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		ZoneId zi = ZoneId.systemDefault();
		LocalDate date = to.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate();
		FileMetadata f;
		
		//Check existence
		Pair<String,Integer> file = getFile(diff.getNewPath());		
		if(file.getKey() != null /*&& !files.get(file.getKey()).get(file.getValue()).isDeleted()*/){
			return;
		}
		
		f = new FileMetadata(diff.getNewPath(), release, to, date);		
		
		computeChanges(f, df, diff);
		insert(release, f);
	}
	
	private void manageModified(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		//RETRIEVE
		Pair<String,Integer> cm = getFile(diff.getNewPath());
		
		FileMetadata f;
		ZoneId zi = ZoneId.systemDefault();	
		LocalDate date = to.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate();
		//LOG
		if(cm.getKey() != null) {
			f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
			LocalDate lastMod = f.getLastModified();
			if(lastMod == null || !lastMod.equals(date)) {
				f.addModification(to, release, date, fix);			
			}else {
				return;
			}					
		}else {	
			return;
		}
		
		computeChanges(f, df, diff);
		insert(release, f);
	}
	
	private void removeAndAdd(String release, DiffEntry diff){
		//RETRIEVE
		Pair<String,Integer> cm = getFile(diff.getOldPath());
		
		FileMetadata f;
		//LOG
		if(cm.getKey() != null) {
			f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
			if(f.getFilename().equals(diff.getNewPath())) {
				return;
			}
			f.setNewFilename(diff.getNewPath());			
		}else {
			return;
		}
		
		insert(release, f);
	}
	
	private void copyAndAdd(String release, DiffEntry diff) {
		//RETRIEVE
		Pair<String,Integer> cm = getFile(diff.getOldPath());
		
		FileMetadata f;
		
		//LOG
		if(cm.getKey() != null) {
			f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
			if(f.getFilename().equals(diff.getNewPath())) {
				return;
			}
			f.addCopy(diff.getNewPath());
		}else {	
			return;
		}
		
		insert(release, f);
	}
	
	private void insert(String release, FileMetadata file) {
		Iterator<String> iter = files.keySet().iterator();
		while(iter.hasNext()) {
			if(release.equals(iter.next())) {
				files.get(release).add(file);
				return;
			}
		}
		
		List<FileMetadata> list = new ArrayList<>();
		list.add(file);
		files.put(release, list);
	}

	private void computeChanges(FileMetadata f, DiffFormatter df, DiffEntry diff) throws IOException {
		int size = f.getSize();
		
		FileHeader fileHeader = df.toFileHeader(diff);
		for(Edit edit: fileHeader.toEditList()) {
			if (edit.getType() == Type.INSERT) {
				size = size + edit.getLengthB();
			} else if (edit.getType() == Type.DELETE) {
				size = size - edit.getLengthA();
			} else if (edit.getType() == Type.REPLACE) {
				size = size - edit.getLengthA();
				size = size + edit.getLengthB();
			}
		}
		
		f.setSize(size);
	}

	private Pair<String, Integer> getFile(String filename) {
		Iterator<String> iter = files.keySet().iterator();
		
		int i = 0;
		Integer idx = null;		
		String rel = null;
		String currRel;
		
		while(iter.hasNext()) {
			currRel = iter.next();			
			for(FileMetadata j: files.get(currRel)) {
				if(filename.equals(j.getFilename())) {
					rel = currRel;
					idx = i;
				}
				i++;
			}
			
			i = 0;
		}
		
		return new Pair<>(rel, idx);
	}
	
	public Map<String, List<FileMetadata>> getFiles(){
		return files;
	}
}
