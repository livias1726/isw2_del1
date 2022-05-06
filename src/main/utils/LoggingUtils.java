package main.utils;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
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

    /*
    public static void logException(Exception e) {
        String log = Arrays.toString(e.getStackTrace());
        logger.log(Level.SEVERE, log);
    }

     */
}
