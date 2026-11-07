package se.dick.geolert;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Map;

/**
 * Foreground service that watches the position while at least one alarm is active,
 * fires alarms on boundary crossings and handles ringing, dismiss and snooze.
 */
public class MonitorService extends Service {

    public static final String ACTION_REFRESH = "se.dick.geolert.REFRESH";
    public static final String ACTION_DISMISS = "se.dick.geolert.DISMISS";
    public static final String ACTION_SNOOZE = "se.dick.geolert.SNOOZE";
    public static final String ACTION_SNOOZE_FIRE = "se.dick.geolert.SNOOZE_FIRE";
    private static final String EXTRA_ALARM_ID = "alarmId";
    private static final String EXTRA_MINUTES = "minutes";

    /** Don't ask for an extra fix more often than this. */
    private static final long MIN_EXTRA_CHECK_GAP_MS = 10_000;

    private AlarmStore store;
    private AppSettings settings;
    private SnoozeStore snoozes;
    private LocationManager lm;
    private AlarmRinger ringer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long ringingAlarmId;
    private long requestedIntervalMs;
    private String requestedProvider;
    private Location anchor;
    private long movingUntil;
    private long lastExtraCheck;
    private long lastFixAt;

    private SensorManager sensors;
    private Sensor motionSensor;
    private ConnectivityManager connectivity;
    private boolean wifiCallbackRegistered;

    private final LocationListener locationListener = this::onLocation;

