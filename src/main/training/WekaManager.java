package main.training;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.util.Pair;
import weka.attributeSelection.ASSearch;
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

public class WekaManager {

	private Map<Integer, List<Integer>> configurations;
	private final ASSearch[] featSels = {null, new BestFirst()};
	private final Filter[] samplings = {null, new Resample(), new SpreadSubsample(), new SMOTE()};
	private final Classifier[] classifiers = {new RandomForest(), new NaiveBayes(), new IBk(3)};
	private final CostMatrix[] sensitivities = {null, new CostMatrix(2), new CostMatrix(2)};
	private Map<Pair<Integer,Integer>, List<Double>> performances = new LinkedHashMap<>();
	
	private static WekaManager instance = null;
	public static WekaManager getInstance() {
        if(instance == null) {
        	instance = new WekaManager();
        }

        return instance;
    }

    private WekaManager() {
    	int idx = 0;
    	configurations = new LinkedHashMap<>();
    	
    	sensitivities[1].setCell(0, 0, 0.0);
    	sensitivities[1].setCell(0, 1, 1.0);
    	sensitivities[1].setCell(1, 0, 1.0);
    	sensitivities[1].setCell(1, 1, 0.0);
    	
    	//CFN = 10*CFP
    	sensitivities[2].setCell(0, 0, 0.0);
    	sensitivities[2].setCell(0, 1, 10.0);
    	sensitivities[2].setCell(1, 0, 1.0);
    	sensitivities[2].setCell(1, 1, 0.0);
    	
    	int i;
    	int j;
    	int z;
    	int k;
    	for(i=0; i<featSels.length; i++) {
    		for(j=0; j<samplings.length; j++){
    			for(z=0; z<classifiers.length; z++) {
    				for(k=0; k<sensitivities.length; k++) {
    					List<Integer> list = new ArrayList<>();
    					list.add(i);
    					list.add(j);
    					list.add(z);
    					list.add(k);
    					configurations.put(idx, list);
    					
    					idx++;
    				}
    			}
    		}
    	}
    }

	public Map<Pair<Integer,Integer>, List<Double>> getPerformances() {
		return performances;
	}
	
	public Object getConfigurations(Integer numConfig, int element) {
		List<Integer> list = this.configurations.get(numConfig);
		switch(element) {
			case 0:
				return this.featSels[list.get(element)];
			case 1:
				return this.samplings[list.get(element)];
			case 2:
				return this.classifiers[list.get(element)];
			case 3:
				return this.sensitivities[list.get(element)];
			default:
				return null;
		}
	}
	
	public void setWeka(String path, Set<String> set) throws Exception {
		//Data set
		CSVLoader loader = new CSVLoader();
		loader.setSource(new File(path));
		Instances data = loader.getDataSet();
		data.setClassIndex(data.numAttributes() - 1);
		
		List<Instances> sets = separateFolds(data, set);
		
		int idx = 0;
		for(List<Integer> l: configurations.values()) {
			walkForwardPerRelease(sets, featSels[l.get(0)], samplings[l.get(1)], classifiers[l.get(2)], sensitivities[l.get(3)], idx++);		
		}
	}

	public List<Instances> separateFolds(Instances data, Set<String> set) {
		List<Instances> res = new ArrayList<>();
		
		Iterator<String> iter = set.iterator();
		String rel;
		int i;
		Integer first = null;
		int toCopy = 0;
		while(iter.hasNext()) {
			rel = iter.next();
			for(i=0; i<data.numInstances(); i++) {
				if(data.instance(i).stringValue(1).equals(rel)) {
					if(first == null) {
						first = i;
					}
					toCopy++;
				}
			}
			
			if(first != null) {
				res.add(new Instances(data,first,toCopy));
				toCopy = 0;
				first = null;
			}
		}
		
		return res;
	}

	private void walkForwardPerRelease(List<Instances> sets, ASSearch filter, Filter sampling, Classifier classifier, CostMatrix sensitivity, int idx) throws Exception {
		int folds = sets.size();
		int i;
		
		Instances trainTmp = new Instances(sets.get(0), 0, 0);
		//Get a fold at a time to use as testing
		for (i=0; i<folds; i++) {
			Instances test = sets.get(i);
			test.setClassIndex(test.numAttributes() - 1);
			
			//Get every fold before the test one to use as training
			if(i>0) {
				trainTmp = combineInstances(trainTmp, sets.get(i-1));
			}
			
			Instances train = trainTmp;
			train.setClassIndex(train.numAttributes() - 1);
			
			Pair<Instances, Instances> instances = new Pair<>(train, test);
			
			//feature selection
			if(filter != null) {
				instances = applyFeatureSelection(filter, instances);
			}
			
			//sampling
			if(sampling != null) {
				instances = applySampling(sampling, instances, classifier);
			}

			train = instances.getKey();
			test = instances.getValue();
			
			Double trainingPerc = computeTrainingPerc(train, sets);
			Pair<Double, Double> defectivePerc = computeDefectivePerc(train, test);
			
			//sensitivity
			Evaluation eval;
			if(sensitivity != null) {
				classifier = applySensitivity(sensitivity, classifier);
				eval = new Evaluation(test, sensitivity);
			}else {
				eval = new Evaluation(test);
			}
			
			//Build classifier
			if(train.numInstances() == 0) {
				classifier.buildClassifier(test);
			}else {
				classifier.buildClassifier(train);
			}
			
			//Evaluation
			eval.evaluateModel(classifier, test);
			saveEvaluation(i, trainingPerc, defectivePerc.getKey(), defectivePerc.getValue(), idx, eval);
		}
	}
	
