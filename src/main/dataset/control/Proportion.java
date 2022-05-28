package main.dataset.control;

import main.dataset.entity.Bug;
import main.utils.LoggingUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Proportion {

    private static double p = 0.68;
    private final String[] projects = {"AVRO", "STORM", "ZOOKEEPER", "SYNCOPE", "TAJO"};

    private static Proportion instance = null;

    private Proportion() throws IOException, GitAPIException {
        if(p == 0){
            coldStart();
        }
    }

    public static Proportion getInstance() throws IOException, GitAPIException {
        if(instance == null){
            instance = new Proportion();
        }

        return instance;
    }

    public void setProportion(double pro){
        p = pro;
    }

    public void coldStart() throws IOException, GitAPIException {
        ReleaseManager relMan = ReleaseManager.getInstance();
        JiraManager jira;
        GitManager git;
        List<Bug> bugs;
        Map<RevCommit, LocalDate> commits;
        List<Bug> valid;

        double tempP = 0;

        for(String projName: projects){
            //JIRA
            jira = JiraManager.getInstance(projName);
            ReleaseManager.setReleases(jira.getProjectVersions());
            bugs = jira.getFixes();
            LoggingUtils.logInt(projName + " initial bugs: ", bugs.size());

            //GIT
            git = GitManager.getInstance(projName);
            commits = git.getCommits(); //list of every commit in the project
            LoggingUtils.logInt(projName + " total commits: ", commits.size());

            bugs = git.manageBugCommits(bugs, commits); //manage list of commits linked to a jira fix ticket
            bugs = git.removeUnreferencedBugs(bugs); //must be after the linkage computation
            bugs = git.processFixCommitInfo(bugs);

            //RELEASES
            bugs = relMan.analyzeOpeningAndFix(bugs);
            LoggingUtils.logInt(projName + " before validation bugs: ", bugs.size());

            valid = new ArrayList<>();
            for(Bug bug: bugs){ //divide bugs with valid AVs and bugs with invalid AVs
                if(relMan.hasValidAVs(bug)){
                    bug.setInjectedVer(relMan.getOldestVersion(bug.getAffectedVers())); //set injected version for bugs with valid affected versions
                    valid.add(bug);
                }
            }

            LoggingUtils.logInt(projName + " valid bugs: ", valid.size());
            tempP += updateProportion(valid);
            LoggingUtils.logDouble("The proportion with " + projName + " is: ", tempP);
        }

        setProportion(tempP/projects.length);
        LoggingUtils.logDouble("The proportion after cold start is: ", p);
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
    public List<Bug> computeProportion(Map<String, List<Bug>> invalid, Map<String, List<Bug>> valid) {
        List<Bug> bugs = new ArrayList<>();

        for(Map.Entry<String, List<Bug>> invEntry: invalid.entrySet()){
            String currRel = invEntry.getKey();

            //Proportion computation
            List<Bug> usedForProportion = new ArrayList<>();
            for(Map.Entry<String, List<Bug>> validEntry: valid.entrySet()){
                if(validEntry.getKey().equals(currRel)){ //when the scan reaches the release to analyze update proportion
                    p = updateProportion(usedForProportion);
                    break;
                }

                usedForProportion.addAll(validEntry.getValue());
            }

            ReleaseManager relMan = ReleaseManager.getInstance();
            //IV + AVs computation
            for(Bug bug: invEntry.getValue()){
                bug.setInjectedVer(relMan.computeInjectedVersion(bug, p)); //use the updated proportion to get IV

                if(!bug.getInjectedVer().equals(bug.getFixVer())){	/*The bug is not considered if it is
																	injected and fixed in the same version*/
                    bug.setAffectedVers(relMan.computeAffectedVersions(bug));
                    bugs.add(bug);
                }
            }
        }

        return bugs;
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

        ReleaseManager relMan = ReleaseManager.getInstance();
        for(Bug bug: bugs){
            iFix = relMan.getIndexFromRelease(bug.getFixVer());
            iOpen = relMan.getIndexFromRelease(bug.getOpeningVer());
            iInj = relMan.getIndexFromRelease(bug.getInjectedVer());

            if(iFix - iOpen != 0){ //if opening version and fix version are the same, discard the bug
                prop += (iFix - iInj)/(iFix - iOpen);
            }
        }

        return prop/bugs.size();
    }

}
