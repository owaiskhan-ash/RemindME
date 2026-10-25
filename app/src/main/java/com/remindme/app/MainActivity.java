package com.remindme.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** The entire, intentionally focused Android experience: write, confirm, and be reminded. */
public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 201;
    // Kept as a literal so the app can be compiled against Android 12 while still
    // requesting the Android 13 notification runtime permission on newer phones.
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    private EditText reminderInput;
    private TextView previewText;
    private TextView previewHint;
    private TextView notificationMessage;
    private Button permissionButton;
    private Button clearCompletedButton;
    private LinearLayout remindersContainer;
    private ReminderStore store;
    private ReminderScheduler scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(getColor(R.color.surface));
        getWindow().setNavigationBarColor(getColor(R.color.surface));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        store = new ReminderStore(this);
        scheduler = new ReminderScheduler(this);
        ReminderReceiver.createNotificationChannel(this);

        reminderInput = findViewById(R.id.reminder_input);
        previewText = findViewById(R.id.preview_text);
        previewHint = findViewById(R.id.preview_hint);
        notificationMessage = findViewById(R.id.notification_message);
        permissionButton = findViewById(R.id.permission_button);
        clearCompletedButton = findViewById(R.id.clear_completed_button);
        remindersContainer = findViewById(R.id.reminders_container);

        reminderInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        findViewById(R.id.set_reminder_button).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { saveReminder(); }
        });
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestRequiredAccess(); }
        });
        clearCompletedButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { clearCompleted(); }
        });

        updatePreview();
        refreshReminderList();
        updateAccessCard();
        scheduler.rescheduleAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            refreshReminderList();
            updateAccessCard();
        }
    }

    private void updatePreview() {
        if (reminderInput == null) return;
        String phrase = reminderInput.getText().toString().trim();
        if (phrase.isEmpty()) {
            previewText.setText(R.string.preview_empty);
            previewHint.setVisibility(View.GONE);
            return;
        }

        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        previewText.setText(parsed.title + "  ·  " + formatWhen(parsed.triggerAt));
        previewHint.setText(parsed.timeWasInferred
                ? "No time mentioned — 9:00 AM is the default."
                : "Ready to schedule.");
        previewHint.setVisibility(View.VISIBLE);
    }

    private void saveReminder() {
        String phrase = reminderInput.getText().toString().trim();
        if (phrase.isEmpty()) {
            reminderInput.setError("Write a reminder first");
            reminderInput.requestFocus();
            return;
        }

        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        Reminder reminder = new Reminder(
                UUID.randomUUID().toString(),
                parsed.title,
                phrase,
                parsed.triggerAt
        );
        store.update(reminder);
        scheduler.schedule(reminder);

        reminderInput.setText("");
        refreshReminderList();
        Toast.makeText(this, "Alarm set for " + formatWhen(reminder.triggerAt), Toast.LENGTH_LONG).show();

        if (needsNotificationPermission()) {
            requestNotificationPermission();
        } else {
            updateAccessCard();
        }
    }

    private void refreshReminderList() {
        if (remindersContainer == null) return;
        ArrayList<Reminder> reminders = store.load();
        remindersContainer.removeAllViews();
        boolean hasCompleted = false;

        if (reminders.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing scheduled yet. Your next gentle nudge will appear here.");
            empty.setTextSize(14);
            empty.setTextColor(getColor(R.color.ink_muted));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(24), dp(18), dp(24));
            empty.setBackgroundResource(R.drawable.bg_card);
            remindersContainer.addView(empty, matchWidthWrap());
        } else {
            for (Reminder reminder : reminders) {
                if (reminder.completed) hasCompleted = true;
                remindersContainer.addView(createReminderCard(reminder));
            }
        }
        clearCompletedButton.setVisibility(hasCompleted ? View.VISIBLE : View.GONE);
    }

    private View createReminderCard(final Reminder reminder) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(11), dp(11));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setAlpha(reminder.completed ? .58f : 1f);
        LinearLayout.LayoutParams cardParams = matchWidthWrap();
        cardParams.topMargin = dp(9);
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(reminder.title);
        title.setTextSize(16);
        title.setTextColor(getColor(R.color.ink));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (reminder.completed) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        card.addView(title, matchWidthWrap());

        TextView when = new TextView(this);
        when.setText(reminder.completed ? "Completed" : reminder.notified ? "Due now" : formatWhen(reminder.triggerAt));
        when.setTextSize(12);
        when.setTextColor(getColor(R.color.ink_muted));
        LinearLayout.LayoutParams whenParams = matchWidthWrap();
        whenParams.topMargin = dp(4);
        card.addView(when, whenParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWidthWrap();
        actionsParams.topMargin = dp(7);

        Button done = compactButton(reminder.completed ? "Undo" : "Done", R.drawable.bg_done_button, getColor(R.color.sage_dark));
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleCompleted(reminder); }
        });
        actions.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        Button remove = compactButton("Delete", 0, getColor(R.color.danger));
        remove.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        removeParams.leftMargin = dp(2);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { deleteReminder(reminder); }
        });
        actions.addView(remove, removeParams);
        card.addView(actions, actionsParams);
        return card;
    }

    private Button compactButton(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTransformationMethod(null);
        button.setTextColor(textColor);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(11), 0, dp(11), 0);
        if (background != 0) button.setBackgroundResource(background);
        return button;
    }

    private void toggleCompleted(Reminder reminder) {
        reminder.completed = !reminder.completed;
        if (reminder.completed) {
            scheduler.cancel(reminder);
            Toast.makeText(this, "Marked complete", Toast.LENGTH_SHORT).show();
        } else {
            reminder.notified = false;
            scheduler.schedule(reminder);
            Toast.makeText(this, "Moved back to upcoming", Toast.LENGTH_SHORT).show();
        }
        store.update(reminder);
        refreshReminderList();
    }

    private void deleteReminder(Reminder reminder) {
        scheduler.cancel(reminder);
        store.delete(reminder.id);
        refreshReminderList();
        Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
    }

    private void clearCompleted() {
        store.clearCompleted();
        refreshReminderList();
        Toast.makeText(this, "Completed reminders cleared", Toast.LENGTH_SHORT).show();
    }

    private void requestRequiredAccess() {
        if (needsNotificationPermission()) {
            requestNotificationPermission();
            return;
        }
        if (needsExactAlarmAccess()) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Open Android settings to allow alarms and reminders.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        Toast.makeText(this, "Alerts are ready.", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        AlarmManager manager = getSystemService(AlarmManager.class);
        return manager != null && !manager.canScheduleExactAlarms();
    }

    private void updateAccessCard() {
        if (notificationMessage == null) return;
        if (needsNotificationPermission()) {
            notificationMessage.setText("Turn on notifications to receive a clear alert and gentle alarm when it is due.");
            permissionButton.setText("Enable");
            permissionButton.setVisibility(View.VISIBLE);
        } else if (needsExactAlarmAccess()) {
            notificationMessage.setText("Notifications are on. Allow exact alarms for minute-accurate reminders.");
            permissionButton.setText("Allow alarms");
            permissionButton.setVisibility(View.VISIBLE);
        } else {
            notificationMessage.setText("Alerts are ready. Your reminder can arrive even after you close the app.");
            permissionButton.setText("On");
            permissionButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Reminders are saved. You can enable alerts later in Android settings.", Toast.LENGTH_LONG).show();
            }
            updateAccessCard();
        }
    }

    private String formatWhen(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        DateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

        if (sameDay(target, now)) return "Today at " + timeFormat.format(new Date(timestamp));
        if (sameDay(target, tomorrow)) return "Tomorrow at " + timeFormat.format(new Date(timestamp));
        DateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        return dateFormat.format(new Date(timestamp)) + " at " + timeFormat.format(new Date(timestamp));
    }

    private boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
