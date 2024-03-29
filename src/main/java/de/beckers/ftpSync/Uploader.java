package de.beckers.ftpsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TimerTask;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Geht beim Aufruf das Verzeichnis aus der Konfiguration durch.
 * Lädt wenn möglich alle Dateien auf den FTP hoch und löscht sie anschließend.
 */
public class Uploader extends TimerTask{

    private static final Logger LOGGER = LogManager.getLogger();

    private final Konfig konf;

    public Uploader(final Konfig k){
        this.konf = k;
    }

    @Override
    public void run() {
        final File dir = new File(this.konf.getLocalCacheDir() );
        if(!dir.exists()){
            LOGGER.error("Locales Cache-Verzeichnis existiert nicht: {}", this.konf.getLocalCacheDir());
            return;
        }
        if(!dir.isDirectory()){
            LOGGER.error("Lokales Cache Verzeichnis ist kein Verzeichnis: {}", this.konf.getLocalCacheDir());
            return;
        }
        final File[] files = dir.listFiles();
        if(files.length == 0){
            LOGGER.debug("Keine Dateien vorhanden");
            return;
        }
        final FTPClient client = new FTPClient();
        try{
            client.connect(this.konf.getFtpHost());
            if(!client.login(this.konf.getFtpUser(), this.konf.getFtpPwd())){
                LOGGER.error("Konnte mich am FTP nicht anmelden");
                return;
            }
            client.enterLocalPassiveMode();
            client.setFileType(FTP.BINARY_FILE_TYPE);
    
            InputStream inputStream;
            final String ftpDir = this.konf.getFtpDir();
            String remoteFileName;
            for(File f : files){
                inputStream = new FileInputStream(f);
                remoteFileName = ftpDir + '/' + f.getName();
                if(client.storeFile(remoteFileName, inputStream)){
                    inputStream.close();
                    deleteFile(f);
                }else{
                    LOGGER.error("Konnte Datei nicht hochladen: {}", remoteFileName);
                }
            }
        }catch(IOException e){
            LOGGER.error("Fehler bei der FTP-Verarbeitung", e);
        }finally{
            if(client.isConnected()){
                try{
                    client.disconnect();
                }catch(IOException ioE){
                    LOGGER.error("Fehler beim Schliessen der FTP-Verbindung", ioE);
                }
            }
        }
    }
    private static void deleteFile(final File f){
        try{
            Files.delete(f.toPath());
        }catch(Exception err){
            LOGGER.error("Konnte Datei nicht löschen {}", f.getAbsolutePath(), err);
        }
    }
    
}
