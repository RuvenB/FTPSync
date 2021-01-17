package de.beckers.ftpsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.regex.Pattern;

import de.beckers.fileutils.ChangeWatcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Kopiert die Dateien in das gewünschte Verzeichnis mit dem passenden Namen
 */
public final class FileCopier implements ChangeWatcher{
    private static final Logger LOGGER = LogManager.getLogger();

    private final String basePath;
    private final File targetDir;
    private final SimpleDateFormat dateFormatter;
    private final Collection<Pattern> toExclude;
    private final FTPWatcher ftpWatcher;

    public FileCopier(final String pBasePath, final File pTargetDir, final Collection<Pattern> exclude, final FTPWatcher ftpW){
        this.basePath = pBasePath;
        this.targetDir = pTargetDir;
        this.dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
        this.toExclude = exclude;
        this.ftpWatcher = ftpW;
    }
    private boolean exclude(final String endPath){
        for(Pattern p : this.toExclude){
            if(p.matcher(endPath).matches()){
                return true;
            }
        }
        return false;
    }
    public void change(final Path toCopy, final Kind type){
        if(Files.isDirectory(toCopy, LinkOption.NOFOLLOW_LINKS)){
            return; //Ganze Verzeichnisse kopiere ich nicht
        }
        final String completePath = toCopy.toString();
        final String endPath = completePath.substring(this.basePath.length() + 1);
        if(exclude(endPath)){
            LOGGER.debug("Datei wird ignoriert: {}", endPath);
            return;
        }
        if(this.ftpWatcher.wurdeGeradeBearbeitet(endPath)){
            LOGGER.debug("Datei wurde gerade erst vom FTPWatcher bearbeitet: {}", endPath);
            return;
        }
        final char typeChar;
        if(type == StandardWatchEventKinds.ENTRY_DELETE){
            typeChar = 'd';
        }else if(type == StandardWatchEventKinds.ENTRY_CREATE){
            typeChar = 'c';
        }else{
            typeChar = 'm';
        }
        final long lastModified;
        if(typeChar == 'd'){
            lastModified = System.currentTimeMillis();
        }else{
            lastModified = toCopy.toFile().lastModified();
        }
        final String fileName = this.dateFormatter.format(lastModified)
        + typeChar + URLEncoder.encode( endPath, StandardCharsets.UTF_8 );

        final File target = new File(this.targetDir, fileName);
        if(typeChar == 'd'){
            //Dann reicht eine leere Datei
            try{
                target.createNewFile();
            }catch(IOException e){
                e.printStackTrace();
            }
            return;
        }
        try{
            //Etwas warten, damit ich wirklich die Datei auch bekomme nach dem
            //Erstellen
            Thread.sleep(10);
        }catch(InterruptedException e){
            LOGGER.error(e);
        }
        try{
            final OutputStream outStream = new FileOutputStream(target);
            Files.copy(toCopy, outStream);
            outStream.flush();
            outStream.close();
        }catch(IOException e){
            LOGGER.error("Fehler beim Kopieren", e);
        }
        
    }
}
