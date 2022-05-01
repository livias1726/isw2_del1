package main;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.logging.*;

import javafx.util.Pair;
import main.dataset.control.DatasetManager;
import main.training.AnalysisManager;

/**
 * Starting class.
 * */
public class Main {

	private static Logger logger = null;
    private static final String PROJECT = "OPENJPA"; //change the name to change the project to analyze

	/**
	 * Main method.
	 *
	 * Calls the controllers to create the dataset and uses it to train the ML models.
	 * */
	public static void main(String[] args) {
		System.setProperty("project_name", PROJECT);

		try {
			//if project is BOOKKEEPER: Jira support ended on 2017-10-17
            if(System.getProperty("project_name").equals("BOOKKEEPER")){
                System.setProperty("date_limit", "2017-10-17");
            }else{
                System.setProperty("date_limit", LocalDate.now().toString());
            }

			//logger configuration
			prepareLogger();

			//dataset construction
			Pair<String, String[]> datasetCSV = DatasetManager.getInstance(PROJECT).getDataset();
			logger.info("Dataset construction: SUCCESS.");

			//training
			//AnalysisManager.getInstance().getAnalysis(PROJECT, datasetCSV.getKey(), datasetCSV.getValue());

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Configures the logger for the program using the properties file in the resources' directory.
	 * */
	private static void prepareLogger() {
		InputStream stream = Main.class.getClassLoader().getResourceAsStream("logging.properties");

		try {
			LogManager.getLogManager().readConfiguration(stream);
			logger = Logger.getLogger(PROJECT);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
