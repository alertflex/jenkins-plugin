package org.alertflex.jenkins;

import hudson.model.Run;
import jenkins.model.RunAction2;

import java.util.List;

public class AlertflexAction implements RunAction2 {

    private String reportType;

    private List<Alerts> alertsList;

    private transient Run<?, ?> run;

    public AlertflexAction(List<Alerts> al, String t) {

        this.alertsList = al;
        this.reportType = t;
    }

    public List<Alerts> getAlertsList() {
        return alertsList;
    }

    public String getReportType() {
        return reportType;
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return "AlertflexReport";
    }

    @Override
    public String getUrlName() {
        return "alertflex-report";
    }
}
