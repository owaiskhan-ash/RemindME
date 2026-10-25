package com.remindme.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, understandable natural-language parser for the seven
 * languages offered in Settings. It never silently schedules a missing date or
 * time: date and time must both be explicit, except for relative durations.
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
    private static final Pattern RELATIVE_SPANISH = Pattern.compile(
            "(?iu)\\b(?:en|dentro\\s+de)\\s+(\\d+)\\s*(minutos?|mins?|horas?)\\b"
    );
    private static final Pattern RELATIVE_PORTUGUESE = Pattern.compile(
            "(?iu)\\b(?:em|daqui\\s+a)\\s+(\\d+)\\s*(minutos?|mins?|horas?)\\b"
    );
    private static final Pattern RELATIVE_HINDI = Pattern.compile(
            "(?u)(\\d+)\\s*(मिनटों?|मिनट|घंटों?|घंटे|घंटा)\\s*(?:में|बाद)"
    );
    private static final Pattern RELATIVE_ARABIC = Pattern.compile(
            "(?u)(?:بعد|خلال)\\s*(\\d+)\\s*(دقائق|دقيقة|ساعات|ساعة)"
    );
    private static final Pattern RELATIVE_CHINESE = Pattern.compile(
            "(?u)(?:再过|在)?\\s*(\\d+)\\s*(个小时|小时|分钟|分)\\s*后"
    );
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?iu)(?:(next|depan|pr[oó]xim[oa]|seguinte|अगले|القادم(?:ة)?|下)\\s*)?"
                    + "(sunday|monday|tuesday|wednesday|thursday|friday|saturday"
                    + "|senin|selasa|rabu|kamis|jumat|jum'at|sabtu|minggu|ahad"
                    + "|domingo|lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado"
                    + "|segunda(?:-feira)?|ter[cç]a(?:-feira)?|quarta(?:-feira)?"
                    + "|quinta(?:-feira)?|sexta(?:-feira)?"
                    + "|रविवार|सोमवार|मंगलवार|बुधवार|गुरुवार|बृहस्पतिवार|शुक्रवार|शनिवार"
                    + "|الأحد|الاحد|الاثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت"
                    + "|(?:星期|周|礼拜)[一二三四五六日天])"
                    + "(?:\\s+(depan|pr[oó]xim[oa]|seguinte|अगले|القادم(?:ة)?))?"
    );
    private static final Pattern SPANISH_TIME = Pattern.compile(
            "(?iu)\\ba\\s+las\\s+([01]?\\d|2[0-3])(?::([0-5]\\d))?"
                    + "(?:\\s+(?:de\\s+la\\s+)?(ma[nñ]ana|tarde|noche))?\\b"
    );
    private static final Pattern SPANISH_PERIOD_TIME = Pattern.compile(
            "(?iu)\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s+"
                    + "(?:de\\s+la\\s+)?(ma[nñ]ana|tarde|noche)\\b"
    );
    private static final Pattern PORTUGUESE_TIME = Pattern.compile(
            "(?iu)\\b(?:às|as)\\s*([01]?\\d|2[0-3])(?::([0-5]\\d))?"
                    + "(?:\\s+(?:da|de)\\s+(manh[ãa]|tarde|noite))?\\b"
    );
    private static final Pattern PORTUGUESE_HOUR_TIME = Pattern.compile(
            "(?iu)\\b([01]?\\d|2[0-3])h([0-5]\\d)?"
                    + "(?:\\s+(?:da|de)\\s+(manh[ãa]|tarde|noite))?\\b"
    );
    private static final Pattern PORTUGUESE_PERIOD_TIME = Pattern.compile(
            "(?iu)\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s+"
                    + "(?:da|de)\\s+(manh[ãa]|tarde|noite)\\b"
    );
    private static final Pattern HINDI_TIME = Pattern.compile(
            "(?u)(?:(सुबह|दोपहर|शाम|रात)\\s*)?([01]?\\d|2[0-3])"
                    + "(?::([0-5]\\d))?\\s*बजे(?:\\s*(सुबह|दोपहर|शाम|रात))?"
    );
    private static final Pattern ARABIC_CLOCK_TIME = Pattern.compile(
            "(?u)(?:الساعة|الساعه)\\s*([01]?\\d|2[0-3])(?::([0-5]\\d))?"
                    + "(?:\\s*(صباح(?:ا|اً)?|مساء(?:ً|ا)?|ليل(?:ا|اً)?|الظهر))?"
    );
    private static final Pattern ARABIC_PERIOD_TIME = Pattern.compile(
            "(?u)\\b([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s+"
                    + "(صباح(?:ا|اً)?|مساء(?:ً|ا)?|ليل(?:ا|اً)?|الظهر)\\b"
    );
    private static final Pattern CHINESE_TIME = Pattern.compile(
            "(?u)(?:(上午|早上|清晨|中午|下午|晚上|傍晚|凌晨)\\s*)?"
                    + "([01]?\\d|2[0-3])(?:点|時|时)(?:([0-5]?\\d)分?)?"
                    + "(?:\\s*(上午|早上|清晨|中午|下午|晚上|傍晚|凌晨))?"
    );
    private static final Pattern GENERIC_COLON_TIME = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b"
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
        } else if (containsAny(text, "day after tomorrow", "lusa", "pasado mañana", "pasado manana",
                "depois de amanhã", "depois de amanha", "परसों", "بعد غد", "后天")) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 2);
            hasDate = true;
        } else if (containsTomorrow(text)) {
            due = startOfDay(now);
            due.add(Calendar.DAY_OF_YEAR, 1);
            hasDate = true;
        } else if (containsAny(text, "today", "hari ini", "hoy", "hoje", "आज", "اليوم", "今天")) {
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
            } else if (containsAny(text, "next week", "minggu depan", "próxima semana",
                    "proxima semana", "semana que vem", "अगले सप्ताह", "अगले हफ्ते",
                    "الأسبوع القادم", "الاسبوع القادم", "الأسبوع المقبل", "下周", "下星期", "下个星期")) {
                due = startOfDay(now);
                due.add(Calendar.DAY_OF_YEAR, 7);
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
        if (containsAny(text, "in half an hour", "en media hora", "em meia hora",
                "आधे घंटे में", "आधे घंटे बाद", "نصف ساعة", "半小时后")) {
            return new RelativeMatch(30, false);
        }
        if (containsAny(text, "بعد ساعتين")) return new RelativeMatch(2, true);
        if (containsAny(text, "بعد ساعة")) return new RelativeMatch(1, true);

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
        Matcher spanish = RELATIVE_SPANISH.matcher(text);
        if (spanish.find()) {
            return new RelativeMatch(safeInt(spanish.group(1), 1),
                    spanish.group(2).toLowerCase(Locale.US).startsWith("h"));
        }
        Matcher portuguese = RELATIVE_PORTUGUESE.matcher(text);
        if (portuguese.find()) {
            return new RelativeMatch(safeInt(portuguese.group(1), 1),
                    portuguese.group(2).toLowerCase(Locale.US).startsWith("h"));
        }
        Matcher hindi = RELATIVE_HINDI.matcher(text);
        if (hindi.find()) {
            return new RelativeMatch(safeInt(hindi.group(1), 1), hindi.group(2).contains("घ"));
        }
        Matcher arabic = RELATIVE_ARABIC.matcher(text);
        if (arabic.find()) {
            return new RelativeMatch(safeInt(arabic.group(1), 1), arabic.group(2).contains("ساعة"));
        }
        Matcher chinese = RELATIVE_CHINESE.matcher(text);
        if (chinese.find()) {
            return new RelativeMatch(safeInt(chinese.group(1), 1), chinese.group(2).contains("小时"));
        }
        return null;
    }

    private static String normalize(String phrase) {
        return normalizeDigits(ReminderText.clean(phrase))
                .replaceAll("(?i)\\btomrws?\\b", "tomorrow")
                .replaceAll("(?i)\\btmrw?\\b", "tomorrow")
                .replaceAll("(?i)\\btomm?orow\\b", "tomorrow")
                .replaceAll("(?i)\\bceck[\\s-]?in\\b", "check-in")
                .replaceAll("(?i)\\bcheck[\\s-]?in\\b", "check-in")
                .replaceAll("(?i)\\bhw\\b", "homework")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeDigits(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '٠' && character <= '٩') {
                result.append((char) ('0' + (character - '٠')));
            } else if (character >= '۰' && character <= '۹') {
                result.append((char) ('0' + (character - '۰')));
            } else if (character >= '०' && character <= '९') {
                result.append((char) ('0' + (character - '०')));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String titleFromPhrase(String text, ParsedTime parsedTime) {
        String title = text.replaceAll(
                "(?iu)^\\s*(?:(?:please\\s+)?(?:can\\s+you\\s+)?remind(?:\\s+me)?"
                        + "|(?:tolong\\s+)?ingatkan(?:\\s+(?:saya|aku|kami))?"
                        + "|(?:por\\s+favor\\s+)?rec[uú][eé]rdame(?:\\s+que)?"
                        + "|(?:por\\s+favor\\s+)?(?:lembre(?:-me)?|me\\s+lembre)(?:\\s+de)?"
                        + "|(?:कृपया\\s*)?(?:मुझे\\s*)?याद\\s+दिलाना"
                        + "|(?:من\\s+فضلك\\s+)?(?:ذكرني|ذكّرني)"
                        + "|(?:请\\s*)?提醒我?)\\s*", "");

        if (parsedTime != null && parsedTime.matched.length() > 0) {
            title = title.replaceFirst("(?iu)" + Pattern.quote(parsedTime.matched.trim()), " ");
        }

        title = title
                .replaceAll("(?iu)\\b(?:for|on|para|el|en|em|untuk)\\s+(?:the\\s+)?"
                        + "(?:day\\s+after\\s+tomorrow|pasado\\s+ma[nñ]ana|depois\\s+de\\s+amanh[ãa])\\b", " ")
                .replaceAll("(?iu)\\b(?:day\\s+after\\s+tomorrow|lusa|pasado\\s+ma[nñ]ana"
                        + "|depois\\s+de\\s+amanh[ãa])\\b", " ")
                .replaceAll("(?iu)\\b(?:for|on|para|el|en|em|untuk)\\s+"
                        + "(?:tomorrow|besok|hoy|hoje|today)\\b", " ")
                .replaceAll("(?iu)\\b(?:tomorrow|besok|today|hari\\s+ini|hoy|hoje|"
                        + "ma[nñ]ana|amanh[ãa]|next\\s+week|minggu\\s+depan|pr[oó]xima\\s+semana"
                        + "|proxima\\s+semana|semana\\s+que\\s+vem)\\b", " ")
                .replace("परसों", " ").replace("कल", " ").replace("आज", " ")
                .replace("بعد غد", " ").replace("غدا", " ").replace("بكرة", " ")
                .replace("اليوم", " ")
                .replace("后天", " ").replace("明天", " ").replace("今天", " ")
                .replaceAll(WEEKDAY.pattern(), " ")
                .replace("下个星期", " ").replace("下星期", " ").replace("下周", " ")
                .replaceAll(RELATIVE_ENGLISH.pattern(), " ")
                .replaceAll(RELATIVE_INDONESIAN_PREFIX.pattern(), " ")
                .replaceAll(RELATIVE_INDONESIAN_SUFFIX.pattern(), " ")
                .replaceAll(RELATIVE_SPANISH.pattern(), " ")
                .replaceAll(RELATIVE_PORTUGUESE.pattern(), " ")
                .replaceAll(RELATIVE_HINDI.pattern(), " ")
                .replaceAll(RELATIVE_ARABIC.pattern(), " ")
                .replaceAll(RELATIVE_CHINESE.pattern(), " ")
                .replaceAll("(?iu)\\b(?:in\\s+half\\s+an\\s+hour|en\\s+media\\s+hora"
                        + "|em\\s+meia\\s+hora)\\b", " ")
                .replace("आधे घंटे में", " ").replace("आधे घंटे बाद", " ")
                .replace("نصف ساعة", " ").replace("بعد ساعتين", " ").replace("بعد ساعة", " ")
                .replace("半小时后", " ")
                .replaceAll("(?iu)\\b(?:at\\s*)?\\d{1,2}(?::\\d{2})?\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b", " ")
                .replaceAll("(?iu)\\bat\\s+([01]?\\d|2[0-3])(?::[0-5]\\d)?\\b", " ")
                .replaceAll(INDONESIAN_TIME.pattern(), " ")
                .replaceAll(SPANISH_TIME.pattern(), " ")
                .replaceAll(SPANISH_PERIOD_TIME.pattern(), " ")
                .replaceAll(PORTUGUESE_TIME.pattern(), " ")
                .replaceAll(PORTUGUESE_HOUR_TIME.pattern(), " ")
                .replaceAll(PORTUGUESE_PERIOD_TIME.pattern(), " ")
                .replaceAll(HINDI_TIME.pattern(), " ")
                .replaceAll(ARABIC_CLOCK_TIME.pattern(), " ")
                .replaceAll(ARABIC_PERIOD_TIME.pattern(), " ")
                .replaceAll(CHINESE_TIME.pattern(), " ")
                .replaceAll(GENERIC_COLON_TIME.pattern(), " ")
                .replaceAll("(?iu)\\b(?:noon|midnight|morning|afternoon|evening|tonight"
                        + "|pagi|siang|sore|malam|tengah\\s+malam|mediod[ií]a|medianoche"
                        + "|meio-dia|meia-noite|por\\s+la\\s+ma[nñ]ana|por\\s+la\\s+tarde"
                        + "|por\\s+la\\s+noche|de\\s+manh[ãa]|à\\s+tarde|à\\s+noite)\\b", " ")
                .replace("सुबह", " ").replace("दोपहर", " ").replace("शाम", " ").replace("रात", " ")
                .replace("आधी रात", " ")
                .replace("منتصف الليل", " ").replace("الظهر", " ").replace("صباحا", " ")
                .replace("مساء", " ").replace("ليلا", " ")
                .replace("上午", " ").replace("早上", " ").replace("清晨", " ")
                .replace("中午", " ").replace("下午", " ").replace("晚上", " ")
                .replace("傍晚", " ").replace("凌晨", " ")
                .replaceFirst("(?iu)^\\s*(?:to\\s+|for\\s+|untuk\\s+|para\\s+|de\\s+)?", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^\\s*[,;:\\-\\u2013\\u2014]+\\s*|\\s*[,;:\\-\\u2013\\u2014]+\\s*$", "")
                .trim();

        if (title.isEmpty()) return "";
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Matcher personMatcher = Pattern.compile("(?iu)\\b(for|with|untuk|dengan)\\s+([a-z])").matcher(title);
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
            return new ParsedTime(adjustIndonesianHour(hour, period), minute, indonesian.group());
        }

        Matcher spanish = SPANISH_TIME.matcher(text);
        if (spanish.find()) {
            int hour = safeInt(spanish.group(1), 9);
            int minute = safeInt(spanish.group(2), 0);
            return new ParsedTime(adjustSpanishHour(hour, spanish.group(3)), minute, spanish.group());
        }
        Matcher spanishPeriod = SPANISH_PERIOD_TIME.matcher(text);
        if (spanishPeriod.find()) {
            int hour = safeInt(spanishPeriod.group(1), 9);
            int minute = safeInt(spanishPeriod.group(2), 0);
            return new ParsedTime(adjustSpanishHour(hour, spanishPeriod.group(3)), minute, spanishPeriod.group());
        }

        Matcher portuguese = PORTUGUESE_TIME.matcher(text);
        if (portuguese.find()) {
            int hour = safeInt(portuguese.group(1), 9);
            int minute = safeInt(portuguese.group(2), 0);
            return new ParsedTime(adjustPortugueseHour(hour, portuguese.group(3)), minute, portuguese.group());
        }
        Matcher portugueseHour = PORTUGUESE_HOUR_TIME.matcher(text);
        if (portugueseHour.find()) {
            int hour = safeInt(portugueseHour.group(1), 9);
            int minute = safeInt(portugueseHour.group(2), 0);
            return new ParsedTime(adjustPortugueseHour(hour, portugueseHour.group(3)), minute, portugueseHour.group());
        }
        Matcher portuguesePeriod = PORTUGUESE_PERIOD_TIME.matcher(text);
        if (portuguesePeriod.find()) {
            int hour = safeInt(portuguesePeriod.group(1), 9);
            int minute = safeInt(portuguesePeriod.group(2), 0);
            return new ParsedTime(adjustPortugueseHour(hour, portuguesePeriod.group(3)), minute, portuguesePeriod.group());
        }

        Matcher hindi = HINDI_TIME.matcher(text);
        if (hindi.find()) {
            String period = hindi.group(1) == null ? hindi.group(4) : hindi.group(1);
            return new ParsedTime(adjustHindiHour(safeInt(hindi.group(2), 9), period),
                    safeInt(hindi.group(3), 0), hindi.group());
        }

        Matcher arabicClock = ARABIC_CLOCK_TIME.matcher(text);
        if (arabicClock.find()) {
            return new ParsedTime(adjustArabicHour(safeInt(arabicClock.group(1), 9), arabicClock.group(3)),
                    safeInt(arabicClock.group(2), 0), arabicClock.group());
        }
        Matcher arabicPeriod = ARABIC_PERIOD_TIME.matcher(text);
        if (arabicPeriod.find()) {
            return new ParsedTime(adjustArabicHour(safeInt(arabicPeriod.group(1), 9), arabicPeriod.group(3)),
                    safeInt(arabicPeriod.group(2), 0), arabicPeriod.group());
        }

        Matcher chinese = CHINESE_TIME.matcher(text);
        if (chinese.find()) {
            String period = chinese.group(1) == null ? chinese.group(4) : chinese.group(1);
            return new ParsedTime(adjustChineseHour(safeInt(chinese.group(2), 9), period),
                    safeInt(chinese.group(3), 0), chinese.group());
        }

        Matcher genericColon = GENERIC_COLON_TIME.matcher(text);
        if (genericColon.find()) {
            return new ParsedTime(safeInt(genericColon.group(1), 9), safeInt(genericColon.group(2), 0),
                    genericColon.group());
        }
        Matcher twentyFour = TWENTY_FOUR_HOUR_ENGLISH.matcher(text);
        if (twentyFour.find()) {
            return new ParsedTime(safeInt(twentyFour.group(1), 9), safeInt(twentyFour.group(2), 0), twentyFour.group());
        }

        String midnight = firstContained(text, "tengah malam", "midnight", "medianoche", "meia-noite",
                "आधी रात", "منتصف الليل", "午夜", "半夜");
        if (midnight != null) return new ParsedTime(0, 0, midnight);
        String noon = firstContained(text, "noon", "mediodía", "mediodia", "meio-dia", "दोपहर", "الظهر", "中午");
        if (noon != null) return new ParsedTime(12, 0, noon);
        String morning = firstContained(text, "morning", "pagi", "por la mañana", "de la mañana",
                "de manhã", "pela manhã", "सुबह", "صباحا", "صباح", "上午", "早上", "清晨");
        if (morning != null) return new ParsedTime(9, 0, morning);
        String afternoon = firstContained(text, "afternoon", "siang", "por la tarde", "de tarde",
                "à tarde", "दोपहर", "بعد الظهر", "下午");
        if (afternoon != null) return new ParsedTime(14, 0, afternoon);
        String evening = firstContained(text, "evening", "tonight", "sore", "por la noche", "de noche",
                "à noite", "शाम", "مساء", "傍晚", "晚上");
        if (evening != null) return new ParsedTime(18, 0, evening);
        String night = firstContained(text, "malam", "night", "noche", "noite", "रात", "ليلا", "ليل", "凌晨");
        if (night != null) return new ParsedTime(20, 0, night);
        return null;
    }

    private static int adjustIndonesianHour(int hour, String period) {
        if ("pagi".equals(period)) return hour == 12 ? 0 : hour;
        if ("siang".equals(period)) return hour > 0 && hour < 6 ? hour + 12 : hour;
        if ("sore".equals(period)) return hour > 0 && hour < 7 ? hour + 12 : hour;
        if ("malam".equals(period)) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        return hour;
    }

    private static int adjustSpanishHour(int hour, String period) {
        if (period == null) return hour;
        String value = period.toLowerCase(Locale.US);
        if (value.startsWith("ma")) return hour == 12 ? 0 : hour;
        if ("tarde".equals(value)) return hour > 0 && hour < 12 ? hour + 12 : hour;
        if ("noche".equals(value)) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        return hour;
    }

    private static int adjustPortugueseHour(int hour, String period) {
        if (period == null) return hour;
        String value = period.toLowerCase(Locale.US);
        if (value.startsWith("manh")) return hour == 12 ? 0 : hour;
        if ("tarde".equals(value)) return hour > 0 && hour < 12 ? hour + 12 : hour;
        if ("noite".equals(value)) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        return hour;
    }

    private static int adjustHindiHour(int hour, String period) {
        if (period == null) return hour;
        if ("सुबह".equals(period)) return hour == 12 ? 0 : hour;
        if ("दोपहर".equals(period)) return hour > 0 && hour < 12 ? hour + 12 : hour;
        if ("शाम".equals(period)) return hour > 0 && hour < 12 ? hour + 12 : hour;
        if ("रात".equals(period)) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        return hour;
    }

    private static int adjustArabicHour(int hour, String period) {
        if (period == null) return hour;
        if (period.startsWith("صباح")) return hour == 12 ? 0 : hour;
        if (period.startsWith("مساء")) return hour > 0 && hour < 12 ? hour + 12 : hour;
        if (period.startsWith("ليل")) return hour == 12 ? 0 : (hour < 12 ? hour + 12 : hour);
        if (period.contains("ظهر")) return hour == 0 ? 12 : hour;
        return hour;
    }

    private static int adjustChineseHour(int hour, String period) {
        if (period == null) return hour;
        if ("上午".equals(period) || "早上".equals(period) || "清晨".equals(period)) {
            return hour == 12 ? 0 : hour;
        }
        if ("中午".equals(period)) return hour > 0 && hour < 11 ? hour + 12 : hour;
        if ("下午".equals(period) || "傍晚".equals(period) || "晚上".equals(period)) {
            return hour > 0 && hour < 12 ? hour + 12 : hour;
        }
        if ("凌晨".equals(period)) return hour == 12 ? 0 : hour;
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
        if (containsAny(name, "monday", "senin", "lunes", "segunda", "सोमवार", "الاثنين", "周一", "星期一", "礼拜一")) {
            return Calendar.MONDAY;
        }
        if (containsAny(name, "tuesday", "selasa", "martes", "terça", "terca", "मंगलवार", "الثلاثاء", "周二", "星期二", "礼拜二")) {
            return Calendar.TUESDAY;
        }
        if (containsAny(name, "wednesday", "rabu", "miércoles", "miercoles", "quarta", "बुधवार", "الأربعاء", "الاربعاء", "周三", "星期三", "礼拜三")) {
            return Calendar.WEDNESDAY;
        }
        if (containsAny(name, "thursday", "kamis", "jueves", "quinta", "गुरुवार", "बृहस्पतिवार", "الخميس", "周四", "星期四", "礼拜四")) {
            return Calendar.THURSDAY;
        }
        if (containsAny(name, "friday", "jumat", "jum'at", "viernes", "sexta", "शुक्रवार", "الجمعة", "周五", "星期五", "礼拜五")) {
            return Calendar.FRIDAY;
        }
        if (containsAny(name, "saturday", "sabtu", "sábado", "sabado", "sábado", "शनिवार", "السبت", "周六", "星期六", "礼拜六")) {
            return Calendar.SATURDAY;
        }
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

    private static boolean containsAny(String text, String... options) {
        return firstContained(text, options) != null;
    }

    private static String firstContained(String text, String... options) {
        for (String option : options) {
            if (contains(text, option)) return option;
        }
        return null;
    }

    private static boolean containsTomorrow(String text) {
        if (containsAny(text, "tomorrow", "besok", "amanhã", "amanha", "कल", "غدا", "بكرة", "明天")) {
            return true;
        }
        // Spanish mañana also means morning. Treat it as tomorrow only when it
        // is not merely the period in a phrase such as "a las 9 de la mañana".
        String withoutMorningPeriod = text.replaceAll("(?iu)\\b(?:de|por)\\s+la\\s+ma[nñ]ana\\b", " ");
        return containsAny(withoutMorningPeriod, "mañana", "manana");
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
