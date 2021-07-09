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
    		fw.append("Project;Version;Filename;Size;NR;NFix;NAuth;LOC added;AVG LOC added;Age");
    		fw.append("\n");
            
            String rel;
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
    				
    				fw.append(String.valueOf(file.getNumberOfReleases()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getFixes()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getNumberOfAuthors()));   				
    				fw.append(";");
    				
    				if(!file.getLOCPerRev().containsKey(rel)) {
    					fw.append("0");
    				}else {
    					fw.append(String.valueOf(file.getLOCPerRev().get(rel)));
    				}
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAvgLOC()));   				
    				fw.append(";");
    				
    				fw.append(String.valueOf(file.getAge()));
    				fw.append("\n");
            	}
    		}
    	}
    	
    	return path;
    }
}
