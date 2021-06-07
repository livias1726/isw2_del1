package main.java;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;

public class Main {
	
	public static void main(String[] args) throws GitAPIException, IOException{	
		String project = "BOOKKEEPER";
	
	    //Get releases to analyze
		Map<String,LocalDate> releases = new FindReleaseInfo(project).getEveryReleaseFromGit();
		releases = new OutputFiltering().removeReleases(releases);
		
		//Order chronologically
		LinkedHashMap<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releases.entrySet().stream().sorted(Map.Entry.comparingByValue())
					  .forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		//Create a map to accept java files
		Map<String, List<String>> files = new LinkedHashMap<>();
		Iterator<String> iter = chronoReleases.keySet().iterator();
		while(iter.hasNext()) {
			files.put(iter.next(), new ArrayList<>());
		}
		
		//Populate map with file lists
		files = new FindJavaFiles(project).getClassesForReleases(chronoReleases, files);
		
		files.forEach((k,v)->System.out.println(k + " - " + v.size()));
		
	}
	
}
