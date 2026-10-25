package com.remindme.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
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
    // The registerReceiver flags overload is available from API 26. This literal
    // is Context.RECEIVER_NOT_EXPORTED on newer SDKs.
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
    private View appRoot;
    private Spinner languageSpinner;
    private RadioGroup alarmSoundGroup;
    private Switch darkModeSwitch;

    private ReminderStore store;
    private ReminderScheduler scheduler;
    private AppPreferences preferences;
    private int selectedScreen = SCREEN_HOME;
    private boolean stateReceiverRegistered;
    // Prevent widget setup and duplicate callbacks from repeatedly recreating
    // the activity. This is especially important after locale changes.
    private boolean syncingSettings;
    private boolean recreatePending;

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
        // Resource qualifiers follow the saved app preferences, rather than only
        // phone-wide settings, so both language and dark-mode choices persist.
        AppPreferences savedPreferences = new AppPreferences(newBase);
        Configuration configuration = new Configuration(newBase.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (savedPreferences.isDarkMode()
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        // Only override resource qualifiers here. Applying layout direction to
        // the activity configuration can repeatedly trigger configuration
        // recreation on some Android builds; the root view handles RTL below.
        configuration.setLocale(savedPreferences.getAppLocale());
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        appRoot = findViewById(R.id.app_root);
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
        languageSpinner = findViewById(R.id.language_spinner);
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

        configureLanguageDropdown();
        // Establish the saved item before the listener is attached so first
        // layout can never interpret the English default as a user change.
        languageSpinner.setSelection(languagePosition(preferences.getLanguageChoice()), false);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingSettings) return;
                String selectedLanguage = languageChoiceForPosition(position);
                if (preferences.getLanguageChoice().equals(selectedLanguage)) return;
                preferences.setLanguageChoice(selectedLanguage);
                requestStableRecreate();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        alarmSoundGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (syncingSettings) return;
                preferences.setSoundChoice(checkedId == R.id.remindme_alarm_radio
                        ? AppPreferences.SOUND_REMINDME : AppPreferences.SOUND_DEVICE);
            }
        });
        darkModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (syncingSettings || preferences.isDarkMode() == isChecked) return;
                preferences.setDarkMode(isChecked);
                requestStableRecreate();
            }
        });
    }

    private void configureLanguageDropdown() {
        String[] labels = new String[]{
                getString(R.string.language_english),
                getString(R.string.language_indonesian),
                getString(R.string.language_spanish),
                getString(R.string.language_portuguese),
                getString(R.string.language_hindi),
                getString(R.string.language_arabic),
                getString(R.string.language_chinese)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleLanguageSpinnerItem(view);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleLanguageSpinnerItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
    }

    private void styleLanguageSpinnerItem(View item) {
        if (!(item instanceof TextView)) return;
        TextView label = (TextView) item;
        label.setTextColor(getColor(R.color.ink));
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        label.setPadding(dp(12), dp(8), dp(12), dp(8));
    }

    private void syncSettingsControls() {
        syncingSettings = true;
        try {
            languageSpinner.setSelection(languagePosition(preferences.getLanguageChoice()), false);
            alarmSoundGroup.check(preferences.usesRemindMeSound()
                    ? R.id.remindme_alarm_radio : R.id.device_alarm_radio);
            darkModeSwitch.setChecked(preferences.isDarkMode());
            applySavedLayoutDirection();
        } finally {
            syncingSettings = false;
        }
    }

    private String languageChoiceForPosition(int position) {
        switch (position) {
            case 1: return AppPreferences.LANGUAGE_INDONESIAN;
            case 2: return AppPreferences.LANGUAGE_SPANISH;
            case 3: return AppPreferences.LANGUAGE_PORTUGUESE;
            case 4: return AppPreferences.LANGUAGE_HINDI;
            case 5: return AppPreferences.LANGUAGE_ARABIC;
            case 6: return AppPreferences.LANGUAGE_CHINESE;
            default: return AppPreferences.LANGUAGE_ENGLISH;
        }
    }

    private int languagePosition(String language) {
        if (AppPreferences.LANGUAGE_INDONESIAN.equals(language)) return 1;
        if (AppPreferences.LANGUAGE_SPANISH.equals(language)) return 2;
        if (AppPreferences.LANGUAGE_PORTUGUESE.equals(language)) return 3;
        if (AppPreferences.LANGUAGE_HINDI.equals(language)) return 4;
        if (AppPreferences.LANGUAGE_ARABIC.equals(language)) return 5;
        if (AppPreferences.LANGUAGE_CHINESE.equals(language)) return 6;
        return 0;
    }

    private void applySavedLayoutDirection() {
        if (appRoot == null) return;
        int direction = preferences.usesRightToLeftLayout()
                ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        appRoot.setLayoutDirection(direction);
        languageSpinner.setLayoutDirection(direction);
    }

    private void requestStableRecreate() {
        if (recreatePending || isFinishing()) return;
        recreatePending = true;
        View root = appRoot == null ? getWindow().getDecorView() : appRoot;
        root.post(new Runnable() {
            @Override public void run() {
                if (!isFinishing() && !isDestroyed()) recreate();
            }
        });
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
        String title = displayTitle(parsed.title);
        if (!parsed.isReadyToSchedule()) {
            previewText.setText(title);
            previewHint.setText(scheduleDetailsMessage(parsed));
            previewHint.setVisibility(View.VISIBLE);
            return;
        }

        previewText.setText(getString(R.string.preview_scheduled, title, formatWhen(parsed.triggerAt)));
        previewHint.setText(R.string.preview_ready);
        previewHint.setVisibility(View.VISIBLE);
    }

    private void saveReminder() {
        String phrase = reminderInput.getText().toString().trim();
        if (phrase.isEmpty()) {
            reminderInput.setError(getString(R.string.write_reminder_first));
            reminderInput.requestFocus();
            return;
        }

        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        if (!parsed.isReadyToSchedule()) {
            String prompt = scheduleDetailsMessage(parsed);
            reminderInput.setError(prompt);
            reminderInput.requestFocus();
            Toast.makeText(this, prompt, Toast.LENGTH_LONG).show();
            return;
        }

        Reminder reminder = new Reminder(
                UUID.randomUUID().toString(),
                displayTitle(parsed.title),
                phrase,
                parsed.triggerAt
        );
        store.update(reminder);
        scheduler.schedule(reminder);

        reminderInput.setText("");
        refreshReminderLists();
        Toast.makeText(this, getString(R.string.alarm_set_for, formatWhen(reminder.triggerAt)),
                Toast.LENGTH_LONG).show();

        if (needsNotificationPermission()) {
            requestNotificationPermission();
        } else {
            updateAccessCard();
        }
    }

    private String scheduleDetailsMessage(ReminderParser.ParsedReminder parsed) {
        if (!parsed.hasDate && !parsed.hasTime) return getString(R.string.missing_date_and_time);
        if (!parsed.hasDate) return getString(R.string.missing_date);
        return getString(R.string.missing_time);
    }

    private String displayTitle(String rawTitle) {
        String title = ReminderText.clean(rawTitle);
        return title.isEmpty() ? getString(R.string.default_reminder_title) : title;
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
            remindersContainer.addView(createEmptyCard(getString(R.string.empty_upcoming)));
        }
        if (!hasCompleted) {
            historyContainer.addView(createEmptyCard(getString(R.string.empty_history)));
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
        card.addView(reminderTitle(reminder.title, false), matchWidthWrap());

        TextView when = new TextView(this);
        when.setText(reminder.notified ? getString(R.string.due_now_action) : formatWhen(reminder.triggerAt));
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

        Button done = compactButton(getString(R.string.button_mark_done), R.drawable.bg_done_button,
                getColor(R.color.sage_dark));
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { completeReminder(reminder); }
        });
        actions.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        Button remove = compactButton(getString(R.string.button_delete), 0, getColor(R.color.danger));
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
        when.setText(R.string.completed);
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

        Button remove = compactButton(getString(R.string.button_delete), 0, getColor(R.color.danger));
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
        title.setText(displayTitle(titleText));
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
        Toast.makeText(this, R.string.done_moved_previous, Toast.LENGTH_SHORT).show();
    }

    private void deleteReminder(Reminder reminder) {
        scheduler.cancel(reminder);
        dismissActiveAlarm(reminder);
        store.delete(reminder.id);
        refreshReminderLists();
        Toast.makeText(this, R.string.reminder_deleted, Toast.LENGTH_SHORT).show();
    }

    private void dismissActiveAlarm(Reminder reminder) {
        AlarmSoundService.stop(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(reminder.requestCode());
    }

    private void clearCompleted() {
        store.clearCompleted();
        refreshReminderLists();
        Toast.makeText(this, R.string.completed_cleared, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, R.string.open_alarm_settings, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (needsDndAlarmAccess()) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.open_dnd_settings, Toast.LENGTH_LONG).show();
            }
            return;
        }
        Toast.makeText(this, R.string.access_ready, Toast.LENGTH_SHORT).show();
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
            notificationMessage.setText(R.string.access_notification_needed);
            permissionButton.setText(R.string.enable);
            permissionButton.setVisibility(View.VISIBLE);
        } else if (needsExactAlarmAccess()) {
            notificationMessage.setText(R.string.access_exact_alarm_needed);
            permissionButton.setText(R.string.allow_alarms);
            permissionButton.setVisibility(View.VISIBLE);
        } else if (needsDndAlarmAccess()) {
            notificationMessage.setText(R.string.access_dnd_needed);
            permissionButton.setText(R.string.dnd_access);
            permissionButton.setVisibility(View.VISIBLE);
        } else {
            notificationMessage.setText(R.string.access_ready);
            permissionButton.setText(R.string.access_on);
            permissionButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.notifications_later, Toast.LENGTH_LONG).show();
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
        Locale locale = preferences.getAppLocale();
        DateFormat timeFormat = new SimpleDateFormat(
                preferences.usesTwentyFourHourLanguageFormat() ? "HH:mm" : "h:mm a", locale);
        String time = timeFormat.format(new Date(timestamp));

        if (sameDay(target, now)) return getString(R.string.today_at, time);
        if (sameDay(target, tomorrow)) return getString(R.string.tomorrow_at, time);
        DateFormat dateFormat = new SimpleDateFormat(
                preferences.usesTwentyFourHourLanguageFormat() ? "EEE, d MMM" : "EEE, MMM d", locale);
        return getString(R.string.date_at, dateFormat.format(new Date(timestamp)), time);
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
