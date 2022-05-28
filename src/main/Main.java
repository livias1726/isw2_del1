package main;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.LogManager;

import main.dataset.control.DatasetManager;
import main.dataset.entity.FileMetadata;
import main.training.control.WekaManager;
import main.training.entity.Configuration;
import main.utils.CSVManager;
import main.utils.LoggingUtils;

import java.util.logging.*;

/**
 * Starting class.
 * */
public class Main {

	private static String project; //change the name on the properties to change the project to analyze
	private static String output;

	/**
	 * Main method.
	 *
	 * Calls the controllers to create the dataset and uses it to train the ML models.
	 * */
	public static void main(String[] args) {

		try {
            manageProperties();

			//if project is BOOKKEEPER: Jira support ended on 2017-10-17
            if(project.equals("BOOKKEEPER")){
                System.setProperty("date_limit", "2017-10-17");
            }else{
                System.setProperty("date_limit", LocalDate.now().toString());
            }

			//-----------------------------------------MILESTONE 1------------------------------------------------------
			//dataset construction
			Map<String, List<FileMetadata>> dataset = DatasetManager.getInstance(project).getDataset();

			//dataset on csv
			String datasetPath = CSVManager.getInstance().getDataset(output, project, dataset);

			//----------------------------------------MILESTONE 2-------------------------------------------------------

			//pre-configuration
			Map<String, Integer> instancesPerRelease = getNumberOfFilesPerRelease(dataset);

			//training
			List<Configuration> wekaOutput =
					WekaManager.getInstance().setWeka(datasetPath, new ArrayList<>(instancesPerRelease.values()));

			//output
			CSVManager.getInstance().getWekaResult(output, project, wekaOutput);

		} catch (Exception e) {
			LoggingUtils.logException(e);
			System.exit(-1);
		}
	}

    private static Map<String, Integer> getNumberOfFilesPerRelease(Map<String, List<FileMetadata>> dataset) {
		Map<String, Integer> res = new LinkedHashMap<>();

		for(Map.Entry<String, List<FileMetadata>> entry: dataset.entrySet()){
			res.put(entry.getKey(), entry.getValue().size());
		}

		return res;
	}

    private static void manageProperties() throws IOException {

        //System configuration
		prepareSystem();

        //Logger
        prepareLogger();
    }

	private static void prepareSystem() throws IOException {
		InputStream stream = Main.class.getClassLoader().getResourceAsStream("config.properties");
		Properties prop = new Properties();
		if (stream != null) {
			prop.load(stream);
		}

		project = prop.getProperty("project");
		output = prop.getProperty("output_path");

		System.setProperty("project_name", prop.getProperty("project"));
		System.setProperty("proportion", prop.getProperty("proportion"));
	}

	/**
	 * Configures the logger for the program using the properties file in the resources' directory.
	 * */
	private static void prepareLogger() throws IOException {
		InputStream stream = Main.class.getClassLoader().getResourceAsStream("logging.properties");

		LogManager.getLogManager().readConfiguration(stream);
		Logger logger = Logger.getLogger(project);

		LoggingUtils.setLogger(logger);
	}
}