    private final TriggerEventListener motionListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            checkNow();
            // One-shot sensor: arm it again
            if (sensors != null && motionSensor != null) sensors.requestTriggerSensor(this, motionSensor);
        }
    };

    private final ConnectivityManager.NetworkCallback wifiCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onLost(Network network) {
            // Left the (home) Wi-Fi: probably on the move
            handler.post(MonitorService.this::checkNow);
        }
    };

    // ---------- Starting from elsewhere ----------

    public static Intent intent(Context ctx, String action, long alarmId, int minutes) {
        return new Intent(ctx, MonitorService.class)
                .setAction(action)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_MINUTES, minutes);
    }

    public static boolean hasLocationPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** True while an alarm is ringing (the service must then keep running). */
    private static volatile boolean ringingNow;

    /** Call after alarms change: starts, updates or stops monitoring. */
    public static void refresh(Context ctx) {
        boolean anyActive = false;
        for (Alarm a : new AlarmStore(ctx).getAll()) {
            if (a.active) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive && !ringingNow) {
            ctx.stopService(new Intent(ctx, MonitorService.class));
            return;
        }
        start(ctx, intent(ctx, ACTION_REFRESH, 0, 0));
    }

    static void start(Context ctx, Intent i) {
        if (!hasLocationPermission(ctx)) {
            if (ACTION_REFRESH.equals(i.getAction())) ctx.stopService(new Intent(ctx, MonitorService.class));
            return;
        }
        try {
            ctx.startForegroundService(i);
        } catch (RuntimeException e) {
            // Not allowed to start from the background right now; next app start or boot will retry
        }
    }

    // ---------- Service lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        store = new AlarmStore(this);
        settings = new AppSettings(this);
        snoozes = new SnoozeStore(this);
        lm = getSystemService(LocationManager.class);
        ringer = new AlarmRinger(this);
        Notifications.createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must be called promptly after startForegroundService()
        try {
            startForeground(Notifications.ID_MONITOR, Notifications.monitor(this, getString(R.string.monitor_starting)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_REFRESH;
        long alarmId = intent != null ? intent.getLongExtra(EXTRA_ALARM_ID, 0) : 0;

        switch (action) {
            case ACTION_SNOOZE_FIRE:
                snoozes.remove(alarmId);
                Alarm a = store.get(alarmId);
                if (a != null) ring(a);
                break;
            case ACTION_DISMISS:
                stopRinging();
                break;
            case ACTION_SNOOZE:
                stopRinging();
                int minutes = intent.getIntExtra(EXTRA_MINUTES, 5);
                scheduleSnooze(this, alarmId, System.currentTimeMillis() + minutes * 60_000L);
                break;
            default:
                break;
        }

        update();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopWatching();
        ringer.stop();
        ringingNow = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Watching ----------

    private List<Alarm> activeAlarms() {
        List<Alarm> list = store.getAll();
        list.removeIf(a -> !a.active);
        return list;
    }

    /** Re-reads alarms and starts, adjusts or stops watching. */
    private void update() {
        List<Alarm> active = activeAlarms();
        if (active.isEmpty() && !ringer.isRinging()) {
            stopWatching();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        if (active.isEmpty()) {
            stopWatching();
            return;
        }
        startWatchers();
        if (requestedIntervalMs == 0) {
            // First start: begin with the ceiling, then adapt after the first fix
            requestUpdates(settings.getCheckIntervalMin() * 60_000L);
        }
        checkNow();
        updateMonitorText(active.size());
    }

    private void startWatchers() {
        if (sensors == null) {
            sensors = getSystemService(SensorManager.class);
            motionSensor = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) : null;
            if (motionSensor != null) sensors.requestTriggerSensor(motionListener, motionSensor);
        }
        if (!wifiCallbackRegistered) {
            connectivity = getSystemService(ConnectivityManager.class);
            if (connectivity != null) {
                try {
                    connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback);
                    wifiCallbackRegistered = true;
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void stopWatching() {
        if (lm != null) lm.removeUpdates(locationListener);
        requestedIntervalMs = 0;
        requestedProvider = null;
        if (sensors != null && motionSensor != null) sensors.cancelTriggerSensor(motionListener, motionSensor);
        sensors = null;
        motionSensor = null;
        if (wifiCallbackRegistered && connectivity != null) {
            try {
                connectivity.unregisterNetworkCallback(wifiCallback);
            } catch (RuntimeException ignored) {
            }
        }
        wifiCallbackRegistered = false;
    }

    private String pickProvider(long intervalMs) {
        if (lm == null) return null;
        boolean fused = android.os.Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER);
        boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (fused) return LocationManager.FUSED_PROVIDER;
        // Long intervals: the network provider saves battery. Short: GPS for accuracy.
        if (intervalMs > 120_000 && network) return LocationManager.NETWORK_PROVIDER;
        if (gps) return LocationManager.GPS_PROVIDER;
        return network ? LocationManager.NETWORK_PROVIDER : null;
    }

    private void requestUpdates(long intervalMs) {
        String provider = pickProvider(intervalMs);
        if (provider == null) return;
        try {
            lm.removeUpdates(locationListener);
            lm.requestLocationUpdates(provider, intervalMs, 0f, locationListener, Looper.getMainLooper());
            requestedIntervalMs = intervalMs;
            requestedProvider = provider;
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    /** An extra, immediate position check (movement detected, Wi-Fi lost, alarm changed). */
    private void checkNow() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastExtraCheck < MIN_EXTRA_CHECK_GAP_MS) return;
        lastExtraCheck = now;
        String provider = pickProvider(0);
        if (provider == null) return;
        try {
            lm.getCurrentLocation(provider, null, getMainExecutor(), loc -> {
                if (loc != null) onLocation(loc);
            });
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private void onLocation(Location loc) {
        long now = SystemClock.elapsedRealtime();
        lastFixAt = now;

        // Moving mode: more than 100 m from the anchor point
        if (anchor == null) {
            anchor = loc;
        } else if (anchor.distanceTo(loc) > ZoneLogic.MOVING_DISTANCE_M) {
            anchor = loc;
            movingUntil = now + ZoneLogic.MOVING_HOLD_MS;
        }
        boolean moving = now < movingUntil;

        double accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 0;
        double nearest = Double.MAX_VALUE;
        List<Alarm> active = activeAlarms();
        for (Alarm a : active) {
            double d = ZoneLogic.distanceM(loc.getLatitude(), loc.getLongitude(), a.lat, a.lon);
            nearest = Math.min(nearest, Math.abs(d - a.radiusM) - accuracy);

            // A fix vaguer than the zone itself can't decide inside/outside
            if (accuracy > a.radiusM) continue;

            ZoneLogic.Step step = ZoneLogic.evaluate(a.state, a.isArrive(), d, a.radiusM);
            if (step.newState != a.state) {
                a.state = step.newState;
                store.save(a);
            }
            if (step.fire) ring(a);
        }

        if (active.isEmpty()) {
            update();
            return;
        }

        long ceiling = settings.getCheckIntervalMin() * 60_000L;
        long next = ZoneLogic.nextIntervalMs(ceiling, nearest, moving);
        boolean providerChanged = requestedProvider == null || !requestedProvider.equals(pickProvider(next));
        // Re-request only on a real change, to avoid churning the location provider
        if (providerChanged || requestedIntervalMs == 0
                || next < requestedIntervalMs * 0.8 || next > requestedIntervalMs * 1.25) {
            requestUpdates(next);
        }
        updateMonitorText(active.size());
    }

    private void updateMonitorText(int count) {
        String text = getResources().getQuantityString(R.plurals.watching_alarms, count, count);
        if (requestedIntervalMs > 0) {
            text += " · " + getString(R.string.check_every, formatInterval(requestedIntervalMs));
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notifications.ID_MONITOR, Notifications.monitor(this, text));
    }

    private static String formatInterval(long ms) {
        long s = ms / 1000;
        if (s < 120) return s + " s";
        return AppSettings.formatMinutes((int) (s / 60));
    }

    // ---------- Ringing, dismiss, snooze ----------

    private void ring(Alarm a) {
        ringingAlarmId = a.id;
        ringingNow = true;
        ringer.start(a.sound, a.vibrate, a.flash, settings.getAlarmSound());
        int snoozeMin = AppSettings.SNOOZE_STEPS_MIN[settings.getLastSnoozeIndex()];
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notifications.ID_ALARM, Notifications.alarm(this, a, snoozeMin));
    }

    private void stopRinging() {
        ringer.stop();
        ringingAlarmId = 0;
        ringingNow = false;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(Notifications.ID_ALARM);
    }

    private static int requestCode(long alarmId) {
        return (int) (alarmId ^ (alarmId >>> 32));
    }

    /** Rings the alarm again at wakeAtMs, even if the phone sleeps or restarts in between. */
    static void scheduleSnooze(Context ctx, long alarmId, long wakeAtMs) {
        new SnoozeStore(ctx).put(alarmId, wakeAtMs);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent fire = PendingIntent.getForegroundService(ctx, requestCode(alarmId),
                intent(ctx, ACTION_SNOOZE_FIRE, alarmId, 0),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            PendingIntent show = PendingIntent.getActivity(ctx, 0,
                    new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(wakeAtMs, show), fire);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAtMs, fire);
        }
    }

    /** After a reboot: put all pending snoozes back on the alarm clock. */
    static void rescheduleSnoozes(Context ctx) {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> e : new SnoozeStore(ctx).getAll().entrySet()) {
            // Missed while the phone was off: ring in a minute
            scheduleSnooze(ctx, e.getKey(), Math.max(e.getValue(), now + 60_000));
        }
    }
}
