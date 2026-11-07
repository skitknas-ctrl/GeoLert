package se.dick.geolert;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;

/** Create or edit an alarm: tap the map for the center, set radius, trigger and alert methods. */
public class EditAlarmActivity extends Activity {

    public static final String EXTRA_ALARM_ID = "alarmId";

    private static final int RADIUS_STEP_M = 50;
    private static final int REQ_LOCATION = 1;

    /** Default view when nothing else is known: Sweden. */
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(62.0, 15.0);

    private AlarmStore store;
    private Alarm alarm;
    private boolean isNew;
    private GeoPoint center;

    private MapView map;
    private Marker marker;
    private Polygon zone;
    private View mapHint;
    private TextView radiusLabel;
    private SeekBar radiusBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid: identify the app to the tile server and keep the tile cache in app storage
        Configuration conf = Configuration.getInstance();
        conf.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        conf.setUserAgentValue(BuildConfig.APPLICATION_ID);
        conf.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        conf.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        setContentView(R.layout.activity_edit_alarm);
        Insets.apply(findViewById(R.id.root));

        store = new AlarmStore(this);
        long id = getIntent().getLongExtra(EXTRA_ALARM_ID, 0);
        alarm = id != 0 ? store.get(id) : null;
        isNew = alarm == null;
        if (isNew) alarm = new Alarm();

        setupForm(); // first: the map reads the radius slider
        setupMap();
    }

    // ---------- Map ----------

    private void setupMap() {
        map = findViewById(R.id.map);
        mapHint = findViewById(R.id.map_hint);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);

        // Tap anywhere on the map = set the zone center
        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                setCenter(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                setCenter(p);
                return true;
            }
        }));

        zone = new Polygon(map);
        zone.getFillPaint().setColor(getColor(R.color.zone_fill));
        zone.getOutlinePaint().setColor(getColor(R.color.zone_stroke));
        zone.getOutlinePaint().setStrokeWidth(4f);
        zone.setOnClickListener((polygon, mapView, eventPos) -> {
            setCenter(eventPos);
            return true;
        });

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setInfoWindow(null);
        marker.setOnMarkerClickListener((m, mapView) -> true);

        findViewById(R.id.btn_my_location).setOnClickListener(v -> goToMyLocation(true));

        if (!isNew) {
            GeoPoint p = new GeoPoint(alarm.lat, alarm.lon);
            map.getController().setZoom(zoomForRadius(alarm.radiusM));
            map.getController().setCenter(p);
            setCenter(p);
        } else {
            map.getController().setZoom(5.0);
            map.getController().setCenter(DEFAULT_CENTER);
            goToMyLocation(false);
        }
    }

    private void setCenter(GeoPoint p) {
        center = p;
        mapHint.setVisibility(View.GONE);
        if (!map.getOverlays().contains(zone)) map.getOverlays().add(zone);
        if (!map.getOverlays().contains(marker)) map.getOverlays().add(marker);
        marker.setPosition(p);
        updateZone();
    }

    private void updateZone() {
        if (center == null) return;
        zone.setPoints(Polygon.pointsAsCircle(center, currentRadius()));
        map.invalidate();
    }

    private static double zoomForRadius(int radiusM) {
        // Roughly: show the whole circle with some margin
        if (radiusM <= 150) return 17.0;
        if (radiusM <= 400) return 16.0;
        if (radiusM <= 800) return 15.0;
        if (radiusM <= 1600) return 14.0;
        if (radiusM <= 3200) return 13.0;
        return 12.0;
    }

    // ---------- My location ----------

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Centers the map on the phone's position. Asks for permission only when the user pressed the button. */
    private void goToMyLocation(boolean userAsked) {
        if (!hasLocationPermission()) {
            if (userAsked) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        if (lm == null) return;

        // Quick: last known position
        Location last = null;
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
            } catch (SecurityException ignored) {
            }
        }
        if (last != null) moveTo(last, userAsked);

        // Then: a fresh position
        String provider = pickProvider(lm);
        if (provider == null) return;
        try {
            lm.getCurrentLocation(provider, new CancellationSignal(), getMainExecutor(), loc -> {
                if (loc != null && !isFinishing()) moveTo(loc, userAsked);
            });
        } catch (SecurityException ignored) {
        }
    }

    private static String pickProvider(LocationManager lm) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        return null;
    }

    private void moveTo(Location loc, boolean userAsked) {
        // For an existing alarm, don't jump away from its zone unless the user asked
        if (!isNew && !userAsked) return;
        map.getController().setZoom(Math.max(map.getZoomLevelDouble(), 15.0));
        map.getController().animateTo(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && hasLocationPermission()) goToMyLocation(true);
    }

    // ---------- Form ----------

    private int currentRadius() {
        return Alarm.MIN_RADIUS_M + radiusBar.getProgress() * RADIUS_STEP_M;
    }

    private void setupForm() {
        EditText name = findViewById(R.id.name);
        radiusLabel = findViewById(R.id.radius_label);
        radiusBar = findViewById(R.id.radius);
        RadioGroup trigger = findViewById(R.id.trigger);
        CheckBox sound = findViewById(R.id.cb_sound);
        CheckBox vibrate = findViewById(R.id.cb_vibrate);
        CheckBox flash = findViewById(R.id.cb_flash);
        EditText message = findViewById(R.id.message);
        Switch active = findViewById(R.id.active);

        name.setText(alarm.name);
        radiusBar.setMax((Alarm.MAX_RADIUS_M - Alarm.MIN_RADIUS_M) / RADIUS_STEP_M);
        radiusBar.setProgress(Math.max(0, (alarm.radiusM - Alarm.MIN_RADIUS_M) / RADIUS_STEP_M));
        updateRadiusLabel();
        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateRadiusLabel();
                updateZone();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        trigger.check(alarm.isArrive() ? R.id.trigger_arrive : R.id.trigger_leave);
        sound.setChecked(alarm.sound);
        vibrate.setChecked(alarm.vibrate);
        flash.setChecked(alarm.flash);
        message.setText(alarm.message);
        active.setChecked(alarm.active);

        View delete = findViewById(R.id.btn_delete);
        delete.setVisibility(isNew ? View.INVISIBLE : View.VISIBLE);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.delete(alarm.id);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (center == null) {
                Toast.makeText(this, R.string.need_point, Toast.LENGTH_SHORT).show();
                return;
            }
            alarm.name = name.getText().toString().trim();
            alarm.lat = center.getLatitude();
            alarm.lon = center.getLongitude();
            alarm.radiusM = currentRadius();
            alarm.trigger = trigger.getCheckedRadioButtonId() == R.id.trigger_leave ? Alarm.LEAVE : Alarm.ARRIVE;
            alarm.sound = sound.isChecked();
            alarm.vibrate = vibrate.isChecked();
            alarm.flash = flash.isChecked();
            alarm.message = message.getText().toString().trim();
            alarm.active = active.isChecked();
            // Saved alarms wait for a real boundary crossing before firing
            alarm.state = Alarm.STATE_UNKNOWN;
            store.save(alarm);
            finish();
        });
    }

    private void updateRadiusLabel() {
        radiusLabel.setText("Radius: " + currentRadius() + " m");
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        map.onPause();
        super.onPause();
    }
}
