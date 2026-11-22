# Changelog

## 0.1.3 - 2026-11-22

- Register reminders with Android as alarm-clock alarms using `AlarmManager.setAlarmClock`.
- Open a full-screen alarm screen with an explicit stop action.
- Play alarm audio on the Android alarm stream for up to 15 seconds; keep the alarm screen open until the user closes it.
- Add exact-alarm and full-screen notification permission handling.
- Improve alarm scheduling, notification behavior, and boot/time-change rescheduling.
