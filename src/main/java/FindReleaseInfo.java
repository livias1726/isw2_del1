package main.java;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

public class FindReleaseInfo {

	private String project;
	
	public FindReleaseInfo(String projName) { 
	    this.project = projName;
	}

	public Map<String,LocalDate> getReleaseDates() throws GitAPIException, IOException{
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		List<Ref> tags = git.tagList().call();
		
		PersonIdent author;
		Date cmDate;	
		ZoneId zi = ZoneId.systemDefault();
		LogCommand log;
		
		HashMap<String,LocalDate> releases = new HashMap<>();
		for (Ref ref: tags) {
			log = git.log();
			
			Ref peeledRef = git.getRepository().peel(ref);
	        if(peeledRef.getPeeledObjectId() != null) {
	            log.add(peeledRef.getPeeledObjectId());
	        } else {
	            log.add(ref.getObjectId());
	        }

	        Iterable<RevCommit> logs = log.call();
	        
	        RevCommit cm = logs.iterator().next();
	        author = cm.getAuthorIdent();
			cmDate = author.getWhen();	
			
			releases.put(ref.getName().substring(18), cmDate.toInstant().atZone(zi).toLocalDate());
		}
		
		git.close();
		
		return releases;
	}

	public Map<RevCommit, LocalDate> getCommitDates(Map<String, LocalDate> releaseDates) throws GitAPIException {
		LocalDate limit = getLimit(releaseDates);
		
		Git git = Git.init().setDirectory(new File("..\\..\\..\\sources\\" + project)).call();
		Iterable<RevCommit> log = git.log().call();
		Iterator<RevCommit> iter = log.iterator();
		
		LocalDate cmDate;	
		ZoneId zi = ZoneId.systemDefault();
		
		Map<RevCommit, LocalDate> commits = new HashMap<>();
		RevCommit cm;
		while(iter.hasNext()) {
			cm = iter.next();
			cmDate = cm.getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate();	
			if(cmDate.isBefore(limit)) {
				commits.put(cm, cmDate);
			}
		}
	       
		git.close();
		
		return commits;
	}

	private LocalDate getLimit(Map<String, LocalDate> releaseDates) {
		//Order chronologically
		LinkedHashMap<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releaseDates.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
					  .forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		return chronoReleases.values().iterator().next();
	}

	public Map<String, Map<RevCommit, LocalDate>> retrieveCommitsByRelease(Map<String, LocalDate> releaseDates, Map<RevCommit, LocalDate> commitDates) {
		Map<String, Map<RevCommit, LocalDate>> cmVer = new LinkedHashMap<>();
		
		//Order chronologically
		LinkedHashMap<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releaseDates.entrySet().stream().sorted(Map.Entry.comparingByValue())
					  .forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		LinkedHashMap<RevCommit, LocalDate> chronoCommits = new LinkedHashMap<>();	 
		commitDates.entrySet().stream().sorted(Map.Entry.comparingByValue())
					  .forEachOrdered(x -> chronoCommits.put(x.getKey(), x.getValue()));
		
		//Iterate over releases and commits
		Iterator<String> releases = chronoReleases.keySet().iterator();
		Iterator<RevCommit> commits;
		
		String rel;
		RevCommit com;
		LocalDate rDate1;
		LocalDate rDate2;
		LocalDate cDate;
		
		rDate1 = null;
		while(releases.hasNext()) {
			Map<RevCommit, LocalDate> cms = new LinkedHashMap<>();
			
			rel = releases.next();
			rDate2 = chronoReleases.get(rel);
			
			commits = chronoCommits.keySet().iterator();
			while(commits.hasNext()) {
				com = commits.next();
				cDate = chronoCommits.get(com);
				if((rDate1 == null && (cDate.isBefore(rDate2) || cDate.isEqual(rDate2)))|| (rDate1 != null && cDate.isAfter(rDate1) && (cDate.isBefore(rDate2)) || cDate.isEqual(rDate2))) {
					cms.put(com, cDate);
				}
			}
			
			rDate1 = rDate2;
			cmVer.put(rel, cms);
		}
		
		return cmVer;
	}

}
