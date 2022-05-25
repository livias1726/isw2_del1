package main.dataset.control;

import javafx.util.Pair;
import main.dataset.entity.Bug;
import main.utils.LoggingUtils;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.*;

/**
 * Controller class.
 *
 * Manages every information related to versions, including computation for
 * the injected version, the opening version and the fix version of every bug found in Jira.
 * */
public class ReleaseManager {

	private static Map<String, LocalDate> releases;
	private static String[] releaseNames;
	private static LocalDate[] startDates;
	private static LocalDate[] endDates;

	private static double p; //Proportion factor

	//Instantiation
	private static ReleaseManager instance = null;

    private ReleaseManager() {/**/}

    public static ReleaseManager getInstance() {
        if(instance == null) {
        	instance = new ReleaseManager();
        }
        return instance;
    }

	//Getters & Setters
	public static void setReleases(Map<String, LocalDate> releasesList){
		releases = releasesList;
		releaseNames = releases.keySet().toArray(new String[0]);
		startDates = releases.values().toArray(new LocalDate[0]);

		endDates = new LocalDate[releases.size()];
		int i;
		for(i=0; i<startDates.length-1; i++){
			endDates[i] = startDates[i+1];
		}
		endDates[i] = LocalDate.parse(System.getProperty("date_limit"));
	}

	public String[] getReleaseNames() {
		return releaseNames;
	}

	public static void setProportion(double newP){ p = newP; }

	//-------------------------------------------Functionalities-----------------------------------------------------
	/**
	 * Sets opening and fix version for every bug in the list, based on the ticket info set during creation.
	 *
	 * @param bugs : list of bugs
	 *
	 * @return : input list updated
	 * */
	public List<Bug> analyzeOpeningAndFix(List<Bug> bugs) {
		List<Bug> tempBugs = new ArrayList<>(bugs);

		Pair<String, String> vers;
		for(Bug bug: bugs) {
			vers = getOpeningAndFixVersions(bug); //manage opening and fix version
			if ((vers.getKey() != null) && (vers.getValue() != null)) {	/*The bug is not considered if the
																					opening and/or the fix date are not
																					contained in the releases*/
				bug.setOpeningVer(vers.getKey());
				bug.setFixVer(vers.getValue());
			}else{
				tempBugs.remove(bug);
			}
		}

		return tempBugs;
	}

	/**
	 * Retrieves the opening and fix versions from the opening and fix date of the ticket.
	 *
	 * @param bug : instance for which retrieve the opening and fix versions
	 *
	 * @return : opening and fix version
	 * */
	private Pair<String, String> getOpeningAndFixVersions(Bug bug) {
		LocalDate openingDate = bug.getOpeningDate();
		LocalDate fixDate = bug.getFixDate();
		LocalDate end;
		String rel;
		LocalDate start;

		String openingVers = null;
		String fixVers = null;

		for(int i=0; i<releaseNames.length; i++){
			rel = releaseNames[i];
			start = startDates[i];
			end = endDates[i];

			//opening date is between the start and the end of the release
			if ((openingDate.isAfter(start) || openingDate.isEqual(start)) && openingDate.isBefore(end)) {
				openingVers = rel;
			}

			//fix date is between the start and the end of the release
			if ((fixDate.isAfter(start) || fixDate.isEqual(start)) && fixDate.isBefore(end)) {
				fixVers = rel;
			}

			//versions are both found
			if(openingVers != null && fixVers != null){
				return new Pair<>(openingVers, fixVers);
			}
		}

		return new Pair<>(openingVers, fixVers);
	}

