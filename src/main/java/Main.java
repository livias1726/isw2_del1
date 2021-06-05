package main.java;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;

public class Main {
	
	public static void main(String[] args) throws GitAPIException, IOException{	
		String project = "BOOKKEEPER";
		
		Map<String,LocalDate> releases = new FindReleaseInfo(project).getEveryReleaseFromGit();

		OutputFiltering out = new OutputFiltering();
		releases = out.removeReleases(releases);
	}
	
}
