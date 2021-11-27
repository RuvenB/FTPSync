package de.beckers.ftpsync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Timer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.beckers.fileutils.DirWatcher;
import de.beckers.fileutils.TimeChangeWatcher;
import de.beckers.timeutil.TimeDB;

/**
 * Beinhaltet die Laufenden Threads, welche auf einmal gestartet
 * und gestoppt werden können
 */
public class Threads implements StartStoppable{

    private static final Logger LOGGER = LogManager.getLogger();

    private final Konfig konf;
    private final TimeDB timeDB;

    private DirWatcher watcher;
    private Timer uploadTimer;
    private Timer ftpScanTimer;

    public Threads(final Konfig k, final TimeDB timeChange){
        this.konf = k;
        this.timeDB = timeChange;
        this.watcher = null;
    }

    @Override
    public void start() {
        final Path dir = new File(konf.getLocalDir()).toPath();
        final FTPWatcher ftpWatcher = new FTPWatcher(konf);
        final FileCopier copier = new FileCopier(konf.getLocalDir(), new File(konf.getLocalCacheDir()),
            konf.getToExclude(), ftpWatcher);
        final long now = System.currentTimeMillis();
        initialLocalScan(dir, copier, this.timeDB.get());
        this.timeDB.set(now); //Damit beim naechsten mal nicht nochmal die gleichen hochlädt
        this.uploadTimer = new Timer("Uploadtimer");
        this.uploadTimer.schedule(new Uploader(this.konf), 100, konf.getUploadInterval());

        this.ftpScanTimer = new Timer("FTPScan");
        this.ftpScanTimer.schedule(ftpWatcher, 5000);

        try{
            this.watcher = new DirWatcher(dir, true, new TimeChangeWatcher(copier, this.timeDB));
            new Thread(this.watcher).start();
        }catch(IOException e){
            LOGGER.error(e);
        }
    }

    @Override
    public void stop() {
        if(this.uploadTimer != null){
            this.uploadTimer.cancel();
        }
        if(this.ftpScanTimer != null){
            this.ftpScanTimer.cancel();
        }
        if(this.watcher != null){
            this.watcher.stop();
        }
    }
    /**
     * Geht initial einmal das Verzeichnis durch um Änderungen vor dem Start
     * des {@link DirWatcher} zu verarbeiten
     * 
     * @param dir Verzeichnis welches durchsucht wird
     * @param copier Wem neuere Dateien verfüttert werden
     * @param cutoff Änderungsdatum ab dem Dateien als neu angesehen werden
     */
    private static void initialLocalScan(final Path dir, final FileCopier copier, final long cutoff){
        try{
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(attrs.lastModifiedTime().toMillis() > cutoff){
                        copier.change(file, StandardWatchEventKinds.ENTRY_MODIFY);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        }catch(IOException e){
            LOGGER.error("Fehler beim initialen Scannen des Verzeichnisses", e);
        }
    }
}
