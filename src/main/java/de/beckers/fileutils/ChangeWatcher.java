package de.beckers.fileutils;

import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;

public interface ChangeWatcher{
    public void change(Path file, Kind kind);
}