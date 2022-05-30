package main.dataset.control;

import main.dataset.entity.FileMetadata;
import main.utils.LoggingUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatasetManagerITT extends DatasetManager{
    public DatasetManagerITT(String projectName) {
        super(projectName);
    }

    /**
     * Creates n datasets related to the number of releases
     *
     * @return : dataset filename and project releases
     * */
    public List<Map<String, List<FileMetadata>>> getDatasetITT() throws GitAPIException, IOException {

        List<Map<String, List<FileMetadata>>> results = new ArrayList<>();

        retrieveFromJira();
        retrieveFromGit();
        Map<String, Map<RevCommit, LocalDate>> cmPerRelease = manageReleases();

        int totConsideredReleases = (int) Math.ceil((double) cmPerRelease.size()/2);
        //Process commits to construct the datasets
        Map<String, Map<RevCommit, LocalDate>> currCmPerRelease = new LinkedHashMap<>();
        for(Map.Entry<String, Map<RevCommit, LocalDate>> currEntry: cmPerRelease.entrySet()){
            if(totConsideredReleases == 0){
                break;
            }else{
                currCmPerRelease.put(currEntry.getKey(), currEntry.getValue());
                results.add(manageFilesITT(currCmPerRelease));
            }

            totConsideredReleases--;
        }

        return results;
    }

    private Map<String, List<FileMetadata>> manageFilesITT(Map<String, Map<RevCommit, LocalDate>> cmPerRelease) throws GitAPIException, IOException {
        FilesManager fm = new FilesManager(project, bugs);

        Map<String, List<FileMetadata>> files = fm.analyzeFilesEvolution(cmPerRelease);
        LoggingUtils.logFilesPerRelease(files.entrySet());

        return files;
    }
}
