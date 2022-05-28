package main.dataset.control;

import main.dataset.entity.Bug;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Controller class.
 *
 * Uses Git API to retrieve commits information and differences between pairs of commits.
 * */
public class GitManager {

    private static String path;
    private static String project;
    private final static String basePath = "..\\Sources\\";

    //Instantiation
    private static GitManager instance = null;

    private GitManager(String projName) {
        project = projName;
        path = basePath + projName;
    }

    public static GitManager getInstance(String projName) {
        if(instance == null) {
            instance = new GitManager(projName);
        }

        path = basePath + projName;
        project = projName;
        return instance;
    }

    /**
     * Retrieves every commit of the repository until the 'date_limit' property value.
     *
     * @return : map of repository commits with date
     * */
    public Map<RevCommit, LocalDate> getCommits() throws GitAPIException {
        Map<RevCommit, LocalDate> commits = new LinkedHashMap<>();
        Git git = Git.init().setDirectory(new File(path)).call();
        Iterable<RevCommit> log;

        //Set date filter to get only the useful commits
        if(project.equals(System.getProperty("project_name"))){
            LocalDate dateLimit = LocalDate.parse(System.getProperty("date_limit"));
            RevFilter filter = CommitTimeRevFilter.before(Date.from(dateLimit.atStartOfDay(ZoneId.systemDefault()).toInstant()));

            //Retrieve log
            log = git.log().setRevFilter(filter).call();
        }else{
            //Retrieve log
            log = git.log().call();
        }

        //Iterate over the commit log for the project
        LocalDate cmDate;
        for(RevCommit cm : log){
            cmDate = cm.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            commits.put(cm, cmDate);
        }

        git.close();
        return commits;
    }

    /**
     * Manages the commits associated to a bug ticket.
     *
     * @param bugs : list of Bug instances
     * @param commits : project commits
     *
     * @return : input list to which Git information is added
     */
    public List<Bug> manageBugCommits(List<Bug> bugs, Map<RevCommit, LocalDate> commits) {

        for(Bug bug: bugs) { //scan bug tickets
            for(Map.Entry<RevCommit, LocalDate> entry: commits.entrySet()){ //scan commits

                if(entry.getKey().getFullMessage().contains(bug.getTicketKey())){ //commit references ticket

                    if(bug.getFixDate().equals(entry.getValue())){ //bug fix date equals commit date
                        bug.setFixCm(entry.getKey()); //commit is the one that fixes the bug

                    }else{
                        bug.setReferencingCms(entry.getKey()); //commit is just referencing the bug
                    }
                }
            }
        }

        return bugs;
    }

    /**
     * Removes from the bug tickets list the ones that no commit references.
     *
     * @param bugs : list of Bug instances
     *
     * @return : updated list of Bug instances
     * */
    public List<Bug> removeUnreferencedBugs(List<Bug> bugs) {
        bugs.removeIf(bug -> bug.getFixCm() == null && bug.getReferencingCms() == null);
        return bugs;
    }

    /**
     * Sets as 'fix commit', for the bugs that don't have one, the latest referencing commit.
     *
     * @param bugs : list of Bug instances
     *
     * @return : updated list of Bug instances
     * */
    public List<Bug> processFixCommitInfo(List<Bug> bugs) {
        for(Bug bug: bugs){
            if(bug.getFixCm() == null){
                bug.setFixCm(bug.getLatestReferencingCm());
            }
        }
        return bugs;
    }

    /**
     * Configures the difference formatter class and retrieve the difference tree between pairs of sequential commits.
     *
     * @param diffFormatter : DiffFormatter instance
     * @param from : first commit from which compute the differences
     * @param to : second commit that produced the differences to compute
     *
     * @return : list of DiffEntry instances
     * */
    public List<DiffEntry> retrieveDifferences(DiffFormatter diffFormatter, RevCommit from, RevCommit to) throws GitAPIException, IOException {
        Git git = Git.init().setDirectory(new File(path)).call();

        diffFormatter.setRepository(git.getRepository());
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        diffFormatter.setPathFilter(PathSuffixFilter.create(".java"));
        diffFormatter.setDetectRenames(true);

        List<DiffEntry> diffs;

        if(from == null) { //First commit
            AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
            ObjectReader reader = git.getRepository().newObjectReader();
            AbstractTreeIterator newTreeIter = new CanonicalTreeParser(null, reader, to.getTree());

            diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
        }else {
            diffs = diffFormatter.scan(from.getTree(), to.getTree());
        }
        git.close();

        return diffs;
    }
}
