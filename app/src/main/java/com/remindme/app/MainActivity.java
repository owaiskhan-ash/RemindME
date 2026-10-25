package com.remindme.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** A focused reminder experience: create, review, and adjust only what matters. */
public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 201;
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_HISTORY = 1;
    private static final int SCREEN_SETTINGS = 2;
    // Available from API 26. This is Context.RECEIVER_NOT_EXPORTED on newer SDKs.
    private static final int RECEIVER_NOT_EXPORTED = 0x4;
    // Kept as a literal so the app compiles against Android 12 while still
    // requesting the Android 13 notification runtime permission on newer phones.
    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    private static final String STATE_SCREEN = "selected_screen";

    private EditText reminderInput;
    private TextView previewText;
    private TextView previewHint;
    private TextView notificationMessage;
    private Button permissionButton;
    private Button clearHistoryButton;
    private LinearLayout remindersContainer;
    private LinearLayout historyContainer;
    private View homeScreen;
    private View historyScreen;
    private View settingsScreen;
    private View navHome;
    private View navHistory;
    private View navSettings;
    private ImageView navHomeIcon;
    private ImageView navHistoryIcon;
    private ImageView navSettingsIcon;
    private TextView navHomeLabel;
    private TextView navHistoryLabel;
    private TextView navSettingsLabel;
    private RadioGroup alarmSoundGroup;
    private Switch darkModeSwitch;

    private ReminderStore store;
    private ReminderScheduler scheduler;
    private AppPreferences preferences;
    private int selectedScreen = SCREEN_HOME;
    private boolean stateReceiverRegistered;

    private final BroadcastReceiver reminderStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ReminderReceiver.ACTION_REMINDER_STATE_CHANGED.equals(intent.getAction())) {
                refreshReminderLists();
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        // Resource qualifiers follow this persisted choice too, rather than the
        // phone-wide setting, so the in-app toggle always has an immediate result.
        AppPreferences savedPreferences = new AppPreferences(newBase);
        Configuration configuration = new Configuration(newBase.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (savedPreferences.isDarkMode()
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Pick the saved palette before the view hierarchy is inflated. Dark is
        // the first-run default, while light remains an explicit user choice.
        preferences = new AppPreferences(this);
        setTheme(preferences.isDarkMode()
                ? R.style.Theme_RemindME_Dark
                : R.style.Theme_RemindME_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureSystemBars();

        selectedScreen = savedInstanceState == null
                ? SCREEN_HOME : savedInstanceState.getInt(STATE_SCREEN, SCREEN_HOME);
        store = new ReminderStore(this);
        scheduler = new ReminderScheduler(this);
        ReminderReceiver.createNotificationChannel(this);

        bindViews();
        bindInteractions();
        syncSettingsControls();
        updatePreview();
        refreshReminderLists();
        updateAccessCard();
        scheduler.rescheduleAll();
        showScreen(selectedScreen);
    }

    private void bindViews() {
        reminderInput = findViewById(R.id.reminder_input);
        previewText = findViewById(R.id.preview_text);
        previewHint = findViewById(R.id.preview_hint);
        notificationMessage = findViewById(R.id.notification_message);
        permissionButton = findViewById(R.id.permission_button);
        clearHistoryButton = findViewById(R.id.clear_history_button);
        remindersContainer = findViewById(R.id.reminders_container);
        historyContainer = findViewById(R.id.history_container);
        homeScreen = findViewById(R.id.home_screen);
        historyScreen = findViewById(R.id.history_screen);
        settingsScreen = findViewById(R.id.settings_screen);
        navHome = findViewById(R.id.nav_home);
        navHistory = findViewById(R.id.nav_history);
        navSettings = findViewById(R.id.nav_settings);
        navHomeIcon = findViewById(R.id.nav_home_icon);
        navHistoryIcon = findViewById(R.id.nav_history_icon);
        navSettingsIcon = findViewById(R.id.nav_settings_icon);
        navHomeLabel = findViewById(R.id.nav_home_label);
        navHistoryLabel = findViewById(R.id.nav_history_label);
        navSettingsLabel = findViewById(R.id.nav_settings_label);
        alarmSoundGroup = findViewById(R.id.alarm_sound_group);
        darkModeSwitch = findViewById(R.id.dark_mode_switch);
    }

    private void bindInteractions() {
        reminderInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        findViewById(R.id.set_reminder_button).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { saveReminder(); }
        });
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestRequiredAccess(); }
        });
        clearHistoryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { clearCompleted(); }
        });

        navHome.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showScreen(SCREEN_HOME); }
        });
        navHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showScreen(SCREEN_HISTORY); }
        });
        navSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showScreen(SCREEN_SETTINGS); }
        });

        alarmSoundGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                preferences.setSoundChoice(checkedId == R.id.remindme_alarm_radio
                        ? AppPreferences.SOUND_REMINDME : AppPreferences.SOUND_DEVICE);
            }
        });
        darkModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (preferences.isDarkMode() == isChecked) return;
                preferences.setDarkMode(isChecked);
                recreate();
            }
        });
    }

    private void syncSettingsControls() {
        alarmSoundGroup.check(preferences.usesRemindMeSound()
                ? R.id.remindme_alarm_radio : R.id.device_alarm_radio);
        darkModeSwitch.setChecked(preferences.isDarkMode());
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.surface));
        getWindow().setNavigationBarColor(getColor(R.color.surface));
        int lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(preferences.isDarkMode()
                ? 0 : lightSystemBars);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!stateReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ReminderReceiver.ACTION_REMINDER_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(reminderStateReceiver, filter, RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(reminderStateReceiver, filter);
            }
            stateReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (stateReceiverRegistered) {
            unregisterReceiver(reminderStateReceiver);
            stateReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            // Recreate the dedicated channel after returning from Android's DND
            // access screen, so it can bypass DND after explicit user approval.
            ReminderReceiver.createNotificationChannel(this);
            refreshReminderLists();
            updateAccessCard();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showScreen(SCREEN_HOME);
        if (store != null) refreshReminderLists();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SCREEN, selectedScreen);
        super.onSaveInstanceState(outState);
    }

    private void showScreen(int screen) {
        selectedScreen = screen;
        homeScreen.setVisibility(screen == SCREEN_HOME ? View.VISIBLE : View.GONE);
        historyScreen.setVisibility(screen == SCREEN_HISTORY ? View.VISIBLE : View.GONE);
        settingsScreen.setVisibility(screen == SCREEN_SETTINGS ? View.VISIBLE : View.GONE);
        updateNavigationItem(navHome, navHomeIcon, navHomeLabel, screen == SCREEN_HOME);
        updateNavigationItem(navHistory, navHistoryIcon, navHistoryLabel, screen == SCREEN_HISTORY);
        updateNavigationItem(navSettings, navSettingsIcon, navSettingsLabel, screen == SCREEN_SETTINGS);
    }

    private void updateNavigationItem(View item, ImageView icon, TextView label, boolean selected) {
        int color = getColor(selected ? R.color.dock_active : R.color.dock_inactive);
        item.setBackgroundResource(selected ? R.drawable.bg_nav_selected : android.R.color.transparent);
        icon.setColorFilter(color);
        label.setTextColor(color);
        label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
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
        refreshReminderLists();
        Toast.makeText(this, "Alarm set for " + formatWhen(reminder.triggerAt), Toast.LENGTH_LONG).show();

        if (needsNotificationPermission()) {
            requestNotificationPermission();
        } else {
            updateAccessCard();
        }
    }

    private void refreshReminderLists() {
        if (remindersContainer == null || historyContainer == null) return;
        ArrayList<Reminder> reminders = store.load();
        remindersContainer.removeAllViews();
        historyContainer.removeAllViews();
        boolean hasUpcoming = false;
        boolean hasCompleted = false;

        for (Reminder reminder : reminders) {
            if (reminder.completed) {
                hasCompleted = true;
                historyContainer.addView(createHistoryCard(reminder));
            } else {
                hasUpcoming = true;
                remindersContainer.addView(createUpcomingCard(reminder));
            }
        }

        if (!hasUpcoming) {
            remindersContainer.addView(createEmptyCard(
                    "Nothing scheduled yet. Your next gentle nudge will appear here."));
        }
        if (!hasCompleted) {
            historyContainer.addView(createEmptyCard(
                    "No previous reminders yet. Mark one done and it will live here."));
        }
        clearHistoryButton.setVisibility(hasCompleted ? View.VISIBLE : View.GONE);
    }

    private View createEmptyCard(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextSize(14);
        empty.setTextColor(getColor(R.color.ink_muted));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(24), dp(18), dp(24));
        empty.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams params = matchWidthWrap();
        params.topMargin = dp(2);
        empty.setLayoutParams(params);
        return empty;
    }

    private View createUpcomingCard(final Reminder reminder) {
        LinearLayout card = createCardShell(false);

        TextView title = reminderTitle(reminder.title, false);
        card.addView(title, matchWidthWrap());

        TextView when = new TextView(this);
        when.setText(reminder.notified
                ? "Due now · Snooze from the alarm or mark it done."
                : formatWhen(reminder.triggerAt));
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

        Button done = compactButton("Mark done", R.drawable.bg_done_button, getColor(R.color.sage_dark));
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { completeReminder(reminder); }
        });
        actions.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        Button remove = compactButton("Delete", 0, getColor(R.color.danger));
        remove.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        removeParams.leftMargin = dp(2);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { deleteReminder(reminder); }
        });
        actions.addView(remove, removeParams);
        card.addView(actions, actionsParams);
        return card;
    }

    private View createHistoryCard(final Reminder reminder) {
        LinearLayout card = createCardShell(true);
        card.addView(reminderTitle(reminder.title, true), matchWidthWrap());

        TextView when = new TextView(this);
        when.setText("Completed");
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

        Button remove = compactButton("Delete", 0, getColor(R.color.danger));
        remove.setBackgroundColor(0x00000000);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { deleteReminder(reminder); }
        });
        actions.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        card.addView(actions, actionsParams);
        return card;
    }

    private LinearLayout createCardShell(boolean completed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(11), dp(11));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setAlpha(completed ? .68f : 1f);
        LinearLayout.LayoutParams cardParams = matchWidthWrap();
        cardParams.topMargin = dp(9);
        card.setLayoutParams(cardParams);
        return card;
    }

    private TextView reminderTitle(String titleText, boolean completed) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(16);
        title.setTextColor(getColor(R.color.ink));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (completed) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return title;
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

    /** Marking done cancels any active alarm and moves the item to Previous. */
    private void completeReminder(Reminder reminder) {
        if (reminder.completed) return;
        reminder.completed = true;
        reminder.notified = true;
        scheduler.cancel(reminder);
        dismissActiveAlarm(reminder);
        store.update(reminder);
        refreshReminderLists();
        Toast.makeText(this, "Marked done — moved to Previous.", Toast.LENGTH_SHORT).show();
    }

    private void deleteReminder(Reminder reminder) {
        scheduler.cancel(reminder);
        dismissActiveAlarm(reminder);
        store.delete(reminder.id);
        refreshReminderLists();
        Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
    }

    private void dismissActiveAlarm(Reminder reminder) {
        AlarmSoundService.stop(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(reminder.requestCode());
    }

    private void clearCompleted() {
        store.clearCompleted();
        refreshReminderLists();
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
        if (needsDndAlarmAccess()) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open Android settings to allow Do Not Disturb access.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        Toast.makeText(this, "Alarm alerts are ready, even when the ringer is silent.", Toast.LENGTH_SHORT).show();
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

    private boolean needsDndAlarmAccess() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && !manager.isNotificationPolicyAccessGranted();
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
        } else if (needsDndAlarmAccess()) {
            notificationMessage.setText("Alarm tones use the alarm volume, even when your phone is silent. Allow Do Not Disturb access only if you want alarms to break through DND too.");
            permissionButton.setText("DND access");
            permissionButton.setVisibility(View.VISIBLE);
        } else {
            notificationMessage.setText("Alarm alerts are ready. They use Android’s alarm stream, so ring/silent mode will not mute them.");
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
