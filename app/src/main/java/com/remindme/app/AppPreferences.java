package com.remindme.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, device-local preferences for how RemindME looks and sounds. */
public final class AppPreferences {
    public static final String REMINDME_ALARM_URL =
            "https://oxtqzqynhtybrgushmjp.supabase.co/storage/v1/object/public/Just%20Apps/Namaaz%20reminder/remind%20ME%20alarm.mp3";

    private static final String PREFS = "remindme_preferences";
    private static final String KEY_SOUND = "alarm_sound";
    private static final String KEY_DARK_MODE = "dark_mode";
    public static final String SOUND_DEVICE = "device";
    public static final String SOUND_REMINDME = "remindme";

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Device alarm audio is deliberately the default. */
    public String getSoundChoice() {
        String choice = preferences.getString(KEY_SOUND, SOUND_DEVICE);
        return SOUND_REMINDME.equals(choice) ? SOUND_REMINDME : SOUND_DEVICE;
    }

    public boolean usesRemindMeSound() {
        return SOUND_REMINDME.equals(getSoundChoice());
    }

    public void setSoundChoice(String choice) {
        preferences.edit()
                .putString(KEY_SOUND, SOUND_REMINDME.equals(choice)
                        ? SOUND_REMINDME : SOUND_DEVICE)
                .apply();
    }

    /** Dark mode is the first-run default and is retained across launches. */
    public boolean isDarkMode() {
        return preferences.getBoolean(KEY_DARK_MODE, true);
    }

    public void setDarkMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }
}
