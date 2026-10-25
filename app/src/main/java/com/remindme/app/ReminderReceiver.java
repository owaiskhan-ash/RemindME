package com.remindme.app;

import android.Manifest;
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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/** Delivers the notification and handles the notification's snooze/complete actions. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "gentle_reminders";
    private static final String CHANNEL_NAME = "Gentle reminders";

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
            reminder.triggerAt = System.currentTimeMillis() + 10 * 60 * 1000L;
            reminder.notified = false;
            store.update(reminder);
            scheduler.schedule(reminder);
            cancelNotification(context, reminder);
            return;
        }

        if (ReminderScheduler.ACTION_COMPLETE.equals(action)) {
            reminder.completed = true;
            reminder.notified = true;
            store.update(reminder);
            scheduler.cancel(reminder);
            cancelNotification(context, reminder);
            return;
        }

        if (!ReminderScheduler.ACTION_FIRE.equals(action) || reminder.completed) return;
        reminder.notified = true;
        store.update(reminder);
        showReminder(context, reminder);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("A clear notification and gentle alarm when a reminder is due.");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 160, 110, 160});
        channel.setSound(alarmSound, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    private static void showReminder(Context context, Reminder reminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

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
                context.getString(R.string.snooze_ten_minutes),
                scheduler.actionPendingIntent(reminder, ReminderScheduler.ACTION_SNOOZE)
        ).build();
        Notification.Action completeAction = new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_complete),
                context.getString(R.string.mark_done),
                scheduler.actionPendingIntent(reminder, ReminderScheduler.ACTION_COMPLETE)
        ).build();

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_re)
                .setColor(Color.rgb(62, 84, 74))
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(reminder.title)
                .setStyle(new Notification.BigTextStyle().bigText(reminder.title))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(snoozeAction)
                .addAction(completeAction)
                .build();

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(reminder.requestCode(), notification);
    }

    private static void cancelNotification(Context context, Reminder reminder) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(reminder.requestCode());
    }
}
