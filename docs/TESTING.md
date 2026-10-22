# Testing MedReminder

## Automated

| What | Command | Where |
|---|---|---|
| Unit tests (schedule, daily reset, input validation, day labels) | `./gradlew testDebugUnitTest` | `app/src/test` |
| Instrumented tests (Room renumbering, schema/migrations) | `./gradlew connectedDebugAndroidTest` (device or emulator) | `app/src/androidTest` |
| Lint (release) | `./gradlew lintRelease` | report in `app/build/reports/lint-results-release.html` |

When the database schema changes, bump the version in `AppDatabase`, commit the new file in
`app/schemas/` and add a migration test to `MigrationTest`.

## Manual alarm matrix

The dose alarm must reach the user in every state. Run this on each Android version you support (at
least 8, 12, 14 and the latest) and on a physical phone from a brand with aggressive battery
management (e.g. Xiaomi, Samsung). Test the **release** build (`bundleRelease` → internal testing
track, or `assembleRelease` signed with your upload key), because R8 is only enabled there.

Set up a plan whose second medication is due 1–2 minutes after the first dose
(e.g. MedA, then MedB with interval 0 h 1 m), set the first dose time to now, then:

| # | Device state when MedB is due | Permissions | Expected |
|---|---|---|---|
| 1 | Screen off / locked | all allowed | Alarm screen over the lock screen, ringing, notification with Taken/Snooze |
| 2 | Locked with a PIN | all allowed | Same, without asking for the PIN |
| 3 | Unlocked, another app open | all allowed | Alarm screen takes over, ringing |
| 4 | Unlocked, another app open | "Display over other apps" off | Heads-up with Taken/Snooze, ringing until handled |
| 5 | Any | notifications off, overlay on | Alarm screen rings by itself |
| 6 | Locked | "Full screen notifications" off | Notification on the lock screen, ringing (alarm screen too if overlay is on) |
| 7 | Two medications due at the same time | all allowed | One at a time, "1 more medication waiting" |
| 8 | Phone rebooted before MedB is due | all allowed | Alarm still rings |
| 9 | "Alarms & reminders" revoked, then allowed again | — | Setup screen asks for it; after allowing, the alarm is back without opening the app |
| 10 | Deep sleep (Doze), see below | all allowed | Alarm and early reminder ring on time |

Also check: Taken and Snooze both on the alarm screen and on the notification; the early reminder
(green, rings once); changing the reminder offset while a schedule is active; deleting a medication
while a schedule is active; the app in English and Italian.

## Useful adb commands

```bash
# Permissions (replace the package if needed)
adb shell appops set io.github.dallanoce.medreminder SCHEDULE_EXACT_ALARM allow   # or deny
adb shell appops set io.github.dallanoce.medreminder SYSTEM_ALERT_WINDOW allow    # or default
adb shell appops set io.github.dallanoce.medreminder USE_FULL_SCREEN_INTENT deny  # Android 14+
adb shell pm revoke io.github.dallanoce.medreminder android.permission.POST_NOTIFICATIONS

# Registered alarms of the app (dose alarms, reminders, snoozes and the daily reset)
adb shell dumpsys alarm | grep -A2 io.github.dallanoce.medreminder

# Lock the screen / set a PIN / remove it
adb shell input keyevent KEYCODE_SLEEP
adb shell locksettings set-pin 1234
adb shell locksettings clear --old 1234

# Deep sleep (Doze): screen off first
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset

# Large text (200%) and 12-hour clock
adb shell settings put system font_scale 2.0
adb shell settings put system time_12_24 12

# Per-app language as set from the system settings
adb shell cmd locale set-app-locales io.github.dallanoce.medreminder --locales en
```
