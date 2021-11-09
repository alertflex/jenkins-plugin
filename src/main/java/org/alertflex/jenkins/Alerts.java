package org.alertflex.jenkins;

public class Alerts {

    String source;
    String severity;
    String num;
    String status;

    public Alerts(String source, String sev, String n, String status) {
        this.source = source;
        this.severity = sev;
        this.num = n;
        this.status = status; // all, new
    }

    public String getSource () {
        return source;
    }

    public void setSource (String s) {
        this.source = s;
    }

    public String getSeverity () {
        return severity;
    }

    public void setSeverity (String s) {
        this.severity = s;
    }

    public String getNum () {
        return num;
    }

    public void setNum (String n) {
        this.num = n;
    }

    public String getStatus () {
        return status;
    }

    public void setStatus (String s) {
        this.status = s;
    }

}
