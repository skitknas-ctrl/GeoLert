package se.dick.geolert;

import org.json.JSONException;
import org.json.JSONObject;

/** One location alarm: a circular zone plus how to alert. */
public class Alarm {

    public static final String ARRIVE = "ARRIVE";
    public static final String LEAVE = "LEAVE";

    /** Where the phone was relative to the zone at the last check. */
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_INSIDE = 1;
    public static final int STATE_OUTSIDE = 2;

    public static final int MIN_RADIUS_M = 100;
    public static final int MAX_RADIUS_M = 5000;

    public long id;
    public String name = "";
    public double lat;
    public double lon;
    public int radiusM = 300;
    public String trigger = ARRIVE;
    public boolean sound = true;
    public boolean vibrate = true;
    public boolean flash = false;
    public String message = "";
    public boolean active = true;
    public int state = STATE_UNKNOWN;

    public boolean isArrive() {
        return ARRIVE.equals(trigger);
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? "Unnamed alarm" : name.trim();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("radiusM", radiusM);
        o.put("trigger", trigger);
        o.put("sound", sound);
        o.put("vibrate", vibrate);
        o.put("flash", flash);
        o.put("message", message);
        o.put("active", active);
        o.put("state", state);
        return o;
    }

    static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optLong("id");
        a.name = o.optString("name", "");
        a.lat = o.optDouble("lat", 0);
        a.lon = o.optDouble("lon", 0);
        a.radiusM = o.optInt("radiusM", 300);
        a.trigger = LEAVE.equals(o.optString("trigger")) ? LEAVE : ARRIVE;
        a.sound = o.optBoolean("sound", true);
        a.vibrate = o.optBoolean("vibrate", true);
        a.flash = o.optBoolean("flash", false);
        a.message = o.optString("message", "");
        a.active = o.optBoolean("active", true);
        a.state = o.optInt("state", STATE_UNKNOWN);
        return a;
    }
}
