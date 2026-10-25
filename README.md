# RemindME

A calm, single-screen reminder app that turns everyday language into a scheduled nudge.

> “Remind me for tomrws homework ceckin for Abdullah”

becomes **Homework check-in for Abdullah**, scheduled for **tomorrow at 9:00 AM**. The parser also corrects a few common typos, understands times such as `at 4pm`, relative phrases such as `in 10 minutes`, and weekdays.

## Run it

This project has no build step or package install. Serve the folder from a local web server so browser notifications and the service worker can work:

```bash
python3 -m http.server 4173 --bind 0.0.0.0
```

Then open `http://localhost:4173`.

## What works

- Natural-language reminder capture with a live parsed preview
- Common typo correction (`tomrws` → `tomorrow`, `ceckin` → `check-in`)
- Browser notification permission, an in-app alarm, and a soft Web Audio chime
- Snooze for 10 minutes, complete, delete, and clear completed reminders
- Local persistence using `localStorage`
- Optional speech-to-text in browsers that support the Web Speech API
- Responsive UI and basic offline app-shell caching

## Important browser note

A browser-only app can reliably schedule the audible/in-app alarm while RemindME is open. Browser notification behavior when a tab or device is fully closed varies by browser and operating system. For dependable closed-app notifications, the next product step is a small backend with authenticated accounts, time-zone-aware scheduled jobs, and web push (or native mobile notifications).

## Product ideas for the next version

1. **Ask one smart follow-up only when needed.** If someone writes “tomorrow,” show the 9 AM default and let them tap “morning,” “afternoon,” or a custom time instead of making them fill a form.
2. **Make people and context first-class.** Turn “Abdullah” into a contact chip, then offer a one-tap follow-up reminder if the check-in is not completed.
3. **Use a calm escalation path.** Start with a quiet notification, then offer a second nudge 15 minutes later—not a noisy alarm by default.
4. **Add a daily planning ritual.** A brief morning view with “one must-do, two may-dos, and open space” keeps the app focused rather than becoming another crowded task manager.
5. **Support recurring care.** Useful natural phrases include “every Sunday evening,” “the first day of each month,” and “two days before an exam.”
6. **Build trust around privacy.** Keep basic reminders local by default; make cloud sync and shared reminders explicit opt-ins.