	private Double computeTrainingPerc(Instances train, List<Instances> sets) {
		int trainIs = train.numInstances();
		int tot = 0;
		for(Instances is: sets) {
			tot += is.numInstances();
		}
		
		if(tot == 0) {
			return null;
		}
		
		return ((double)trainIs/tot)*100;
	}

	private Pair<Double, Double> computeDefectivePerc(Instances train, Instances test) {
		Double trainDef;
		Double testDef;
		if(train.numInstances() == 0) {
			trainDef = 0.0;
		}else {
			int totTrain = train.numInstances();
			int defTrain = countDefectiveInstances(train, totTrain);
			
			trainDef = ((double)defTrain/totTrain)*100;
		}
		
		int totTest = test.numInstances();
		int defTest = countDefectiveInstances(test, totTest);
		
		testDef = ((double)defTest/totTest)*100;
		
		return new Pair<>(trainDef, testDef);
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

	private CostSensitiveClassifier applySensitivity(CostMatrix sensitivity, Classifier classifier) throws Exception {
		CostSensitiveClassifier csc = new CostSensitiveClassifier();
		csc.setClassifier(classifier);
		csc.setCostMatrix(sensitivity);
		
		boolean minExpCost;
		if(sensitivity.getElement(0, 1) == 10.0) {
			minExpCost = false;
		}else {
			minExpCost = true;
		}
		csc.setMinimizeExpectedCost(minExpCost);
		
		return csc;
	}

	private Pair<Instances, Instances> applySampling(Filter sampling, Pair<Instances, Instances> instances, Classifier classifier) throws Exception {
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
		
		Instances train = instances.getKey();
		Instances test = instances.getValue();
		
		if(train.numInstances() != 0) {
			sampling.setInputFormat(train);
			fc.setFilter(sampling);
			train = Filter.useFilter(train, sampling);
		}else {
			sampling.setInputFormat(test);
			fc.setFilter(sampling);
			test = Filter.useFilter(test, sampling);
		}
		
		return new Pair<>(train, test);
	}

	private Pair<Instances, Instances> applyFeatureSelection(ASSearch featSel, Pair<Instances, Instances> instances) throws Exception {
		AttributeSelection filter = new AttributeSelection();
		CfsSubsetEval evaluator = new CfsSubsetEval();
		filter.setEvaluator(evaluator);
		filter.setSearch(featSel);
		
		Instances train = instances.getKey();
		Instances test = instances.getValue();
		
		if(train.numInstances() != 0) {
			filter.setInputFormat(train);
			train = Filter.useFilter(train, filter);
		}else {
			filter.setInputFormat(test);
		}
		
		test = Filter.useFilter(test, filter);
		
		return new Pair<>(train, test);
	}

	private void saveEvaluation(int numTrainRel, double trainPerc, double defectiveTrain, double defectiveTest, int configIdx, Evaluation eval) {
		List<Double> newPerf = new ArrayList<>();
		newPerf.add(trainPerc);
		newPerf.add(defectiveTrain);
		newPerf.add(defectiveTest);
		
		newPerf.add(eval.numTruePositives(1));
		newPerf.add(eval.numFalsePositives(1)); 
		newPerf.add(eval.numTrueNegatives(1)); 
		newPerf.add(eval.numFalseNegatives(1));
		newPerf.add(eval.precision(1));
		newPerf.add(eval.recall(1));
		newPerf.add(eval.areaUnderROC(1));
		newPerf.add(eval.kappa());
		
		Pair<Integer, Integer> pair = new Pair<>(configIdx, numTrainRel);
		performances.put(pair, newPerf);
	}

	public Instances combineInstances(Instances inst1, Instances inst2) {
		Instances inst = new Instances(inst1);
		for(int i = 0; i < inst2.numInstances(); i++) {
			inst.add(inst2.instance(i));
		}
		
		return inst;
	}
}