	/**
	 * Sets opening and fix version for every bug in the list, based on the ticket info set during creation.
	 *
	 * For every bug that is considered, the affected versions, if present, are validated and
	 * the Proportion variable 'p' is updated or used to compute an approximation of the affected versions.
	 *
	 * The injected version is computed in one of 2 ways:
	 * 		- If the affected versions are valid, the IV is the oldest and the proportion variable is updated;
	 * 		- Else, the proportion variable is used.
	 *
	 * Every bug that has the same version for injection and fix is removed from the list:
	 * if the project is analyzed at the end of that release, those bugs will not be a failure in the release.
	 *
	 * @param bugs : list of bugs
	 *
	 * @return : input list updated
	 * */
	public List<Bug> analyzeBugInfection(List<Bug> bugs) {
		List<Bug> valid = new ArrayList<>();
		List<Bug> invalid = new ArrayList<>();

		for(Bug bug: bugs){ //divide bugs with valid AVs and bugs with invalid AVs
			if(hasValidAVs(bug)){
				bug.setInjectedVer(getOldestVersion(bug.getAffectedVers())); //set injected version for bugs with valid affected versions
				valid.add(bug);
			}else{
				invalid.add(bug);
			}
		}

		LoggingUtils.logInt("Number of issues with valid affected versions: ", valid.size());
		LoggingUtils.logInt("Number of issues with invalid affected versions: ", invalid.size());

		Map<String, List<Bug>> validOrderedByFix = getBugsByRelease(valid);
		Map<String, List<Bug>> invalidOrderedByFix = getBugsByRelease(invalid);
		invalidOrderedByFix.remove(releaseNames[0]); //bugs fixed in the first release don't affect other releases

		invalid = computeProportion(invalidOrderedByFix, validOrderedByFix);
		valid.addAll(invalid);

		return valid;
	}

	/**
	 * Computes the AVs of the tickets fixed on release R with the proportion of the tickets
	 * that has valid AVs from release 1 to R-1.
	 *
	 * @param invalid : list of tickets to compute the AVs of
	 * @param valid : list of tickets with valid AVs
	 *
	 * @return : list of tickets with updated AVs
	 * */
	private List<Bug> computeProportion(Map<String, List<Bug>> invalid, Map<String, List<Bug>> valid) {
		List<Bug> bugs = new ArrayList<>();

		for(Map.Entry<String, List<Bug>> invEntry: invalid.entrySet()){
			String currRel = invEntry.getKey();

			//Proportion computation
			List<Bug> usedForProportion = new ArrayList<>();
			for(Map.Entry<String, List<Bug>> validEntry: valid.entrySet()){
				if(validEntry.getKey().equals(currRel)){ //when the scan reaches the release to analyze update proportion
					setProportion(updateProportion(usedForProportion));
					break;
				}

				usedForProportion.addAll(validEntry.getValue());
			}

			//IV + AVs computation
			for(Bug bug: invEntry.getValue()){
				bug.setInjectedVer(computeInjectedVersion(bug)); //use the updated proportion to get IV

				if(!bug.getInjectedVer().equals(bug.getFixVer())){	/*The bug is not considered if it is
																	injected and fixed in the same version*/
					bug.setAffectedVers(computeAffectedVersions(bug));
					bugs.add(bug);
				}
			}
		}

		return bugs;
	}

	/**
	 * Maps the bugs on the release in which they were fixed.
	 *
	 * @param bugs : list of bugs
	 *
	 * @return : the bugs mapped on their fix release
	 * */
	private Map<String, List<Bug>> getBugsByRelease(List<Bug> bugs) {
		Map<String, List<Bug>> bugsByRelease = new LinkedHashMap<>();

		for(String rel: releaseNames){
			List<Bug> list = new ArrayList<>();

			for(Bug bug: bugs){
				if(rel.equals(bug.getFixVer())){
					list.add(bug);
				}
			}

			bugsByRelease.put(rel, list);
		}

		return bugsByRelease;
	}

