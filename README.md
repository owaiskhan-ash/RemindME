# RemindME for Android

A focused native Android reminder app — not a web landing page.

Write one thought in plain language, such as:

> `Remind me for tomrws homework ceckin for Abdullah`

RemindME turns it into **Homework check-in for Abdullah** and schedules it for **tomorrow at 9:00 AM** when no time is specified.

## What the app does

- Parses everyday reminder phrases, including `tomrws` → `tomorrow` and `ceckin` → `check-in`
- Understands tomorrow, today, weekdays, `at 4pm`, morning/evening, and `in 10 minutes`
- Schedules a device alarm with `AlarmManager`, so it can arrive after the app is closed
- Shows a high-priority Android notification with a notification-channel alarm sound
- Provides **Snooze 10 min** and **Mark done** actions directly from the notification
- Reschedules outstanding reminders after a reboot or app update
- Keeps reminder text in local `SharedPreferences` on the device
- Requests notification and, where needed, exact-alarm access at the appropriate moment

The native logo is rendered in-app as **RE** block lettering recessed into a custom felt-textured green box. It is drawn with Android Canvas, so it remains sharp at all densities without relying on a web image.

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

`.github/workflows/android-release.yml` builds and uploads `RemindME-v1.0.0-release-apk` on every push to this Arena branch. The workflow uses JDK 17 and Android API 35.

## Important Android behavior

Android 13+ requires the user to allow notifications. Android 12+ may require **Alarms & reminders** access for minute-accurate delivery; without it, RemindME uses Android's battery-friendly inexact alarm fallback. Both cases are explained in the minimal in-app permission card.
