package main.java;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

/*
 * Separate this class in 2 classes for every different task!
 */
public class GitBugMatcher {
	
	private String project;
	
	public GitBugMatcher(String project) {
		this.project = project;
	}
	
	/*
	 * Return a pair of (opening commit, fix commit) for every bug issue found in jira
	 */
	public Map<String, Pair<RevCommit, RevCommit>> getFixDateFromGit(List<String> bugs) throws GitAPIException {
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		RevCommit cm;
		Iterator<RevCommit> log;
		
		List<RevCommit> cmForBug;	
		Map<String, Pair<RevCommit, RevCommit>> bugsCycle = new LinkedHashMap<>();
		
		Pair<RevCommit, RevCommit> cycle;
		RevCommit open;
		RevCommit fix;
		
		for(String s: bugs) {
			cmForBug = new ArrayList<>();
			log = git.log().call().iterator();
			while(log.hasNext()) {
				cm = log.next();
				if(cm.getFullMessage().contains(s)) {
					cmForBug.add(cm);
				}
			}
			
			open = retrieveOpeningCommit(cmForBug);
			fix = retrieveFixingCommit(cmForBug);
			
			if(open == null || fix == null) {
				continue;
			}
			
			cycle = new Pair<>(open, fix);
			bugsCycle.put(s, cycle);
		}
		
		git.close();		
		return bugsCycle;
	}
	
	private RevCommit retrieveOpeningCommit(List<RevCommit> cmForBug) {
		RevCommit open = null;	
		
		ZoneId zi = ZoneId.systemDefault();
		
		LocalDate opening = null;
		LocalDate temp;
				
		for(RevCommit cm: cmForBug) {
			temp = cm.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate();
			
			if(opening == null || opening.isAfter(temp)) {
				opening = temp;
				open = cm;
			}
		}
		
		return open;
	}

	private RevCommit retrieveFixingCommit(List<RevCommit> cmForBug) {
		RevCommit fix = null;
		
		ZoneId zi = ZoneId.systemDefault();
		
		LocalDate temp;
		LocalDate closing = null;
				
		for(RevCommit cm: cmForBug) {
			temp = cm.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate();
			
			if(closing == null || closing.isBefore(temp)) {
				closing = temp;
				fix = cm;
			}
		}
		
		if(fix != null && (fix.getFullMessage().contains("fix") || fix.getFullMessage().contains("Fix"))){
			return fix;
		}
		
		return fix;
	}
}
