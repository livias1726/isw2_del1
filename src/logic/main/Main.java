package logic.main;

import java.util.Set;

import javafx.util.Pair;
import logic.analysis.AnalysisManager;
import logic.dataset.DatasetManager;

public class Main {
	
	private static String project = "BOOKKEEPER"; //change the name to change the project to analyze	
	
	public static void main(String[] args) {
		try {
			Pair<String,Set<String>> datasetCSV = DatasetManager.getInstance(project).getDataset();
			AnalysisManager.getInstance().getAnalysis(project, datasetCSV.getKey(), datasetCSV.getValue());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
