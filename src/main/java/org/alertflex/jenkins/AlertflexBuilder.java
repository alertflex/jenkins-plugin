package org.alertflex.jenkins;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Result;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.net.ssl.*;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import org.apache.commons.codec.binary.Base64;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class AlertflexBuilder extends Builder implements SimpleBuildStep {

    private final String url;
    private final String user;
    private final String password;
    private final String playbook;
    private final String timer;
    private final String critical;
    private final String major;
    private final String minor;
    private final int timerThld;
    private final int criticalThld;
    private final int majorThld;
    private final int minorThld;

    @DataBoundConstructor
    public AlertflexBuilder(String url, String user, String password, String playbook,
        String timer, String critical, String major, String minor) {

        this.url = url;
        this.user = user;
        this.password = password;
        this.playbook = playbook;
        this.timer = timer;
        this.critical = critical;
        this.major = major;
        this.minor = minor;
        this.timerThld = Integer.parseInt(timer);
        this.criticalThld = Integer.parseInt(critical);
        this.majorThld = Integer.parseInt(major);
        this.minorThld = Integer.parseInt(minor);
    }

    public String getUrl() {
        return url;
    }
    public String getUser() {
        return user;
    }
    public String getPassword() {
        return password;
    }
    public String getPlaybook() {
        return playbook;
    }
    public String getTimer() {
        return timer;
    }
    public String getCritical() {
        return critical;
    }
    public String getMajor() {
        return major;
    }
    public String getMinor() {
        return minor;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        disableSslVerification();

        String authString = user + ":" + password;
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);

        URL workerUrl = new URL(url + ":8181/alertflex-ctrl/rest/playbook/" + playbook);
        HttpURLConnection urlConnection = (HttpURLConnection) workerUrl.openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        urlConnection.setRequestMethod("POST");

        int responseCode = urlConnection.getResponseCode();

        if (responseCode != 200) {
            listener.getLogger().println("Error run playbook");
            urlConnection.disconnect();
            run.setResult(Result.FAILURE);
            return;
        } else listener.getLogger().println("Playbook is running");

        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        String report_uuid = response.toString();

        urlConnection.disconnect();

        if (report_uuid == null || report_uuid.isEmpty()) {
            listener.getLogger().println("Error get report UUID");
            run.setResult(Result.FAILURE);
            return;
        }

        workerUrl = new URL(url + ":8181/alertflex-ctrl/rest/report/" + report_uuid);
        String report = "";

        for (int i = 0; i < timerThld; i++) {

            urlConnection = (HttpURLConnection) workerUrl.openConnection();
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
            urlConnection.setRequestProperty("Content-Type", "application/json");
            urlConnection.setRequestMethod("GET");

            responseCode = urlConnection.getResponseCode();

            if (responseCode != 200) {
                listener.getLogger().println("Error get report");
                urlConnection.disconnect();
                run.setResult(Result.FAILURE);
                return;
            }

            in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));
            response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            report = response.toString();

            urlConnection.disconnect();

            if (report == null || report.isEmpty()) {

                listener.getLogger().println("Report error");
                run.setResult(Result.FAILURE);
                return;
            }

            if (report.equals("wait")) {
                Thread.sleep(60000L);
                continue;
            }
            else break;
        }

        if (report.equals("wait")) {
            listener.getLogger().println("Report wait error");
            run.setResult(Result.FAILURE);
            return;
        }

        JSONObject reportObj = new JSONObject(report);
        String reportType = reportObj.getString("report_type");

        List<Alerts> alertsList = new ArrayList();
        JSONArray alertsArray  = reportObj.getJSONArray("report");

        int criticalCounter = 0;
        int majorCounter = 0;
        int minorCounter = 0;

        for (int i=0; i < alertsArray.length(); i++) {

            JSONObject alertObj = alertsArray.getJSONObject(i);

            String source = alertObj.getString("source");

            String status = alertObj.getString("status");

            int severity = alertObj.getInt("severity");

            int num = alertObj.getInt("num");

            switch (severity) {
                case 1 : minorCounter = minorCounter + num;
                    break;
                case 2 : majorCounter = majorCounter + num;
                    break;
                case 3 : criticalCounter = criticalCounter + num;
                    break;
                default:
                    break;
            }

            alertsList.add(new Alerts(source, Integer.toString(severity), Integer.toString(num), status));
        }

        run.addAction(new AlertflexAction(alertsList, reportType));

        if ( criticalCounter >= criticalThld && criticalThld != 0) {
            listener.getLogger().println("The threshold for critical alerts has been reached.");
            run.setResult(Result.FAILURE);
            return;
        }

        if ( majorCounter >= majorThld && majorThld != 0) {
            listener.getLogger().println("The threshold for major alerts has been reached.");
            run.setResult(Result.FAILURE);
            return;
        }

        if ( minorCounter >= minorThld && minorThld != 0) {
            listener.getLogger().println("The threshold for minor alerts has been reached.");
            run.setResult(Result.FAILURE);
            return;
        }

        run.setResult(Result.SUCCESS);
    }

    private static void disableSslVerification() {
        try
        {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("valid error");
            if (value.length() < 4)
                return FormValidation.warning("too short");
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Alertflex plugin";
        }
    }
}
