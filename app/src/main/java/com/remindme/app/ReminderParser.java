package com.remindme.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, understandable natural-language parser for English and
 * Bahasa Indonesia. It never silently schedules a missing date or time.
 */
public final class ReminderParser {
    private static final Pattern RELATIVE_ENGLISH = Pattern.compile(
            "\\bin\\s+(\\d+)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RELATIVE_INDONESIAN_PREFIX = Pattern.compile(
            "\\bdalam\\s+(\\d+)\\s*(menit|mnt|jam)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RELATIVE_INDONESIAN_SUFFIX = Pattern.compile(
            "\\b(\\d+)\\s*(menit|mnt|jam)\\s+lagi\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WEEKDAY = Pattern.compile(
            "\\b(?:(next|depan)\\s+)?"
                    + "(sunday|monday|tuesday|wednesday|thursday|friday|saturday"
                    + "|senin|selasa|rabu|kamis|jumat|jum'at|sabtu|minggu|ahad)"
                    + "(?:\\s+(depan))?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AM_PM_TIME = Pattern.compile(
            "\\b(?:at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWENTY_FOUR_HOUR_ENGLISH = Pattern.compile(
            "\\bat\\s+([01]?\\d|2[0-3])(?::([0-5]\\d))?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INDONESIAN_TIME = Pattern.compile(
            "\\b(?:jam|pukul)\\s+([01]?\\d|2[0-3])(?::([0-5]\\d))?"
                    + "(?:\\s+(pagi|siang|sore|malam))?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ReminderParser() { }

    public static ParsedReminder parse(String phrase) {
        String text = normalize(phrase);
        Calendar now = Calendar.getInstance();
        Calendar due = (Calendar) now.clone();
        boolean hasDate = false;
        boolean hasTime = false;
        boolean relative = false;

        RelativeMatch relativeMatch = findRelativeTime(text);
        if (relativeMatch != null) {
            due.add(relativeMatch.hours ? Calendar.HOUR_OF_DAY : Calendar.MINUTE, relativeMatch.amount);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);
            hasDate = true;
            hasTime = true;
            relative = true;
        } else if (contains(text, "day after tomorrow") || contains(text, "lusa")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 2);
            hasDate = true;
        } else if (contains(text, "next week") || contains(text, "minggu depan")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 7);
            hasDate = true;
        } else if (contains(text, "tomorrow") || contains(text, "besok")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 1);
            hasDate = true;
        } else if (contains(text, "today") || contains(text, "hari ini")) {
            due = startOfDay(now);
            hasDate = true;
        } else {
            Matcher weekdayMatcher = WEEKDAY.matcher(text);
            if (weekdayMatcher.find()) {
                int weekday = weekdayToCalendar(weekdayMatcher.group(2));
                int daysAway = (weekday - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
                if (weekdayMatcher.group(1) != null || weekdayMatcher.group(3) != null || daysAway == 0) {
                    daysAway += 7;
                }
                due = startOfDay(now);
                due.add(Calendar.DAY_OF_YEAR, daysAway);
                hasDate = true;
            }
        }

        ParsedTime parsedTime = relative ? null : parseTime(text);
        if (parsedTime != null) {
            setTime(due, parsedTime.hour, parsedTime.minute);
            hasTime = true;
            // A time without a named date is only a preview fallback. It is not
            // schedulable until the user adds a date.
            if (!hasDate && due.getTimeInMillis() <= now.getTimeInMillis()) {
                due.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else if (!relative) {
            // Retain a useful preview moment, but keep hasTime false so the UI
            // asks for an explicit time instead of silently defaulting to 9 AM.
            if (!hasDate) {
                due = startOfDay(now);
                due.add(Calendar.DAY_OF_YEAR, 1);
            }
            setTime(due, 9, 0);
        }

        String title = ReminderText.clean(titleFromPhrase(text, parsedTime));
        return new ParsedReminder(title, due.getTimeInMillis(), hasDate, hasTime);
    }

    private static RelativeMatch findRelativeTime(String text) {
        Matcher english = RELATIVE_ENGLISH.matcher(text);
        if (english.find()) {
            String unit = english.group(2).toLowerCase(Locale.US);
            return new RelativeMatch(safeInt(english.group(1), 1), unit.startsWith("h"));
        }
        Matcher indonesianPrefix = RELATIVE_INDONESIAN_PREFIX.matcher(text);
        if (indonesianPrefix.find()) {
            return new RelativeMatch(
                    safeInt(indonesianPrefix.group(1), 1),
                    indonesianPrefix.group(2).toLowerCase(Locale.US).startsWith("j")
            );
        }
        Matcher indonesianSuffix = RELATIVE_INDONESIAN_SUFFIX.matcher(text);
        if (indonesianSuffix.find()) {
            return new RelativeMatch(
                    safeInt(indonesianSuffix.group(1), 1),
                    indonesianSuffix.group(2).toLowerCase(Locale.US).startsWith("j")
            );
        }
        return null;
    }

    private static String normalize(String phrase) {
        return ReminderText.clean(phrase)
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
                .replaceAll("(?i)^\\s*(?:(?:please\\s+)?(?:can\\s+you\\s+)?remind(?:\\s+me)?|(?:tolong\\s+)?ingatkan(?:\\s+(?:saya|aku|kami))?)\\s*", "")
                .replaceAll("(?i)\\b(?:for|on)\\s+(?:the\\s+)?day after tomorrow\\b", " ")
                .replaceAll("(?i)\\bday after tomorrow\\b", " ")
                .replaceAll("(?i)\\b(?:for|on)\\s+tomorrow\\b", " ")
                .replaceAll("(?i)\\btomorrow\\b", " ")
                .replaceAll("(?i)\\b(?:for|on)\\s+today\\b", " ")
                .replaceAll("(?i)\\btoday\\b", " ")
                .replaceAll("(?i)\\b(?:next\\s+week|minggu\\s+depan)\\b", " ")
                .replaceAll("(?i)\\b(?:lusa|besok|hari\\s+ini)\\b", " ")
                .replaceAll("(?i)\\b(?:(?:next|depan)\\s+)?(?:hari\\s+)?"
                        + "(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday"
                        + "|senin|selasa|rabu|kamis|jumat|jum'at|sabtu|minggu|ahad)(?:\\s+depan)?\\b", " ")
                .replaceAll("(?i)\\b(?:in|dalam)\\s+\\d+\\s*(?:minute|minutes|min|mins|hour|hours|hr|hrs|menit|mnt|jam)\\b", " ")
                .replaceAll("(?i)\\b\\d+\\s*(?:menit|mnt|jam)\\s+lagi\\b", " ");

        if (parsedTime != null && parsedTime.matched.length() > 0) {
            title = title.replaceFirst("(?i)" + Pattern.quote(parsedTime.matched.trim()), " ");
        }

        title = title
                .replaceAll("(?i)\\b(?:at\\s*)?\\d{1,2}(?::\\d{2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b", " ")
                .replaceAll("(?i)\\bat\\s+([01]?\\d|2[0-3])(?::[0-5]\\d)?\\b", " ")
                .replaceAll("(?i)\\b(?:jam|pukul)\\s+([01]?\\d|2[0-3])(?::[0-5]\\d)?\\s*(?:pagi|siang|sore|malam)?\\b", " ")
                .replaceAll("(?i)\\b(?:noon|midnight|morning|afternoon|evening|tonight|pagi|siang|sore|malam|tengah\\s+malam)\\b", " ")
                .replaceFirst("(?i)^\\s*(?:to\\s+|for\\s+|untuk\\s+)?", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^\\s*[,;:\\-\\u2013\\u2014]+\\s*|\\s*[,;:\\-\\u2013\\u2014]+\\s*$", "")
                .trim();

        if (title.isEmpty()) return "";
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Matcher personMatcher = Pattern.compile("\\b(for|with|untuk|dengan)\\s+([a-z])").matcher(title);
        StringBuffer result = new StringBuffer();
        while (personMatcher.find()) {
            personMatcher.appendReplacement(result,
                    personMatcher.group(1) + " " + personMatcher.group(2).toUpperCase(Locale.US));
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

        Matcher indonesian = INDONESIAN_TIME.matcher(text);
        if (indonesian.find()) {
            int hour = safeInt(indonesian.group(1), 9);
            int minute = safeInt(indonesian.group(2), 0);
            String period = indonesian.group(3) == null
                    ? "" : indonesian.group(3).toLowerCase(Locale.US);
            hour = adjustIndonesianHour(hour, period);
            return new ParsedTime(hour, minute, indonesian.group());
        }

        Matcher twentyFour = TWENTY_FOUR_HOUR_ENGLISH.matcher(text);
        if (twentyFour.find()) {
            return new ParsedTime(safeInt(twentyFour.group(1), 9), safeInt(twentyFour.group(2), 0), twentyFour.group());
        }

        if (contains(text, "tengah malam") || contains(text, "midnight")) {
            return new ParsedTime(0, 0, contains(text, "tengah malam") ? "tengah malam" : "midnight");
        }
        if (contains(text, "noon")) return new ParsedTime(12, 0, "noon");
        if (contains(text, "morning") || contains(text, "pagi")) {
            return new ParsedTime(9, 0, contains(text, "pagi") ? "pagi" : "morning");
        }
        if (contains(text, "afternoon") || contains(text, "siang")) {
            return new ParsedTime(14, 0, contains(text, "siang") ? "siang" : "afternoon");
        }
        if (contains(text, "evening") || contains(text, "tonight") || contains(text, "sore")) {
            return new ParsedTime(18, 0, contains(text, "sore") ? "sore" : "evening");
        }
        if (contains(text, "malam")) return new ParsedTime(20, 0, "malam");
        return null;
    }

    private static int adjustIndonesianHour(int hour, String period) {
        if ("pagi".equals(period)) return hour == 12 ? 0 : hour;
        if ("siang".equals(period)) return hour > 0 && hour < 6 ? hour + 12 : hour;
        if ("sore".equals(period)) return hour > 0 && hour < 7 ? hour + 12 : hour;
        if ("malam".equals(period)) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        return hour;
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

    private static int weekdayToCalendar(String weekday) {
        String name = weekday.toLowerCase(Locale.US);
        if ("monday".equals(name) || "senin".equals(name)) return Calendar.MONDAY;
        if ("tuesday".equals(name) || "selasa".equals(name)) return Calendar.TUESDAY;
        if ("wednesday".equals(name) || "rabu".equals(name)) return Calendar.WEDNESDAY;
        if ("thursday".equals(name) || "kamis".equals(name)) return Calendar.THURSDAY;
        if ("friday".equals(name) || "jumat".equals(name) || "jum'at".equals(name)) return Calendar.FRIDAY;
        if ("saturday".equals(name) || "sabtu".equals(name)) return Calendar.SATURDAY;
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

    private static final class RelativeMatch {
        final int amount;
        final boolean hours;

        RelativeMatch(int amount, boolean hours) {
            this.amount = Math.max(1, amount);
            this.hours = hours;
        }
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
        public final boolean hasDate;
        public final boolean hasTime;
        // Retained for callers from earlier versions. A missing explicit time is
        // no longer scheduled by default.
        public final boolean timeWasInferred;

        ParsedReminder(String title, long triggerAt, boolean hasDate, boolean hasTime) {
            this.title = title;
            this.triggerAt = triggerAt;
            this.hasDate = hasDate;
            this.hasTime = hasTime;
            this.timeWasInferred = !hasTime;
        }

        public boolean isReadyToSchedule() {
            return hasDate && hasTime;
        }
    }
}
