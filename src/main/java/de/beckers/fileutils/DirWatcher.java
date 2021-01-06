package de.beckers.fileutils;

import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Überwacht Verzeichnisse nach Änderung und informiert
 * einen {@link ChangeWatcher} darüber.
 * 
 * Ideen von: https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 */
public class DirWatcher implements Runnable{

    private static final Logger LOGGER = LogManager.getLogger();

    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private boolean trace = false;

    private volatile boolean canRun = true;

    /**
     * Wird bei Änderungen informiert
     */
    private final ChangeWatcher toInform;
    /**
     * Gibt an, ob Verzeichnisse rekursiv durchsucht werden
     */
    private final boolean recursive;

    public DirWatcher(final Path dir, final boolean recure, final ChangeWatcher inform) throws IOException {
        this.toInform = inform;
        this.recursive = recure;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
 
        if (recursive) {
            LOGGER.info("Scanning: {}", dir);
            registerAll(dir);
            LOGGER.info("done");
        } else {
            register(dir);
        }
         // enable trace after initial registration
         this.trace = true;
    }
    public void stop(){
        this.canRun = false;
    }
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                LOGGER.debug("register: {}", dir);
            } else if (!dir.equals(prev)) {
                 LOGGER.debug("update: {}} -> {}}", prev, dir);
            }
        }
        keys.put(key, dir);
    }
    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    private boolean bearbeiteEvent(final WatchEvent event, final Path dir){
        if(!this.canRun){
            return false;
        }
        WatchEvent.Kind kind = event.kind();

        // TBD - provide example of how OVERFLOW event is handled
        if (kind == OVERFLOW) {
            return true;
        }

        // Context for directory entry event is the file name of entry
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = dir.resolve(name);

        // print out event
        LOGGER.debug("{}: {}", event.kind().name(), child);

        if(kind == ENTRY_DELETE && Files.exists(child, LinkOption.NOFOLLOW_LINKS)){
            LOGGER.info("Datei als gelöscht gemeldet, aber noch vorhanden");
            return true;
        }

        this.toInform.change(child, kind);

        // if directory is created, and watching recursively, then
        // register it and its sub-directories
        if (recursive && (kind == ENTRY_CREATE)) {
            try {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    registerAll(child);
                }
            } catch (IOException x) {
                LOGGER.error(x);
            }
        }
        return true;
    }
    private boolean doAction(){
        // wait for key to be signalled
        WatchKey key;
        try {
            key = watcher.take();
        } catch (InterruptedException x) {
            return false;
        }
        if(!this.canRun){
            return false;
        }

        Path dir = keys.get(key);
        if (dir == null) {
            LOGGER.error("WatchKey not recognized!! {}", key);
            return true;
        }

        for (WatchEvent<?> event: key.pollEvents()) {
            if(!bearbeiteEvent(event, dir)){
                return false;
            }
        }

        // reset key and remove from set if directory no longer accessible
        boolean valid = key.reset();
        if (!valid) {
            LOGGER.info("Key nicht mehr valide. Verzeichnis wohl nicht mehr da: {}", key);
            keys.remove(key);

            // all directories are inaccessible
            if (keys.isEmpty()) {
                LOGGER.info("Keine Keys mehr vorhanden. Höre auf");
                return false;
            }
        }
        return true;
    }

    @Override
    public void run() {
        while (this.canRun) {
            if(!doAction()){
                break;
            }
        }
    }
    
}