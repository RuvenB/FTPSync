package de.beckers.ftpsync;

import java.util.prefs.Preferences;
import java.awt.*;

import de.beckers.timeutil.PreferencesTime;
import de.beckers.timeutil.TimeDB;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main( String[] args ){
        /* TODO:
         - Job zum Upload starten
            - der geht erstmal sein Verzeichnis einmal durch
            - dann startet er die Überwachung des Verzeichnisses
         - FTP Überwacher starten
            der geht regelmäßig über den FTP
        */
        final Preferences prefs = Preferences.userNodeForPackage(App.class);
        final Konfig konf = loadKonfig(prefs);
        final TimeDB lastChange = new PreferencesTime(prefs, PrefKeys.LAST_CHANGE.name());
        final Threads th = new Threads(konf, lastChange);
        if(isKonfValid(konf)){
            th.start();
        }else{
            editKonfig(konf, prefs, th);
        }
        trayIcon(konf, prefs, th);
    }
    private static void trayIcon(final Konfig konf, final Preferences prefs, final StartStoppable threads){
        final Image image = Toolkit.getDefaultToolkit().createImage(App.class.getResource("/ftp.png"));
        final TrayIcon trayIcon = new TrayIcon(image, "FTP Sync");
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("FTP Sync");

        final SystemTray tray = SystemTray.getSystemTray();
        
        final PopupMenu menu = new PopupMenu();
        final MenuItem confiItem = new MenuItem();
        confiItem.setLabel("Konfiguration öffnen");
        confiItem.addActionListener(e-> editKonfig(konf, prefs, threads) );
        menu.add(confiItem);
        final MenuItem stopItem = new MenuItem();
        stopItem.setLabel("Beenden");
        stopItem.addActionListener(e->{
            threads.stop();
            tray.remove(trayIcon); //Verschwinde damit optisch
            //Komplett beendet sich das Ganze nach der nächsten Dateiänderung
        });
        menu.add(stopItem);
        trayIcon.setPopupMenu(menu);

        try{
            tray.add(trayIcon);
        }catch(AWTException e){
            LOGGER.error("AWTExpception beim setzen des TrayIcons", e);
        }
    }
    private static Konfig loadKonfig(Preferences preferences){
        final Konfig ret = new Konfig();
        
        ret.setLocalDir(preferences.get(PrefKeys.LOCAL_DIR.name(), ""));
        ret.setLocalCacheDir(preferences.get(PrefKeys.LOCAL_CACHE.name(), ""));
        ret.setFtpDir(preferences.get(PrefKeys.FTP_DIR.name(), ""));
        ret.setFtpHost(preferences.get(PrefKeys.FTP_HOST.name(), ""));
        ret.setFtpUser(preferences.get(PrefKeys.FTP_USER.name(), ""));
        ret.setFtpPwd(preferences.get(PrefKeys.FTP_PWD.name(), ""));
        ret.setToExclude(preferences.get(PrefKeys.EXCLUDE_PATTERN.name(), ""));

        ret.setFtpScanInterval(preferences.getLong(PrefKeys.SCAN_INTERVAL.name(), (long)5 * 60 * 1000));
        ret.setUploadInterval(preferences.getLong(PrefKeys.UPLOAD_INTERVAL.name(), (long)5 * 60 * 1000));

        return ret;
    }
    private static Label createLabel(final int top, final String text){
        final Label l = new Label(text);
        l.setBounds(30, top, 100, 20);

        return l;
    }
    private static TextField createField(final int top, final String value){
        final TextField ret = new TextField(value);
        ret.setBounds(170, top, 230, 20);

        return ret;
    }
    private static void editKonfig(final Konfig konf, final Preferences pref, final StartStoppable threads){
        final Frame f = new Frame();
        final Dialog d = new Dialog(f, "FTPSync Konfiguration");
        
        d.add(createLabel(30, "Lokales Verzeichnis"));
        final TextField localDirField = createField(30, konf.getLocalDir());
        d.add(localDirField);
        d.add(createLabel(60, "Fernes Verzeichnis"));
        final TextField ftpDirField = createField(60, konf.getFtpDir());
        d.add(ftpDirField);
        d.add(createLabel(90, "FTP Host"));
        final TextField ftpHostField = createField(90, konf.getFtpHost());
        d.add(ftpHostField);
        d.add(createLabel(120, "FTP User"));
        final TextField ftpUserField = createField(120, konf.getFtpUser());
        d.add(ftpUserField);
        d.add(createLabel(150, "FTP Passwort"));
        final TextField ftpPwdField = createField(150, konf.getFtpPwd());
        d.add(ftpPwdField);
        d.add(createLabel(180, "Lokales Cache-Verzeichnis"));
        final TextField cacheDirField = createField(180, konf.getLocalCacheDir());
        d.add(cacheDirField);
        d.add(createLabel(210, "Interval des Updates vom FTP (in ms)"));
        final TextField scanIntField = createField(210, Long.toString(konf.getFtpScanInterval()));
        d.add(scanIntField);
        d.add(createLabel(240, "Interval des Uploads auf den FTP"));
        final TextField uploadInterValField = createField(240, Long.toString( konf.getUploadInterval()) );
        d.add(uploadInterValField);
        d.add(createLabel(270, "Änderungen hochladen ab"));
        final TextField cutoffDateField = createField(270, pref.get(PrefKeys.LAST_CHANGE.name(), "0"));
        d.add(cutoffDateField);

        d.add(createLabel(270, "Pattern um Dateien auszuschließen"));

        final TextArea area = new TextArea(konf.getToExcludeString(), 10, 40);
        area.setBounds(10, 300, 300, 100);
        d.add(area);

        Button b = new Button("OK");
        b.addActionListener(e -> {
		    konf.setLocalDir(localDirField.getText());
		    konf.setFtpDir(ftpDirField.getText());
		    konf.setFtpHost(ftpHostField.getText());
		    konf.setFtpUser(ftpUserField.getText());
		    konf.setFtpPwd(ftpPwdField.getText());
		    konf.setLocalCacheDir(cacheDirField.getText());
            konf.setFtpScanInterval(Long.parseLong(scanIntField.getText()));
            konf.setUploadInterval(Long.parseLong(uploadInterValField.getText()));
            konf.setToExclude(area.getText());

            pref.put(PrefKeys.LAST_CHANGE.name(), cutoffDateField.getText());
            saveKonfig(konf, pref);
		    if(isKonfValid(konf)){
                threads.start();
		    }

		    closeDia(d,f);
        });
        b.setBounds(10, 400, 40, 30);
        d.add(b);

        b = new Button("Abbrechen");
        b.addActionListener(e->{
            closeDia(d,f);
            if(isKonfValid(konf)){
                threads.start();
            }
        }  );
        b.setBounds(100, 400, 40, 30);
        d.add(b);

        d.setBounds(100, 100, 500, 600);

        threads.stop();

        d.setVisible(true);
    }
    private static void closeDia(final Dialog d, final Frame f){
        d.setVisible(false);
        d.dispose();
        f.dispose();
    }
    /**
     * Gibt zurueck, ob die Konfiguration genug Informationen enthaelt
     */
    private static boolean isKonfValid(final Konfig konf){
        return !konf.getLocalDir().isBlank() &&
            !konf.getFtpDir().isBlank() &&
            !konf.getFtpHost().isBlank() &&
            !konf.getFtpUser().isBlank() &&
            !konf.getFtpPwd().isBlank() &&
            !konf.getLocalCacheDir().isBlank() &&
            konf.getFtpScanInterval() > 1000 &&
            konf.getUploadInterval() > 1000;
    }
    /**
     * Schreibt die Konfiguration zurueck in die {@link Preferences}
     */
    private static void saveKonfig(final Konfig konf, final Preferences preferences){
        preferences.put(PrefKeys.LOCAL_DIR.name(), konf.getLocalDir());
        preferences.put(PrefKeys.FTP_DIR.name(), konf.getFtpDir());
        preferences.put(PrefKeys.FTP_HOST.name(), konf.getFtpHost());
        preferences.put(PrefKeys.FTP_USER.name(), konf.getFtpUser());
        preferences.put(PrefKeys.FTP_PWD.name(), konf.getFtpPwd());
        preferences.put(PrefKeys.LOCAL_CACHE.name(), konf.getLocalCacheDir());
        preferences.putLong(PrefKeys.SCAN_INTERVAL.name(), konf.getFtpScanInterval());
        preferences.putLong(PrefKeys.UPLOAD_INTERVAL.name(), konf.getUploadInterval());
        preferences.put(PrefKeys.EXCLUDE_PATTERN.name(), konf.getToExcludeString());
    }
    private enum PrefKeys{
        LOCAL_DIR,
        FTP_DIR,
        FTP_HOST,
        FTP_USER,
        FTP_PWD,
        IGNORE_PATTERNS,
        LOCAL_CACHE,
        SCAN_INTERVAL,
        LAST_CHANGE,
        UPLOAD_INTERVAL,
        EXCLUDE_PATTERN
    }
}
