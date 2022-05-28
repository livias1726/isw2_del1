package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import main.utils.LoggingUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Controller class.
 *
 * Retrieves information to create the dataset.
 * */
public class DatasetManager {
	
	private final String project;
	private Map<String, LocalDate> releases;
	private List<Bug> bugs;
	private Map<RevCommit, LocalDate> commits;

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
	public Map<String, List<FileMetadata>> getDataset() throws GitAPIException, IOException {

		retrieveFromJira();

		retrieveFromGit();

		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = manageReleases();

		return manageFiles(cmPerRelease);
	}

	private Map<String, List<FileMetadata>> manageFiles(Map<String, Map<RevCommit, LocalDate>> cmPerRelease) throws GitAPIException, IOException {
		DifferenceTreeManager dt = DifferenceTreeManager.getInstance(project, bugs);

		Map<String, List<FileMetadata>> files = dt.analyzeFilesEvolution(cmPerRelease);
		LoggingUtils.logFilesPerRelease(files.entrySet());

		removeSecondHalfOfReleases(files, ReleaseManager.getInstance().getReleaseNames());	/*Cut the second half of
		 																					releases to get reliable
		 																					input*/
		LoggingUtils.logList("Trimmed releases: ", files.keySet());

		return files;
	}

	private Map<String, Map<RevCommit, LocalDate>> manageReleases() throws IOException, GitAPIException {
		ReleaseManager.setReleases(releases); //set releases

		ReleaseManager relMan = ReleaseManager.getInstance();

		bugs = relMan.analyzeOpeningAndFix(bugs); //get opening and fix versions
		LoggingUtils.logInt("Number of bugs with valid opening and fix version: ", bugs.size());

		bugs = relMan.analyzeBugInfection(bugs); //get injected and affected versions
		LoggingUtils.logBugsInformation(bugs);

		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = relMan.matchCommitsAndReleases(commits);
		LoggingUtils.logCommitsPerRelease(relMan.getReleaseNames(), cmPerRelease);

		return cmPerRelease;
	}

	private void retrieveFromGit() throws GitAPIException {
		GitManager git = GitManager.getInstance(project);

		commits = git.getCommits(project); //list of every commit in the project
		LoggingUtils.logInt("Total number of commits retrieved: ", commits.size());

		bugs = git.manageBugCommits(bugs, commits); //manage list of commits linked to a jira fix ticket
		LoggingUtils.logDouble("Project linkage is: ", getBugLinkage(commits.size(), bugs)*100);

		bugs = git.removeUnreferencedBugs(bugs); //must be after the linkage computation
		LoggingUtils.logInt("Number of bugs referenced: ", bugs.size());

		bugs = git.processFixCommitInfo(bugs); //set the fix commit for every bug considered
	}

	private void retrieveFromJira() throws IOException {
		JiraManager jira = JiraManager.getInstance(project);

		releases = jira.getProjectVersions(); //list of releases
		LoggingUtils.logList("Releases: ", releases.keySet());

		bugs = jira.getFixes(); //list of jira fix tickets
		LoggingUtils.logInt("Total number of Jira tickets retrieved: ", bugs.size());
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
		double tot = releases.length;
		int half = (int) Math.ceil(tot/2);

		for(int i=half; i<tot; i++){
			files.remove(releases[i]);
		}
	}

}
