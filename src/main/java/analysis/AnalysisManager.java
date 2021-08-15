package main.java.analysis;

import java.util.Set;

import main.java.CSVManager;

public class AnalysisManager {

	private static AnalysisManager instance = null;

    private AnalysisManager() {
    	/**/
    }

    public static AnalysisManager getInstance() {
        if(instance == null) {
        	instance = new AnalysisManager();
        }

        return instance;
    }
    
    public void getAnalysis(String project, String path, Set<String> set) throws Exception {
    	
    	WekaManager.getInstance().setWeka(path, set);
		
		CSVManager.getInstance().getWekaResult(project, WekaManager.getInstance().getPerformances());
    }
}
