package main.training.control;

import main.training.entity.Configuration;
import main.utils.LoggingUtils;
import weka.attributeSelection.ASSearch;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.filters.Filter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WekaManagerITT extends WekaManager{

    public WekaManagerITT() throws Exception {
        super();
    }

    /**
     * Executes the ML analysis on the dataset.
     * @return : list of Configuration instances to give output analysis
     * */
    public List<Configuration> setWekaITT(String trainingSetPath, String testSetPath, int idx) throws Exception {

        String arffTraining = convertCSVToArff(trainingSetPath); //get the arff from the csv
        String arffTest = convertCSVToArff(testSetPath); //get the arff from the csv

        //Load the dataset
        ArffLoader loader = new ArffLoader();

        loader.setSource(new File(arffTraining));
        trainingSet = loader.getDataSet();
        trainingSet.setClassIndex(trainingSet.numAttributes() - 1); //set class index

        loader.setSource(new File(arffTest));
        testSet = loader.getDataSet();
        testSet.setClassIndex(testSet.numAttributes() - 1); //set class index

        //walk-forward
        List<Configuration> localConfigurations = new ArrayList<>();
        for(Configuration config: configurations) {
            localConfigurations.add(walkForwardEvaluationITT(config, idx));
        }

        return localConfigurations;
    }

    /**
     * Implements Walk-forward method to train and test the classifier with chosen model configuration.
     * */
    private Configuration walkForwardEvaluationITT(Configuration config, int idx) throws Exception {

        //Configuration
        ASSearch filter = config.getFeatSelection();
        Filter sampling = config.getSampling();
        Classifier classifier = config.getClassifier();
        Classifier baseClassifier = config.getClassifier(); //untouched copy of the classifier instance to get its name
        CostMatrix sensitivity = config.getSensitivity();

        if(filter != null){
            classifier = applyFeatureSelection(filter, classifier); //feature selection
        }

        double minority = countDefectiveInstances(trainingSet, trainingSet.numInstances());
        if(sampling != null && minority != 0) {
            classifier = applySampling(sampling, classifier); //sampling
        }else{
            sampling = null;
        }

        List<Instances> sets = new ArrayList<>();
        sets.add(trainingSet);
        sets.add(testSet);

        Configuration newConfig = setLocalConfiguration(sets, baseClassifier, filter, sampling, sensitivity); //new configuration
        newConfig.setNumTrainingReleases(idx);

        Map<String,Double> performance = computeEvaluation(classifier, sensitivity); //evaluation
        newConfig.setPerformances(performance);

        LoggingUtils.logPerformances(newConfig, performance);

        return newConfig;
    }
}
