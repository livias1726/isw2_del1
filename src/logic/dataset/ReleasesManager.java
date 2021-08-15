package logic.dataset;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

public class ReleasesManager {

	private String project;
	private String path;
	
	private static ReleasesManager instance = null;

    private ReleasesManager(String projName) {
    	this.project = projName;
	    this.path = "..\\..\\..\\sources\\" + project;
    }

    public static ReleasesManager getInstance(String projName) {
        if(instance == null) {
        	instance = new ReleasesManager(projName);
        }

        return instance;
    }

	public Map<String,LocalDate> getReleaseDates() throws GitAPIException, IOException{
		Git git = Git.init().setDirectory(new File(path)).call();
		List<Ref> tags = git.tagList().call();
		
		ZoneId zi = ZoneId.systemDefault();
		LogCommand log;
		
		Map<String,LocalDate> releases = new LinkedHashMap<>();
		Ref peeledRef;
		
		for (Ref ref: tags) {
			log = git.log();
			
			peeledRef = git.getRepository().peel(ref);
	        if(peeledRef.getPeeledObjectId() != null) {
	            log.add(peeledRef.getPeeledObjectId());
	        } else {
	            log.add(ref.getObjectId());
	        }

			releases.put(ref.getName(), log.call().iterator().next().getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate());
		}
		
		git.close();
		
		return releases;
	}

	public Map<RevCommit, LocalDate> getCommitDates(Map<String, LocalDate> releaseDates) throws GitAPIException {
		LocalDate limit = getLimit(releaseDates);
		
		Git git = Git.init().setDirectory(new File(path)).call();
		Iterable<RevCommit> log = git.log().call();
		Iterator<RevCommit> iter = log.iterator();
		
		LocalDate cmDate;	
		ZoneId zi = ZoneId.systemDefault();
		
		Map<RevCommit, LocalDate> commits = new LinkedHashMap<>();
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
		Map<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releaseDates.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		return chronoReleases.values().iterator().next();
	}
	
	public List<Pair<String,LocalDate>> getCommits() throws GitAPIException, IOException{
		Git git = Git.init().setDirectory(new File(path)).call();
		List<Ref> tagList = git.tagList().call();
		ZoneId zi = ZoneId.systemDefault();
		List<Pair<String,LocalDate>> result = new ArrayList<>();
		
		LogCommand log;
		for (Ref ref: tagList) {
			log = git.log();
			
			Ref peeledRef = git.getRepository().peel(ref);
	        if(peeledRef.getPeeledObjectId() != null) {
	        	log.add(peeledRef.getPeeledObjectId());
	        } else {
	        	log.add(ref.getObjectId());
	        }
	        
	        result.add(new Pair<>(ref.getName(), log.call().iterator().next().getAuthorIdent().getWhen().toInstant().atZone(zi).toLocalDate()));
		}
		
		git.close();		
		return result;
	}
	
	public Map<String, Map<RevCommit, LocalDate>> getCommitsByRelease(Map<String, LocalDate> releaseDates, Map<RevCommit, LocalDate> commitDates){
		Map<String, Map<RevCommit, LocalDate>> cmVer = new LinkedHashMap<>();
		
		//Order chronologically
		Map<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releaseDates.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		Map<RevCommit, LocalDate> chronoCommits = new LinkedHashMap<>();	 
		commitDates.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEachOrdered(x -> chronoCommits.put(x.getKey(), x.getValue()));
		
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
				if((rDate1 == null && (cDate.isBefore(rDate2) || cDate.isEqual(rDate2))) 
			    || (rDate1 != null && cDate.isAfter(rDate1) && (cDate.isBefore(rDate2)) || cDate.isEqual(rDate2))) {
					cms.put(com, cDate);
				}
			}
			
			rDate1 = rDate2;
			cmVer.put(rel, cms);
		}
		
		return cmVer;
	}
	
	public Map<String, LocalDate> removeRecentReleases(Map<String, LocalDate> releases) {
		Map<String, LocalDate> chronoReleases = new LinkedHashMap<>();	 
		releases.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEachOrdered(x -> chronoReleases.put(x.getKey(), x.getValue()));
		
		Map<String, LocalDate> avlb = new LinkedHashMap<>();
		int total = chronoReleases.size()/2;
		Iterator<String> iter = chronoReleases.keySet().iterator();
		
		String r;
		while(iter.hasNext() && total != 0) {
			r = iter.next();
			avlb.put(r, chronoReleases.get(r));
			total--;
		}
		
		return avlb;
	}
}
