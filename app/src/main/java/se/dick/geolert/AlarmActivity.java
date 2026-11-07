package se.dick.geolert;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

/** Full-screen alarm, also over the lock screen: dismiss, or snooze 1 min – 24 h with a slider. */
public class AlarmActivity extends Activity {

    public static final String EXTRA_ALARM_ID = "alarmId";

    private long alarmId;
    private AppSettings settings;
    private SeekBar snooze;
    private TextView snoozeLabel;
    private Button snoozeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setContentView(R.layout.activity_alarm);
        Insets.apply(findViewById(R.id.root));

        settings = new AppSettings(this);
        snooze = findViewById(R.id.snooze);
        snoozeLabel = findViewById(R.id.snooze_label);
        snoozeButton = findViewById(R.id.btn_snooze);

        snooze.setMax(AppSettings.SNOOZE_STEPS_MIN.length - 1);
        snooze.setProgress(settings.getLastSnoozeIndex());
        snooze.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                showSnooze();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        snoozeButton.setOnClickListener(v -> {
            int index = snooze.getProgress();
            settings.setLastSnoozeIndex(index);
            MonitorService.start(this, MonitorService.intent(this, MonitorService.ACTION_SNOOZE,
                    alarmId, AppSettings.SNOOZE_STEPS_MIN[index]));
            finish();
        });
        findViewById(R.id.btn_dismiss).setOnClickListener(v -> {
            MonitorService.start(this, MonitorService.intent(this, MonitorService.ACTION_DISMISS, alarmId, 0));
            finish();
        });

        showAlarm();
        showSnooze();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showAlarm();
    }

    private void showAlarm() {
        alarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, 0);
        Alarm a = new AlarmStore(this).get(alarmId);
        TextView name = findViewById(R.id.name);
        TextView message = findViewById(R.id.message);
        if (a == null) {
            name.setText(R.string.app_name);
            message.setText("");
            return;
        }
        name.setText(a.displayName());
        message.setText(a.message == null || a.message.isEmpty()
                ? getString(a.isArrive() ? R.string.arrived_at : R.string.left_zone, a.displayName())
                : a.message);
    }

    private void showSnooze() {
        String time = AppSettings.formatMinutes(AppSettings.SNOOZE_STEPS_MIN[snooze.getProgress()]);
        snoozeLabel.setText(getString(R.string.snooze_for, time));
        snoozeButton.setText(R.string.snooze);
    }
}
