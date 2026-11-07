package se.dick.geolert;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

/** App-wide settings. */
public class AppSettings {

    /** Choices for "Check position at least every…", in minutes. */
    public static final int[] CHECK_INTERVALS_MIN = {1, 2, 5, 10, 15, 30, 60, 120};
    public static final int DEFAULT_CHECK_INTERVAL_MIN = 15;

    /** Snooze steps, in minutes: 1 min … 24 h. */
    public static final int[] SNOOZE_STEPS_MIN = {
            1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50, 60, 90,
            120, 180, 240, 300, 360, 480, 600, 720, 960, 1200, 1440
    };
    public static final int DEFAULT_SNOOZE_INDEX = 4; // 5 minutes

    private static final String PREFS = "settings";
    private static final String KEY_SOUND = "alarmSound";
    private static final String KEY_INTERVAL = "checkIntervalMin";
    private static final String KEY_SNOOZE = "lastSnoozeIndex";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The chosen alarm sound, or the phone's default alarm sound. */
    public Uri getAlarmSound() {
        String s = prefs.getString(KEY_SOUND, null);
        if (s != null) return Uri.parse(s);
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    public void setAlarmSound(Uri uri) {
        prefs.edit().putString(KEY_SOUND, uri == null ? null : uri.toString()).apply();
    }

    public int getCheckIntervalMin() {
        return prefs.getInt(KEY_INTERVAL, DEFAULT_CHECK_INTERVAL_MIN);
    }

    public void setCheckIntervalMin(int minutes) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply();
    }

    public int getLastSnoozeIndex() {
        int i = prefs.getInt(KEY_SNOOZE, DEFAULT_SNOOZE_INDEX);
        return Math.max(0, Math.min(SNOOZE_STEPS_MIN.length - 1, i));
    }

    public void setLastSnoozeIndex(int index) {
        prefs.edit().putInt(KEY_SNOOZE, index).apply();
    }

    /** "5 min", "90 min", "2 h", "24 h". */
    public static String formatMinutes(int minutes) {
        if (minutes < 120) return minutes + " min";
        return (minutes / 60) + " h";
    }
}
