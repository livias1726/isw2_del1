package main.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import main.dataset.entity.FileMetadata;
import main.training.entity.Configuration;

public class CSVManager {

	//Instantiation
	private static CSVManager instance = null;

    private CSVManager() { /**/ }

    public static CSVManager getInstance() {
        if(instance == null) {
        	instance = new CSVManager();
        }
        return instance;
    }

	/**
	 * Creates the csv file used as dataset.
	 *
	 * @param outputPath : directory of output files
	 * @param datasetName: name of the file
	 * @param files: lists of FileMetadata per release
	 * */
    public String getDataset(String outputPath, String datasetName, Map<String, List<FileMetadata>> files) throws IOException {

    	String path = outputPath + datasetName + ".csv";

    	try(FileWriter fw = new FileWriter(path)){
    		fw.append("Project,Version,Filename," +
					  "Size,LOC_touched,NR,NFix,NAuth," +
					  "LOC_added,MAX_LOC_added,AVG_LOC_added," +
					  "Churn,MAX_Churn,AVG_Churn," +
					  "ChgSetSize,MAX_ChgSet,AVG_ChgSet," +
					  "Age,Buggy");
    		fw.append("\n");

			String rel;
			for(Map.Entry<String, List<FileMetadata>> entry: files.entrySet()){
				rel = entry.getKey();

				for(FileMetadata file: entry.getValue()) {
					if(file.getSize() < 0) {
						continue;
					}

					fw.append(datasetName).append(","); 		//Project
					fw.append(rel).append(","); 				//Version
					fw.append(file.getFilename()).append(","); 	//Filename

					fw.append(String.valueOf(file.getSize())).append(",");  							//Size
					fw.append(String.valueOf(file.getLOCTouchedOverRevision(rel))).append(","); 		//LOC_touched
					fw.append(String.valueOf(file.getNumberOfRevisionsPerRelease(rel))).append(",");	//NR
					fw.append(String.valueOf(file.getFixCounter())).append(","); 						//NFix
					fw.append(String.valueOf(file.getNumberOfAuthors())).append(","); 					//NAuth

					fw.append(String.valueOf(file.getLOCAddedOverRevision(rel))).append(",");	//LOC_added
					fw.append(String.valueOf(file.getMaxLOCAddedPerRelease(rel))).append(","); 	//MAX_LOC_added
					fw.append(String.valueOf(file.getAvgLOCAddedPerRelease(rel))).append(","); 	//AVG_LOC_added

					fw.append(String.valueOf(file.getChurnOverRevision(rel))).append(",");	//Churn
					fw.append(String.valueOf(file.getMaxChurnPerRelease(rel))).append(","); //MAX_Churn
					fw.append(String.valueOf(file.getAvgChurnPerRelease(rel))).append(","); //AVG_Churn

					fw.append(String.valueOf(file.getChgSetSizeOverRevisions(rel))).append(",");	//ChgSetSize
					fw.append(String.valueOf(file.getMaxChgSetSizePerRelease(rel))).append(",");	//MAX_ChgSet
 					fw.append(String.valueOf(file.getAvgChgSetSizePerRelease(rel))).append(","); 	//AVG_ChgSet

					fw.append(String.valueOf(file.getAge())).append(",");	//Age

                    if(file.getBuggynessSet().contains(rel)) { //Buggy
                        fw.append("Yes");
                    }else {
                        fw.append("No");
                    }

					fw.append("\n");
				}
			}
    	}
    	
    	return path;
    }

	/**
	 * Creates a csv file with Weka training results.
	 *
	 * @param outputPath : directory of output files
	 * @param project : project name
	 * @param configurations : values related to every computed
	 * */
	public void getWekaResult(String outputPath, String project, List<Configuration> configurations) throws IOException {
    	
    	String path = outputPath + project + "_Weka.csv";
    	try(FileWriter fw = new FileWriter(path)){
    		fw.append("Dataset,"
					+ "#Training Releases,%Training,%DefectiveTraining,%DefectiveTesting,"
    				+ "Classifier,Balancing,FeatureSelection,Sensitivity,"
    				+ "TP,FP,TN,FN,Precision,Recall,AUC,Kappa");
    		fw.append("\n");

			for(Configuration config: configurations){

				fw.append(project).append(",");	//Dataset
				fw.append(String.valueOf(config.getNumTrainingReleases())).append(",");	//#Training Releases

				fw.append(String.valueOf(config.getTrainingPercentage())).append(","); 			//%Training
				fw.append(String.valueOf(config.getDefectiveTrainingPercentage())).append(",");	// %DefectiveTraining
				fw.append(String.valueOf(config.getDefectiveTestPercentage())).append(",");		//%DefectiveTesting

				//ML model settings
				appendModelSettings(fw, config);

				//Performances
				fw.append(String.valueOf(config.getPerformances().get("TP"))).append(",");			//TP
				fw.append(String.valueOf(config.getPerformances().get("FP"))).append(",");			//FP
				fw.append(String.valueOf(config.getPerformances().get("TN"))).append(",");			//TN
				fw.append(String.valueOf(config.getPerformances().get("FN"))).append(",");			//FN
				fw.append(String.valueOf(config.getPerformances().get("Precision"))).append(",");	//Precision
				fw.append(String.valueOf(config.getPerformances().get("Recall"))).append(",");		//Recall
				fw.append(String.valueOf(config.getPerformances().get("AUC"))).append(",");			//AUC
				fw.append(String.valueOf(config.getPerformances().get("Kappa"))).append(",");		//Kappa

				fw.append("\n");
			}
    	}

	}

	/**
	 * Writes in the output file the configuration related to the performances.
	 * */
	private void appendModelSettings(FileWriter fw, Configuration config) throws IOException {
		fw.append(config.getClassifierName()).append(",");//Classifier

		//Balancing
		if(config.getSampling() == null) {
			fw.append("/,");
		}else {
			fw.append(config.getSamplingMethod()).append(",");
		}

		//FeatureSelection
		if(config.getFeatSelection() == null) {
			fw.append("/,");
		}else {
			fw.append(config.getFeatSelectionMethod()).append(",");
		}

		//Sensitivity
		if(config.getSensitivity() == null) {
			fw.append("/,");
		}else {
			fw.append(config.getCostSensitivity()).append(",");
		}
	}
}
