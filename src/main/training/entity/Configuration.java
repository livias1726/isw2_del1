package main.training.entity;

import weka.attributeSelection.ASSearch;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.filters.Filter;

import java.util.LinkedHashMap;
import java.util.Map;

public class Configuration {

    private Classifier classifier;
    private ASSearch featSelection;
    private Filter sampling;
    private CostMatrix sensitivity;

    private double trainingPercentage;
    private double defectiveTrainingPercentage;
    private double defectiveTestPercentage;

    private int numTrainingReleases;

    private Map<String, Double> performances;

    public Configuration(Classifier classifier, ASSearch featSelection, Filter sampling, CostMatrix sensitivity){
        this.classifier = classifier;
        this.featSelection = featSelection;
        this.sampling = sampling;
        this.sensitivity = sensitivity;

        this.performances = new LinkedHashMap<>();
    }

    //----------------------------------------------------Getters & Setters---------------------------------------------

    public Classifier getClassifier() {
        return classifier;
    }

    public void setClassifier(Classifier classifier) {
        this.classifier = classifier;
    }

    public ASSearch getFeatSelection() {
        return featSelection;
    }

    public void setFeatSelection(ASSearch featSelection) {
        this.featSelection = featSelection;
    }

    public Filter getSampling() {
        return sampling;
    }

    public void setSampling(Filter sampling) {
        this.sampling = sampling;
    }

    public CostMatrix getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(CostMatrix sensitivity) {
        this.sensitivity = sensitivity;
    }

    public Map<String, Double> getPerformances() {
        return performances;
    }

    public double getTrainingPercentage() {
        return trainingPercentage;
    }

    public void setTrainingPercentage(double trainingPercentage) {
        this.trainingPercentage = trainingPercentage;
    }

    public double getDefectiveTrainingPercentage() {
        return defectiveTrainingPercentage;
    }

    public void setDefectiveTrainingPercentage(double defectiveTrainingPercentage) {
        this.defectiveTrainingPercentage = defectiveTrainingPercentage;
    }

    public double getDefectiveTestPercentage() {
        return defectiveTestPercentage;
    }

    public void setDefectiveTestPercentage(double defectiveTestPercentage) {
        this.defectiveTestPercentage = defectiveTestPercentage;
    }

    public void setPerformances(Map<String, Double> performances) {
        this.performances = performances;
    }

    public int getNumTrainingReleases() {
        return numTrainingReleases;
    }

    public void setNumTrainingReleases(int numTrainingReleases) {
        this.numTrainingReleases = numTrainingReleases;
    }
}
