package main.training.control;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javafx.util.Pair;
import main.training.entity.Configuration;
import weka.attributeSelection.ASSearch;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

/**
 * Uses Weka API to perform a machine learning classification on the dataset.
 * */
public class WekaManager {

	private Instances trainingSet;
	private Instances testSet;

	private List<Configuration> configurations;

	//-------------------------------------------------Instantiation-----------------------------------------------
	private static WekaManager instance = null; //Singleton
	public static WekaManager getInstance() {
		if(instance == null) {
			instance = new WekaManager();
		}
		return instance;
	}

	/**
	 * Prepares the Weka analysis with every possible configuration of:
	 * 	- Feature selection models
	 * 	- Sampling models
	 * 	- Classifiers
	 * 	- Cost matrices
	 * */
	private WekaManager() {

		ASSearch[] featSelection = new ASSearch[]{null, new BestFirst()};
		Filter[] samplings = new Filter[]{null, new Resample(), new SpreadSubsample(), new SMOTE()};
		Classifier[] classifiers = new Classifier[]{new RandomForest(), new NaiveBayes(), new IBk(3)};

		CostMatrix mat1 = populateCostMatrix(1.0);
		CostMatrix mat2 = populateCostMatrix(10.0); //CFN = 10*CFP
		CostMatrix[] sensitivities = new CostMatrix[]{null, mat1, mat2};

		configureAnalysis(featSelection, samplings, classifiers, sensitivities);
	}

	private CostMatrix populateCostMatrix(double cfn) {
		CostMatrix matrix = new CostMatrix(2);

		matrix.setCell(0, 0, 0.0);
		matrix.setCell(0, 1, cfn);
		matrix.setCell(1, 0, 1.0);
		matrix.setCell(1, 1, 0.0);

		return matrix;
	}

	private void configureAnalysis(ASSearch[] featSelections, Filter[] samplings, Classifier[] classifiers, CostMatrix[] sensitivities) {
		configurations = new ArrayList<>();

		for(Classifier classifier: classifiers){
			for(ASSearch featSelection: featSelections){
				for(Filter sampling: samplings){
					for(CostMatrix sensitivity: sensitivities){
						Configuration config = new Configuration(classifier, featSelection, sampling, sensitivity);
						configurations.add(config);
					}
				}
			}
		}
	}

	//-------------------------------------------------Getters & Setters------------------------------------------------
	/**
	 * Executes the ML analysis on the dataset.
	 *
	 * @param datasetName : path of the dataset file
	 * @param values : number of files per release
	 *
	 * @return : list of Configuration instances to give output analysis
	 * */
	public List<Configuration> setWeka(String datasetName, List<Integer> values) throws Exception {

		String arffDataset = convertCSVToArff(datasetName); //get the arff from the csv

		//Load the dataset
		ArffLoader loader = new ArffLoader();
		loader.setSource(new File(arffDataset));

		Instances data = loader.getDataSet();
		data.setClassIndex(data.numAttributes() - 1); //set class index
		
		List<Instances> sets = separateInstances(data, values); //generate instances set per release

		//walk-forward
		int idx = 0;
		List<Configuration> localConfigurations = null;

		for(Configuration config: configurations) {
			if(idx == 0){
				localConfigurations = walkForwardEvaluation(sets, config);
			}else{
				localConfigurations.addAll(walkForwardEvaluation(sets, config));
			}

			idx++;
		}

		return localConfigurations;
	}

	/**
	 * Converts a CSV file in an ARFF file.
	 *
	 * @param csvFile : the path of the csv file
	 *
	 * @return : new path
	 * */
	private String convertCSVToArff(String csvFile) throws IOException {
		//Load CSV
		CSVLoader loader = new CSVLoader();
		loader.setSource(new File(csvFile));

		//Save ARFF
		ArffSaver saver = new ArffSaver();
		saver.setInstances(loader.getDataSet());

		saver.getInstances().deleteAttributeAt(0); //project
		saver.getInstances().deleteAttributeAt(0); //version
		saver.getInstances().deleteAttributeAt(0); //filename

		csvFile = csvFile.replace(".csv", ".arff");

		saver.setFile(new File(csvFile));
		saver.writeBatch();

		return csvFile;
	}

