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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import javafx.util.Pair;

/*
 * Get pairs of commits
 * Retrieve added/modified/renamed/copied files (ignore the deleted ones)
 * Separate files for every release
 * Manage files' characteristics modifications among commits
 */
public class DifferenceTreeManager {
	
	private String project;
	private Map<String, List<FileMetadata>> files = new LinkedHashMap<>();
	private Map<Pair<RevCommit, RevCommit>, List<String>> av;
	private Map<RevCommit, List<FileMetadata>> opChgSets = new LinkedHashMap<>(); //to retrieve the chgSet between opening commit and fix commit
	private boolean fix;
	private boolean opening;
	
	
	private int chgSetSize = 0;
	private List<FileMetadata> chgSet = new ArrayList<>();
	
	public DifferenceTreeManager(String projName, Map<Pair<RevCommit, RevCommit>, List<String>> av) { 
	    this.project = projName;
	    this.av = av;
	    this.fix = false;
	}
	
	private void updateChgSet(FileMetadata f) {
		if(f == null) {
			this.chgSet.clear();
			this.chgSetSize = 0;
		}else{
			this.chgSetSize++;
			this.chgSet.add(f);
		}
	}
	
	public Map<String, List<FileMetadata>> getFiles(){
		return this.files;
	}
	
	public void setFix(boolean fix) {
		this.fix = fix;
	}
	
	public void setOpening(boolean opening) {
		this.opening = opening;
	}
	
	public void setOpChgSet(RevCommit cm, List<FileMetadata> list) {
		this.opChgSets.put(cm, list);
	}
	
	/*
	 * Retrieve every file with extension .java from the commits log
	 * Usage:
	 * 		Scan the log in order and select pairs of subsequent commits
	 * 		"markFixCommit" -> set a boolean flag to label the destination commit (cmId2) as a fix one
	 * 		"computeChanges" -> compute the differences between a pair of subsequent commits
	 * 		manage the chgSet to keep track of every file committed together
	 */
	public void getFilesFromCommits(Map<String, Map<RevCommit, LocalDate>> commits) throws GitAPIException, IOException {
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		
		Iterator<String> iterRelease = commits.keySet().iterator();
		Iterator<RevCommit> iterCommit;
		
		RevCommit cmId1 = null;
		RevCommit cmId2;
		String currRel;
		
		while(iterRelease.hasNext()) {
			currRel = iterRelease.next();
			
			iterCommit = commits.get(currRel).keySet().iterator();
			while(iterCommit.hasNext()) {		
				cmId2 = iterCommit.next();
				setOpening(scanAVForOpening(cmId2));
				setFix(scanAVForFix(cmId2));
				
				computeChanges(git, currRel, cmId1, cmId2);
				
				if(opening) {
					setOpChgSet(cmId2, this.chgSet);
				}
				
				if(fix) {
					setBugginess(this.opChgSets, this.chgSet, cmId2);
				}
				updateChgSet(null);
				
				setFix(false);
				setOpening(false);
				cmId1 = cmId2;
			}
		}
	}

	private void setBugginess(Map<RevCommit, List<FileMetadata>> om, List<FileMetadata> fs, RevCommit fc) {
		Iterator<Pair<RevCommit,RevCommit>> iter = av.keySet().iterator();
		Pair<RevCommit,RevCommit> pair;
		RevCommit oc = null;
		List<String> affectedVers = null;
		while(iter.hasNext()) {
			pair = iter.next();
			if(pair.getValue().equals(fc)) {
				oc = pair.getKey();
				affectedVers = av.get(pair);
			}
		}
		
		List<FileMetadata> os = om.get(oc);
		
		List<FileMetadata> affectedFiles = new ArrayList<>();
		for(FileMetadata fi: os) {
			for(FileMetadata fe: fs) {
				if(fe.equals(fi)) {
					affectedFiles.add(fe);
				}
			}
		}
		
		for(FileMetadata f: affectedFiles) {
			f.setBuggy(affectedVers);
		}
	}

