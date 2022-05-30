package main.training.control;

import java.io.File;
import java.util.*;

import javafx.util.Pair;
import main.training.entity.Configuration;
import main.utils.LoggingUtils;
import weka.attributeSelection.ASSearch;
import weka.classifiers.rules.ZeroR;
import weka.core.SelectedTag;
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
import weka.filters.supervised.instance.SpreadSubsample;

import static weka.attributeSelection.BestFirst.TAGS_SELECTION;

/**
 * Uses Weka API to perform a machine learning classification on the dataset.
 * */
public class WekaManager {

	protected Instances trainingSet;
	protected Instances testSet;

	protected List<Configuration> configurations;

	/**
	 * Prepares the Weka analysis with every possible configuration of:
	 * 	- Feature selection models
	 * 	- Sampling models
	 * 	- Classifiers
	 * 	- Cost matrices
	 * */
	public WekaManager() {
		Classifier[] classifiers = prepareClassifiers();
		ASSearch[] featSelection = prepareFeaturesSelection();
		Filter[] samplings = prepareSampling();
		CostMatrix[] sensitivities = prepareSensitivity();

		configureAnalysis(classifiers, featSelection, samplings, sensitivities);
	}

	private ASSearch[] prepareFeaturesSelection() {

		ASSearch[] featSel = new ASSearch[2];

		BestFirst forwardSearch = new BestFirst(); // default best first
		featSel[0] = forwardSearch; // forward search

		BestFirst backwardSearch = new BestFirst();
		backwardSearch.setDirection(new SelectedTag(0, TAGS_SELECTION)); // backward search
		featSel[1] = backwardSearch;

		return featSel;
	}

	private Filter[] prepareSampling() {
		Filter[] sampling = new Filter[2];

		Resample resample = new Resample();
		resample.setNoReplacement(false); // noReplacement = false
		resample.setBiasToUniformClass(1.0); // -B 1.0
		sampling[0] = resample;

		SpreadSubsample spreadSubsample = new SpreadSubsample();
		spreadSubsample.setDistributionSpread(1.0);
		sampling[1] = spreadSubsample;

		return sampling;
	}

	private Classifier[] prepareClassifiers() {
		Classifier[] classifiers = new Classifier[4];

		ZeroR zeroR = new ZeroR();
		classifiers[0] = zeroR; // BASELINE: dummy classifier

		RandomForest randomForest = new RandomForest();
		classifiers[1] = randomForest; // TREE: forest of 100 random trees with unlimited depth

		NaiveBayes naiveBayes = new NaiveBayes();
		classifiers[2] = naiveBayes; // PROBABILISTIC: bayesian "naive" classification

		IBk iBk = new IBk(3);
		classifiers[3] = iBk; // LAZY: k-nearest neighbours

		return classifiers;
	}

	private CostMatrix[] prepareSensitivity() {
		CostMatrix[] costMatrices = new CostMatrix[3];

		costMatrices[0] = null; // no cost matrix

		costMatrices[1] = populateCostMatrix(1.0); //CFN = CFP

		costMatrices[2] = populateCostMatrix(10.0); //CFN = 10*CFP

		return costMatrices;
	}

	private CostMatrix populateCostMatrix(double cfn) {
		CostMatrix matrix = new CostMatrix(2);

		matrix.setCell(0, 0, 0.0);
		matrix.setCell(0, 1, cfn);
		matrix.setCell(1, 0, 1.0);
		matrix.setCell(1, 1, 0.0);

		return matrix;
	}

