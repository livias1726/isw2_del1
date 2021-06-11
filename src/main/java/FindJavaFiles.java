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
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/*
 * Get releases list
 * 	For every release create a new list: file java in every commit for that release
 * */
public class FindJavaFiles {
	
	private String project;
	
	private static Map<RevCommit, List<JavaFilesMetadata>> addedFiles = new LinkedHashMap<>();
	private static Map<RevCommit, List<JavaFilesMetadata>> modifiedFiles = new LinkedHashMap<>();
	private static Map<RevCommit, List<JavaFilesMetadata>> deletedFiles = new LinkedHashMap<>();
	private static Map<RevCommit, List<JavaFilesMetadata>> renamedFiles = new LinkedHashMap<>();
	
	public FindJavaFiles(String projName) { 
	    this.project = projName;
	}
	
	public void getClassesFromCommits(Map<String, Map<RevCommit, LocalDate>> commitsByRelease) throws GitAPIException, IOException {
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		
		Iterator<Map<RevCommit, LocalDate>> commits = commitsByRelease.values().iterator();		
		Iterator<RevCommit> cmList;
		Map<RevCommit, LocalDate> cm;
		RevCommit cmId1 = null;
		RevCommit cmId2 = null;

		
		while(commits.hasNext()) {
			cm = commits.next();
			cmList = cm.keySet().iterator();
			while(cmList.hasNext()) {
				cmId2 = cmList.next();
				getChangesFromCommit(git, cmId1, cmId2);
				System.out.println(addedFiles.size() + " " + modifiedFiles.size() + " " + deletedFiles.size() + " " + renamedFiles.size());
				cmId1 = cmId2;
			}
		}
	}

