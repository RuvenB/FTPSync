package de.beckers.timeutil;

public interface TimeDB {
    /**
     * Gibt die gespeicherte Zeit zurück.
     * In Millisekunden von 1.1.1970.
     * Wie bei System.currentTimeMillis();
     * @return
     */
    public long get();
    
    /**
     * Setzt die gespeicherte Zeit auf den gewünschten Wert
     * @param time
     */
    public void set(long time);

    /**
     * Gibt zurueck, ob die übergebene Zeit neuer als die intern gespeicherte ist
     * @param timeToCheck
     * @return
     */
    public boolean isNewer(long timeToCheck);

    /**
     * Wenn die uebergebene Zeit neuer als die gespeicherte ist, wird diese
     * als interne Zeit abgelegt
     * @param newTime
     * @return
     */
    public boolean setIfNewer(long newTime);
}
