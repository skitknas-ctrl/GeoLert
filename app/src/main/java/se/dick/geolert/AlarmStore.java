package se.dick.geolert;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Stores all alarms as a JSON array, only on the phone. */
public class AlarmStore {

    private static final String PREFS = "alarms";
    private static final String KEY = "list";

    private final SharedPreferences prefs;

    public AlarmStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Alarm> getAll() {
        List<Alarm> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt data: start with an empty list rather than crash
        }
        return list;
    }

    public synchronized Alarm get(long id) {
        for (Alarm a : getAll()) {
            if (a.id == id) return a;
        }
        return null;
    }

    /** Inserts a new alarm (id 0) or replaces the one with the same id. */
    public synchronized void save(Alarm alarm) {
        List<Alarm> list = getAll();
        if (alarm.id == 0) {
            alarm.id = System.currentTimeMillis();
            list.add(alarm);
        } else {
            boolean replaced = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == alarm.id) {
                    list.set(i, alarm);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) list.add(alarm);
        }
        write(list);
    }

    public synchronized void delete(long id) {
        List<Alarm> list = getAll();
        list.removeIf(a -> a.id == id);
        write(list);
    }

    private void write(List<Alarm> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : list) arr.put(a.toJson());
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}
