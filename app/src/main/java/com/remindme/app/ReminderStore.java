package com.remindme.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Device-local persistence. No reminder text leaves the phone. */
public final class ReminderStore {
    private static final String PREFS = "remindme_storage";
    private static final String REMINDERS = "reminders";

    private final SharedPreferences preferences;

    public ReminderStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized ArrayList<Reminder> load() {
        ArrayList<Reminder> reminders = new ArrayList<>();
        String serialized = preferences.getString(REMINDERS, "[]");
        try {
            JSONArray values = new JSONArray(serialized);
            for (int index = 0; index < values.length(); index++) {
                try {
                    reminders.add(Reminder.fromJson(values.getJSONObject(index)));
                } catch (Exception ignored) {
                    // Keep healthy reminders if one old record is malformed.
                }
            }
        } catch (Exception ignored) {
            // A fresh empty list is safer than failing to open the app.
        }
        sort(reminders);
        return reminders;
    }

    public synchronized void save(List<Reminder> reminders) {
        JSONArray values = new JSONArray();
        for (Reminder reminder : reminders) {
            try {
                values.put(reminder.toJson());
            } catch (Exception ignored) {
                // A single unsaved reminder should not prevent other reminders from persisting.
            }
        }
        preferences.edit().putString(REMINDERS, values.toString()).apply();
    }

    public synchronized Reminder find(String id) {
        for (Reminder reminder : load()) {
            if (reminder.id.equals(id)) return reminder;
        }
        return null;
    }

    public synchronized void update(Reminder changed) {
        ArrayList<Reminder> reminders = load();
        boolean replaced = false;
        for (int index = 0; index < reminders.size(); index++) {
            if (reminders.get(index).id.equals(changed.id)) {
                reminders.set(index, changed);
                replaced = true;
                break;
            }
        }
        if (!replaced) reminders.add(changed);
        sort(reminders);
        save(reminders);
    }

    public synchronized void delete(String id) {
        ArrayList<Reminder> reminders = load();
        for (int index = reminders.size() - 1; index >= 0; index--) {
            if (reminders.get(index).id.equals(id)) reminders.remove(index);
        }
        save(reminders);
    }

    public synchronized void clearCompleted() {
        ArrayList<Reminder> reminders = load();
        for (int index = reminders.size() - 1; index >= 0; index--) {
            if (reminders.get(index).completed) reminders.remove(index);
        }
        save(reminders);
    }

    private static void sort(List<Reminder> reminders) {
        Collections.sort(reminders, new Comparator<Reminder>() {
            @Override
            public int compare(Reminder first, Reminder second) {
                if (first.completed != second.completed) return first.completed ? 1 : -1;
                return Long.compare(first.triggerAt, second.triggerAt);
            }
        });
    }
}
