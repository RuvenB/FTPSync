package de.beckers.ftpsync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Enthält die Konfiguration, welche in der
 * {@link java.util.prefs.Preferences} abgelegt wurde.
 */
public class Konfig {
    private String localDir;
    private String ftpDir;
    private String ftpHost;
    private String ftpUser;
    private String ftpPwd;
    private String localCacheDir;
    private Collection<Pattern> toExclude;
    private long ftpScanInterval;
    private long uploadInterval;

    public String getLocalDir() {
        return localDir;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public String getFtpDir() {
        return ftpDir;
    }

    public void setFtpDir(String ftpDir) {
        this.ftpDir = ftpDir;
    }

    public String getFtpHost() {
        return ftpHost;
    }

    public void setFtpHost(String ftpHost) {
        this.ftpHost = ftpHost;
    }

    public String getFtpUser() {
        return ftpUser;
    }

    public void setFtpUser(String ftpUser) {
        this.ftpUser = ftpUser;
    }

    public String getFtpPwd() {
        return ftpPwd;
    }

    public void setFtpPwd(String ftpPwd) {
        this.ftpPwd = ftpPwd;
    }

    public String getLocalCacheDir() {
        return localCacheDir;
    }

    public void setLocalCacheDir(String localCacheDir) {
        this.localCacheDir = localCacheDir;
    }

    public long getFtpScanInterval() {
        return ftpScanInterval;
    }

    public void setFtpScanInterval(long ftpScanInterval) {
        this.ftpScanInterval = ftpScanInterval;
    }

    public long getUploadInterval() {
        return uploadInterval;
    }

    public void setUploadInterval(long uploadInterval) {
        this.uploadInterval = uploadInterval;
    }

    public Collection<Pattern> getToExclude() {
        return toExclude;
    }

    public void setToExclude(Collection<Pattern> toExclude) {
        this.toExclude = toExclude;
    }
    public void setToExclude(final String excString){
        this.toExclude = stringToPatt(excString);
    }
    private static Collection<Pattern> stringToPatt(final String excString){
        if(excString == null || excString.isBlank()){
            return Collections.emptyList();
        }
        final String[] parts = excString.split("\n");
        final Collection<Pattern> patts = new ArrayList<>(parts.length);
        for(String p : parts){
            patts.add(Pattern.compile(p.trim()));
        }
        return patts;
    }
    public String getToExcludeString(){
        if(this.toExclude == null || this.toExclude.isEmpty()){
            return "";
        }
        final StringBuilder b = new StringBuilder();
        boolean isFirst = true;
        for(Pattern p : this.toExclude){
            if(isFirst){
                isFirst = false;
            }else{
                b.append('\n');
            }
            b.append(p.pattern());
        }
        return b.toString();
    }
}
