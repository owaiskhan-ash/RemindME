package com.remindme.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.os.Build;

/** Delivers the alarm notification and its Snooze/Mark done actions. */
public final class ReminderReceiver extends BroadcastReceiver {
    // A new ID gives language-aware alarms their own app-managed notification channel.
    public static final String CHANNEL_ID = "reminder_alarms_v3";
    public static final String ACTION_REMINDER_STATE_CHANGED =
            "com.remindme.app.REMINDER_STATE_CHANGED";
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        if (reminderId == null) return;

        ReminderStore store = new ReminderStore(context);
        Reminder reminder = store.find(reminderId);
        if (reminder == null) return;

        String action = intent.getAction();
        ReminderScheduler scheduler = new ReminderScheduler(context);
        if (ReminderScheduler.ACTION_SNOOZE.equals(action)) {
            AlarmSoundService.stop(context);
            reminder.triggerAt = System.currentTimeMillis() + 10 * 60 * 1000L;
            reminder.notified = false;
            store.update(reminder);
            scheduler.schedule(reminder);
            cancelNotification(context, reminder);
            dispatchStateChanged(context, reminder.id);
            return;
        }

        if (ReminderScheduler.ACTION_COMPLETE.equals(action)) {
            AlarmSoundService.stop(context);
            reminder.completed = true;
            reminder.notified = true;
            store.update(reminder);
            scheduler.cancel(reminder);
            cancelNotification(context, reminder);
            dispatchStateChanged(context, reminder.id);
            return;
        }

        if (!ReminderScheduler.ACTION_FIRE.equals(action) || reminder.completed) return;
        reminder.notified = true;
        store.update(reminder);
        dispatchStateChanged(context, reminder.id);
        showReminder(context, reminder);
        try {
            // A foreground service keeps the looping alarm stream alive after this
            // short-lived receiver returns, including while the app UI is closed.
            AlarmSoundService.start(context, reminder);
        } catch (RuntimeException ignored) {
            // The visible alarm notification remains available if a device blocks a
            // foreground-service launch under a vendor-specific battery policy.
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        Context textContext = new AppPreferences(context).localizedContext(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean hasDndAccess = manager.isNotificationPolicyAccessGranted();
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);

        // A channel's DND behavior is immutable after creation. Once the user
        // explicitly grants policy access, rebuild this app-owned channel so it
        // can show the active alarm notification during Do Not Disturb as well.
        if (existing != null && hasDndAccess && !existing.canBypassDnd()) {
            manager.deleteNotificationChannel(CHANNEL_ID);
            existing = null;
        }
        if (existing != null) return;

        AudioAttributes alarmAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                textContext.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(textContext.getString(R.string.alarm_channel_description));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 160, 110, 160});
        // AlarmSoundService owns the audible tone to avoid duplicate sounds. The
        // service uses USAGE_ALARM, which is separate from ring/silent mode.
        channel.setSound(null, alarmAttributes);
        if (hasDndAccess) channel.setBypassDnd(true);
        manager.createNotificationChannel(channel);
    }

    @SuppressWarnings("deprecation")
    static Notification buildReminderNotification(Context context, Reminder reminder, boolean alarmActive) {
        Context textContext = new AppPreferences(context).localizedContext(context);
        createNotificationChannel(context);
        ReminderScheduler scheduler = new ReminderScheduler(context);
        Intent openApp = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                reminder.requestCode(),
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Action snoozeAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_snooze),
                textContext.getString(R.string.snooze_ten_minutes),
                scheduler.actionPendingIntent(reminder, ReminderScheduler.ACTION_SNOOZE)
        ).build();
        Notification.Action completeAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_complete),
                textContext.getString(R.string.mark_done),
                scheduler.actionPendingIntent(reminder, ReminderScheduler.ACTION_COMPLETE)
        ).build();

        String alarmText = ReminderText.clean(reminder.title);
        if (alarmText.isEmpty()) alarmText = textContext.getString(R.string.default_reminder_title);
        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_re)
                .setColor(Color.rgb(62, 84, 74))
                .setContentTitle(alarmActive
                        ? textContext.getString(R.string.alarm_active_title)
                        : textContext.getString(R.string.notification_title))
                .setContentText(alarmText)
                .setStyle(new Notification.BigTextStyle().bigText(alarmText))
                .setContentIntent(contentIntent)
                .setAutoCancel(!alarmActive)
                .setOngoing(alarmActive)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(snoozeAction)
                .addAction(completeAction)
                .build();
    }

    private static void dispatchStateChanged(Context context, String reminderId) {
        Intent changed = new Intent(ACTION_REMINDER_STATE_CHANGED)
                .setPackage(context.getPackageName())
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId);
        context.sendBroadcast(changed);
    }

    private static void showReminder(Context context, Reminder reminder) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(reminder.requestCode(), buildReminderNotification(context, reminder, false));
    }

    private static void cancelNotification(Context context, Reminder reminder) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(reminder.requestCode());
    }
}
