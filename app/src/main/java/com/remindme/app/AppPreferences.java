package com.remindme.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Small, device-local preferences for how RemindME looks, sounds, and reads. */
public final class AppPreferences {
    public static final String REMINDME_ALARM_URL =
            "https://oxtqzqynhtybrgushmjp.supabase.co/storage/v1/object/public/Just%20Apps/Namaaz%20reminder/remind%20ME%20alarm.mp3";

    private static final String PREFS = "remindme_preferences";
    private static final String KEY_SOUND = "alarm_sound";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_LANGUAGE = "language";
    public static final String SOUND_DEVICE = "device";
    public static final String SOUND_REMINDME = "remindme";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_INDONESIAN = "id";

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

    /** English remains the default while Bahasa Indonesia is always available. */
    public String getLanguageChoice() {
        String choice = preferences.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH);
        return LANGUAGE_INDONESIAN.equals(choice) ? LANGUAGE_INDONESIAN : LANGUAGE_ENGLISH;
    }

    public boolean usesIndonesian() {
        return LANGUAGE_INDONESIAN.equals(getLanguageChoice());
    }

    public void setLanguageChoice(String choice) {
        preferences.edit()
                .putString(KEY_LANGUAGE, LANGUAGE_INDONESIAN.equals(choice)
                        ? LANGUAGE_INDONESIAN : LANGUAGE_ENGLISH)
                .apply();
    }

    /** Locale used for the in-app language, independent of the phone-wide setting. */
    public Locale getAppLocale() {
        return usesIndonesian() ? new Locale("in", "ID") : Locale.ENGLISH;
    }

    /** Gives receivers and notifications the saved in-app language as well. */
    public Context localizedContext(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(getAppLocale());
        return context.createConfigurationContext(configuration);
    }

    /** Dark mode is the first-run default and is retained across launches. */
    public boolean isDarkMode() {
        return preferences.getBoolean(KEY_DARK_MODE, true);
    }

    public void setDarkMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }
}
