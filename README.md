# RemindME for Android

A focused native Android reminder app — not a web landing page.

Write one thought in plain language, such as:

> `Tomorrow at 9 AM — check in with Abdullah about homework`

RemindME turns it into **Check-in with Abdullah about homework** and schedules it for **tomorrow at 9:00 AM**. It also corrects common input such as `tomrws` and `ceckin`.

## What the app does

- Parses everyday reminder phrases, including `tomrws` → `tomorrow` and `ceckin` → `check-in`
- Understands tomorrow, today, weekdays, `at 4pm`, morning/evening, and `in 10 minutes`
- Schedules a device alarm with `AlarmManager`, so it can arrive after the app is closed
- Plays the system alarm tone through Android's dedicated alarm audio stream, so ordinary ring/silent mode does not mute a due reminder
- Shows a high-priority foreground alarm notification with **Snooze 10 min** and **Mark done** actions
- Stops the active alarm immediately when the reminder is snoozed, completed, or deleted
- Reschedules outstanding reminders after a reboot or app update
- Keeps reminder text in local `SharedPreferences` on the device
- Requests notification and, where needed, exact-alarm access at the appropriate moment

The native logo uses bold **RE** lettering recessed into a dark-green felt panel with stitched leather trim. The selected artwork is shown in the app header and exported as density-specific Android launcher icons.

## Build locally

Requirements: Android SDK 35, Build Tools 35.0.0, and JDK 17.

```bash
./gradlew :app:assembleRelease
```

The installable APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the standard debug key so it is installable for testing. Before Google Play distribution, replace the `signingConfigs.debug` release configuration in `app/build.gradle` with your private upload key; do not commit that key.

## CI release artifact

`.github/workflows/android-release.yml` builds and uploads `RemindME-v1.0.2-release-apk` on every push to this Arena branch. The workflow uses JDK 17 and Android API 35.

## Important Android behavior

Android 13+ requires the user to allow notifications. Android 12+ may require **Alarms & reminders** access for minute-accurate delivery; without it, RemindME uses Android's battery-friendly inexact alarm fallback.

The reminder tone is played through the device's **alarm** stream, which is separate from ring/silent mode. A user can still intentionally mute the alarm volume. **Do Not Disturb** is also controlled by Android, so the app offers an optional Do Not Disturb access screen; only grant it if you want reminder notifications to appear during DND.
