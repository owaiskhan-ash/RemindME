package com.remindme.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, understandable natural-language parser.
 * It favors a safe default (tomorrow at 9:00 AM) instead of guessing silently.
 */
public final class ReminderParser {
    private static final Pattern RELATIVE = Pattern.compile(
            "\\bin\\s+(\\d+)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(next\\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AM_PM_TIME = Pattern.compile(
            "\\b(?:at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWENTY_FOUR_HOUR_TIME = Pattern.compile(
            "\\bat\\s+([01]?\\d|2[0-3])(?::([0-5]\\d))?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ReminderParser() { }

    public static ParsedReminder parse(String phrase) {
        String text = normalize(phrase);
        Calendar now = Calendar.getInstance();
        Calendar due = (Calendar) now.clone();
        boolean hasDate = false;
        boolean relative = false;

        Matcher relativeMatcher = RELATIVE.matcher(text);
        if (relativeMatcher.find()) {
            int amount = Math.max(1, safeInt(relativeMatcher.group(1), 1));
            String unit = relativeMatcher.group(2).toLowerCase(Locale.US);
            due.add(unit.startsWith("h") ? Calendar.HOUR_OF_DAY : Calendar.MINUTE, amount);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);
            hasDate = true;
            relative = true;
        } else if (contains(text, "day after tomorrow")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 2);
            hasDate = true;
        } else if (contains(text, "next week")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 7);
            hasDate = true;
        } else if (contains(text, "tomorrow")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 1);
            hasDate = true;
        } else if (contains(text, "today")) {
            due = startOfDay(now);
            hasDate = true;
        } else {
            Matcher weekdayMatcher = WEEKDAY.matcher(text);
            if (weekdayMatcher.find()) {
                int weekday = weekdayToCalendar(weekdayMatcher.group(2));
                int daysAway = (weekday - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                if (weekdayMatcher.group(1) != null || daysAway == 0) daysAway += 7;
                due = startOfDay(now);
                due.add(Calendar.DAY_OF_YEAR, daysAway);
                hasDate = true;
            }
        }

        ParsedTime parsedTime = parseTime(text);
        boolean timeWasInferred = false;
        if (!relative && parsedTime != null) {
            setTime(due, parsedTime.hour, parsedTime.minute);
        } else if (!relative && !hasDate) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 1);
            setTime(due, 9, 0);
            timeWasInferred = true;
        } else if (!relative && sameDay(due, now)) {
            due = (Calendar) now.clone();
            due.add(Calendar.HOUR_OF_DAY, 1);
            int roundedMinutes = ((due.get(Calendar.MINUTE) + 4) / 5) * 5;
            due.set(Calendar.MINUTE, roundedMinutes);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);
            timeWasInferred = true;
        } else if (!relative) {
            setTime(due, 9, 0);
            timeWasInferred = true;
        }

        String title = titleFromPhrase(text, parsedTime);
        if (title.isEmpty()) title = "A gentle reminder";
        return new ParsedReminder(title, due.getTimeInMillis(), timeWasInferred);
    }

    private static String normalize(String text) {
        return text
                .replaceAll("(?i)\\btomrws?\\b", "tomorrow")
                .replaceAll("(?i)\\btmrw?\\b", "tomorrow")
                .replaceAll("(?i)\\btomm?orow\\b", "tomorrow")
                .replaceAll("(?i)\\bceck[\\s-]?in\\b", "check-in")
                .replaceAll("(?i)\\bcheck[\\s-]?in\\b", "check-in")
                .replaceAll("(?i)\\bhw\\b", "homework")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String titleFromPhrase(String text, ParsedTime parsedTime) {
        String title = text
                .replaceAll("(?i)^\\s*(?:please\\s+)?(?:can\\s+you\\s+)?remind(?:\\s+me)?\\s*", "")
                .replaceAll("(?i)\\b(?:for|on)\\s+(?:the\\s+)?day after tomorrow\\b", " ")
                .replaceAll("(?i)\\bday after tomorrow\\b", " ")
                .replaceAll("(?i)\\b(?:for|on)\\s+tomorrow\\b", " ")
                .replaceAll("(?i)\\btomorrow\\b", " ")
                .replaceAll("(?i)\\b(?:for|on)\\s+today\\b", " ")
                .replaceAll("(?i)\\btoday\\b", " ")
                .replaceAll("(?i)\\bnext\\s+week\\b", " ")
                .replaceAll("(?i)\\bnext\\s+(?=sunday|monday|tuesday|wednesday|thursday|friday|saturday)", " ")
                .replaceAll("(?i)\\b(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b", " ")
                .replaceAll("(?i)\\bin\\s+\\d+\\s*(?:minute|minutes|min|mins|hour|hours|hr|hrs)\\b", " ");

        if (parsedTime != null && parsedTime.matched.length() > 0) {
            title = title.replaceFirst("(?i)\\b" + Pattern.quote(parsedTime.matched.trim()) + "\\b", " ");
        }

        title = title
                .replaceAll("(?i)\\b(?:at\\s*)?\\d{1,2}(?::\\d{2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b", " ")
                .replaceAll("(?i)\\bat\\s+([01]?\\d|2[0-3])(?::[0-5]\\d)?\\b", " ")
                .replaceAll("(?i)\\b(?:noon|midnight|morning|afternoon|evening|tonight)\\b", " ")
                .replaceFirst("(?i)^\\s*(?:to\\s+)?", "")
                .replaceFirst("(?i)^\\s*for\\s+", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^\\s*[,;:—–-]+\\s*|\\s*[,;:—–-]+\\s*$", "")
                .trim();

        if (title.isEmpty()) return "";
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Matcher personMatcher = Pattern.compile("\\b(for|with)\\s+([a-z])").matcher(title);
        StringBuffer result = new StringBuffer();
        while (personMatcher.find()) {
            personMatcher.appendReplacement(result, personMatcher.group(1) + " " + personMatcher.group(2).toUpperCase(Locale.US));
        }
        personMatcher.appendTail(result);
        return result.toString();
    }

    private static ParsedTime parseTime(String text) {
        Matcher amPm = AM_PM_TIME.matcher(text);
        if (amPm.find()) {
            int hour = safeInt(amPm.group(1), 9);
            int minute = safeInt(amPm.group(2), 0);
            String suffix = amPm.group(3).replace(".", "").toLowerCase(Locale.US);
            if ("pm".equals(suffix) && hour != 12) hour += 12;
            if ("am".equals(suffix) && hour == 12) hour = 0;
            return new ParsedTime(hour, minute, amPm.group());
        }

        Matcher twentyFour = TWENTY_FOUR_HOUR_TIME.matcher(text);
        if (twentyFour.find()) {
            return new ParsedTime(safeInt(twentyFour.group(1), 9), safeInt(twentyFour.group(2), 0), twentyFour.group());
        }

        if (contains(text, "noon")) return new ParsedTime(12, 0, "noon");
        if (contains(text, "midnight")) return new ParsedTime(0, 0, "midnight");
        if (contains(text, "morning")) return new ParsedTime(9, 0, "morning");
        if (contains(text, "afternoon")) return new ParsedTime(14, 0, "afternoon");
        if (contains(text, "evening") || contains(text, "tonight")) return new ParsedTime(18, 0, "evening");
        return null;
    }

    private static void setTime(Calendar calendar, int hour, int minute) {
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static Calendar startOfDay(Calendar original) {
        Calendar copy = (Calendar) original.clone();
        setTime(copy, 0, 0);
        return copy;
    }

    private static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private static int weekdayToCalendar(String weekday) {
        String name = weekday.toLowerCase(Locale.US);
        if ("monday".equals(name)) return Calendar.MONDAY;
        if ("tuesday".equals(name)) return Calendar.TUESDAY;
        if ("wednesday".equals(name)) return Calendar.WEDNESDAY;
        if ("thursday".equals(name)) return Calendar.THURSDAY;
        if ("friday".equals(name)) return Calendar.FRIDAY;
        if ("saturday".equals(name)) return Calendar.SATURDAY;
        return Calendar.SUNDAY;
    }

    private static int safeInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean contains(String text, String expected) {
        return text.toLowerCase(Locale.US).contains(expected.toLowerCase(Locale.US));
    }

    private static final class ParsedTime {
        final int hour;
        final int minute;
        final String matched;

        ParsedTime(int hour, int minute, String matched) {
            this.hour = hour;
            this.minute = minute;
            this.matched = matched;
        }
    }

    public static final class ParsedReminder {
        public final String title;
        public final long triggerAt;
        public final boolean timeWasInferred;

        ParsedReminder(String title, long triggerAt, boolean timeWasInferred) {
            this.title = title;
            this.triggerAt = triggerAt;
            this.timeWasInferred = timeWasInferred;
        }
    }
}
