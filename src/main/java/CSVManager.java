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
    				+ "ChgSetSize;AVG ChgSet;Age");
    		fw.append("\n");
            
            String rel;
            Map<String,Integer> touched;
            Map<String,Integer> added;
            Map<String,Integer> churn;
            while(iter.hasNext()) {
            	rel = iter.next();
            	
            	for(FileMetadata file: files.get(rel)) {
            		fw.append(project);
    				fw.append(";");
    				
            		fw.append(rel);
    				fw.append(";");
    				
    				fw.append(file.getFilename());
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getSize()));
    				fw.append(";");
    				
    				touched = file.getLOCTouchedPerRev();
    				if(!touched.containsKey(rel)) {
    					fw.append("0");
    				}else {
    					fw.append(String.valueOf(touched.get(rel)));
    				}
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getNumberOfReleases()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getFixes()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getNumberOfAuthors()));   				
    				fw.append(";");
    				
    				added = file.getLOCAddedPerRev();
    				if(!added.containsKey(rel)) {
    					fw.append("0");
    				}else {
    					fw.append(String.valueOf(added.get(rel)));
    				}
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAvgLOCAdded()));   				
    				fw.append(";");
    				
    				churn = file.getChurnPerRev(-1);
    				if(!churn.containsKey(rel)) {
    					fw.append("0");
    				}else {
    					fw.append(String.valueOf(churn.get(rel)));
    				}
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAvgChurn()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getChgSetSize(rel)));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAvgChgSetSize()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAge()));
    				fw.append("\n");
            	}
    		}
    	}
    	
    	return path;
    }
}
