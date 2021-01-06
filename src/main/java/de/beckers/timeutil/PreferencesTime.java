package de.beckers.timeutil;

import java.util.prefs.Preferences;

public class PreferencesTime extends AbstractTimeDB{
    final Preferences pref;
    final String key;

    public PreferencesTime(final Preferences p, final String k){
        this.pref = p;
        this.key = k;
    }

    @Override
    public long get() {       
        return pref.getLong(this.key, 0);
    }

    @Override
    public void set(final long time) {
        pref.putLong(this.key, time);
    }
    
}
