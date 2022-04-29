package main.dataset.entity;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class Bug {

    //Jira
    private final String ticketKey; //not null
    private final LocalDate openingDate; //not null
    private String openingVer; //not null
    private final LocalDate fixDate; //not null
    private String fixVer; //not null

    //Git
    private RevCommit fixCm; //not null -- if commit date does not match ticket fix date, take the last referencing cm
    private List<RevCommit> referencingCms; //could be null if the referencing cms are only the opening and/or the fix cm

    //Computed
    private List<String> affectedVers; //not null -- computed in ReleaseManager
    private String injectedVer; //not null -- computed in ReleaseManager

    public Bug(String ticketKey, LocalDate openingDate, LocalDate fixDate, List<String> affectedVersions){
        this.ticketKey = ticketKey;
        this.openingDate = openingDate;
        this.fixDate = fixDate;
        this.affectedVers = affectedVersions;
    }

    public String getTicketKey() {
        return ticketKey;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public String getOpeningVer() {
        return openingVer;
    }

    public void setOpeningVer(String openingVer) {
        this.openingVer = openingVer;
    }

    public RevCommit getFixCm() {
        return fixCm;
    }

    public void setFixCm(RevCommit fixCm) {
        this.fixCm = fixCm;
    }

    public LocalDate getFixDate() {
        return fixDate;
    }

    public String getFixVer() {
        return fixVer;
    }

    public void setFixVer(String fixVer) {
        this.fixVer = fixVer;
    }

    public List<String> getAffectedVers() {
        return affectedVers;
    }

    public void setAffectedVers(List<String> affectedVers) {
        this.affectedVers = affectedVers;
    }

    public String getInjectedVer() {
        return injectedVer;
    }

    public void setInjectedVer(String injVer) {
        this.injectedVer = injVer;
    }

    public List<RevCommit> getReferencingCms() {return this.referencingCms;}

    public void setReferencingCms(RevCommit refCm) {
        if (this.referencingCms == null) {
            this.referencingCms = new ArrayList<>();
        }

        this.referencingCms.add(refCm);
    }

    //Called in a situation where referencingCms cannot be NULL
    public RevCommit getLatestReferencingCm() {
        RevCommit latest = null;
        LocalDate latestDate;
        LocalDate cmDate;

        for(RevCommit cm: referencingCms){
            if(latest == null){
                latest = cm;

            }else{
                latestDate = latest.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                cmDate = cm.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if(cmDate.isAfter(latestDate)){
                    latest = cm;
                }
            }
        }

        return latest;
    }
}
