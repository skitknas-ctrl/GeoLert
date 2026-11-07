package se.dick.geolert;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.provider.Settings;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Start screen: the list of alarms. */
public class MainActivity extends Activity {

    private AlarmStore store;
    private final List<Alarm> alarms = new ArrayList<>();
    private AlarmAdapter adapter;
    private View empty;
    private View setupCard;
    private TextView setupText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Insets.apply(findViewById(R.id.root));

        store = new AlarmStore(this);
        empty = findViewById(R.id.empty);

        ListView list = findViewById(R.id.list);
        adapter = new AlarmAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openEditor(alarms.get(position).id));

        setupCard = findViewById(R.id.setup_card);
        setupText = findViewById(R.id.setup_text);
        findViewById(R.id.setup_button).setOnClickListener(v -> {
            Setup.Step step = Setup.nextStep(this);
            if (step != null) Setup.run(this, step);
        });

        findViewById(R.id.btn_new).setOnClickListener(v -> openEditor(0));
        findViewById(R.id.btn_settings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        showSetup();
        MonitorService.refresh(this);
    }

    private void showSetup() {
        Setup.Step step = Setup.nextStep(this);
        if (step == null) {
            setupCard.setVisibility(View.GONE);
            return;
        }
        setupCard.setVisibility(View.VISIBLE);
        setupText.setText(getString(R.string.setup_needed, Setup.missingCount(this), getString(step.title)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != Setup.REQ_PERMISSION) return;
        boolean denied = false;
        boolean canAskAgain = false;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults.length > i && grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                denied = true;
                if (shouldShowRequestPermissionRationale(permissions[i])) canAskAgain = true;
            }
        }
        // Android stops showing the prompt after repeated "Don't allow": open the app's settings page instead
        if (denied && !canAskAgain) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
        showSetup();
        MonitorService.refresh(this);
    }

    private void reload() {
        alarms.clear();
        alarms.addAll(store.getAll());
        adapter.notifyDataSetChanged();
        empty.setVisibility(alarms.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditor(long alarmId) {
        Intent i = new Intent(this, EditAlarmActivity.class);
        i.putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarmId);
        startActivity(i);
    }

    private class AlarmAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return alarms.size();
        }

        @Override
        public Object getItem(int position) {
            return alarms.get(position);
        }

        @Override
        public long getItemId(int position) {
            return alarms.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.item_alarm, parent, false);
            Alarm a = alarms.get(position);

            ((TextView) row.findViewById(R.id.name)).setText(a.displayName());
            String trigger = getString(a.isArrive() ? R.string.arrive : R.string.leave);
            ((TextView) row.findViewById(R.id.details)).setText(trigger + " · " + a.radiusM + " m");

            Switch sw = row.findViewById(R.id.active);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(a.active);
            sw.setOnCheckedChangeListener((button, checked) -> {
                a.active = checked;
                // Re-activating starts from "unknown": it waits for a real crossing
                a.state = Alarm.STATE_UNKNOWN;
                store.save(a);
                MonitorService.refresh(MainActivity.this);
                row.setAlpha(checked ? 1f : 0.55f);
            });
            row.setAlpha(a.active ? 1f : 0.55f);
            return row;
        }
    }
}
