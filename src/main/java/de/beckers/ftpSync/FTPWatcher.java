package de.beckers.ftpsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verbindet sich mit dem FTP und schaut, ob es
 * Änderungen fuer mich gibt
 */
public class FTPWatcher extends TimerTask {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Konfig konf;
    /**
     * Die Dateien, bei denen ein Download stattfand
     */
    private final Collection<String> bearbeiteteDateien;

    public FTPWatcher(final Konfig konfig){
        this.konf = konfig;
        this.bearbeiteteDateien = new CopyOnWriteArraySet<>();
    }
    public boolean wurdeGeradeBearbeitet(final String datei){
        return this.bearbeiteteDateien.contains(datei);
    }

    @Override
    public void run() {
        this.bearbeiteteDateien.clear();
        final FTPClient ftp = new FTPClient();
        final FTPClientConfig conf = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
        conf.setServerLanguageCode("de");
        ftp.configure(conf);
        try{
            ftp.setCharset(StandardCharsets.ISO_8859_1);
            ftp.setControlEncoding(StandardCharsets.ISO_8859_1.name());
            ftp.connect(konf.getFtpHost());
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.setFileTransferMode(FTP.BINARY_FILE_TYPE);;
            if(!ftp.login(konf.getFtpUser(), konf.getFtpPwd())){
                LOGGER.warn("Konnte mich nicht am FTP anmelden");
                return;
            }
            if(!ftp.changeWorkingDirectory(konf.getFtpDir())){
                LOGGER.error("Konnte nicht in FTP-Verzeichnis wechseln");
                return;
            }
            ftp.type(FTP.BINARY_FILE_TYPE);
            final var fileNames = ftp.listNames();
            if(fileNames != null && fileNames.length > 0){
                bearbeiteNamen(fileNames, ftp);
            }
        }catch(IOException e){
            LOGGER.error(e);
        }finally{
            if(ftp.isConnected()){
                try{
                    ftp.disconnect();
                }catch(IOException ioE){
                    LOGGER.error("Exception beim Disconnect", ioE);
                }
            }
        }
    }
    private void bearbeiteNamen(final String[] names, final FTPClient ftp){
        /**
         * Enthält die schon bearbeiteten Dateien.
         * Will ja immer nur den letzten Stand
         */
        final Collection<String> usedFiles = new ArrayList<>();
        final var fileNamePattern = Pattern.compile("^(\\d){14}([cmd])(.+)$");
        final var sf = new SimpleDateFormat("yyyyMMddHHmmss");
        for(int i = names.length-1; i>= 0; i--){
            bearbeiteDateiNamen(names[i], usedFiles, fileNamePattern, sf, ftp);
        }
    }
    private void bearbeiteDateiNamen(final String ftpName, final Collection<String> usedFiles,
    final Pattern fileNamePattern, final SimpleDateFormat sf, final FTPClient ftp){
        final Matcher fileNameMatcher = fileNamePattern.matcher(ftpName);
        if(!fileNameMatcher.matches()){
            LOGGER.warn("Datei passt nicht zu pattern: {}", ftpName);
            return;
        }
        final var dateiNameUndPfad = URLDecoder.decode(fileNameMatcher.group(3), StandardCharsets.UTF_8) ;
        if(usedFiles.contains(dateiNameUndPfad)){
            LOGGER.debug("Datei kam schon mal vor: {}", ftpName);
            return;
        }
        usedFiles.add(dateiNameUndPfad);
        final File localFile = getFile(dateiNameUndPfad);
        if("d".equals(fileNameMatcher.group(2))){
            this.bearbeiteteDateien.add(dateiNameUndPfad);
            //Loeschen. Der einfachste Fall
            if(localFile.exists()){
                LOGGER.debug("Datei soll gelöscht werden {}", dateiNameUndPfad);
                deleteFile(localFile);
            }else{
                LOGGER.info("Datei war lokal nicht vorhanden und musste daher nicht gelöscht werden: {}", dateiNameUndPfad);
            }
            return;
        }
        try{
            final Date changeDate = sf.parse(fileNameMatcher.group(1));
            if(!localFile.exists()){
                this.bearbeiteteDateien.add(dateiNameUndPfad);
                LOGGER.debug("Habe Datei lokal noch nicht und lade herunter: {}", dateiNameUndPfad);
                loadFile(localFile, ftpName, ftp, changeDate);
                return;
            }
            //Neu erstellt oder geändert. Kommt für mich hier auf das gleiche raus
            if(localFile.lastModified() >= changeDate.getTime()){
                LOGGER.debug("Lokale Datei ist aktuell genug {}", ftpName);
            }else{
                this.bearbeiteteDateien.add(dateiNameUndPfad);
                loadFile(localFile, ftpName, ftp, changeDate);
            }
        }catch(ParseException p){
            LOGGER.error("Kein richtiges Datum in {}. Datei: {}", fileNameMatcher.group(1), ftpName);
        }
    }
    private void loadFile(final File target, final String ftpFileName, final FTPClient client, final Date lastModified){
        final File parentDir = target.getParentFile();
        boolean error = false;
        if(!parentDir.exists() && !parentDir.mkdirs()){
            LOGGER.error("Konnte Verzeichnis nicht erstellen: {}", parentDir.getAbsolutePath());
            return;
        }
        try(final FileOutputStream fStream = new FileOutputStream(target)){
            if(!client.retrieveFile(ftpFileName, fStream)){
                LOGGER.error("Konnte Datei nicht herunterladen {}", ftpFileName);
            }
        }catch(IOException io){
            LOGGER.error("Fehler beim Download von {}", ftpFileName, io);
            error = true;
        }
        if(!error){
            target.setLastModified(lastModified.getTime());
        }
    }
    private File getFile(final String dateiNameUndPfad){
        final String path = this.konf.getLocalDir() + '/' + dateiNameUndPfad;
        return new File(path);
    }
    private static void deleteFile(final File f){
        try{
            Files.delete(f.toPath());
        }catch(Exception err){
            LOGGER.error("Konnte Datei nicht löschen {}", f.getAbsolutePath(), err);
        }
    }
    
}
