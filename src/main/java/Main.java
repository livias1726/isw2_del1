package main.java;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

public class Main {
	
	private static String project = "BOOKKEEPER";
	
	public static void main(String[] args) throws GitAPIException, IOException{	
		
		FindReleaseInfo fir = new FindReleaseInfo(project);
		Map<String,LocalDate> releaseDates = fir.getReleaseDates();
		
		OutputManager om = new OutputManager();
		releaseDates = om.removeRecentReleases(releaseDates);
		
		Map<RevCommit,LocalDate> commitDates = fir.getCommitDates(releaseDates);

		Map<String,Map<RevCommit,LocalDate>> commitsByRelease = fir.retrieveCommitsByRelease(releaseDates, commitDates);
		
		//Populate map with file lists
		FindJavaFiles fjf = new FindJavaFiles(project);
		fjf.getClassesFromCommits(commitsByRelease);
	}
}
