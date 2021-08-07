package main.java;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    
    public String fileCSV(String project, Map<String, List<FileMetadata>> files) throws IOException {
    	
    	Iterator<String> iter = files.keySet().iterator();
    	
    	String path = project + ".csv";
    	try(FileWriter fw = new FileWriter(new File(path))){
    		fw.append("Project;Version;Filename;"
    				+ "Size;LOC touched;NR;"
    				+ "NFix;NAuth;LOC added;"
    				+ "AVG LOC added;Churn;AVG Churn;"
    				+ "ChgSetSize;AVG ChgSet;Age;"
    				+ "Buggy");
    		fw.append("\n");
            
            String rel;
            
            while(iter.hasNext()) {
            	rel = iter.next();
            	
            	for(FileMetadata file: files.get(rel)) {
            		if(file.getSize() < 0) {
            			continue;
            		}
            		
            		fw.append(project + ";");
            		fw.append(rel + ";");
    				fw.append(file.getFilename() + ";");
    				fw.append(String.valueOf(file.getSize()) + ";");
    				
    				putLOCInfo(fw, rel, file.getLOCTouchedPerRev());
    				
    				fw.append(String.valueOf(file.getNumberOfReleases()) + ";");
    				fw.append(String.valueOf(file.getFixCounter()) + ";");
    				fw.append(String.valueOf(file.getNumberOfAuthors()) + ";");
    				
    				putLOCInfo(fw, rel, file.getLOCAddedPerRev());
    				fw.append(String.valueOf(file.getAvgLOCAdded()) + ";");
    				putLOCInfo(fw, rel, file.getChurnPerRev());
    				fw.append(String.valueOf(file.getAvgChurn()) + ";");
    				
    				fw.append(String.valueOf(file.getChgSetSize(rel)) + ";");
    				fw.append(String.valueOf(file.getAvgChgSetSize()) + ";"); 
    				fw.append(String.valueOf(file.getAge()) + ";");
    				
    				putBuggynessInfo(fw, file.getBuggyness().get(rel));
    				
    				fw.append("\n");
            	}
    		}
    	}
    	
    	return path;
    }

	private void putBuggynessInfo(FileWriter fw, Boolean buggy) throws IOException {
		if(Boolean.TRUE.equals(buggy)) {
			fw.append("1");
		}else {
			fw.append("0");
		}
	}

	private void putLOCInfo(FileWriter fw, String rel, Map<String, Integer> map) throws IOException {
		if(!map.containsKey(rel)) {
			fw.append("0");
		}else {
			fw.append(String.valueOf(map.get(rel)));
		}
		fw.append(";");
	}
}
