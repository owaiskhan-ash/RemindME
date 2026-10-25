package com.remindme.app;

/** Keeps reminder titles readable on every Android font and notification surface. */
public final class ReminderText {
    private ReminderText() { }

    /**
     * Replaces common copy/paste punctuation with simple ASCII equivalents and
     * removes repeated-question-mark artifacts seen in old alarms.
     */
    public static String clean(String value) {
        if (value == null) return "";
        String cleaned = value
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2022', '-')
                .replace('\u00b7', '-')
                .replace('\ufffd', ' ')
                .replace('\u00a0', ' ');
        // Repeated question marks are an old fallback-glyph artifact, not useful
        // reminder content, so do not carry them into an alarm.
        cleaned = cleaned.replaceAll("\\?{2,}", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }
}
