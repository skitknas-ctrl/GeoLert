package se.dick.geolert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a phone restart or app update: start watching again and restore pending snoozes. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            MonitorService.rescheduleSnoozes(context);
            MonitorService.refresh(context);
        }
    }
}
