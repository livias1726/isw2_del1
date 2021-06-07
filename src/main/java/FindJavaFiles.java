package main.java;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;

/*
 * Get releases list
 * 	For every release create a new list: file java in every commit for that release
 * */
public class FindJavaFiles {
	
	private String project;
	
	public FindJavaFiles(String projName) { 
	    this.project = projName;
	}
	
	public Map<String, List<String>> getClassesForReleases(Map<String, LocalDate> map, Map<String, List<String>> files) throws GitAPIException, IOException{
		
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		Iterable<RevCommit> logs = git.log().call();
		
		PersonIdent author;
		Date cmDate;
		LocalDate lDate;
		ZoneId zi = ZoneId.systemDefault();
		
		Iterator<Map.Entry<String, LocalDate>> itr;
		Map.Entry<String, LocalDate> entry1;
		Map.Entry<String, LocalDate> entry2;
		
		TreeWalk treeWalk = new TreeWalk(git.getRepository());
		RevTree tree;
		
		//Get every (new) file in every commit for every release
		for(RevCommit cm: logs) {
			author = cm.getAuthorIdent();
			cmDate = author.getWhen();
			lDate = cmDate.toInstant().atZone(zi).toLocalDate();
			
			itr = map.entrySet().iterator();
			entry1 = itr.next();
			
			while(itr.hasNext()){ 
				entry2 = itr.next();
	            if(lDate.isAfter(entry1.getValue()) && lDate.isBefore(entry2.getValue())) {
	            	tree = cm.getTree();           		
            		treeWalk.addTree(tree);
            		treeWalk.setRecursive(true);
            		treeWalk.setFilter(PathSuffixFilter.create(".java"));
            		
            		while (treeWalk.next()) {
            			addFileToVers(entry1.getKey(), treeWalk.getPathString(), files);          			
            		}	
	            }
	            entry1 = entry2;
	        }
		}
		
		git.close();
		
		return files;
	}

	private void addFileToVers(String version, String filename, Map<String, List<String>> files) {
		if(!files.get(version).contains(filename)) {
			files.get(version).add(filename);
		}
	}
}
