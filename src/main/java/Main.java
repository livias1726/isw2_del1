package main.java;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

public class Main {
	
	private static String project = "BOOKKEEPER";
	
	public static void main(String[] args) throws GitAPIException, IOException{	
		
		//Match commits and releases
		Map<String,Map<RevCommit,LocalDate>> commitsByRelease = commitsRetrieval();
		
		//JIRA + GIT MANAGEMENT
		List<RevCommit> fixCm = fixesRetrieval();
		
		//FILE RETRIEVAL
		FindJavaFiles fjf = new FindJavaFiles(project, fixCm);
		fjf.getClassesFromCommits(commitsByRelease);
		
		CSVManager.getInstance().fileCSV(project, fjf.getFiles());
	}
	
	public static Map<String,Map<RevCommit,LocalDate>> commitsRetrieval() throws GitAPIException, IOException{
		FindReleaseInfo fir = new FindReleaseInfo(project);
		Map<String,LocalDate> releaseDates = fir.getReleaseDates();
		
		OutputManager om = new OutputManager();
		releaseDates = om.removeRecentReleases(releaseDates);
		
		Map<RevCommit,LocalDate> commitDates = fir.getCommitDates(releaseDates);
		
		return fir.retrieveCommitsByRelease(releaseDates, commitDates);
	}
	
	public static List<RevCommit> fixesRetrieval() throws IOException, GitAPIException{
		
		boolean done = true;
		int i = 0;
		int j;
		
		List<String> res;
		List<String> bugs = new ArrayList<>();
	    
		JiraInspector jira;
		GitBugMatcher git = new GitBugMatcher(project);
		
		while(done) {
			j = i + 50;
			String jiraUrl = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" 
					  + project
					  + "%22AND%22issuetype%22=%22Bug%22AND%22status%22=%22Resolved%22&fields=key,resolutiondate&startAt="
					  + i + "&maxResults=" + j;
			
			jira = new JiraInspector(jiraUrl);
			
			
			res = jira.retrieveKeysFromJira(i, j);
			if(res == null) {
				done = false;
				continue;
			}
			
			i += res.size();
			bugs.addAll(res);
		}
		
		return git.getFixDateFromGit(bugs);
	}
	
}
