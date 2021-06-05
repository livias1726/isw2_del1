package main.java;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
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
	
	public Map<String,LocalDate> getEveryReleaseFromGit() throws GitAPIException, IOException{
		Git git = Git.init().setDirectory(new File("..\\..\\..\\..\\sources\\" + project)).call();
		List<Ref> tags = git.tagList().call();
		
		PersonIdent author;
		Date cmDate;	
		ZoneId zi = ZoneId.systemDefault();
		LogCommand log;
		
		HashMap<String,LocalDate> releases = new HashMap<>();
		for (Ref ref: tags) {
			
			if(ref.getName().contains("docker")) {
				continue;
			}	
			
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
		
		return releases;
	}
}
