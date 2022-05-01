package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import main.utils.CSVManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

/**
 * Controller class.
 *
 * Retrieves information to create the dataset.
 * */
public class DatasetManager {
	
	private final String project;

	//Instantiation
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

	/**
	 * Creates the dataset with information about:
	 * 		- Bugs fixed (Jira)
	 * 		- Commits (Git)
	 * 		- Every java file that is and was part of the project
	 *
	 * @return : dataset filename and project releases
	 * */
	public Pair<String, String[]> getDataset() throws GitAPIException, IOException {
		Logger logger = Logger.getLogger(project);

		StringBuilder stringBuilder = new StringBuilder();
		String log;

		//--------------------------------------------------JIRA--------------------------------------------------------

		JiraManager jira = JiraManager.getInstance(project);
		ReleaseManager relMan = ReleaseManager.getInstance(); //Get releases
		ReleaseManager.setReleases(jira.getProjectVersions());

		/*LOG*/
		stringBuilder.append("Releases: ");
		stringBuilder.append(Arrays.toString(relMan.getReleaseNames()));
		log = stringBuilder.toString();
		logger.info(log);

		List<Bug> bugs = jira.getFixes(); //Get list of jira fix tickets

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Total number of Jira tickets retrieved: " + bugs.size());
		log = stringBuilder.toString();
		logger.info(log);

		//---------------------------------------------------GIT--------------------------------------------------------

		GitManager git = GitManager.getInstance(project);
		Map<RevCommit, LocalDate> commits = git.getCommits(); //Get list of every commit in the project

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Total number of commits retrieved: " + commits.size());
		log = stringBuilder.toString();
		logger.info(log);

		bugs = git.manageBugCommits(bugs, commits); //Manage list of commits linked to a jira fix ticket

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Project linkage is: " + getBugLinkage(commits.size(), bugs)*100);
		log = stringBuilder.toString();
		logger.info(log);

		bugs = git.removeUnreferencedBugs(bugs); //Must be after the linkage computation

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Number of bugs referenced: " + bugs.size());
		log = stringBuilder.toString();
		logger.info(log);

		bugs = git.processFixCommitInfo(bugs);

		//-------------------------------------------------RELEASES-----------------------------------------------------

		bugs = relMan.analyzeOpeningAndFix(bugs); //get opening and fix versions

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Number of bugs with valid opening and fix version: ");
		stringBuilder.append(bugs.size());
		log = stringBuilder.toString();
		logger.info(log);

		bugs = relMan.analyzeBugInfection(bugs); //get injected and affected versions

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Number of bugs with complete information: " + bugs.size() + "\nBUGS:");
		for(Bug bug: bugs){
			stringBuilder.append("\n\t").append(bug.getTicketKey()).
					append(": {Injected: ").append(bug.getInjectedVer()).
					append(", Opening: ").append(bug.getOpeningVer()).
					append(", Fix: ").append(bug.getFixVer()).
					append(", Affected: ").append(bug.getAffectedVers()).
					append("}");
		}
		log = stringBuilder.toString();
		logger.info(log);

		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = relMan.matchCommitsAndReleases(commits);

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"\nCOMMITS PER RELEASE:");
		for(String rel: relMan.getReleaseNames()){
			stringBuilder.append("\n\t").append(rel).
					append(" -> ").append(cmPerRelease.get(rel).keySet().size());
		}
		log = stringBuilder.toString();
		logger.info(log);

		//--------------------------------------------------FILES-------------------------------------------------------

		DifferenceTreeManager dt = DifferenceTreeManager.getInstance(project, bugs);
		Map<String, List<FileMetadata>> files = dt.analyzeFilesEvolution(cmPerRelease);

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"\nFILES PER RELEASE:");
		for(Map.Entry<String, List<FileMetadata>> entry: files.entrySet()){
			stringBuilder.append("\n\t").append(entry.getKey()).
					append(" -> ").append(entry.getValue().size());
		}
		log = stringBuilder.toString();
		logger.info(log);

		removeSecondHalfOfReleases(files, relMan.getReleaseNames()); //Cut the second half of the releases to get reliable input

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"Trimmed releases: ");
		stringBuilder.append(Arrays.toString(files.keySet().toArray()));
		log = stringBuilder.toString();
		logger.info(log);

		String dataset = CSVManager.getInstance().getDataset(project, files); //Create the dataset

		return new Pair<>(dataset, relMan.getReleaseNames());
	}

	/**
	 * The linkage is the percentage of commits linked to a Jira ticket for a project.
	 * The bugLinkage is related only to those tickets marked as bugs.
	 *
	 * @param numCommits: total number of commits in the repository
	 * @param bugs: list of Bug instances representing the Jira tickets and the commits referencing them
	 *
	 * @return bugLinkage: percentage of bug linkage
	 * */
	private double getBugLinkage(int numCommits, List<Bug> bugs) {
		int counter = 0;

		for(Bug bug: bugs){
			if(bug.getFixCm() != null){
				counter++;
			}

			if(bug.getReferencingCms() != null){
				counter += bug.getReferencingCms().size();
			}
		}

		return (double)counter/numCommits;
	}

	/**
	 * Cuts the dataset to the first half of the releases retrieved
	 * to get more reliable input in terms of percentage of snoring classes.
	 *
	 * @param files : input list to cut in half
	 * @param releases : list of available releases used to retrieve keys of 'files'
	 * */
	private void removeSecondHalfOfReleases(Map<String, List<FileMetadata>> files, String[] releases) {
		int tot = releases.length;
		int half = Math.floorDiv(tot,2);

		for(int i=half; i<tot; i++){
			files.remove(releases[i]);
		}
	}

}
