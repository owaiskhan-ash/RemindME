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
    public static final String LANGUAGE_SPANISH = "es";
    public static final String LANGUAGE_PORTUGUESE = "pt";
    public static final String LANGUAGE_HINDI = "hi";
    public static final String LANGUAGE_ARABIC = "ar";
    public static final String LANGUAGE_CHINESE = "zh";

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

    /** English is the default, with commonly used world languages available. */
    public String getLanguageChoice() {
        return normalizeLanguage(preferences.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH));
    }

    public boolean usesIndonesian() {
        return LANGUAGE_INDONESIAN.equals(getLanguageChoice());
    }

    public boolean usesTwentyFourHourLanguageFormat() {
        return !LANGUAGE_ENGLISH.equals(getLanguageChoice());
    }

    /** Arabic is the only currently offered right-to-left app language. */
    public boolean usesRightToLeftLayout() {
        return LANGUAGE_ARABIC.equals(getLanguageChoice());
    }

    public void setLanguageChoice(String choice) {
        preferences.edit().putString(KEY_LANGUAGE, normalizeLanguage(choice)).apply();
    }

    /** Locale used for the in-app language, independent of the phone-wide setting. */
    public Locale getAppLocale() {
        String language = getLanguageChoice();
        if (LANGUAGE_INDONESIAN.equals(language)) return new Locale("in", "ID");
        if (LANGUAGE_SPANISH.equals(language)) return new Locale("es", "ES");
        if (LANGUAGE_PORTUGUESE.equals(language)) return new Locale("pt", "BR");
        if (LANGUAGE_HINDI.equals(language)) return new Locale("hi", "IN");
        if (LANGUAGE_ARABIC.equals(language)) return new Locale("ar");
        if (LANGUAGE_CHINESE.equals(language)) return new Locale("zh", "CN");
        return Locale.ENGLISH;
    }

    /** Gives receivers and notifications the saved in-app language as well. */
    public Context localizedContext(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(getAppLocale());
        return context.createConfigurationContext(configuration);
    }

    private static String normalizeLanguage(String choice) {
        if (LANGUAGE_INDONESIAN.equals(choice)
                || LANGUAGE_SPANISH.equals(choice)
                || LANGUAGE_PORTUGUESE.equals(choice)
                || LANGUAGE_HINDI.equals(choice)
                || LANGUAGE_ARABIC.equals(choice)
                || LANGUAGE_CHINESE.equals(choice)) {
            return choice;
        }
        return LANGUAGE_ENGLISH;
    }

    /** Dark mode is the first-run default and is retained across launches. */
    public boolean isDarkMode() {
        return preferences.getBoolean(KEY_DARK_MODE, true);
    }

    public void setDarkMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }
}
