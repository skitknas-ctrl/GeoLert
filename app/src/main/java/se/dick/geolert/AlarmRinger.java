package se.dick.geolert;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Plays the alarm: looping sound on the alarm stream, vibration and a blinking flashlight. */
public class AlarmRinger {

    /** Stop by itself after this long if nobody reacts. */
    private static final long MAX_RING_MS = 10 * 60_000;
    private static final long FLASH_TOGGLE_MS = 400;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private String torchId;
    private boolean torchOn;
    private boolean ringing;

    private final Runnable flashToggle = new Runnable() {
        @Override
        public void run() {
            torchOn = !torchOn;
            setTorch(torchOn);
            handler.postDelayed(this, FLASH_TOGGLE_MS);
        }
    };

    public AlarmRinger(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRinging() {
        return ringing;
    }

    public void start(boolean sound, boolean vibrate, boolean flash, Uri soundUri) {
        stop();
        ringing = true;

        PowerManager pm = context.getSystemService(PowerManager.class);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoLert:ring");
            wakeLock.acquire(MAX_RING_MS + 5_000);
        }

        AudioAttributes alarmAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        if (sound) startSound(soundUri, alarmAttrs);

        if (vibrate) {
            vibrator = context.getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0), alarmAttrs);
            }
        }

        if (flash) {
            torchId = findTorch();
            if (torchId != null) handler.post(flashToggle);
        }

        handler.postDelayed(this::stop, MAX_RING_MS);
    }

    private void startSound(Uri uri, AudioAttributes attrs) {
        Uri[] candidates = {
                uri,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        };
        for (Uri u : candidates) {
            if (u == null) continue;
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(attrs);
                mp.setDataSource(context, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception e) {
                // Try the next sound
            }
        }
    }

    private String findTorch() {
        CameraManager cm = context.getSystemService(CameraManager.class);
        if (cm == null) return null;
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean has = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(has)) return id;
            }
        } catch (CameraAccessException | RuntimeException ignored) {
        }
        return null;
    }

    private void setTorch(boolean on) {
        CameraManager cm = context.getSystemService(CameraManager.class);
        if (cm == null || torchId == null) return;
        try {
            cm.setTorchMode(torchId, on);
        } catch (CameraAccessException | RuntimeException ignored) {
        }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (torchId != null) {
            setTorch(false);
            torchId = null;
            torchOn = false;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        ringing = false;
    }
}