	private void getChangesFromCommit(Git git, RevCommit from, RevCommit to) throws IOException {
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
		    	case COPY:
		    		insertInAdded(to, diffFormatter, diff);
		    		break;
		    	case MODIFY:
		    		insertInModified(to, diffFormatter, diff);
		    		break;
		    	case DELETE:
		    		insertInDeleted(to, diff);
		    		break;
		    	case RENAME:
		    		insertInRenamed(to, diff);
		    		break;
		    	/*case COPY:
		    		insertInCopied(to, diff);
		    		break;*/
		    }
	    }
	}
	
	private void insertInAdded(RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		ZoneId zi = ZoneId.systemDefault();
		JavaFilesMetadata f = new JavaFilesMetadata(diff.getNewPath(), to.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate());
		
		FileHeader fileHeader = df.toFileHeader(diff);
		for(Edit edit: fileHeader.toEditList()) {
			if((edit.getBeginA() == edit.getEndA()) && (edit.getBeginB() < edit.getEndB())) { //Insertion
				f.setSize(edit.getEndB()-edit.getBeginB());
			}
		}
		
		Iterator<RevCommit> iter = addedFiles.keySet().iterator();
		while(iter.hasNext()) {
			if(to.equals(iter.next())) {
				addedFiles.get(to).add(f);
				return;
			}
		}
		
		List<JavaFilesMetadata> list = new ArrayList<>();
		list.add(f);
		addedFiles.put(to, list);
	}
	
	private void insertInModified(RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		
		JavaFilesMetadata f = checkInModified(diff.getNewPath());	
		if(f == null) {
			f = checkInAdded(diff.getNewPath());
		}
		
		ZoneId zi = ZoneId.systemDefault();
		
		if(f != null) {
			f.setLastModified(to.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate());
			
			computeChanges(f, df, diff);
			Iterator<RevCommit> iter = modifiedFiles.keySet().iterator();
			while(iter.hasNext()) {
				if(to.equals(iter.next())) {
					modifiedFiles.get(to).add(f);
					return;
				}
			}
			
			List<JavaFilesMetadata> list = new ArrayList<>();
			list.add(f);
			modifiedFiles.put(to, list);
		}else {
			throw new IOException("Added file not found");
		}
	}

	private void insertInDeleted(RevCommit to, DiffEntry diff) throws IOException {
		JavaFilesMetadata f = checkInModified(diff.getOldPath());
		if(f == null) {
			f = checkInRenamed(diff.getOldPath());
			if(f == null) {
				f = checkInAdded(diff.getOldPath());
			}
		}
		
		ZoneId zi = ZoneId.systemDefault();
		if(f != null) {
			f.setDeletion(to.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate());
			Iterator<RevCommit> iter = deletedFiles.keySet().iterator();
			while(iter.hasNext()) {
				if(to.equals(iter.next())) {
					deletedFiles.get(to).add(f);
					return;
				}
			}
			
			List<JavaFilesMetadata> list = new ArrayList<>();
			list.add(f);
			deletedFiles.put(to, list);
		}else {
			throw new IOException("File not found for deletion");
		}
		
	}

	private void insertInRenamed(RevCommit to, DiffEntry diff) throws IOException {		
		JavaFilesMetadata f = checkInModified(diff.getOldPath());
		if(f == null) {
			f = checkInRenamed(diff.getOldPath());
			if(f == null) {
				f = checkInAdded(diff.getOldPath());
			}
		}
		
		if(f != null) {
			f.addOldName(f.getFilename());
			f.setFilename(diff.getNewPath());
			Iterator<RevCommit> iter = renamedFiles.keySet().iterator();
			while(iter.hasNext()) {
				if(to.equals(iter.next())) {
					renamedFiles.get(to).add(f);
					return;
				}
			}
			
			List<JavaFilesMetadata> list = new ArrayList<>();
			list.add(f);
			renamedFiles.put(to, list);
		}else {
			throw new IOException("File not found for renaming");
		}
	}

	/*private void insertInCopied(RevCommit to, DiffEntry diff) throws IOException {
		JavaFilesMetadata f = checkInModified(diff.getOldPath());
		if(f == null) {
			f = checkInRenamed(diff.getOldPath());
			if(f == null) {
				f = checkInAdded(diff.getOldPath());
			}
		}
		
		if(f != null) {
			f.setFilename(diff.getNewPath());
			
			Iterator<RevCommit> iter = addedFiles.keySet().iterator();
			while(iter.hasNext()) {
				if(to.equals(iter.next())) {
					addedFiles.get(to).add(f);
					return;
				}
			}
			
			List<JavaFilesMetadata> list = new ArrayList<>();
			list.add(f);
			addedFiles.put(to, list);
		}else {
			throw new IOException("File not found");
		}
	}*/

	private void computeChanges(JavaFilesMetadata f, DiffFormatter df, DiffEntry diff) throws IOException {
		int size = f.getSize();
		
		FileHeader fileHeader = df.toFileHeader(diff);
		for(Edit edit: fileHeader.toEditList()) {
			if((edit.getBeginA() == edit.getEndA()) && (edit.getBeginB() < edit.getEndB())) { //Insertion
				size = size + edit.getEndB()-edit.getBeginB();
			}
			
			if((edit.getBeginA() < edit.getEndA()) && (edit.getBeginB() == edit.getEndB())) { //Remotion
				size = size - (edit.getEndA()-edit.getBeginA());
			}
			
			if((edit.getBeginA() < edit.getEndA()) && (edit.getBeginB() < edit.getEndB())) { //Replacement
				//LOC TOUCHED
			}
			
			f.setSize(size);
		}
	}

	private JavaFilesMetadata checkInAdded(String filename) {
		Iterator<RevCommit> iter = addedFiles.keySet().iterator();
		JavaFilesMetadata ret = null;
		RevCommit curr;
		
		while(iter.hasNext()) {
			curr = iter.next();
			for(JavaFilesMetadata j: addedFiles.get(curr)) {
				if(filename.equals(j.getFilename())) {
					ret = j;
				}
			}
		}
		
		return ret;
	}

	private JavaFilesMetadata checkInModified(String filename) {
		Iterator<RevCommit> iter = modifiedFiles.keySet().iterator();
		JavaFilesMetadata ret = null;
		RevCommit curr;
		
		while(iter.hasNext()) {
			curr = iter.next();
			for(JavaFilesMetadata j: modifiedFiles.get(curr)) {
				if(filename.equals(j.getFilename())) {
					ret = j;
				}
			}
		}
		
		return ret;
	}
	
	private JavaFilesMetadata checkInRenamed(String filename) {
		Iterator<RevCommit> iter = renamedFiles.keySet().iterator();
		JavaFilesMetadata ret = null;
		RevCommit curr;
		
		while(iter.hasNext()) {
			curr = iter.next();
			for(JavaFilesMetadata j: renamedFiles.get(curr)) {
				if(filename.equals(j.getFilename())) {
					ret = j;
				}
			}
		}
		
		return ret;
	}
}
