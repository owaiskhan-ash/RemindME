package com.remindme.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

/**
 * Plays due reminders through Android's dedicated alarm stream.
 *
 * Ring/silent mode controls the ringtone and notification streams, not the alarm
 * stream, so a device in silent mode still plays a reminder as long as its alarm
 * volume is not set to zero. Do Not Disturb remains an explicit user-controlled
 * Android setting and is handled by the optional policy-access path in the UI.
 */
public final class AlarmSoundService extends Service {
    private static final String ACTION_START = "com.remindme.app.START_ALARM_SOUND";
    private static final long MAX_RING_DURATION_MS = 60_000L;

    private final Handler handler = new Handler();
    private MediaPlayer player;
    private AudioManager audioManager;
    private int activeNotificationId = 0;
    private final Runnable stopAfterTimeout = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    public static void start(Context context, Reminder reminder) {
        Intent intent = new Intent(context, AlarmSoundService.class)
                .setAction(ACTION_START)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmSoundService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        Reminder reminder = reminderId == null ? null : new ReminderStore(this).find(reminderId);
        if (reminder == null || reminder.completed) {
            stopSelf();
            return START_NOT_STICKY;
        }

        activeNotificationId = reminder.requestCode();
        Notification notification = ReminderReceiver.buildReminderNotification(this, reminder, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    activeNotificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(activeNotificationId, notification);
        }

        startAlarmTone();
        handler.removeCallbacks(stopAfterTimeout);
        handler.postDelayed(stopAfterTimeout, MAX_RING_DURATION_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(stopAfterTimeout);
        stopAlarmTone();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAlarmTone() {
        stopAlarmTone();
        requestAlarmAudioFocus();
        Uri alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmTone == null) {
            alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (alarmTone == null) return;

        try {
            MediaPlayer nextPlayer = new MediaPlayer();
            nextPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            nextPlayer.setDataSource(getApplicationContext(), alarmTone);
            nextPlayer.setLooping(true);
            nextPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer preparedPlayer) {
                    preparedPlayer.start();
                }
            });
            nextPlayer.prepareAsync();
            player = nextPlayer;
        } catch (Exception ignored) {
            // The notification stays available with Snooze and Mark done even if
            // a device has no usable system alarm tone configured.
            stopAlarmTone();
        }
    }

    @SuppressWarnings("deprecation")
    private void requestAlarmAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            );
        }
    }

    @SuppressWarnings("deprecation")
    private void stopAlarmTone() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (IllegalStateException ignored) {
                // A player that failed while preparing can be released safely.
            }
            player.reset();
            player.release();
            player = null;
        }
        if (audioManager != null) {
            audioManager.abandonAudioFocus(null);
            audioManager = null;
        }
    }
}
