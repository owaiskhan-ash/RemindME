package com.remindme.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores pending device alarms after a reboot or an app update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        new ReminderScheduler(context).rescheduleAll();
    }
}
