package de.beckers.timeutil;

public abstract class AbstractTimeDB implements TimeDB{
    @Override
    public boolean isNewer(final long other){
        return this.get() < other;
    }
    @Override
    public boolean setIfNewer(final long neuWert){
        if(this.isNewer(neuWert)){
            this.set(neuWert);
            return true;
        }
        return false;
    }
}