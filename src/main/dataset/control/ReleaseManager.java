package main.dataset.control;

import main.dataset.entity.Bug;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.*;

public class ReleaseManager {

	private static Map<String, LocalDate> releases;
	private static String[] releaseNames;
	private static LocalDate[] startDates;
	private static LocalDate[] endDates;

	private static double p; //Proportion factor

	//Instantiation
	private static ReleaseManager instance = null;

    private ReleaseManager(Map<String, LocalDate> releases) {
		ReleaseManager.releases = releases;
		ReleaseManager.releaseNames = releases.keySet().toArray(new String[0]);
		ReleaseManager.startDates = releases.values().toArray(new LocalDate[0]);

		ReleaseManager.endDates = new LocalDate[releases.size()];
		int i;
		for(i=0; i<startDates.length-1; i++){
			endDates[i] = startDates[i+1];
		}
		endDates[i] = LocalDate.parse(System.getProperty("date_limit"));
	}

    public static ReleaseManager getInstance(Map<String, LocalDate> releases) {
        if(instance == null) {
        	instance = new ReleaseManager(releases);
        }
        return instance;
    }

	//Getters
	public static String[] getReleaseNames() {
		return releaseNames;
	}

	public static LocalDate[] getStartDates() {
		return startDates;
	}

	/**
	 * Sets opening and fix version for every bug in the list, based on the ticket info set during creation.
	 *
	 * For every bug that is considered:
	 * 		the affected versions are validated, if present, and the Proportion variable ('p') is updated or used to
	 * 		compute an approximation of the affected versions.
	 *
	 * The injected version is computed in one of 2 ways:
	 * 		If the affected versions are valid, the IV is the oldest and the proportion variable is updated;
	 * 		Else, the proportion variable is used.
	 *
	 * Every bug that has the same version for injection and fix is removed from the list: if the project is analyzed
	 * at the end of that release, those bug will not be a failure in the release.
	 *
	 * @param bugs : list of bugs
	 *
	 * @return : input list updated
	 * */
	public List<Bug> analyzeBugReleases(List<Bug> bugs) {
		List<Bug> newBugs = new ArrayList<>();
		for(Bug bug: bugs){

			//Manage opening and fix version
			Bug newBug = getOpeningAndFixVersions(bug);
			//The bug is not considered if the opening and/or the fix date are not contained in the releases
			if((newBug.getOpeningVer() != null) && (newBug.getFixVer() != null)){
				//Compute injected version with Proportion
				computeInjectedVersion(newBug);

				//The bug is not considered if it is injected and fixed in the same version
				if(!newBug.getInjectedVer().equals(newBug.getFixVer())){
					if(newBug.getAffectedVers() == null){
						newBug.setAffectedVers(new ArrayList<>());
					}
					//If the bug does not have valid AVs, compute them
					if(newBug.getAffectedVers().isEmpty()){
						computeAffectedVersions(newBug);
					}

					newBugs.add(newBug); //Store the updated bug
				}
			}
		}

		return newBugs;
	}

	/**
	 * Computes the AVs for the bug using information about IV and FV.
	 * */
	private void computeAffectedVersions(Bug bug) {
		List<String> avs = bug.getAffectedVers();
		int inj = getIndexFromRelease(bug.getInjectedVer());
		int fix = getIndexFromRelease(bug.getFixVer());

		List<String> listRel = new ArrayList<>(releases.keySet());

		avs.addAll(listRel.subList(inj, fix - 1));
		bug.setAffectedVers(avs);
	}

	/**
	 * Retrieves the opening and fix versions from the opening and fix date of the ticket.
	 * */
	private Bug getOpeningAndFixVersions(Bug bug) {
		LocalDate openingDate = bug.getOpeningDate();
		LocalDate fixDate = bug.getFixDate();
		LocalDate end;
		String rel;
		LocalDate start;

		boolean openIsPresent = false;
		boolean fixIsPresent = false;

		for(int i=0; i<releaseNames.length; i++){
			rel = releaseNames[i];
			start = startDates[i];
			end = endDates[i];

			//opening date is between the start and the end of the release -> set as opening release
			if ((openingDate.isAfter(start) || openingDate.isEqual(start)) && openingDate.isBefore(end)) {
				bug.setOpeningVer(rel);
				openIsPresent = true;
			}

			//fix date is between the start and the end of the release -> set as fix release
			if ((fixDate.isAfter(start) || fixDate.isEqual(start)) && fixDate.isBefore(end)) {
				bug.setFixVer(rel);
				fixIsPresent = true;
			}

			//versions are both found
			if(openIsPresent && fixIsPresent){
				return bug;
			}
		}

		return bug;
	}

