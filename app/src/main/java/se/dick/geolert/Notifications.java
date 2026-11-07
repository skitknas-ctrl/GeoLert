package se.dick.geolert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Notification channels and builders. */
public final class Notifications {

    public static final String CH_MONITOR = "monitor";
    public static final String CH_ALARM = "alarm";
    public static final int ID_MONITOR = 1;
    public static final int ID_ALARM = 2;

    private Notifications() {
    }

    public static void createChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel monitor = new NotificationChannel(CH_MONITOR,
                ctx.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW);
        monitor.setShowBadge(false);
        nm.createNotificationChannel(monitor);

        // Sound and vibration are played by AlarmRinger, so the channel itself is silent
        NotificationChannel alarm = new NotificationChannel(CH_ALARM,
                ctx.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null);
        alarm.enableVibration(false);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alarm);
    }

    public static Notification monitor(Context ctx, String text) {
        PendingIntent open = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(ctx, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    public static Notification alarm(Context ctx, Alarm a, int snoozeMinutes) {
        Intent show = new Intent(ctx, AlarmActivity.class)
                .putExtra(AlarmActivity.EXTRA_ALARM_ID, a.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent full = PendingIntent.getActivity(ctx, 1, show,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent dismiss = PendingIntent.getService(ctx, 2,
                MonitorService.intent(ctx, MonitorService.ACTION_DISMISS, a.id, 0),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent snooze = PendingIntent.getService(ctx, 3,
                MonitorService.intent(ctx, MonitorService.ACTION_SNOOZE, a.id, snoozeMinutes),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = a.message == null || a.message.isEmpty()
                ? ctx.getString(a.isArrive() ? R.string.arrived_at : R.string.left_zone, a.displayName())
                : a.message;

        return new Notification.Builder(ctx, CH_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(a.displayName())
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(new Notification.Action.Builder(null,
                        ctx.getString(R.string.snooze_for, AppSettings.formatMinutes(snoozeMinutes)), snooze).build())
                .addAction(new Notification.Action.Builder(null,
                        ctx.getString(R.string.dismiss), dismiss).build())
                .build();
    }
}
