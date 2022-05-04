package main.utils;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class LoggingUtils {

    private static Logger logger;

    //Instantiation
    private static LoggingUtils instance = null;

    private LoggingUtils(Logger loggerArg) {
        logger = loggerArg;
    }

    public static LoggingUtils getInstance(Logger logger) {
        if(instance == null) {
            instance = new LoggingUtils(logger);
        }
        return instance;
    }

    public static void logList(String msg, Set<String> list) {
        logger.info(msg + Arrays.toString(list.toArray()));
    }

    public static void logInt(String msg, int arg){
        logger.info(msg + arg);
    }

    public static void logDouble(String msg, double arg) {
        logger.info(msg + arg);
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

        logger.info(stringBuilder.toString());
    }

    public static void logCommitsPerRelease(String[] releases, Map<String, Map<RevCommit, LocalDate>> cmPerRelease) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\nCOMMITS PER RELEASE:");
        for(String rel: releases){
            stringBuilder.append("\n\t").append(rel).append(" -> ").append(cmPerRelease.get(rel).keySet().size());
        }

        logger.info(stringBuilder.toString());
    }

    public static void logFilesPerRelease(Set<Map.Entry<String, List<FileMetadata>>> entries) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\nFILES PER RELEASE:");
        for(Map.Entry<String, List<FileMetadata>> entry: entries){
            stringBuilder.append("\n\t").append(entry.getKey()).append(" -> ").append(entry.getValue().size());
        }

        logger.info(stringBuilder.toString());
    }
}
