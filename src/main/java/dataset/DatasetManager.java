package main.java.dataset;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;
import main.java.CSVManager;

public class DatasetManager {
	
	private String project;
	private Map<String, Integer> releasesId = new LinkedHashMap<>();
	
	private static DatasetManager instance = null;

    private DatasetManager(String projectName) {
    	this.project = projectName;
    }

    public static DatasetManager getInstance(String projectName) {
        if(instance == null) {
        	instance = new DatasetManager(projectName);
        }

        return instance;
    }
    
	public Pair<String, Set<String>> getDataset() throws GitAPIException, IOException {	
		
		//Match commits and releases
		ReleasesManager relMan = ReleasesManager.getInstance(project);
		Map<String, Map<RevCommit, LocalDate>> commits = releasesRetrieval(relMan);
	
		//JIRA + GIT MANAGEMENT
		Map<String, Pair<RevCommit, RevCommit>> fixCommits = fixesRetrieval();
		
		//Affected Versions
		Map<Pair<RevCommit, RevCommit>, List<String>> affectedVersions = retrieveAffectedVersions(fixCommits, commits);
		
		//FILE RETRIEVAL
		DifferenceTreeManager fjf = new DifferenceTreeManager(project, affectedVersions);
		fjf.getFilesFromCommits(commits);
		
		String dataset = CSVManager.getInstance().getDataset(project, fjf.getFiles());
		
		return new Pair<>(dataset, commits.keySet());
	}
	
	/*
	 * Get all the commits related to the project alongside their release.
	 * Store the releases with indexes.
	 * 
	 * Usage
	 * 		ReleasesManager: retrieve every release in the project
	 * 						 delete the latest half of the found releases/commits
	 * 						 match the commits with the respective release
	 */
	public Map<String, Map<RevCommit, LocalDate>> releasesRetrieval(ReleasesManager relMan) throws GitAPIException, IOException{
		Map<String, LocalDate> releases = relMan.getReleaseDates();	
		releases = relMan.removeRecentReleases(releases);
		Iterator<String> rel = releases.keySet().iterator();
		while(rel.hasNext()) {
			releasesId.put(rel.next(), releasesId.size()+1);
		}
		Map<RevCommit, LocalDate> commits = relMan.getCommitDates(releases);	
		
		return relMan.getCommitsByRelease(releases, commits);
	}
	
	/*
	 * Get every jira issue marked as resolved bug and match the commit from git
	 * 
	 * Usage
	 * 		JiraInspector: get every jira issue (tag) marked as resolved bug
	 * 		GitBugMatcher: find the commits related to the found issues
	 */
	public Map<String, Pair<RevCommit, RevCommit>> fixesRetrieval() throws IOException, GitAPIException{
		
		int i = 0;
		int j;
		
		List<String> res;
		List<String> bugs = new ArrayList<>();
	    
		JiraInspector jira;
		GitBugMatcher git = new GitBugMatcher(project);
		
		while(true) {
			j = i + 50;
			String jiraUrl = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" 
							  + project
							  + "%22AND%22issuetype%22=%22Bug%22AND(%22status%22=%22Resolved%22OR%22status%22=%22Closed%22)"
							  + "AND%22resolution%22=%22Fixed%22"
							  + "&startAt=" + i + "&maxResults=" + j;
			
			
			jira = new JiraInspector(jiraUrl);
			
			res = jira.retrieveKeysFromJira(i, j);
			if(res == null) {
				break;
			}
			
			i += res.size();
			bugs.addAll(res);
		}
		
		return git.getFixDateFromGit(bugs);
	}
	
	/*
	 * Get every affected version using the increment proportion method	
	 */
	public Map<Pair<RevCommit, RevCommit>, List<String>> retrieveAffectedVersions(Map<String, Pair<RevCommit, RevCommit>> fixes, Map<String, Map<RevCommit, LocalDate>> releases) {
		//commits related to bugs fixed
		Iterator<String> iterBugs = fixes.keySet().iterator(); //iterator over jira issues to retrieve the opening commit and the fix commit
		Pair<RevCommit, RevCommit> currCycle; //keep track of the current bug lifecycle
		Integer ovIdx = null; //keep track of the opening version index
		Integer fvIdx = null; //keep track of the fixing version index
		
		//commits related to releases
		Iterator<String> iterReleases; //iterator over the available releases to retrieve the release related to the opening and fix commit
		Iterator<RevCommit> iterCommit; //iterator over the commits related to a specific release
		RevCommit currCm; //keep track of the current commit
		String currRel; //keep track of the current release
		
		//affected versions
		Map<Pair<RevCommit, RevCommit>, List<String>> av = new LinkedHashMap<>();
		List<List<String>> recordAV = new ArrayList<>();
		
		//ITERATION
		while(iterBugs.hasNext()) { //JIRA ISSUES
			currCycle = fixes.get(iterBugs.next());
			
			iterReleases = releases.keySet().iterator();
			while(iterReleases.hasNext()) { //RELEASES
				currRel = iterReleases.next();
				
				iterCommit = releases.get(currRel).keySet().iterator();
				while(iterCommit.hasNext()) { //COMMITS
					currCm = iterCommit.next();
					
					if(currCycle.getKey().equals(currCm)) {
						ovIdx = releasesId.get(currRel);
					}
					
					if(currCycle.getValue().equals(currCm)) {
						fvIdx = releasesId.get(currRel);
					}
				}
			}
			
			List<String> avList = computeAffectedList(recordAV, ovIdx, fvIdx);
			av.put(currCycle, avList);
			recordAV.add(avList);
		}
		
		return av;
	}

	private List<String> computeAffectedList(List<List<String>> recordAV, Integer opening, Integer fix) {
		List<String> list = new ArrayList<>();
		
		if(opening == null || fix == null) {
			return list;
		}
		
		Proportion prop = Proportion.getInstance();
		if(recordAV.isEmpty()) {
			prop.setP(1);
		}else {
			double propVar = prop.getAVPercentage(recordAV);
			prop.setP(propVar);
		}
		
		Integer injection = prop.getInjectedVersion(opening, fix);	
		
		String release = null;
		String r;
		Iterator<String> rel;
		
		for(int i=injection; i<fix; i++) {
			
			rel = releasesId.keySet().iterator();
			
			while(rel.hasNext()) {
				r = rel.next();
				if(i == releasesId.get(r)) {
					release = r;
				}
			}
			
			if(release != null) {
				list.add(release);
			}
		}
		
		return list;
	}
}
