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
	public Pair<String, String[]> getDataset(Logger LOGGER) throws GitAPIException, IOException {
		//--------------------------------------------------JIRA--------------------------------------------------------

		JiraManager jira = JiraManager.getInstance(project);
		ReleaseManager relMan = ReleaseManager.getInstance(jira.getProjectVersions()); //Get releases

		/*LOG*/
		LOGGER.info("\tReleases: ");
		for(String rel: ReleaseManager.getReleaseNames()){
			LOGGER.info("\t\t" + rel);
		}

		List<Bug> bugs = jira.getFixes(); //Get list of jira fix tickets
        /*LOG*/ LOGGER.info("\tTotal number of Jira tickets retrieved: " + bugs.size());

		//---------------------------------------------------GIT--------------------------------------------------------

		GitManager git = GitManager.getInstance(project);

		Map<RevCommit, LocalDate> commits = git.getCommits(); //Get list of every commit in the project
        /*LOG*/ LOGGER.info("\tTotal number of commits retrieved: " + commits.size());

		bugs = git.manageBugCommits(bugs, commits); //Manage list of commits linked to a jira fix ticket

        /*LOG*/ LOGGER.info("\tProject linkage is: " + getBugLinkage(commits.size(), bugs)*100);

		bugs = git.removeUnreferencedBugs(bugs); //Must be after the linkage computation
        /*LOG*/ LOGGER.info("\tNumber of bugs referenced: " + bugs.size());

		bugs = git.processFixCommitInfo(bugs);

		//-------------------------------------------------RELEASES-----------------------------------------------------

		bugs = relMan.analyzeBugReleases(bugs);
        /*LOG*/
		for(Bug bug: bugs){
			LOGGER.info(bug.getTicketKey());
			LOGGER.info("\tInjected: " + bug.getInjectedVer() + ", Opening: " + bug.getOpeningVer() + ", Fix: " + bug.getFixVer());
		}

		//TODO: Remove second half of releases during retrieval of commits
		// to get reliable info in terms of snoring classes.
		//relMan.removeSecondHalfOfReleases();

		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = relMan.matchCommitsAndReleases(commits);

		/*LOG*/
		for(String rel: ReleaseManager.getReleaseNames()){
			LOGGER.info(rel);
			LOGGER.info("\tNumber of related commits: " + cmPerRelease.get(rel).keySet().size());
		}

		//--------------------------------------------------FILES-------------------------------------------------------

		DifferenceTreeManager dt = DifferenceTreeManager.getInstance(project, bugs);
		Map<String, List<FileMetadata>> files = dt.analyzeFilesEvolution(cmPerRelease);

		/*LOG*/
		for(String rel: files.keySet()){
			LOGGER.info(rel);
			LOGGER.info("\tNumber of related files: " + files.get(rel).size());
		}

		String dataset = CSVManager.getInstance().getDataset(project, dt.getFiles()); //Create the dataset

		return new Pair<>(dataset, ReleaseManager.getReleaseNames());
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

}
