package main.java;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

public class Main {
	
	private static String project = "BOOKKEEPER";
	
	public static void main(String[] args) throws GitAPIException, IOException{	
		
		//1. Get every release with id and release date
		FindReleaseInfo fir = new FindReleaseInfo(project);
		Map<String,LocalDate> releaseDates = fir.getReleaseDates();
		
		//2. Delete newer releases
		OutputManager om = new OutputManager();
		releaseDates = om.removeRecentReleases(releaseDates);
		
		//3. Get every commit with id and release date
		Map<RevCommit,LocalDate> commitDates = fir.getCommitDates(releaseDates);
		
		//4. Match commit to release with dates
		Map<String,Map<RevCommit,LocalDate>> commitsByRelease = fir.retrieveCommitsByRelease(releaseDates, commitDates);
		
		//Populate map with file lists
		FindJavaFiles fjf = new FindJavaFiles(project);
		fjf.getClassesFromCommits(commitsByRelease);
		
		CSVManager.getInstance().fileCSV(project, fjf.getFiles());
		
	}
}
