package com.remindme.app;

import java.util.Calendar;

/**
 * Plain-Java regression coverage for the language examples exposed in Settings.
 * It deliberately has no Android dependencies so it can run in the lightweight
 * release verification path as well as under an Android test runner.
 */
public final class ReminderParserRegression {
    private ReminderParserRegression() { }

    public static void main(String[] args) {
        expectClock("Recuérdame llamar a mamá mañana a las 9:15", 9, 15, "Llamar a mamá");
        expectClock("Lembre-me de ligar para a mãe amanhã às 18:30", 18, 30, "Ligar para a mãe");
        expectClock("मुझे कल शाम 6:45 बजे माँ को फोन करने की याद दिलाना", 18, 45, null);
        expectClock("कल सुबह ९ बजे पानी पीना", 9, 0, null);
        expectClock("ذكرني غدا الساعة 9 مساء بالاتصال بأمي", 21, 0, null);
        expectClock("ذكرني غدا الساعة ٩ مساء بالاتصال بأمي", 21, 0, null);
        expectClock("提醒我明天上午9点给妈妈打电话", 9, 0, "给妈妈打电话");
        expectClock("明天晚上8点吃药", 20, 0, "吃药");
        expectClock("Ligar para mãe próxima segunda-feira às 9", 9, 0, null);

        expectRelative("en 15 minutos beber agua", 14 * 60L, "Beber agua");
        expectRelative("daqui a 2 horas revisar relatório", 119 * 60L, null);
        expectRelative("20 मिनट में पानी पीना", 19 * 60L, null);
        expectRelative("بعد 15 دقيقة اشرب الماء", 14 * 60L, null);
        expectRelative("20分钟后喝水", 19 * 60L, null);

        expectIncomplete("Llamar a mamá a las 9", false, true);
        expectIncomplete("明天给妈妈打电话", true, false);
        expectTitle("Call mom?? tomorrow at 9 AM", "Call mom");
        System.out.println("ReminderParser multilingual regression checks passed.");
    }

    private static void expectClock(String phrase, int expectedHour, int expectedMinute, String expectedTitle) {
        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        if (!parsed.isReadyToSchedule()) fail("Expected a schedulable reminder: " + phrase);
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(parsed.triggerAt);
        if (due.get(Calendar.HOUR_OF_DAY) != expectedHour || due.get(Calendar.MINUTE) != expectedMinute) {
            fail("Wrong time for " + phrase + ": " + due.get(Calendar.HOUR_OF_DAY) + ":" + due.get(Calendar.MINUTE));
        }
        if (expectedTitle != null && !expectedTitle.equals(parsed.title)) {
            fail("Wrong title for " + phrase + ": " + parsed.title);
        }
    }

    private static void expectRelative(String phrase, long minimumSecondsAhead, String expectedTitle) {
        long before = System.currentTimeMillis();
        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        if (!parsed.isReadyToSchedule()) fail("Expected a relative reminder: " + phrase);
        long minimumMillis = before + minimumSecondsAhead * 1000L;
        if (parsed.triggerAt < minimumMillis) {
            fail("Relative reminder is too early for " + phrase);
        }
        if (expectedTitle != null && !expectedTitle.equals(parsed.title)) {
            fail("Wrong title for " + phrase + ": " + parsed.title);
        }
    }

    private static void expectIncomplete(String phrase, boolean expectedDate, boolean expectedTime) {
        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        if (parsed.isReadyToSchedule() || parsed.hasDate != expectedDate || parsed.hasTime != expectedTime) {
            fail("Unexpected schedule state for " + phrase);
        }
    }

    private static void expectTitle(String phrase, String expectedTitle) {
        ReminderParser.ParsedReminder parsed = ReminderParser.parse(phrase);
        if (!expectedTitle.equals(parsed.title)) {
            fail("Wrong sanitized title: " + parsed.title);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