	/**
	 * Prepares folds to execute a Walk-forward training approach.
	 * Separates the dataset per release.
	 * */
	public List<Instances> separateInstances(Instances instances, List<Integer> values) {
		List<Instances> res = new ArrayList<>();

		int tot = instances.numInstances();

		int copied = 0;
		int toCopy;
		int remaining;
		for(int numFiles: values){
			remaining = tot - copied;
			toCopy = Math.min(numFiles, remaining); //to overcome out of bound exceptions

			res.add(new Instances(instances, copied, toCopy));

			copied += toCopy;
		}
		
		return res;
	}

	/**
	 * Implements Walk-forward method to train and test the classifier with chosen model configuration.
	 * @param sets : instances separated by release
	 * @param config :
	 *
	 * @return :
	 * */
	private List<Configuration> walkForwardEvaluation(List<Instances> sets, Configuration config) throws Exception {
		List<Configuration> localConfig = new ArrayList<>();

		//Configuration
		ASSearch filter = config.getFeatSelection();
		Filter sampling = config.getSampling();
		Classifier classifier = config.getClassifier();
		CostMatrix sensitivity = config.getSensitivity();

		int i;

		for (i = 0; i < sets.size(); i ++) { //get one set at a time to use as testing

			buildTrainingAndTestSet(sets, i); //sets initialization

			if(filter != null) {
				applyFeatureSelection(filter); //feature selection
			}

			if(sampling != null) {
				applySampling(sampling, classifier); //sampling
			}

			Configuration newConfig = setLocalConfiguration(sets, classifier, filter, sampling, sensitivity); //new configuration
			newConfig.setNumTrainingReleases(i);

			Map<String,Double> performance = computeEvaluation(classifier, sensitivity); //evaluation

			newConfig.setPerformances(performance);
			localConfig.add(newConfig);
		}

		return localConfig;
	}

	/**
	 * Initializes the training and test sets with the walk forward method.
	 *
	 * @param sets : instances separated by release
	 * @param wfIndex : walk forward test index
	 * */
	private void buildTrainingAndTestSet(List<Instances> sets, int wfIndex) {
		Instances test = sets.get(wfIndex); //set used as test
		test.setClassIndex(test.numAttributes() - 1);

		Instances train = new Instances(sets.get(0), 0, 0);
		for(int j = 0; j < wfIndex; j ++){
			train.addAll(sets.get(j)); //get every fold before the test one to use as training
		}
		train.setClassIndex(train.numAttributes() - 1);

		trainingSet = train;
		testSet = test;
	}

	/**
	 * Performs a feature selection on training and test set using the model in input.
	 *
	 * @param featSel : feature selection filter
	 * */
	private void applyFeatureSelection(ASSearch featSel) throws Exception {
		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval evaluator = new CfsSubsetEval();
		filter.setEvaluator(evaluator);
		filter.setSearch(featSel);

		if(trainingSet.numInstances() != 0) {
			filter.setInputFormat(trainingSet);
			trainingSet = Filter.useFilter(trainingSet, filter);
		}else {
			filter.setInputFormat(testSet);
		}

		testSet = Filter.useFilter(testSet, filter);
	}

	/**
	 * Performs a sampling method on training and test set.
	 *
	 * @param sampling : sampling filter
	 * @param classifier : classifier used
	 * */
	private void applySampling(Filter sampling, Classifier classifier) throws Exception {
		FilteredClassifier fc = new FilteredClassifier();
		fc.setClassifier(classifier);

		if(sampling.getClass() == Resample.class) {
			double maxSampleSizePercent = 99.00;
			double biasToUniformClass = 1.0;

			((Resample)sampling).setBiasToUniformClass(biasToUniformClass);
			((Resample)sampling).setNoReplacement(false);
			((Resample)sampling).setSampleSizePercent(maxSampleSizePercent*2);

		}else if(sampling.getClass() == SpreadSubsample.class){
			double distributionSpread = 1.0;
			((SpreadSubsample)sampling).setDistributionSpread(distributionSpread);
		}

		if(trainingSet.numInstances() != 0) {
			sampling.setInputFormat(trainingSet);
			fc.setFilter(sampling);
			trainingSet = Filter.useFilter(trainingSet, sampling);
		}else {
			sampling.setInputFormat(testSet);
			fc.setFilter(sampling);
			testSet = Filter.useFilter(testSet, sampling);
		}
	}

