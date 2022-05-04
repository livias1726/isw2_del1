package main.training;

import main.utils.CSVManager;

public class AnalysisManager {

    //Instantiation
	private static AnalysisManager instance = null;

    private AnalysisManager() {/**/}

    public static AnalysisManager getInstance() {
        if(instance == null) {
        	instance = new AnalysisManager();
        }
        return instance;
    }
    
    public void getAnalysis(String project, String dataset, String[] releases) throws Exception {

        CSVManager csv = CSVManager.getInstance();
        csv.prepareCSVForWeka(dataset);

    	WekaManager.getInstance().setWeka(dataset, releases);

		csv.getWekaResult(project, WekaManager.getInstance().getPerformances());
    }
}
