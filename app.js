(() => {
  'use strict';

  const STORAGE_KEY = 'remindme.reminders.v1';
  const MAX_TIMEOUT = 2_147_000_000;
  const CHECK_INTERVAL = 30_000;

  const elements = {
    form: document.querySelector('#reminderForm'),
    input: document.querySelector('#reminderInput'),
    preview: document.querySelector('#smartPreview'),
    previewTitle: document.querySelector('#previewTitle'),
    previewWhen: document.querySelector('#previewWhen'),
    defaultTimeNote: document.querySelector('#defaultTimeNote'),
    reminderList: document.querySelector('#reminderList'),
    reminderCount: document.querySelector('#reminderCount'),
    clearCompleted: document.querySelector('#clearCompleted'),
    todayLabel: document.querySelector('#todayLabel'),
    tomorrowDay: document.querySelector('#tomorrowDay'),
    tomorrowMonth: document.querySelector('#tomorrowMonth'),
    notificationTitle: document.querySelector('#notificationTitle'),
    notificationDescription: document.querySelector('#notificationDescription'),
    enableNotifications: document.querySelector('#enableNotifications'),
    notificationNavButton: document.querySelector('#notificationNavButton'),
    navNotificationDot: document.querySelector('#navNotificationDot'),
    testAlarm: document.querySelector('#testAlarm'),
    toast: document.querySelector('#toast'),
    toastMessage: document.querySelector('#toastMessage'),
    toastIcon: document.querySelector('#toastIcon'),
    toastClose: document.querySelector('#toastClose'),
    alarmOverlay: document.querySelector('#alarmOverlay'),
    alarmTitle: document.querySelector('#alarmTitle'),
    alarmTime: document.querySelector('#alarmTime'),
    snoozeButton: document.querySelector('#snoozeButton'),
    dismissAlarm: document.querySelector('#dismissAlarm'),
    voiceButton: document.querySelector('#voiceButton'),
    quickChips: document.querySelectorAll('.quick-chip')
  };

  const state = {
    reminders: loadReminders(),
    timers: new Map(),
    activeAlarmId: null,
    isTestAlarm: false,
    toastTimer: null,
    audioContext: null,
    recognition: null
  };

  const weekdayNumbers = {
    sunday: 0,
    monday: 1,
    tuesday: 2,
    wednesday: 3,
    thursday: 4,
    friday: 5,
    saturday: 6
  };

  const checkIcon = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m5.5 12.5 4.1 4.1 8.9-9" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const trashIcon = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4.5 7h15M9.5 10.5v6M14.5 10.5v6M6.5 7l1 12h9l1-12M9 7V4.5h6V7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  function loadReminders() {
    try {
      const stored = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '[]');
      if (!Array.isArray(stored)) return [];
      return stored.filter((reminder) => (
        reminder &&
        typeof reminder.id === 'string' &&
        typeof reminder.title === 'string' &&
        Number.isFinite(Number(reminder.scheduledAt))
      ));
    } catch (_) {
      return [];
    }
  }

  function saveReminders() {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state.reminders));
    } catch (_) {
      showToast('Your reminder is active for this visit, but storage is unavailable.', 'alert');
    }
  }

  function createId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    return `reminder-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function startOfDay(date) {
    const copy = new Date(date);
    copy.setHours(0, 0, 0, 0);
    return copy;
  }

  function sameDay(first, second) {
    return startOfDay(first).getTime() === startOfDay(second).getTime();
  }

  function addDays(date, amount) {
    const copy = new Date(date);
    copy.setDate(copy.getDate() + amount);
    return copy;
  }

  function normalizeLanguage(text) {
    return text
      .replace(/\btomrws?\b/gi, 'tomorrow')
      .replace(/\btmrw?\b/gi, 'tomorrow')
      .replace(/\btomm?orow\b/gi, 'tomorrow')
      .replace(/\bceck[\s-]?in\b/gi, 'check-in')
      .replace(/\bcheck[\s-]?in\b/gi, 'check-in')
      .replace(/\bhw\b/gi, 'homework')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function setTime(date, hour, minute = 0) {
    const copy = new Date(date);
    copy.setHours(hour, minute, 0, 0);
    return copy;
  }

  function parseTime(text) {
    const amPmMatch = text.match(/\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b/i);
    if (amPmMatch) {
      let hour = Number(amPmMatch[1]);
      const minute = Number(amPmMatch[2] || 0);
      const suffix = amPmMatch[3].toLowerCase().replace(/\./g, '');
      if (suffix === 'pm' && hour !== 12) hour += 12;
      if (suffix === 'am' && hour === 12) hour = 0;
      return { hour, minute, matched: amPmMatch[0], explicit: true };
    }

    const twentyFourMatch = text.match(/\bat\s+([01]?\d|2[0-3])(?::([0-5]\d))?\b/i);
    if (twentyFourMatch) {
      return {
        hour: Number(twentyFourMatch[1]),
        minute: Number(twentyFourMatch[2] || 0),
        matched: twentyFourMatch[0],
        explicit: true
      };
    }

    if (/\bnoon\b/i.test(text)) return { hour: 12, minute: 0, matched: 'noon', explicit: true };
    if (/\bmidnight\b/i.test(text)) return { hour: 0, minute: 0, matched: 'midnight', explicit: true };
    if (/\bmorning\b/i.test(text)) return { hour: 9, minute: 0, matched: 'morning', explicit: true };
    if (/\bafternoon\b/i.test(text)) return { hour: 14, minute: 0, matched: 'afternoon', explicit: true };
    if (/\bevening\b|\btonight\b/i.test(text)) return { hour: 18, minute: 0, matched: 'evening', explicit: true };

    return null;
  }

  function parseReminder(rawText) {
    const raw = normalizeLanguage(rawText || '');
    const now = new Date();
    let due = new Date(now);
    let hasDate = false;
    let dateWasInferred = false;
    let usedRelativeTime = false;

    const relativeMatch = raw.match(/\bin\s+(\d+)\s*(minute|minutes|min|mins|hour|hours|hr|hrs)\b/i);
    if (relativeMatch) {
      const amount = Math.max(1, Number(relativeMatch[1]));
      const unit = relativeMatch[2].toLowerCase();
      due = new Date(now.getTime() + amount * (unit.startsWith('h') ? 3_600_000 : 60_000));
      due.setSeconds(0, 0);
      hasDate = true;
      usedRelativeTime = true;
    } else {
      if (/\bday after tomorrow\b/i.test(raw)) {
        due = addDays(startOfDay(now), 2);
        hasDate = true;
      } else if (/\bnext\s+week\b/i.test(raw)) {
        due = addDays(startOfDay(now), 7);
        hasDate = true;
      } else if (/\btomorrow\b/i.test(raw)) {
        due = addDays(startOfDay(now), 1);
        hasDate = true;
      } else if (/\btoday\b/i.test(raw)) {
        due = startOfDay(now);
        hasDate = true;
      } else {
        const weekdayMatch = raw.match(/\b(next\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b/i);
        if (weekdayMatch) {
          const target = weekdayNumbers[weekdayMatch[2].toLowerCase()];
          let daysAway = (target - now.getDay() + 7) % 7;
          if (weekdayMatch[1] || daysAway === 0) daysAway += 7;
          due = addDays(startOfDay(now), daysAway);
          hasDate = true;
        }
      }
    }

    const parsedTime = parseTime(raw);
    let timeWasInferred = false;

    if (usedRelativeTime) {
      // Relative reminders keep their calculated clock time, even if the phrase also says "morning".
    } else if (parsedTime) {
      due = setTime(due, parsedTime.hour, parsedTime.minute);
    } else if (!hasDate) {
      due = setTime(addDays(startOfDay(now), 1), 9, 0);
      hasDate = true;
      dateWasInferred = true;
      timeWasInferred = true;
    } else if (sameDay(due, now)) {
      // A no-time "today" reminder should be useful rather than immediately overdue.
      due = new Date(now.getTime() + 60 * 60 * 1000);
      due.setMinutes(Math.ceil(due.getMinutes() / 5) * 5, 0, 0);
      timeWasInferred = true;
    } else {
      due = setTime(due, 9, 0);
      timeWasInferred = true;
    }

    const title = titleFromPhrase(raw, parsedTime, relativeMatch);
    const personMatch = title.match(/\b(?:with|for)\s+([A-Z][A-Za-z'’-]*)\b/);

    return {
      raw,
      title: title || 'A gentle reminder',
      person: personMatch ? personMatch[1] : '',
      scheduledAt: due.getTime(),
      timeWasInferred,
      dateWasInferred,
      hasExplicitTime: Boolean(parsedTime || relativeMatch)
    };
  }

  function titleFromPhrase(text, parsedTime, relativeMatch) {
    let title = text
      .replace(/^\s*(?:please\s+)?(?:can\s+you\s+)?remind(?:\s+me)?\s*/i, '')
      .replace(/\b(?:for|on)\s+(?:the\s+)?day after tomorrow\b/gi, ' ')
      .replace(/\bday after tomorrow\b/gi, ' ')
      .replace(/\b(?:for|on)\s+tomorrow\b/gi, ' ')
      .replace(/\btomorrow\b/gi, ' ')
      .replace(/\b(?:for|on)\s+today\b/gi, ' ')
      .replace(/\btoday\b/gi, ' ')
      .replace(/\bnext\s+week\b/gi, ' ')
      .replace(/\bnext\s+(?=sunday|monday|tuesday|wednesday|thursday|friday|saturday)/gi, ' ')
      .replace(/\b(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b/gi, ' ')
      .replace(/\bin\s+\d+\s*(?:minute|minutes|min|mins|hour|hours|hr|hrs)\b/gi, ' ');

    if (parsedTime && parsedTime.matched) {
      const escaped = escapeForRegExp(parsedTime.matched.trim());
      title = title.replace(new RegExp(`\\b${escaped}\\b`, 'i'), ' ');
    }

    title = title
      .replace(/\b(?:at\s*)?\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)\b/gi, ' ')
      .replace(/\bat\s+([01]?\d|2[0-3])(?::[0-5]\d)?\b/gi, ' ')
      .replace(/\b(?:noon|midnight|morning|afternoon|evening|tonight)\b/gi, ' ')
      .replace(/^\s*(?:to\s+)?/i, '')
      .replace(/^\s*for\s+/i, '')
      .replace(/\s+/g, ' ')
      .replace(/^[,;:—–-]+|[,;:—–-]+$/g, '')
      .trim();

    // Preserve the user's wording but tidy the first letter and the name after "for" or "with".
    if (!title) return '';
    title = title.charAt(0).toUpperCase() + title.slice(1);
    title = title.replace(/\b(for|with)\s+([a-z])/g, (_, connector, firstLetter) => `${connector} ${firstLetter.toUpperCase()}`);
    return title;
  }

  function escapeForRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function formatTime(timestamp) {
    return new Intl.DateTimeFormat(undefined, {
      hour: 'numeric',
      minute: '2-digit'
    }).format(new Date(timestamp));
  }

  function formatWhen(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const tomorrow = addDays(startOfDay(now), 1);
    const time = formatTime(timestamp);

    if (sameDay(date, now)) return `Today at ${time}`;
    if (sameDay(date, tomorrow)) return `Tomorrow at ${time}`;

    const datePart = new Intl.DateTimeFormat(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric'
    }).format(date);
    return `${datePart} at ${time}`;
  }

  function formatListWhen(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    if (sameDay(date, now)) return `Today · ${formatTime(timestamp)}`;
    if (sameDay(date, addDays(startOfDay(now), 1))) return `Tomorrow · ${formatTime(timestamp)}`;
    return new Intl.DateTimeFormat(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit'
    }).format(date);
  }

  function updateDateUi() {
    const now = new Date();
    const tomorrow = addDays(now, 1);
    elements.todayLabel.textContent = new Intl.DateTimeFormat(undefined, {
      weekday: 'long', month: 'short', day: 'numeric'
    }).format(now).toUpperCase();
    elements.tomorrowDay.textContent = String(tomorrow.getDate()).padStart(2, '0');
    elements.tomorrowMonth.textContent = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(tomorrow).toUpperCase();
  }

  function updatePreview() {
    const value = elements.input.value.trim();
    if (!value) {
      elements.preview.classList.add('is-empty');
      elements.previewTitle.textContent = 'Describe it naturally';
      elements.previewWhen.textContent = 'we’ll find the moment';
      elements.defaultTimeNote.textContent = '';
      elements.preview.title = '';
      return;
    }

    const parsed = parseReminder(value);
    elements.preview.classList.remove('is-empty');
    elements.previewTitle.textContent = parsed.title;
    elements.previewWhen.textContent = formatWhen(parsed.scheduledAt);
    elements.defaultTimeNote.textContent = parsed.timeWasInferred ? 'No time mentioned — using a gentle default.' : '';
    elements.preview.title = parsed.timeWasInferred ? 'No time was mentioned, so RemindME selected a sensible time.' : '';
  }

  function escapeHtml(value) {
    const holder = document.createElement('div');
    holder.textContent = value;
    return holder.innerHTML;
  }

  function renderReminders() {
    const ordered = [...state.reminders].sort((a, b) => {
      const completedDifference = Number(Boolean(a.completedAt)) - Number(Boolean(b.completedAt));
      if (completedDifference !== 0) return completedDifference;
      return Number(a.scheduledAt) - Number(b.scheduledAt);
    });
    const activeCount = state.reminders.filter((item) => !item.completedAt).length;
    const completedCount = state.reminders.length - activeCount;

    elements.reminderCount.textContent = String(activeCount);
    elements.clearCompleted.hidden = completedCount === 0;

    if (!ordered.length) {
      elements.reminderList.innerHTML = `
        <div class="empty-state">
          <span class="empty-orb" aria-hidden="true">✦</span>
          <div>
            <h3>Your day has some breathing room.</h3>
            <p>Set your first reminder above and it will appear here.</p>
          </div>
        </div>`;
      return;
    }

    elements.reminderList.innerHTML = ordered.map((reminder) => {
      const completed = Boolean(reminder.completedAt);
      const due = !completed && Number(reminder.scheduledAt) <= Date.now();
      const pillText = completed ? 'Completed' : due ? 'Due now' : 'Scheduled';
      const title = escapeHtml(reminder.title);
      return `
        <article class="reminder-item ${completed ? 'is-completed' : ''}" data-reminder-id="${escapeHtml(reminder.id)}">
          <button class="complete-button ${completed ? 'is-completed' : ''}" type="button" data-action="complete" aria-label="${completed ? 'Mark' : 'Mark'} ${title} as ${completed ? 'not completed' : 'completed'}" title="${completed ? 'Mark as not completed' : 'Mark as completed'}">${checkIcon}</button>
          <div class="reminder-content">
            <p class="reminder-title">${title}</p>
            <div class="reminder-meta">
              <span>${escapeHtml(formatListWhen(reminder.scheduledAt))}</span>
              <span class="meta-dot" aria-hidden="true"></span>
              <span>${completed ? 'Done' : 'Gentle alarm'}</span>
            </div>
          </div>
          <div class="reminder-actions">
            <span class="reminder-pill">${pillText}</span>
            <button class="delete-button" type="button" data-action="delete" aria-label="Delete ${title}" title="Delete reminder">${trashIcon}</button>
          </div>
        </article>`;
    }).join('');
  }

  function clearSchedules() {
    state.timers.forEach((timer) => window.clearTimeout(timer));
    state.timers.clear();
  }

  function scheduleAllReminders() {
    clearSchedules();
    state.reminders.forEach(scheduleReminder);
  }

  function scheduleReminder(reminder) {
    if (reminder.completedAt || reminder.notifiedAt) return;

    const delay = Number(reminder.scheduledAt) - Date.now();
    const waitFor = Math.max(0, Math.min(delay, MAX_TIMEOUT));
    const timer = window.setTimeout(() => {
      state.timers.delete(reminder.id);
      const freshReminder = state.reminders.find((item) => item.id === reminder.id);
      if (!freshReminder || freshReminder.completedAt || freshReminder.notifiedAt) return;
      if (Number(freshReminder.scheduledAt) > Date.now()) {
        scheduleReminder(freshReminder);
      } else {
        fireReminder(freshReminder);
      }
    }, waitFor);
    state.timers.set(reminder.id, timer);
  }

  function checkForDueReminders() {
    state.reminders
      .filter((reminder) => !reminder.completedAt && !reminder.notifiedAt && Number(reminder.scheduledAt) <= Date.now())
      .forEach(fireReminder);
  }

  async function fireReminder(reminder) {
    if (!reminder || reminder.completedAt || reminder.notifiedAt) return;
    reminder.notifiedAt = Date.now();
    saveReminders();
    renderReminders();
    await playChime();
    showBrowserNotification(reminder.title, `Scheduled for ${formatWhen(reminder.scheduledAt)}`);
    openAlarm(reminder);
  }

  function supportsNotifications() {
    return 'Notification' in window;
  }

  function notificationPermission() {
    return supportsNotifications() ? window.Notification.permission : 'unsupported';
  }

  function updateNotificationUi() {
    const permission = notificationPermission();
    const isGranted = permission === 'granted';
    elements.navNotificationDot.classList.toggle('is-visible', permission === 'default');

    if (isGranted) {
      elements.notificationTitle.textContent = 'Gentle alerts are on';
      elements.notificationDescription.textContent = 'We’ll show a notification and play a soft chime while RemindME is open.';
      elements.enableNotifications.textContent = 'On';
      elements.enableNotifications.disabled = true;
      return;
    }

    if (permission === 'denied') {
      elements.notificationTitle.textContent = 'Alerts are blocked';
      elements.notificationDescription.textContent = 'You can still use in-app alarms. Allow notifications in your browser settings for pop-up alerts.';
      elements.enableNotifications.textContent = 'Blocked';
      elements.enableNotifications.disabled = true;
      return;
    }

    if (permission === 'unsupported') {
      elements.notificationTitle.textContent = 'In-app alarms are ready';
      elements.notificationDescription.textContent = 'This browser does not support notification pop-ups, but RemindME can still alert you here.';
      elements.enableNotifications.textContent = 'Unavailable';
      elements.enableNotifications.disabled = true;
      return;
    }

    elements.notificationTitle.textContent = 'Turn on gentle alerts';
    elements.notificationDescription.textContent = 'Get a notification and a soft chime when a reminder is due.';
    elements.enableNotifications.textContent = 'Enable';
    elements.enableNotifications.disabled = false;
  }

  async function requestNotificationPermission(showSuccess = true) {
    if (!supportsNotifications()) {
      updateNotificationUi();
      if (showSuccess) showToast('This browser can use the in-app alarm, but not notification pop-ups.', 'alert');
      return false;
    }

    if (window.Notification.permission === 'granted') {
      updateNotificationUi();
      return true;
    }

    if (window.Notification.permission === 'denied') {
      updateNotificationUi();
      if (showSuccess) showToast('Notifications are blocked in browser settings.', 'alert');
      return false;
    }

    try {
      const permission = await window.Notification.requestPermission();
      updateNotificationUi();
      if (showSuccess) {
        showToast(
          permission === 'granted' ? 'Gentle alerts are now on.' : 'No problem — your reminder is still saved here.',
          permission === 'granted' ? 'success' : 'alert'
        );
      }
      return permission === 'granted';
    } catch (_) {
      updateNotificationUi();
      if (showSuccess) showToast('We could not request browser notifications.', 'alert');
      return false;
    }
  }

  function showBrowserNotification(title, detail) {
    if (notificationPermission() !== 'granted') return;
    try {
      const notification = new window.Notification('RemindME', {
        body: detail ? `${title} — ${detail}` : title,
        tag: `remindme-${Date.now()}`,
        silent: true
      });
      notification.onclick = () => {
        window.focus();
        notification.close();
      };
    } catch (_) {
      // The in-app alarm remains the reliable fallback.
    }
  }

  async function getAudioContext() {
    const AudioContextConstructor = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextConstructor) return null;
    if (!state.audioContext) state.audioContext = new AudioContextConstructor();
    if (state.audioContext.state === 'suspended') {
      try {
        await state.audioContext.resume();
      } catch (_) {
        return null;
      }
    }
    return state.audioContext;
  }

  async function playChime() {
    const context = await getAudioContext();
    if (!context) return;
    const now = context.currentTime;
    [523.25, 659.25, 783.99].forEach((frequency, index) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      const start = now + index * 0.17;
      oscillator.type = 'sine';
      oscillator.frequency.setValueAtTime(frequency, start);
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.13, start + 0.018);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.7);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(start);
      oscillator.stop(start + 0.72);
    });
  }

  function openAlarm(reminder, isTest = false) {
    state.activeAlarmId = reminder && reminder.id ? reminder.id : null;
    state.isTestAlarm = isTest;
    elements.alarmTitle.textContent = isTest ? 'This is your gentle nudge.' : reminder.title;
    elements.alarmTime.textContent = isTest
      ? 'Your reminder sound is working.'
      : `It’s time — ${formatWhen(reminder.scheduledAt)}.`;
    elements.snoozeButton.hidden = isTest;
    elements.alarmOverlay.classList.add('is-open');
    elements.alarmOverlay.setAttribute('aria-hidden', 'false');
    window.setTimeout(() => elements.dismissAlarm.focus(), 100);
  }

  function closeAlarm() {
    elements.alarmOverlay.classList.remove('is-open');
    elements.alarmOverlay.setAttribute('aria-hidden', 'true');
    state.activeAlarmId = null;
    state.isTestAlarm = false;
  }

  function snoozeActiveAlarm() {
    const reminder = state.reminders.find((item) => item.id === state.activeAlarmId);
    if (!reminder || state.isTestAlarm) {
      closeAlarm();
      return;
    }
    reminder.scheduledAt = Date.now() + 10 * 60 * 1000;
    reminder.notifiedAt = null;
    saveReminders();
    scheduleAllReminders();
    renderReminders();
    closeAlarm();
    showToast('Snoozed for 10 minutes.', 'success');
  }

  function showToast(message, tone = 'success') {
    window.clearTimeout(state.toastTimer);
    elements.toastMessage.textContent = message;
    elements.toastIcon.textContent = tone === 'alert' ? '!' : '✓';
    elements.toastIcon.classList.toggle('is-alert', tone === 'alert');
    elements.toast.classList.add('is-visible');
    state.toastTimer = window.setTimeout(hideToast, 4600);
  }

  function hideToast() {
    elements.toast.classList.remove('is-visible');
  }

  async function handleReminderSubmit(event) {
    event.preventDefault();
    // Create/resume the audio context during this user gesture so a future alarm
    // has the best chance of being allowed to play by the browser.
    void getAudioContext();
    const input = elements.input.value.trim();
    if (!input) {
      showToast('Write the thing you would like to remember first.', 'alert');
      elements.input.focus();
      return;
    }

    // Prompt at the moment the user consciously asks to set an alarm.
    const permissionWasRequested = notificationPermission() === 'default';
    const notificationsEnabled = permissionWasRequested
      ? await requestNotificationPermission(false)
      : notificationPermission() === 'granted';

    const parsed = parseReminder(input);
    const reminder = {
      id: createId(),
      title: parsed.title,
      person: parsed.person,
      rawText: parsed.raw,
      scheduledAt: parsed.scheduledAt,
      createdAt: Date.now(),
      notifiedAt: null,
      completedAt: null
    };

    state.reminders.push(reminder);
    saveReminders();
    scheduleAllReminders();
    renderReminders();
    elements.input.value = '';
    updatePreview();

    const alertSuffix = notificationsEnabled ? ' Alerts are on.' : ' Turn on alerts whenever you’re ready.';
    showToast(`${reminder.title} is set for ${formatWhen(reminder.scheduledAt)}.${alertSuffix}`, notificationsEnabled ? 'success' : 'alert');
  }

  function handleListClick(event) {
    const actionButton = event.target.closest('[data-action]');
    if (!actionButton) return;
    const item = actionButton.closest('[data-reminder-id]');
    if (!item) return;
    const reminder = state.reminders.find((entry) => entry.id === item.dataset.reminderId);
    if (!reminder) return;

    if (actionButton.dataset.action === 'complete') {
      reminder.completedAt = reminder.completedAt ? null : Date.now();
      if (!reminder.completedAt) reminder.notifiedAt = null;
      saveReminders();
      scheduleAllReminders();
      renderReminders();
      showToast(reminder.completedAt ? 'Marked complete. Nicely done.' : 'Reminder moved back to your list.', 'success');
      return;
    }

    if (actionButton.dataset.action === 'delete') {
      state.reminders = state.reminders.filter((entry) => entry.id !== reminder.id);
      const timer = state.timers.get(reminder.id);
      if (timer) window.clearTimeout(timer);
      state.timers.delete(reminder.id);
      saveReminders();
      renderReminders();
      showToast('Reminder removed.', 'success');
    }
  }

  function clearCompleted() {
    const previousLength = state.reminders.length;
    state.reminders = state.reminders.filter((reminder) => !reminder.completedAt);
    if (state.reminders.length === previousLength) return;
    saveReminders();
    scheduleAllReminders();
    renderReminders();
    showToast('Completed reminders cleared.', 'success');
  }

  async function testAlarm() {
    await playChime();
    showBrowserNotification('Test reminder', 'Your gentle RemindME alarm is working.');
    openAlarm({ title: 'Test reminder', scheduledAt: Date.now() }, true);
  }

  function setupVoiceInput() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      showToast('Voice input is not available in this browser. You can type naturally instead.', 'alert');
      return;
    }

    if (state.recognition) {
      try { state.recognition.stop(); } catch (_) { /* no-op */ }
      return;
    }

    const recognition = new SpeechRecognition();
    state.recognition = recognition;
    recognition.lang = document.documentElement.lang || navigator.language || 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    recognition.onstart = () => elements.voiceButton.classList.add('is-listening');
    recognition.onresult = (event) => {
      elements.input.value = event.results[0][0].transcript;
      updatePreview();
      elements.input.focus();
    };
    recognition.onerror = () => showToast('We could not hear that. Please try again or type it out.', 'alert');
    recognition.onend = () => {
      elements.voiceButton.classList.remove('is-listening');
      state.recognition = null;
    };
    try {
      recognition.start();
    } catch (_) {
      state.recognition = null;
      showToast('Voice input is already starting. Please try again in a moment.', 'alert');
    }
  }

  function bindEvents() {
    elements.input.addEventListener('input', updatePreview);
    elements.form.addEventListener('submit', handleReminderSubmit);
    elements.reminderList.addEventListener('click', handleListClick);
    elements.clearCompleted.addEventListener('click', clearCompleted);
    elements.enableNotifications.addEventListener('click', () => requestNotificationPermission(true));
    elements.notificationNavButton.addEventListener('click', () => {
      document.querySelector('#notificationCard').scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    elements.testAlarm.addEventListener('click', testAlarm);
    elements.dismissAlarm.addEventListener('click', closeAlarm);
    elements.snoozeButton.addEventListener('click', snoozeActiveAlarm);
    elements.alarmOverlay.addEventListener('click', (event) => {
      if (event.target === elements.alarmOverlay) closeAlarm();
    });
    elements.toastClose.addEventListener('click', hideToast);
    elements.voiceButton.addEventListener('click', setupVoiceInput);
    elements.quickChips.forEach((chip) => {
      chip.addEventListener('click', () => {
        elements.input.value = chip.dataset.phrase || '';
        updatePreview();
        elements.input.focus();
      });
    });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) checkForDueReminders();
    });
    window.addEventListener('focus', checkForDueReminders);
  }

  function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register('./sw.js').catch(() => {
      // Offline support is a progressive enhancement; reminders still work without it.
    });
  }

  function init() {
    updateDateUi();
    updateNotificationUi();
    updatePreview();
    renderReminders();
    scheduleAllReminders();
    bindEvents();
    registerServiceWorker();
    window.setInterval(checkForDueReminders, CHECK_INTERVAL);
  }

  init();
})();