	private boolean scanAVForFix(RevCommit cm) {
		Iterator<Pair<RevCommit,RevCommit>> iter = av.keySet().iterator();
		Pair<RevCommit,RevCommit> pair;
		while(iter.hasNext()) {
			pair = iter.next();
			if(pair.getValue().equals(cm)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean scanAVForOpening(RevCommit cm) {
		Iterator<Pair<RevCommit,RevCommit>> iter = av.keySet().iterator();
		Pair<RevCommit,RevCommit> pair;
		while(iter.hasNext()) {
			pair = iter.next();
			if(pair.getKey().equals(cm)) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Scan the differences between the old tree and the new tree and manage every type of difference separately
	 * Usage:
	 * 		configure the differences formatter to scan the given repository and only java files
	 * 		retrieve differences
	 * 		manage each difference separately
	 * Called one time per commit
	 */
	private void computeChanges(Git git, String release, RevCommit from, RevCommit to) throws IOException {
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
	    
	    for(FileMetadata f: chgSet) {
			f.addChgSetCommit(release, to, this.chgSetSize);
		}
	}

	/*
	 * A file has been detected as new: addition gets triggered
	 * Usage:
	 * 		"getFile" -> check if the file already exists: if so do nothing
	 * 		create a new file object with given initial characteristics
	 * 		"computeChanges" -> manage the LOC of the file
	 * 		"insert" -> add the file to a global list related to the release
	 * 		"updateChgSet" -> manages the chgSet of the file to add at the end of the commit analysis
	 */
	private void manageAddition(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {
		ZoneId zi = ZoneId.systemDefault();
		PersonIdent pi = to.getAuthorIdent();
		LocalDate date = pi.getWhen().toInstant().atZone(zi).toLocalDate();
		FileMetadata f;
		
		Pair<String,Integer> file = getFile(diff.getNewPath());		
		if(file.getKey() != null){
			return;
		}
		
		f = new FileMetadata(diff.getNewPath(), release, to, date, pi.getName());
		
		computeChanges(f, df, diff, release);
		insert(release, f);
		updateChgSet(f);
	}
	
	/*
	 * A file has been detected as modified: modification gets triggered
	 * Usage:
	 * 		"getFile" -> check if the file already exists: if not do nothing
	 * 		retrieve the file
	 * 		"addModification" -> modify the file if the last modification was before the date of the current commit
	 * 		"computeChanges" -> manage the LOC of the file
	 * 		"insert" -> add the file to a global list related to the release
	 * 		"updateChgSet" -> manages the chgSet of the file to add at the end of the commit analysis
	 */
	private void manageModified(String release, RevCommit to, DiffFormatter df, DiffEntry diff) throws IOException {		
		Pair<String,Integer> cm = getFile(diff.getNewPath());
		if(cm.getKey() == null) {
			return;				
		}
		
		PersonIdent pi = to.getAuthorIdent();
		LocalDate date = pi.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		FileMetadata f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
		
		LocalDate lastMod = f.getLastModified();
		if(lastMod == null || lastMod.isBefore(date)) {
			f.addModification(to, release, date, fix, pi.getName());			
		}else {
			return;
		}		
		
		computeChanges(f, df, diff, release);
		insert(release, f);
		updateChgSet(f);
	}
	
	/*
	 * A file has been detected as renamed: renaming gets triggered
	 * Usage:
	 * 		"getFile" -> check if the file already exists: if not do nothing
	 * 		retrieve the file and set the new name
	 * 		"insert" -> add the file to a global list related to the release
	 * 		"updateChgSet" -> manages the chgSet of the file to add at the end of the commit analysis
	 */
	private void removeAndAdd(String release, DiffEntry diff){
		Pair<String,Integer> cm = getFile(diff.getOldPath());
		if(cm.getKey() == null) {
			return;				
		}
		
		FileMetadata f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
		if(f.getFilename().equals(diff.getNewPath())) {
			return;
		}
		f.setNewFilename(diff.getNewPath());		
		
		insert(release, f);
		updateChgSet(f);
	}
	
	/*
	 * A file has been detected as renamed: renaming gets triggered
	 * Usage:
	 * 		"getFile" -> check if the file already exists: if not do nothing
	 * 		retrieve the file and copy it
	 * 		"insert" -> add the file to a global list related to the release
	 * 		"updateChgSet" -> manages the chgSet of the file to add at the end of the commit analysis
	 */
	private void copyAndAdd(String release, DiffEntry diff) {
		Pair<String,Integer> cm = getFile(diff.getOldPath());
		if(cm.getKey() == null) {
			return;				
		}
		
		FileMetadata f = new FileMetadata(files.get(cm.getKey()).get(cm.getValue()));
		if(f.getFilename().equals(diff.getNewPath())) {
			return;
		}
		f.addCopy(diff.getNewPath());
		
		insert(release, f);
		updateChgSet(f);
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
	
	private void insert(String release, FileMetadata file) {
		if(!files.keySet().contains(release)) {
			List<FileMetadata> list = new ArrayList<>();
			files.put(release, list);
		}
		
		files.get(release).add(file);
	}

	/*
	 * Manage the LOC updates for the file
	 * Usage:
	 * 		LOC ADDED -> increment the size of the file and update the list for the "LOC added"
	 * 		LOC DELETED -> decrement the size and update the list for the "LOC deleted"
	 * 		LOC REPLACED -> update the size and perform an addition or deletion and an update for LOC touched
	 */
	private void computeChanges(FileMetadata f, DiffFormatter df, DiffEntry diff, String release) throws IOException {
		int size = f.getSize();
		FileHeader fileHeader = df.toFileHeader(diff);
		
		for(Edit edit: fileHeader.toEditList()) {
			if (edit.getType() == Type.INSERT) {
				size = size + edit.getLengthB();
				f.setLOCPerRevision(release, edit.getLengthB(), 0);
				
			} else if (edit.getType() == Type.DELETE) {
				size = size - edit.getLengthA();
				f.setLOCPerRevision(release, edit.getLengthB(), 1);
				
			} else if (edit.getType() == Type.REPLACE) {
				size = size + edit.getLengthB() - edit.getLengthA();
				
				if(edit.getLengthB() > edit.getLengthA()) {
					f.setLOCPerRevision(release, edit.getLengthB()-edit.getLengthA(), 0);
					f.setLOCPerRevision(release, edit.getLengthA(), 2);
					
				}else if(edit.getLengthB() < edit.getLengthA()) { 
					f.setLOCPerRevision(release, edit.getLengthA()-edit.getLengthB(), 1);
					f.setLOCPerRevision(release, edit.getLengthB(), 2);
					
				}else {
					f.setLOCPerRevision(release, edit.getLengthA(), 2);
				}
			}
		}
		
		f.setSize(size);
	}
}
