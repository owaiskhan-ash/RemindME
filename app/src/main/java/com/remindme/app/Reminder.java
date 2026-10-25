package com.remindme.app;

import org.json.JSONException;
import org.json.JSONObject;

/** A small, serializable reminder record kept only on the device. */
public final class Reminder {
    public final String id;
    public final String title;
    public final String sourceText;
    public long triggerAt;
    public boolean completed;
    public boolean notified;

    public Reminder(String id, String title, String sourceText, long triggerAt) {
        this.id = id;
        this.title = ReminderText.clean(title);
        this.sourceText = sourceText;
        this.triggerAt = triggerAt;
        this.completed = false;
        this.notified = false;
    }

    public int requestCode() {
        return id.hashCode() & 0x7fffffff;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", id);
        value.put("title", title);
        value.put("sourceText", sourceText);
        value.put("triggerAt", triggerAt);
        value.put("completed", completed);
        value.put("notified", notified);
        return value;
    }

    public static Reminder fromJson(JSONObject value) throws JSONException {
        Reminder reminder = new Reminder(
                value.getString("id"),
                value.getString("title"),
                value.optString("sourceText", ""),
                value.getLong("triggerAt")
        );
        reminder.completed = value.optBoolean("completed", false);
        reminder.notified = value.optBoolean("notified", false);
        return reminder;
    }
}
