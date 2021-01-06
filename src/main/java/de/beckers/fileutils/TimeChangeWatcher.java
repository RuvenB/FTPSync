package de.beckers.fileutils;

import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;

import de.beckers.timeutil.TimeDB;

/**
 * Speichert die Zeit der Änderung in eine {@link de.beckers.timeutil.TimeDB}
 * und ruft einen weiteren {@link ChangeWatcher} auf.
 */
public class TimeChangeWatcher implements ChangeWatcher {

    private final TimeDB toSet;
    private final ChangeWatcher toCall;

    public TimeChangeWatcher(final ChangeWatcher watcher, final TimeDB timer){
        this.toSet = timer;
        this.toCall = watcher;
    }

    @Override
    public void change(Path file, Kind kind) {
        this.toSet.setIfNewer(System.currentTimeMillis());
        this.toCall.change(file, kind);
    }
    
}