	/**
	 * Computes the proportion factor with a list of bugs.
	 *
	 * @param bugs : list of bugs with valid AVs
	 *
	 * @return : proportion factor
	 * */
	private double updateProportion(List<Bug> bugs) {
		double iFix;
		double iOpen;
		double iInj;

		double prop = 0;
		if(bugs.isEmpty()){
			return prop;
		}

		for(Bug bug: bugs){
			iFix = getIndexFromRelease(bug.getFixVer());
			iOpen = getIndexFromRelease(bug.getOpeningVer());
			iInj = getIndexFromRelease(bug.getInjectedVer());

			if(iFix - iOpen != 0){ //if opening version and fix version are the same, discard the bug
				prop += (iFix - iInj)/(iFix - iOpen);
			}
		}

		return prop/bugs.size();
	}

	/**
	 * Computes the affected versions for the bug using information about injected version and fix version.
	 *
	 * @param bug : instance for which the affected versions are computed
	 *
	 * @return : affected versions
	 * */
	private List<String> computeAffectedVersions(Bug bug) {

		int inj = getIndexFromRelease(bug.getInjectedVer());
		int fix = getIndexFromRelease(bug.getFixVer());

		List<String> listRel = new ArrayList<>(releases.keySet());
		return new ArrayList<>(listRel.subList(inj-1, fix-1)); //indexes retrieved start from 1 by construction
	}

	/**
	 * Checks if the affected versions listed in the Jira ticket are coherent.
	 *
	 * @param bug : Bug instance
	 *
	 * @return : flag for affected versions validity
	 * */
	private boolean hasValidAVs(Bug bug) {
		List<String> avs = bug.getAffectedVers();

		if(avs == null || avs.isEmpty()){ //NO info on AVs is present in Jira
			return false;
		}

		for(String av: avs) {
			if(!releases.containsKey(av)){ //Version not in the released list
				bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
				return false;
			}
		}

		String injVer = getOldestVersion(avs);
		if (releases.get(injVer).isAfter(bug.getOpeningDate())){ //The older AV is after the OV
			bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
			return false;
		}

		if(avs.contains(bug.getFixVer())){ //Fix version is in the list
			bug.setAffectedVers(new ArrayList<>()); //Clean up the AVs list
			return false;
		}

		return true;
	}

	/**
	 * Gets the oldest (that was first released) version between a list of versions.
	 *
	 * @param versions : list of versions
	 *
	 * @return : oldest version
	 * */
	public String getOldestVersion(List<String> versions) {
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

	/**
	 * Uses the computed value for Proportion to calculate the injected version of a Bug.
	 *
	 * @param bug : the instance to compute the injected version of
	 *
	 * @return : injected version
	 * */
	private String computeInjectedVersion(Bug bug) {
		int iOpen = getIndexFromRelease(bug.getOpeningVer());
		int iFix = getIndexFromRelease(bug.getFixVer());
		int iInj = (int) Math.floor(iFix - (p*(iFix-iOpen)));

		if(iInj < 1){
			iInj = 1;
		}

		return getReleaseFromIndex(iInj);
	}

	/**
	 * Retrieves a release from its index in the releases list.
	 *
	 * @param i : index
	 *
	 * @return : release
	 * */
	private String getReleaseFromIndex(int i) {
		for(String rel: releases.keySet()){
			if(i == 1){
				return rel;
			}
			i--;
		}
		return null;
	}

	/**
	 * Retrieves the index of a release in the releases list.
	 *
	 * @param ver : release
	 *
	 * @return : index
	 * */
	private int getIndexFromRelease(String ver) {
		int idx = 1;
		for(String rel: releases.keySet()){
			if(ver.equals(rel)){
				return idx;
			}
			idx++;
		}

		return -1;
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
		RevCommit commit;

		for(int i=0; i<releaseNames.length; i++){
			rel = releaseNames[i];
			start = startDates[i];
			end = endDates[i];

			Map<RevCommit, LocalDate> cmMap = new LinkedHashMap<>();
			for(Map.Entry<RevCommit, LocalDate> entry: commits.entrySet()){
				cmDate = entry.getValue();
				commit = entry.getKey();

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
