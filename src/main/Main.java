package main;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.logging.*;

import javafx.util.Pair;
import main.dataset.control.DatasetManager;
import main.training.AnalysisManager;

public class Main {

	private static Logger LOGGER = null;
    private static final String project = "BOOKKEEPER"; //change the name to change the project to analyze

	public static void main(String[] args) {
		System.setProperty("project_name", project);

		try {
			//if project is BOOKKEEPER: Jira support ended on 2017-10-17
            if(System.getProperty("project_name").equals("BOOKKEEPER")){
                System.setProperty("date_limit", "2017-10-17");
            }else{
                System.setProperty("date_limit", LocalDate.now().toString());
            }

			prepareLogger();

			//dataset construction
			Pair<String, String[]> datasetCSV = DatasetManager.getInstance(project).getDataset(LOGGER);
			LOGGER.info("Dataset construction: SUCCESS.");

			//training
			//AnalysisManager.getInstance().getAnalysis(project, datasetCSV.getKey(), datasetCSV.getValue());

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static void prepareLogger() {
		InputStream stream = Main.class.getClassLoader().getResourceAsStream("logging.properties");

		try {
			LogManager.getLogManager().readConfiguration(stream);
			LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