	/**
	 * Checks if the affected versions listed in the Jira ticket are coherent.
	 *
	 * @param bug : Bug instance
	 * */
	private void computeInjectedVersion(Bug bug) {

		String injVer = null;
		boolean valid = true;
		List<String> avs = bug.getAffectedVers();

		if(avs == null || avs.isEmpty()){ //NO info on AVs is present in Jira
			valid = false;
		}

		if(valid){
			for(String av: avs) {
				if(!releases.containsKey(av)){ //Version not in the released list
					bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
					valid = false;
					break;
				}
			}
		}

		if(valid && avs.contains(bug.getFixVer())){ //Fix version is in the list
			bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
			valid = false;
		}

		if(valid){
			injVer = getOlderVersion(avs);
			if (releases.get(injVer).isAfter(bug.getOpeningDate())){ //The older AV is after the OV
				bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
				valid = false;
			}
		}

		if(valid){ //Affected versions are valid: realist method to compute AVs and IV
			bug.setInjectedVer(injVer);
			updateProportion(bug);

		}else{
			injVer = retrieveInjectedVersion(bug); //Use the proportion percentage
			bug.setInjectedVer(injVer);
		}
	}

	private String getOlderVersion(List<String> versions) {
		LocalDate firstDate = null;
		String firstAv = null;

		for(String version: versions){
			if(firstDate == null || releases.get(version).isBefore(firstDate)) {
				firstAv = version;
				firstDate = releases.get(version);
			}
		}

		return firstAv;
	}

	private void updateProportion(Bug bug) {
		int iFix = getIndexFromRelease(bug.getFixVer());
		int iOpen = getIndexFromRelease(bug.getOpeningVer());
		int iInj = getIndexFromRelease(bug.getInjectedVer());

		//TODO: check if the corrective term (+1) is correct
		p = ((double)(iFix - iInj)/(iFix - iOpen + 1) + p)/2; //Mean value between the new proportion and the old
	}

	private String retrieveInjectedVersion(Bug bug) {
		int iFix = getIndexFromRelease(bug.getFixVer());
		int iOpen = getIndexFromRelease(bug.getOpeningVer());

		if(p == 0){
			p = 2;
		}

		int iInj = (int) (iFix - p*(iFix-iOpen));

		if(iInj < 1){
			iInj = 1;
		}

		return getReleaseFromIndex(iInj);
	}

	public String getReleaseFromIndex(int i) {
		for(String rel: releases.keySet()){
			if(i == 1){
				return rel;
			}

			i--;
		}
		return null;
	}

	public int getIndexFromRelease(String ver) {
		int idx = 1;
		for(String rel: releases.keySet()){
			if(ver.equals(rel)){
				return idx;
			}

			idx++;
		}

		return -1;
	}

	public void removeSecondHalfOfReleases() {
		int tot = releaseNames.length;
		int half = tot/2;

		for(int i=half; i<tot; i++){
			ArrayUtils.remove(releaseNames, i);
			ArrayUtils.remove(startDates, i);
			ArrayUtils.remove(endDates, i);
		}
	}

	/**
	 * Separates the commits retrieved in the log of the repository per release.
	 *
	 * @param commits: list of commits with relative commit date
	 *
	 * @return : commits per release
	 * */
	public Map<String, Map<RevCommit, LocalDate>> matchCommitsAndReleases(Map<RevCommit, LocalDate> commits) {
		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = new LinkedHashMap<>();

		String rel;
		LocalDate start;
		LocalDate end;
		LocalDate cmDate;

		for(int i=0; i<releaseNames.length; i++){
			rel = releaseNames[i];
			start = startDates[i];
			end = endDates[i];

			Map<RevCommit, LocalDate> cmMap = new LinkedHashMap<>();
			for(RevCommit commit: commits.keySet()){
				cmDate = commits.get(commit);

				if ((cmDate.isAfter(start) || cmDate.isEqual(start)) && cmDate.isBefore(end)) {
					cmMap.put(commit, cmDate);
				}
			}

			Map<RevCommit, LocalDate> reversedCmMap = new LinkedHashMap<>(); //commits are read from the latest to the oldest
			cmMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).
					forEachOrdered(x -> reversedCmMap.put(x.getKey(), x.getValue()));

			cmPerRelease.put(rel, reversedCmMap);
		}

		return cmPerRelease;
	}
}