	private void configureAnalysis(Classifier[] classifiers, ASSearch[] featSelections, Filter[] samplings, CostMatrix[] sensitivities) {
		configurations = new ArrayList<>();

		for(Classifier classifier: classifiers){
			if(classifier.getClass().equals(ZeroR.class)){ // ZeroR is not affected by tuning methods
				Configuration config = new Configuration(classifier, null, null, null);
				configurations.add(config);

				continue;
			}

			for(ASSearch featSelection: featSelections){
				for(Filter sampling: samplings){
					for(CostMatrix sensitivity: sensitivities){
						if(sensitivity != null && sensitivity.getCell(0, 1).equals(10.0)){
							continue; // BestFirst throws an exception when dealing with CFN = 10*CPN
						}

						Configuration config = new Configuration(classifier, featSelection, sampling, sensitivity);
						configurations.add(config);
					}
				}
			}
		}
	}

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
		List<Configuration> localConfigurations = new ArrayList<>();
		for(Configuration config: configurations) {
			localConfigurations.addAll(walkForwardEvaluation(sets, config));
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
	protected String convertCSVToArff(String csvFile) throws Exception {
		//Load CSV
		CSVLoader loader = new CSVLoader();
		loader.setSource(new File(csvFile));

		loader.setOptions(new String[]{"-L", "Buggy:No,Yes"}); //Some sets may not have positive instances

		//Save ARFF
		ArffSaver saver = new ArffSaver();
		saver.setInstances(loader.getDataSet());

		saver.getInstances().deleteAttributeAt(0); //project
		saver.getInstances().deleteAttributeAt(0); //version
		saver.getInstances().deleteAttributeAt(0); //filename

		String arffFile = csvFile.replace(".csv", ".arff");

		saver.setFile(new File(arffFile));
		saver.writeBatch();

		return arffFile;
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
	protected List<Configuration> walkForwardEvaluation(List<Instances> sets, Configuration config) throws Exception {
		List<Configuration> localConfig = new ArrayList<>();

		//Configuration
		ASSearch filter = config.getFeatSelection();
		Filter sampling;
		Classifier classifier;
		Classifier baseClassifier = config.getClassifier(); //untouched copy of the classifier instance to get its name
		CostMatrix sensitivity = config.getSensitivity();

		for (int i=1; i<sets.size(); i++) { //get one set at a time to use as testing

			//Init
			classifier = config.getClassifier();
			sampling = config.getSampling();

			buildTrainingAndTestSet(sets, i); //sets initialization

			if(filter != null){
				classifier = applyFeatureSelection(filter, classifier); //feature selection
			}

			double minority = countDefectiveInstances(trainingSet, trainingSet.numInstances());
			if(sampling != null && minority != 0) {
				classifier = applySampling(sampling, classifier); //sampling
			}else{
				sampling = null;
			}

			Configuration newConfig = setLocalConfiguration(sets, baseClassifier, filter, sampling, sensitivity); //new configuration
			newConfig.setNumTrainingReleases(i);

			Map<String,Double> performance = computeEvaluation(classifier, sensitivity); //evaluation
			newConfig.setPerformances(performance);

			LoggingUtils.logPerformances(newConfig, performance);

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
	protected FilteredClassifier applyFeatureSelection(ASSearch featSel, Classifier classifier) {
		FilteredClassifier fc = new FilteredClassifier();

		AttributeSelection attSelection = new AttributeSelection();
		CfsSubsetEval eval = new CfsSubsetEval();

		attSelection.setEvaluator(eval);
		attSelection.setSearch(featSel); // Set the algorithm to use

		fc.setFilter(attSelection); // Apply filter
		fc.setClassifier(classifier);

		return fc;
	}

	/**
	 * Performs a sampling method on training and test set.
	 *
	 * @param sampling : sampling filter
	 * @param classifier : classifier used
	 * */
	protected FilteredClassifier applySampling(Filter sampling, Classifier classifier) {
		FilteredClassifier fc = new FilteredClassifier();

		if(sampling.getClass() == Resample.class) {
			int tot = trainingSet.numInstances();
			double minority = countDefectiveInstances(trainingSet, tot);
			double majority = tot - minority;

			if(minority != 0){
				double sampleSizePercent = 100 * ((majority-minority)/minority);
				((Resample)sampling).setSampleSizePercent(sampleSizePercent);
			}
		}

		fc.setFilter(sampling);
		fc.setClassifier(classifier);
		return fc;
	}

	/**
	 * Instantiates a new Configuration instance to separate different types of percentages.
	 * */
	protected Configuration setLocalConfiguration(List<Instances> sets, Classifier classifier, ASSearch filter, Filter sampling, CostMatrix sensitivity) {
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

	protected int countDefectiveInstances(Instances data, int tot) {
		int defective = 0;
		for(int i=0; i<tot; i++) {
			if(data.instance(i).stringValue(data.classIndex()).equals("Yes")) {
				defective++;
			}
		}

		return defective;
	}

	protected Map<String, Double> computeEvaluation(Classifier classifier, CostMatrix sensitivity) throws Exception {
		Evaluation eval;
		if(sensitivity != null) { //sensitivity
			classifier = applySensitivity(sensitivity, classifier);
			eval = new Evaluation(trainingSet, sensitivity);
		}else {
			eval = new Evaluation(trainingSet);
		}

		classifier.buildClassifier(trainingSet); //build classifier

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