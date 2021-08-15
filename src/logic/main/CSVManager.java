package logic.main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javafx.util.Pair;
import logic.analysis.WekaManager;
import logic.dataset.FileMetadata;

/*SINGLETON*/
public class CSVManager {
	
	private static CSVManager instance = null;

    private CSVManager() {
    	/**/
    }

    public static CSVManager getInstance() {
        if(instance == null) {
        	instance = new CSVManager();
        }

        return instance;
    }
    
    public String getDataset(String project, Map<String, List<FileMetadata>> files) throws IOException {
    	
    	Iterator<String> iter = files.keySet().iterator();
    	
    	String path = project + ".csv";
    	try(FileWriter fw = new FileWriter(new File(path))){
    		fw.append("Project,Version,Filename,Size,LOC touched,NR,NFix,NAuth,LOC added,"
    				+ "AVG LOC added,Churn,AVG Churn,ChgSetSize,AVG ChgSet,Age,Buggy");
    		fw.append("\n");
            
            String rel;
            
            while(iter.hasNext()) {
            	rel = iter.next();
            	
            	for(FileMetadata file: files.get(rel)) {
            		if(file.getSize() < 0) {
            			continue;
            		}
            		
            		fw.append(project + ",");
            		fw.append(rel + ",");
    				fw.append(file.getFilename() + ",");
    				fw.append(String.valueOf(file.getSize()) + ",");
    				
    				putLOCInfo(fw, rel, file.getLOCTouchedPerRev());
    				
    				fw.append(String.valueOf(file.getNumberOfReleases()) + ",");
    				fw.append(String.valueOf(file.getFixCounter()) + ",");
    				fw.append(String.valueOf(file.getNumberOfAuthors()) + ",");
    				
    				putLOCInfo(fw, rel, file.getLOCAddedPerRev());
    				fw.append(String.valueOf(file.getAvgLOCAdded()) + ",");
    				putLOCInfo(fw, rel, file.getChurnPerRev());
    				fw.append(String.valueOf(file.getAvgChurn()) + ",");
    				
    				fw.append(String.valueOf(file.getChgSetSize(rel)) + ",");
    				fw.append(String.valueOf(file.getAvgChgSetSize()) + ","); 
    				fw.append(String.valueOf(file.getAge()) + ",");
    				
    				putBuggynessInfo(fw, file.getBuggyness().get(rel));
    				
    				fw.append("\n");
            	}
    		}
    	}
    	
    	return path;
    }
    
	private void putBuggynessInfo(FileWriter fw, Boolean buggy) throws IOException {
		if(Boolean.TRUE.equals(buggy)) {
			fw.append("Yes");
		}else {
			fw.append("No");
		}
	}

	private void putLOCInfo(FileWriter fw, String rel, Map<String, Integer> map) throws IOException {
		if(!map.containsKey(rel)) {
			fw.append("0");
		}else {
			fw.append(String.valueOf(map.get(rel)));
		}
		fw.append(",");
	}
	
	public String getWekaResult(String project, Map<Pair<Integer, Integer>, List<Double>> map) throws IOException {
    	
    	String path = project + "_weka.csv";
    	try(FileWriter fw = new FileWriter(new File(path))){
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
    			fw.append(project + ".csv;");
    			
    			//training + testing
    			fw.append(idx.getValue() + ";");
    			for(i=0; i<3; i++) {
    				fw.append(df.format(list.get(i)) + ";");
    			}
        		
        		//configuration
        		writeConfigSettings(fw, idx);
        		
        		//metrics
        		for(i=3; i<list.size(); i++) {
        			if(Double.isNaN(list.get(i))) {
        				fw.append(list.get(i) + ";");
        			}else {
        				fw.append(df.format(list.get(i)) + ";");
        			}
        		}
				
        		fw.append("\n");
    		}
    	}
    	
    	return path;
    }

	private void writeConfigSettings(FileWriter fw, Pair<Integer, Integer> idx) throws IOException {
		String className = WekaManager.getInstance().getConfigurations(idx.getKey(), 2).toString();
		fw.append(className.substring(0, className.indexOf("\n")) + ";");
		
		if(WekaManager.getInstance().getConfigurations(idx.getKey(),1) == null) {
			fw.append("/;");
		}else {
			fw.append(WekaManager.getInstance().getConfigurations(idx.getKey(),1).toString().substring(33) + ";");
		}
		
		if(WekaManager.getInstance().getConfigurations(idx.getKey(),0) == null) {
			fw.append("/;");
		}else {
			String featSelName = WekaManager.getInstance().getConfigurations(idx.getKey(), 0).toString();
    		fw.append(featSelName.substring(0, featSelName.indexOf(".")) + ";");
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
