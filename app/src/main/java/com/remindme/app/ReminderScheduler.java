package com.remindme.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/** Schedules device alarms so reminders do not depend on the activity staying open. */
public final class ReminderScheduler {
    public static final String ACTION_FIRE = "com.remindme.app.FIRE_REMINDER";
    public static final String ACTION_SNOOZE = "com.remindme.app.SNOOZE_REMINDER";
    public static final String ACTION_COMPLETE = "com.remindme.app.COMPLETE_REMINDER";
    public static final String EXTRA_REMINDER_ID = "reminder_id";

    private final Context context;
    private final AlarmManager alarmManager;

    public ReminderScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    public void schedule(Reminder reminder) {
        if (reminder.completed || reminder.triggerAt <= System.currentTimeMillis()) return;
        PendingIntent pendingIntent = alarmPendingIntent(reminder, ACTION_FIRE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAt,
                    pendingIntent
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Inexact fallback still wakes the phone and works without special access.
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAt,
                    pendingIntent
            );
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent);
        }
    }

    public void cancel(Reminder reminder) {
        alarmManager.cancel(alarmPendingIntent(reminder, ACTION_FIRE));
    }

    public void rescheduleAll() {
        List<Reminder> reminders = new ReminderStore(context).load();
        for (Reminder reminder : reminders) {
            if (!reminder.completed && !reminder.notified) schedule(reminder);
        }
    }

    public PendingIntent actionPendingIntent(Reminder reminder, String action) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminder.id);
        int requestCode = reminder.requestCode() ^ action.hashCode();
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent alarmPendingIntent(Reminder reminder, String action) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminder.id);
        return PendingIntent.getBroadcast(
                context,
                reminder.requestCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
