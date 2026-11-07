package se.dick.geolert;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Pending snoozes: alarm id → time (epoch ms) when it rings again. Survives restarts. */
public class SnoozeStore {

    private static final String PREFS = "snoozes";
    private static final String KEY = "map";

    private final SharedPreferences prefs;

    public SnoozeStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized Map<Long, Long> getAll() {
        Map<Long, Long> map = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString(KEY, "{}"));
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(Long.parseLong(k), o.getLong(k));
            }
        } catch (JSONException | NumberFormatException ignored) {
        }
        return map;
    }

    public synchronized void put(long alarmId, long wakeAtMs) {
        Map<Long, Long> map = getAll();
        map.put(alarmId, wakeAtMs);
        write(map);
    }

    public synchronized void remove(long alarmId) {
        Map<Long, Long> map = getAll();
        if (map.remove(alarmId) != null) write(map);
    }

    private void write(Map<Long, Long> map) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<Long, Long> e : map.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY, o.toString()).apply();
    }
}
