# RemindME for Android

A focused native Android reminder app - not a web landing page.

Write one thought in plain language, such as:

> `Tomorrow at 9 AM - check in with Abdullah about homework`

or in Bahasa Indonesia:

> `Ingatkan saya besok jam 9 pagi untuk cek PR Abdullah`

or in Spanish:

> `Recuérdame llamar a mamá mañana a las 9:15`

RemindME turns those phrases into a scheduled Android alarm. It also corrects common English input such as `tomrws` and `ceckin`.

## What the app does

- Offers persistent in-app language choices in a compact Settings dropdown for **English, Bahasa Indonesia, Español, Português, हिन्दी, العربية, and 简体中文**
- Localizes the app interface, alarm notifications, Snooze and Mark done actions in every offered language; Arabic receives an in-view right-to-left layout direction without a configuration-recreation loop
- Parses common reminder phrases in all seven languages, including dates, weekdays, clock times, day periods, and relative durations such as `mañana a las 9`, `amanhã às 18:30`, `कल शाम 6 बजे`, `غدا الساعة 9 مساء`, `明天上午9点`, `besok jam 9 pagi`, and `in 10 minutes`
- Requires an explicit date and time before scheduling; the preview clearly asks for any missing detail instead of silently guessing a time
- Schedules a device alarm with `AlarmManager`, so it can arrive after the app is closed
- Plays through Android's dedicated **alarm** audio stream, so ordinary ring/silent mode does not mute a due reminder
- Uses the device's default alarm sound by default; Settings also offers the optional RemindME alarm clip, streamed only when selected and safely falling back to the device alarm if it cannot play
- Shows a high-priority foreground alarm notification with localized **Snooze 10 min** and **Mark done** actions
- Moves a snoozed reminder exactly 10 minutes ahead and refreshes its visible upcoming state
- Stops the active alarm and moves a completed reminder to **Previous**
- Cleans stale replacement-glyph and repeated-question-mark artifacts from reminder titles before they reach the alarm notification
- Keeps the interface intentionally small: **Home**, **Previous**, and **Settings** are always available from the bottom dock
- Starts in persisted dark mode by default, with a light-mode toggle in Settings
- Reschedules outstanding reminders after a reboot or app update
- Keeps reminder text in local `SharedPreferences` on the device

The native logo uses bold **RE** lettering recessed into a dark-green felt panel with stitched leather trim. Its app-header asset has a transparent external background, and the selected artwork is also exported as density-specific Android launcher icons.

## Build locally

Requirements: Android SDK 35, Build Tools 35.0.0, and JDK 17.

```bash
./gradlew :app:assembleRelease
```

The installable APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The Gradle release build is signed with the standard debug key so it is installable for testing. Before Google Play distribution, replace the `signingConfigs.debug` release configuration in `app/build.gradle` with your private upload key; do not commit that key. Tagged sandbox APKs are also test-signed; because no production key is stored in this repository, Android may require uninstalling an older test APK before installing a newer tagged test APK.

## CI release artifact

`.github/workflows/android-release.yml` builds and uploads `RemindME-v1.0.6-release-apk` on every push to this Arena branch. The workflow uses JDK 17 and Android API 35.

## Important Android behavior

Android 13+ requires the user to allow notifications. Android 12+ may require **Alarms & reminders** access for minute-accurate delivery; without it, RemindME uses Android's battery-friendly inexact alarm fallback.

The reminder tone is played through the device's **alarm** stream, which is separate from ring/silent mode. A user can still intentionally mute the alarm volume. **Do Not Disturb** is also controlled by Android, so the app offers an optional Do Not Disturb access screen; only grant it if you want reminder notifications to appear during DND.

If the optional RemindME sound is selected, the app requests only that MP3 over HTTPS at the moment an alarm plays. Reminder text is never sent with that request.
