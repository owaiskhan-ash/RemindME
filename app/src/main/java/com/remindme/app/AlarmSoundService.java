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
import android.os.Looper;

/**
 * Plays due reminders through Android's dedicated alarm stream.
 *
 * Ring/silent mode controls ringtone and notification streams, not the alarm
 * stream, so a device in silent mode still plays a reminder while its alarm
 * volume is nonzero. Do Not Disturb remains an explicit user-controlled Android
 * setting. The optional RemindME sound safely falls back to the device alarm.
 */
public final class AlarmSoundService extends Service {
    private static final String ACTION_START = "com.remindme.app.START_ALARM_SOUND";
    private static final long MAX_RING_DURATION_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audioManager;
    private int activeNotificationId = 0;
    private boolean remoteFallbackUsed;
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
        remoteFallbackUsed = false;
        if (new AppPreferences(this).usesRemindMeSound()) {
            startRemoteAlarmTone();
        } else {
            startDeviceAlarmTone();
        }
    }

    private void startRemoteAlarmTone() {
        startTone(Uri.parse(AppPreferences.REMINDME_ALARM_URL), true);
    }

    private void startDeviceAlarmTone() {
        Uri alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmTone == null) {
            alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (alarmTone == null) {
            abandonAlarmAudioFocus();
            return;
        }
        startTone(alarmTone, false);
    }

    private void startTone(Uri toneUri, final boolean isRemoteTone) {
        MediaPlayer nextPlayer = new MediaPlayer();
        try {
            nextPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            nextPlayer.setDataSource(getApplicationContext(), toneUri);
            nextPlayer.setLooping(true);
            nextPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer preparedPlayer) {
                    if (player != preparedPlayer) return;
                    try {
                        preparedPlayer.start();
                    } catch (IllegalStateException ignored) {
                        handlePlaybackFailure(preparedPlayer, isRemoteTone);
                    }
                }
            });
            nextPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer failedPlayer, int what, int extra) {
                    handlePlaybackFailure(failedPlayer, isRemoteTone);
                    return true;
                }
            });
            player = nextPlayer;
            nextPlayer.prepareAsync();
        } catch (Exception ignored) {
            releasePlayer(nextPlayer);
            if (player == nextPlayer) player = null;
            if (isRemoteTone && !remoteFallbackUsed) {
                remoteFallbackUsed = true;
                startDeviceAlarmTone();
            } else {
                abandonAlarmAudioFocus();
            }
        }
    }

    private void handlePlaybackFailure(MediaPlayer failedPlayer, boolean wasRemoteTone) {
        if (player != failedPlayer) return;
        player = null;
        releasePlayer(failedPlayer);
        if (wasRemoteTone && !remoteFallbackUsed) {
            remoteFallbackUsed = true;
            startDeviceAlarmTone();
        } else {
            abandonAlarmAudioFocus();
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

    private void releasePlayer(MediaPlayer target) {
        if (target == null) return;
        try {
            if (target.isPlaying()) target.stop();
        } catch (IllegalStateException ignored) {
            // A player that failed while preparing can still be released safely.
        }
        try {
            target.reset();
        } catch (IllegalStateException ignored) {
            // Release below is still valid after a media-server error.
        }
        target.release();
    }

    @SuppressWarnings("deprecation")
    private void abandonAlarmAudioFocus() {
        if (audioManager != null) {
            audioManager.abandonAudioFocus(null);
            audioManager = null;
        }
    }

    private void stopAlarmTone() {
        if (player != null) {
            MediaPlayer currentPlayer = player;
            player = null;
            releasePlayer(currentPlayer);
        }
        abandonAlarmAudioFocus();
    }
}
