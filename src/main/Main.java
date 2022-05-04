package main;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.*;

import javafx.util.Pair;
import main.dataset.control.DatasetManager;
import main.dataset.entity.FileMetadata;
import main.training.AnalysisManager;
import main.utils.CSVManager;
import main.utils.LoggingUtils;

/**
 * Starting class.
 * */
public class Main {

	private static final String PROJECT = "BOOKKEEPER"; //change the name to change the project to analyze

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
			Map<String, List<FileMetadata>> dataset = DatasetManager.getInstance(PROJECT).getDataset();

			//dataset on csv
			String datasetName = CSVManager.getInstance().getDataset(PROJECT, dataset);

			//training
			//AnalysisManager.getInstance().getAnalysis(PROJECT, datasetName, dataset.keySet().toArray(new String[0]));

			 /*
			String filename = PROJECT+".csv";
			String[] releases = new String[]{
					"0.9.0", "0.9.6", "0.9.7", "1.0.0", "1.0.1", "1.0.2", "1.1.0", "1.0.3", "1.2.0", "2.0.0-M1",
					"1.2.1", "2.0.0-M2", "2.0.0-M3", "1.2.2", "2.0.0-beta", "2.0.0-beta2", "2.0.0-beta3", "2.0.0"};

			AnalysisManager.getInstance().getAnalysis(PROJECT, filename, releases);*/

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
			Logger logger = Logger.getLogger(PROJECT);
			LoggingUtils.getInstance(logger);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
