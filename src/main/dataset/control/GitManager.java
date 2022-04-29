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

public class GitManager {

    private final String path;

    //Instantiation
    private static GitManager instance = null;
    private GitManager(String projName) {
        this.path = "..\\Sources\\" + projName;
    }
    public static GitManager getInstance(String projName) {
        if(instance == null) {
            instance = new GitManager(projName);
        }
        return instance;
    }

    /**
     * Retrieves every commit associated to a Jira bug ticket.
     *
     * @return : map of repository commits with date
     * */
    public Map<RevCommit, LocalDate> getCommits() throws GitAPIException {
        Map<RevCommit, LocalDate> commits = new HashMap<>();

        //Set date filter to get only the useful commits
        LocalDate dateLimit = LocalDate.parse(System.getProperty("date_limit"));
        RevFilter filter = CommitTimeRevFilter.before(Date.from(dateLimit.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        //Retrieve log
        Git git = Git.init().setDirectory(new File(path)).call();
        Iterable<RevCommit> log = git.log().setRevFilter(filter).call();

        //Iterate over the commit log for the project
        LocalDate cmDate;
        for(RevCommit cm : log){
            cmDate = cm.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if(cmDate.isBefore(dateLimit) || cmDate.isEqual(dateLimit)){
                commits.put(cm, cmDate);
            }
        }

        git.close();
        return commits;
    }

    /**
     * Manages the commits associated to a bug.
     *
     * @param bugs : list of Bugs
     * @param commits : project commits
     *
     * @return : input list to which Git information is added
     */
    public List<Bug> manageBugCommits(List<Bug> bugs, Map<RevCommit, LocalDate> commits) {
        LocalDate cmDate;

        for(Bug bug: bugs) {
            for(Map.Entry<RevCommit, LocalDate> entry: commits.entrySet()){
                if(entry.getKey().getFullMessage().contains(bug.getTicketKey())){
                    cmDate = entry.getValue();
                    if(bug.getFixDate().equals(cmDate)){
                        bug.setFixCm(entry.getKey());

                    }else{
                        bug.setReferencingCms(entry.getKey());
                    }
                }
            }
        }
        return bugs;
    }

    public List<Bug> removeUnreferencedBugs(List<Bug> bugs) {
        bugs.removeIf(bug -> bug.getFixCm() == null && bug.getReferencingCms() == null);
        return bugs;
    }

    public List<Bug> processFixCommitInfo(List<Bug> bugs) {
        for(Bug bug: bugs){
            if(bug.getFixCm() == null){
                bug.setFixCm(bug.getLatestReferencingCm());
            }
        }
        return bugs;
    }

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
