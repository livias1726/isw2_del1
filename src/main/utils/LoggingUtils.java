package main.utils;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import main.training.entity.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingUtils {

    private static Logger logger;
    public static void setLogger(Logger loggerPar) {
        logger = loggerPar;
    }

    private LoggingUtils(){/**/}

    public static void logList(String msg, Set<String> list) {
        String log = msg + Arrays.toString(list.toArray());
        logger.info(log);
    }

    public static void logInt(String msg, int arg){
        String log = msg + arg;
        logger.info(log);
    }

    public static void logDouble(String msg, double arg) {
        DecimalFormat df = new DecimalFormat("#.00");

        String log = msg + df.format(arg);
        logger.info(log);
    }

    public static void logBugsInformation(List<Bug> bugs) {
        logInt("Number of bugs with complete information: ", bugs.size());

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\nBUGS:");
        for(Bug bug: bugs){
            stringBuilder.append("\n\t").append(bug.getTicketKey()).
                    append(": {Injected: ").append(bug.getInjectedVer()).
                    append(", Opening: ").append(bug.getOpeningVer()).
                    append(", Fix: ").append(bug.getFixVer()).
                    append(", Affected: ").append(bug.getAffectedVers()).
                    append("}");
        }

        String log = stringBuilder.toString();
        logger.info(log);
    }

    public static void logCommitsPerRelease(String[] releases, Map<String, Map<RevCommit, LocalDate>> cmPerRelease) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\nCOMMITS PER RELEASE:");
        for(String rel: releases){
            stringBuilder.append("\n\t").append(rel).append(" -> ").append(cmPerRelease.get(rel).keySet().size());
        }

        String log = stringBuilder.toString();
        logger.info(log);
    }

    public static void logFilesPerRelease(Set<Map.Entry<String, List<FileMetadata>>> entries) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\nFILES PER RELEASE:");
        for(Map.Entry<String, List<FileMetadata>> entry: entries){
            stringBuilder.append("\n\t").append(entry.getKey()).append(" -> ").append(entry.getValue().size());
        }

        String log = stringBuilder.toString();
        logger.info(log);
    }

    public static void logException(Exception e) {
        String log = "An exception occurred: " +  e.getMessage();
        logger.log(Level.SEVERE, log);
    }

    public static void logPerformances(Configuration config, Map<String, Double> performance) {
        logger.setLevel(Level.INFO);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\nPERFORMANCES:");

        stringBuilder.append("\n\t").append("Classifier: ").append(config.getClassifierName()); //Classifier

        //Balancing
        stringBuilder.append("\n\t").append("Sampling: ");
        if(config.getSampling() != null) {
            stringBuilder.append(config.getSamplingMethod());
        }

        //FeatureSelection
        stringBuilder.append("\n\t").append("Features selection: ");
        if(config.getFeatSelection() != null) {
            stringBuilder.append(config.getFeatSelectionMethod());
        }

        //Sensitivity
        stringBuilder.append("\n\t").append("Cost Matrix: ");
        if(config.getSensitivity() != null) {
            stringBuilder.append(config.getCostSensitivity());
        }

        stringBuilder.append("\n\t").append("Performances: ").append(performance);

        String log = stringBuilder.toString();

        logger.info(log);
    }
}
