package main.java;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

/*
 * Separate this class in 2 classes for every different task!
 */
public class GitBugMatcher {
	
	private String project;
	
	public GitBugMatcher(String project) {
		this.project = project;
	}
	
	public List<RevCommit> getFixDateFromGit(List<String> bugs) throws GitAPIException {
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		
		List<RevCommit> fixes = new ArrayList<>();		
		Iterable<RevCommit> log = git.log().call();
		
		String msg;
        for(RevCommit cm: log) {
			msg = cm.getFullMessage();
			for (int i=0; i < bugs.size(); i++) {
				if(msg.contains("[" + bugs.get(i) + "]") && (msg.contains("fix") || msg.contains("Fix"))) {	  
					fixes.add(cm);	
				}
		    }	
		}	
		
		git.close();
		
		return fixes;
	}
}
