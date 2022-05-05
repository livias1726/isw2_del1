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
import main.training.WekaManager;
import main.utils.CSVManager;
import main.utils.LoggingUtils;

/**
 * Starting class.
 * */
public class Main {

	private static final String PROJECT = "OPENJPA"; //change the name to change the project to analyze
	private static final String OUTPUT_PATH = "..\\Outputs\\";

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
			String datasetPath = CSVManager.getInstance().getDataset(OUTPUT_PATH, PROJECT, dataset);

			//training
			Map<Pair<Integer, Integer>, List<Double>> performances =  WekaManager.getInstance().setWeka(datasetPath);
			CSVManager.getInstance().getWekaResult(PROJECT, performances);

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

			LoggingUtils.setLogger(logger);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
