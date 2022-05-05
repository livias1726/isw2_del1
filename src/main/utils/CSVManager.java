package main.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javafx.util.Pair;
import main.training.WekaManager;
import main.dataset.entity.FileMetadata;

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

	public void getWekaResult(String project, Map<Pair<Integer, Integer>, List<Double>> map) throws IOException {
    	
    	String path = project + "_weka.csv";
    	try(FileWriter fw = new FileWriter(path)){
    		fw.append("Dataset;"
    				+ "#TrainingRelease;%Training;%DefectiveTraining;%DefectiveTesting;"
    				+ "Classifier;Balancing;FeatureSelection;Sensitivity;"
    				+ "TP;FP;TN;FN;Precision;Recall;AUC;Kappa");
    		fw.append("\n");
    		
    		DecimalFormat df = new DecimalFormat("#.00");
    		
    		Iterator<Pair<Integer, Integer>> iter = map.keySet().iterator();
    		Pair<Integer, Integer> idx;
    		List<Double> list;
    		
    		int i;
    		while(iter.hasNext()) {
    			idx = iter.next();
    			list = map.get(idx);
    			
    			//data set
    			fw.append(project).append(".csv;");
    			
    			//training + testing
    			fw.append(String.valueOf(idx.getValue())).append(";");
    			for(i=0; i<3; i++) {
    				fw.append(df.format(list.get(i))).append(";");
    			}
        		
        		//configuration
        		writeConfigSettings(fw, idx);
        		
        		//metrics
        		for(i=3; i<list.size(); i++) {
        			if(Double.isNaN(list.get(i))) {
        				fw.append(String.valueOf(list.get(i))).append(";");
        			}else {
        				fw.append(df.format(list.get(i))).append(";");
        			}
        		}
				
        		fw.append("\n");
    		}
    	}

	}

	private void writeConfigSettings(FileWriter fw, Pair<Integer, Integer> idx) throws IOException {
		String className = WekaManager.getInstance().getConfigurations(idx.getKey(), 2).toString();
		fw.append(className.substring(0, className.indexOf("\n"))).append(";");
		
		if(WekaManager.getInstance().getConfigurations(idx.getKey(),1) == null) {
			fw.append("/;");
		}else {
			fw.append(WekaManager.getInstance().getConfigurations(idx.getKey(), 1).toString().substring(33)).append(";");
		}
		
		if(WekaManager.getInstance().getConfigurations(idx.getKey(),0) == null) {
			fw.append("/;");
		}else {
			String featSelName = WekaManager.getInstance().getConfigurations(idx.getKey(), 0).toString();
    		fw.append(featSelName.substring(0, featSelName.indexOf("."))).append(";");
		}
		
		if(WekaManager.getInstance().getConfigurations(idx.getKey(),3) == null) {
			fw.append("/;");
		}else {
			String matrix = WekaManager.getInstance().getConfigurations(idx.getKey(),3).toString();
			if(matrix.contains("10")) {
				fw.append("Learning;");
			}else {
				fw.append("Threshold=0.5;");
			}
		}
	}
}
