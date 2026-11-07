package se.dick.geolert;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** The permissions GeoLert needs, asked for one step at a time with an explanation. */
public final class Setup {

    public static final int REQ_PERMISSION = 10;

    public enum Step {
        LOCATION(R.string.setup_location_title, R.string.setup_location_text),
        BACKGROUND(R.string.setup_background_title, R.string.setup_background_text),
        NOTIFICATIONS(R.string.setup_notifications_title, R.string.setup_notifications_text),
        FULL_SCREEN(R.string.setup_fullscreen_title, R.string.setup_fullscreen_text),
        BATTERY(R.string.setup_battery_title, R.string.setup_battery_text);

        public final int title;
        public final int text;

        Step(int title, int text) {
            this.title = title;
            this.text = text;
        }
    }

    private Setup() {
    }

    private static boolean granted(Context ctx, String permission) {
        return ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDone(Context ctx, Step step) {
        switch (step) {
            case LOCATION:
                return granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION);
            case BACKGROUND:
                return granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            case NOTIFICATIONS:
                return Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS);
            case FULL_SCREEN:
                if (Build.VERSION.SDK_INT < 34) return true;
                NotificationManager nm = ctx.getSystemService(NotificationManager.class);
                return nm == null || nm.canUseFullScreenIntent();
            case BATTERY:
                PowerManager pm = ctx.getSystemService(PowerManager.class);
                return pm == null || pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
            default:
                return true;
        }
    }

    /** The first step not done yet, or null when everything is set up. */
    public static Step nextStep(Context ctx) {
        for (Step s : Step.values()) {
            if (!isDone(ctx, s)) return s;
        }
        return null;
    }

    public static int missingCount(Context ctx) {
        int n = 0;
        for (Step s : Step.values()) {
            if (!isDone(ctx, s)) n++;
        }
        return n;
    }

    /** Explains the step, then opens the system prompt or settings page for it. */
    public static void run(Activity activity, Step step) {
        new AlertDialog.Builder(activity)
                .setTitle(step.title)
                .setMessage(step.text)
                .setPositiveButton(R.string.continue_label, (d, w) -> perform(activity, step))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void perform(Activity a, Step step) {
        Uri pkg = Uri.parse("package:" + a.getPackageName());
        try {
            switch (step) {
                case LOCATION:
                    a.requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMISSION);
                    break;
                case BACKGROUND:
                    // Opens the app's location page, where "Allow all the time" is chosen
                    a.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_PERMISSION);
                    break;
                case NOTIFICATIONS:
                    a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMISSION);
                    break;
                case FULL_SCREEN:
                    a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg));
                    break;
                case BATTERY:
                    a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg));
                    break;
            }
        } catch (RuntimeException e) {
            // Fallback: the app's own settings page
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
        }
    }
}