	/**
	 * Instantiates a new Configuration instance to separate different types of percentages.
	 * */
	private Configuration setLocalConfiguration(List<Instances> sets, Classifier classifier, ASSearch filter, Filter sampling, CostMatrix sensitivity) {
		Configuration config = new Configuration(classifier, filter, sampling, sensitivity);

		double trainingPerc = computeTrainingPerc(sets);
		config.setTrainingPercentage(trainingPerc);

		Pair<Double, Double> defectivePerc = computeDefectivePerc();
		config.setDefectiveTrainingPercentage(defectivePerc.getKey());
		config.setDefectiveTestPercentage(defectivePerc.getValue());

		return config;
	}

	private Double computeTrainingPerc(List<Instances> sets) {
		double tot = 0;
		for(Instances is: sets) {
			tot += is.numInstances();
		}
		
		if(tot == 0) {
			return tot;
		}
		return (trainingSet.numInstances()/tot)*100;
	}

	private Pair<Double, Double> computeDefectivePerc() {
		double trainDefPerc;
		double testDefPerc;

		int totTrain = trainingSet.numInstances();
		int totTest = testSet.numInstances();

		if(totTrain == 0) {
			trainDefPerc = 0;
		}else {
			int defTrain = countDefectiveInstances(trainingSet, totTrain);
			trainDefPerc = ((double)defTrain/totTrain)*100;
		}

		int defTest = countDefectiveInstances(testSet, totTest);
		testDefPerc = ((double)defTest/totTest)*100;
		
		return new Pair<>(trainDefPerc, testDefPerc);
	}

	private int countDefectiveInstances(Instances data, int tot) {
		int defective = 0;
		for(int i=0; i<tot; i++) {
			if(data.instance(i).stringValue(data.classIndex()) .equals("Yes")) {
				defective++;
			}
		}
		
		return defective;
	}

	private Map<String, Double> computeEvaluation(Classifier classifier, CostMatrix sensitivity) throws Exception {
		Evaluation eval;
		if(sensitivity != null) { //sensitivity
			classifier = applySensitivity(sensitivity, classifier);
			eval = new Evaluation(testSet, sensitivity);
		}else {
			eval = new Evaluation(testSet);
		}

		//Build classifier
		if(trainingSet.numInstances() == 0) {
			classifier.buildClassifier(testSet);
		}else {
			classifier.buildClassifier(trainingSet);
		}

		eval.evaluateModel(classifier, testSet); //evaluation

		return parsePerformance(eval);
	}

	private CostSensitiveClassifier applySensitivity(CostMatrix sensitivity, Classifier classifier) throws Exception {
		CostSensitiveClassifier csc = new CostSensitiveClassifier();
		csc.setClassifier(classifier);
		csc.setCostMatrix(sensitivity);
		
		boolean minExpCost;
		minExpCost = sensitivity.getElement(0, 1) != 10.0;
		csc.setMinimizeExpectedCost(minExpCost);
		
		return csc;
	}

	private Map<String, Double> parsePerformance(Evaluation eval) {
		Map<String, Double> performance = new LinkedHashMap<>();

		performance.put("TP", eval.numTruePositives(1));
		performance.put("FP", eval.numFalsePositives(1));
		performance.put("TN", eval.numTrueNegatives(1));
		performance.put("FN", eval.numFalseNegatives(1));
		performance.put("Precision", eval.precision(1));
		performance.put("Recall", eval.recall(1));
		performance.put("AUC", eval.areaUnderROC(1));
		performance.put("Kappa", eval.kappa());
		
		return performance;
	}
}
