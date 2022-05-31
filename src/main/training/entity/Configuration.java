package main.training.entity;

import weka.attributeSelection.ASSearch;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;

import java.util.LinkedHashMap;
import java.util.Map;

public class Configuration {

    private final Classifier classifier;
    private final String classifierName;
    private final ASSearch featSelection;
    private String featSelectionMethod;
    private final Filter sampling;
    private String samplingMethod;
    private final CostMatrix sensitivity;
    private String costSensitivity;

    private double trainingPercentage;
    private double defectiveTrainingPercentage;
    private double defectiveTestPercentage;

    private int numTrainingReleases;

    private Map<String, Double> performances;

    public Configuration(Classifier classifier, ASSearch featSelection, Filter sampling, CostMatrix sensitivity){
        this.classifier = classifier;
        this.classifierName = classifier.getClass().toString()
                                .replaceFirst("class weka\\.classifiers\\.(.*)\\.", "");

        this.featSelection = featSelection;
        if(featSelection != null){
            setFeatSelectionMethod(featSelection.getOptions());
        }

        this.sampling = sampling;
        if(sampling != null){
            if(sampling.getClass().equals(Resample.class))
                this.samplingMethod = "Over-sampling";
            else{
                this.samplingMethod = "Under-sampling";
            }
        }

        this.sensitivity = sensitivity;
        if(sensitivity != null){
            this.costSensitivity = "Sensitive threshold";
        }

        this.performances = new LinkedHashMap<>();
    }

    //----------------------------------------------------Getters & Setters---------------------------------------------

    public Classifier getClassifier() {
        return classifier;
    }

    public ASSearch getFeatSelection() {
        return featSelection;
    }

    public Filter getSampling() {
        return sampling;
    }

    public CostMatrix getSensitivity() {
        return sensitivity;
    }

    public Map<String, Double> getPerformances() {
        return performances;
    }

    public void setPerformances(Map<String, Double> performances) {
        this.performances = performances;
    }

    public String getClassifierName() {
        return classifierName;
    }

    public String getFeatSelectionMethod() {
        return featSelectionMethod;
    }

    public void setFeatSelectionMethod(String[] options) {
        int dirIndex = 0;
        for(String opt: options){
            if(opt.equals("-D")){
                dirIndex++;
                break;
            }
            dirIndex++;
        }

        if(options[dirIndex].equals("0")){
            this.featSelectionMethod = "Backward search";
        }else if(options[dirIndex].equals("1")){
            this.featSelectionMethod = "Forward search";
        }
    }

    public String getSamplingMethod() {
        return samplingMethod;
    }

    public String getCostSensitivity() {
        return costSensitivity;
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

    public int getNumTrainingReleases() {
        return numTrainingReleases;
    }

    public void setNumTrainingReleases(int numTrainingReleases) {
        this.numTrainingReleases = numTrainingReleases;
    }
}
