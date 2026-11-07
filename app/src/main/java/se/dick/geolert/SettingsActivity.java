package se.dick.geolert;

import android.app.Activity;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

/** App settings: alarm sound and "Check position at least every…". */
public class SettingsActivity extends Activity {

    private static final int REQ_SOUND = 1;

    private AppSettings settings;
    private TextView soundName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Insets.apply(findViewById(R.id.root));

        settings = new AppSettings(this);
        soundName = findViewById(R.id.sound_name);
        showSoundName();

        findViewById(R.id.row_sound).setOnClickListener(v -> pickSound());

        // Check interval
        Spinner interval = findViewById(R.id.interval);
        String[] labels = new String[AppSettings.CHECK_INTERVALS_MIN.length];
        int selected = 0;
        for (int i = 0; i < labels.length; i++) {
            int min = AppSettings.CHECK_INTERVALS_MIN[i];
            labels[i] = AppSettings.formatMinutes(min);
            if (min == settings.getCheckIntervalMin()) selected = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        interval.setAdapter(adapter);
        interval.setSelection(selected);
        interval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setCheckIntervalMin(AppSettings.CHECK_INTERVALS_MIN[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ((TextView) findViewById(R.id.version)).setText(
                getString(R.string.version) + " " + BuildConfig.VERSION_NAME);
    }

    private void pickSound() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.alarm_sound));
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.getAlarmSound());
        startActivityForResult(i, REQ_SOUND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SOUND && resultCode == RESULT_OK && data != null) {
            @SuppressWarnings("deprecation") // the typed variant needs Android 13
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            settings.setAlarmSound(uri);
            showSoundName();
        }
    }

    private void showSoundName() {
        String title = "";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, settings.getAlarmSound());
            if (r != null) title = r.getTitle(this);
        } catch (Exception ignored) {
        }
        soundName.setText(title);
    }
}
