# MedReminder — Play Store readiness checklist

Based on a review of the codebase at commit `6d74e80` (2026-10-22) and `./gradlew lintRelease` (0 errors, 56 warnings).

**Priority levels**
- **P0** — Play Console will reject or block the release.
- **P1** — Bugs that cause missed, silent or crashing alarms. For a medication app these are as serious as blockers, and they show up in Play vitals (crashes, ANRs) and in reviews.
- **P2** — Store-review risk and user-facing polish.
- **P3** — Build and maintenance work.

---

## P0 — Blockers

- [x] **Change the application ID.** Play rejects `com.example.medreminder` (`app/build.gradle.kts:13`) because `com.example` is a restricted prefix. Pick a permanent ID (e.g. `it.<yourname>.medreminder`), since it can never change after the first upload. Rename these to match:
  - `namespace`
  - the source package folders
  - the `ACTION_DAILY_RESET` string (`AlarmScheduler.kt:17`)
  - the package assertion in `ExampleInstrumentedTest.kt`
  - *Done:* the app ID, namespace and Kotlin packages are now `io.github.dallanoce.medreminder`.
- [x] **Target API 36.** Since 31 Aug 2026, new apps and updates must target Android 16 (API 36). Set `compileSdk = 36` and `targetSdk = 36` (`app/build.gradle.kts:10,15`).
  - AGP 8.7.3 was only built for API ≤ 35, so upgrade AGP together with Kotlin and KSP. Lint reports 9.4.1 as the latest. AGP 9 is a major release (built-in Kotlin), so the latest 8.x is the smaller step.
  - The API 36 SDK platform is already installed.
  - Then retest the Android 16 changes: edge-to-edge can no longer be opted out of, and predictive back is on by default.
  - *Done:* compileSdk/targetSdk 36, AGP 9.4.1 (built-in Kotlin, so the `kotlin-android` plugin is removed), Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.12 (KSP2), Room 2.8.5; `kotlinOptions` was migrated to `compilerOptions`.
  - *Edge-to-edge retest on Android 16:* found and fixed the status-bar inset being applied twice (an outer `Scaffold` in `MainActivity`). The alarm screen draws correctly.
- [x] **Release signing and App Bundle (build setup).** There is no `signingConfig` yet.
  - Create an upload keystore and load it from a git-ignored `keystore.properties` (or environment variables).
  - Enroll in Play App Signing.
  - Upload the `.aab` produced by `./gradlew bundleRelease`. New apps can't be uploaded as APKs.
  - Add `*.jks`, `*.keystore` and `keystore.properties` to `.gitignore`.
  - Back up the key and its passwords somewhere safe.
  - *Done:* `app/build.gradle.kts` signs release builds from `keystore.properties` and stays unsigned without it. Added `keystore.properties.example`, `docs/RELEASE.md` and the ignore rules. Verified with a throwaway key: the AAB passes `jarsigner` and the APK passes `apksigner`.
  - [ ] **You:** create the real upload key and `keystore.properties` (steps in `docs/RELEASE.md`) and back them up. Keep Play App Signing enabled on the first upload.
- [x] **Clean up permissions** (`app/src/main/AndroidManifest.xml`):
  - **Keep** `SYSTEM_ALERT_WINDOW` (line 12) and actually request it. Tested on Android 16: it's the only thing that lets the alarm take over the screen while the phone is unlocked and in use (see P1-9). Today it's declared but never requested, so it has no effect.
  - Remove `USE_EXACT_ALARM` (line 8). Play reserves it for alarm-clock/timer and calendar apps, so a medication reminder risks rejection. Keep `SCHEDULE_EXACT_ALARM`, which the app already has a grant flow for.
    - **Do this together with P1-1.** Tested on Android 16: without `USE_EXACT_ALARM`, a fresh install starts with exact alarms off, and the current code crashes on first launch.
    - Once the user grants "Alarms & reminders", the scheduled alarms are identical (same type and flags, still exact).
    - The only lasting difference is that the user can revoke the grant later. That kills the app and deletes every pending alarm without any callback, so reschedule on every app start and on `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
  - Keep `USE_FULL_SCREEN_INTENT` (line 6) but complete its Play Console declaration (alarm use). On Android 14+, Play auto-grants it only to calling and alarm apps, so the code must also cope with it being off (P1-8).
  - Remove `WAKE_LOCK` (line 9). It's unused, and AlarmManager already holds a wake lock while the receiver runs.
  - *Done:*
    - `USE_EXACT_ALARM` and `WAKE_LOCK` are removed; `SYSTEM_ALERT_WINDOW` is kept and requested.
    - A new setup screen (`PermissionsScreen`) shows the status of notifications, alarms & reminders, full screen notifications and display over other apps. Each "Allow" button opens the matching Settings page, and "Continue anyway" is always visible.
    - Home shows a warning banner with a "Fix" button while anything is missing.
    - Alarms are restored on app start and on `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
    - Verified on Android 16:
      - a fresh install without exact-alarm access no longer crashes
      - revoking the access deletes the alarms, and granting it again restores them without opening the app
      - after a force-stop, opening the app restores them
  - [ ] **You:** complete the full-screen intent declaration in Play Console (*App content → Full-screen intent*: alarm functionality).
- [ ] **Play Console "App content" forms.** These involve no code but are required:
  - **Privacy policy:** a public URL that isn't a PDF. The app stores a user name and medication names, which count as health-related data. Health apps must also link the policy inside the app (see P2, "Settings → About").
  - **Health apps declaration:** declare medication or treatment management.
  - **Data safety:** the merged release manifest has no `INTERNET` permission, so data only leaves the device through Android Auto Backup (see P2, Backup). The answer is probably "no data collected", but confirm how Google treats backups first.
  - **Other forms:** content rating questionnaire, target audience (adults, not children), ads = none.
  - **Store listing (EN + IT):** title, short and full description, 512×512 icon, 1024×500 feature graphic, at least 2 phone screenshots. Category: Medical.
  - **Disclaimer in the description:** state that the app is not a medical device and that users should consult a healthcare professional.
  - [x] *Prepared:* `docs/privacy-policy.md` (EN + IT, ready for GitHub Pages), `docs/play-store/listing.md` (name, short and full description in EN + IT, within Play's length limits) and `docs/play-store/console-answers.md` (suggested answers for every App content form).
  - [ ] **You:** replace `CONTACT_EMAIL` in the policy, publish it (GitHub Pages: *Settings → Pages → main / docs*), then fill in the forms and the store listing in Play Console.
  - [ ] **You:** create the graphics: 512×512 icon, 1024×500 feature graphic, phone screenshots.
- [ ] **Closed testing requirement.** Personal developer accounts created after 13 Nov 2023 must run a closed test with ≥ 12 testers opted in for 14 consecutive days before production access can be requested. This has the longest lead time on the list, so start it as soon as a build can be uploaded.
  - [x] *Prepared:* step-by-step instructions in `docs/RELEASE.md`.
  - [ ] **You:** recruit 12 testers and run the 14-day closed test in Play Console.

---

## P1 — Reliability bugs (missed, silent or crashing alarms)

1. [x] **Crash on launch without exact-alarm permission.**
   - `MainActivity.onCreate` calls `scheduleDailyReset()` (`MainActivity.kt:47`), which calls `setExactAndAllowWhileIdle` (`AlarmScheduler.kt:86`) with no permission check. That throws `SecurityException`.
   - Today this crash happens on Android 12/12L if the user revokes "Alarms & reminders". Once `USE_EXACT_ALARM` is removed, it happens on every fresh install on Android 14+.
   - Fix: the 03:00 reset doesn't need to be exact, so use `setAndAllowWhileIdle()` for it. Guard every exact call with `canScheduleExactAlarms()`.
   - *Done (with P0 permissions):*
     - The reset stays exact when allowed and falls back to an inexact `setWindow` (no permission needed) otherwise. `scheduleAlarm` checks `canScheduleExactAlarms()`.
     - A reset delivered late no longer clears a schedule set after the reset time (`shouldClearOnReset`, unit-tested in `DailyResetTest`).
2. [x] **The reminder offset is ignored, and changing it never reschedules.**
   - Each NavHost destination gets its own `MedViewModel`. If nothing collects a `WhileSubscribed` StateFlow, its `.value` is just the initial value.
   - On Home, `scheduleAllAlarms` reads `reminderOffset.value` (`MedViewModel.kt:108`), which is never collected there, so it is always 15.
   - In Settings, `setReminderOffset` reads `firstDoseTime.value` (`MedViewModel.kt:90`), which is never collected there, so it is always null.
   - Fix: read the source inside the coroutine, e.g. `dataStoreManager.reminderOffset.first()` and `medicationDao.getAllMedications().first()`.
   - *Done (with P1-5):* scheduling always goes through `AlarmScheduler.rescheduleAll()`, which reads the plan, first dose time and offset from storage. Verified on Android 16: offset 30 → reminder at dose −30; changing it to 10 moves the reminder to dose −10 and removes the old one.
3. [x] **Overdue doses all ring at once.** `MedViewModel.kt:123` schedules each dose without checking that it is in the future (the reminder at line 127 does check). Setting a first-dose time earlier in the day makes every overdue alarm fire immediately.
   - *Done (with P1-5):* only doses and reminders still ahead are scheduled (unit-tested). Verified on Android 16: a first dose 2 h in the past schedules nothing and nothing rings.
4. [x] **Integer overflow and no input validation.**
   - `(intervalHours * 3600 + intervalMinutes * 60) * 1000` is computed as `Int` and overflows from about 597 h. Locations: `MedViewModel.kt:120`, `AlarmReceiver.kt:124`, `HomeScreen.kt:176`.
   - `offsetMin * 60 * 1000` has the same problem (`MedViewModel.kt:126`, `AlarmReceiver.kt:139`).
   - The hours/minutes fields accept any number of digits (`MedicationPlanScreen.kt:116,123`), and the offset field accepts negative values (`SettingsScreen.kt:191`).
   - Fix: use `Long` arithmetic (or `kotlin.time.Duration`) and validate ranges, e.g. minutes 0–59, hours 0–24, offset 0–120.
   - *Overflow done (with P1-5):* `DoseSchedule` computes in `Long` (unit test with a 700 h interval).
   - *Validation done:*
     - New `ui/InputValidation.kt` (5 tests in `InputValidationTest`): interval 0–24 h and 0–59 min (24 h max in total), offset 0–120 (0 = no reminder), name required and at most 40 characters, numeric fields digits only.
     - The dialogs show the error inline and disable Add/Save while the input is invalid. The offset dialog's hard-coded "Minutes" label is now localized.
     - Verified on Android 16 for each invalid case.
5. [x] **The schedule is computed in three places, and they disagree.**
   - `MedViewModel.scheduleAllAlarms` skips the first medication.
   - `AlarmReceiver.rescheduleAlarms` (after a reboot) schedules it (`AlarmReceiver.kt:121`).
   - `HomeScreen.calculateScheduledTime` is a third copy.
   - Fix: extract one pure function (meds + first-dose time → list of `(medId, time, isReminder)`), use it in all three places, and unit-test it.
   - *Done:*
     - `alarm/DoseSchedule.kt` is the single source of truth, covered by 8 tests in `DoseScheduleTest`. The Home screen, the ViewModel and the scheduler (boot, permission grant, app start) all use it.
     - Decision: the first medication gets an alarm too when its time is still ahead; a reminder offset of 0 turns reminders off.
     - Snoozes now have their own request codes, so a reschedule doesn't drop them (verified).
6. [x] **Editing the plan during an active day breaks the schedule.**
   - Adding or deleting a medication doesn't reschedule the doses that follow it.
   - `orderIndex` isn't renumbered after a delete, so a later add can create a duplicate (`MedViewModel.kt:48`).
   - The UI decides which medication is first from `orderIndex > 0` (`HomeScreen.kt:161`, `MedicationPlanScreen.kt:75`), but scheduling uses list position, so the two can disagree.
   - `MedicationDao.updatePlan` already renumbers correctly but is never called.
   - *Done:*
     - Adding or deleting a medication reschedules the day. A delete renumbers the remaining medications in one transaction (`deleteAndRenumber`, replacing the unused `updatePlan`), and new medications get the next free position.
     - Both screens now decide which medication is first from list position.
     - 3 instrumented Room tests in `MedicationDaoTest`. Verified on Android 16: deleting a middle or the first medication and adding a new one moves the alarms accordingly.
7. [x] **The alarm sound can crash or stop after one play.**
   - `RingtoneManager.getRingtone()` returns null when the saved URI no longer exists (e.g. a deleted ringtone, or a backup restored on a new phone). That causes an NPE in `AlarmActivity.kt:171`, which crashes the alarm screen.
   - The ringtone isn't looped, so it plays once and goes quiet.
   - Fix: fall back to the default alarm sound, and set `isLooping = true` (API 28+) or use a looping `MediaPlayer`.
   - *Done:* the alarm plays the first sound that actually starts: chosen ringtone → default alarm → default notification. It checks `isPlaying`, which also catches a non-null `Ringtone` that plays nothing, and loops. Verified on Android 16: with a nonexistent ringtone the old code rang **silently** (0 alarm players); now the fallback plays and is still playing after 45 s, also with a short sound.
8. [x] **Several paths end in a silent alarm or no alarm.**
   - The notification channel has sound and vibration turned off (`AlarmReceiver.kt:155-157`), so all audio comes from the full-screen activity. On Android 14+, if the full-screen intent isn't allowed (`NotificationManager.canUseFullScreenIntent()`), the user only gets a silent heads-up.
   - If `POST_NOTIFICATIONS` is denied, the app shows no rationale and gives no way to re-enable it (`MainActivity.kt:80`). Verified on Android 16: with the phone locked and notifications denied, the screen stays off and nothing appears.
   - Fix part 1: create a new channel ID with alarm sound (`USAGE_ALARM`) and vibration, and set `FLAG_INSISTENT` on the notification.
   - Fix part 2: add Taken / Snooze action buttons to the notification.
   - Fix part 3: show a persistent warning on Home, with a Settings deep link, whenever notifications, full-screen intents or exact alarms are disabled. *(Done with P0 permissions: Home banner + setup screen.)*
   - *Done (with P1-9):*
     - New `AlarmNotifications`: the dose notification rings on the alarm stream, vibrates and repeats (`FLAG_INSISTENT`) until the user reacts. Its channel id carries the chosen ringtone, because channel sounds can't change, and falls back to the default alarm sound when the ringtone can't be read.
     - The notification has Taken / Snooze buttons, handled by `AlarmActionReceiver` (not exported).
     - The alarm screen plays its own sound only when the notification can't (the app's notifications or the channel's sound are off).
9. [x] **When the phone is unlocked and in use, the alert is lost completely (verified on Android 16).**
   - When the phone is unlocked, `AlarmReceiver.kt:97` calls `startActivity` from the background.
   - Android blocked the launch (`Background activity launch blocked … BAL_BLOCK`) without throwing an exception. The `catch` fallback never ran, so there was no screen, no notification and no sound.
   - With "Display over other apps" granted, the same launch was allowed (`BAL_ALLOW_SAW_PERMISSION`) and the alarm took over the screen.
   - Fix part 1: always post the full-screen-intent notification with sound. Locked, it opens full screen; unlocked, it shows as a heads-up.
   - Fix part 2: additionally call `startActivity` when `Settings.canDrawOverlays()` is true, so the alarm takes over the screen even while the phone is in use.
   - Fix part 3: request the overlay permission during onboarding (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`). *(Done with P0 permissions.)*
   - *Done:*
     - The receiver always posts the notification, and opens the alarm screen directly whenever "Display over other apps" is allowed.
     - Also found and fixed: the alarm screen called `requestDismissKeyguard()`, which on a PIN-locked phone asks for the PIN when the alarm appears. It now shows over the lock screen without unlocking it.
     - Verified on Android 16, real alarms, every state:

       | State | Result |
       |---|---|
       | Locked | Alarm screen + sound |
       | PIN-locked | Alarm screen over the lock screen + sound |
       | In use, overlay allowed | Alarm screen takes over + sound |
       | In use, no overlay | Heads-up with Taken/Snooze, still ringing after 30 s (before: nothing at all) |
       | Taken / Snooze from the notification | Sound stops; snooze registered at +5 min |
       | Notifications off, overlay allowed | Alarm screen rings by itself |
       | Full-screen intents revoked, locked | Lock-screen notification with sound; with overlay, the alarm screen |
       | Reminder | Its own channel, sound doesn't repeat |
10. [x] **Two doses due at the same time.**
    - `AlarmActivity` is `singleInstance` (`AndroidManifest.xml:41`) but doesn't override `onNewIntent`.
    - So if a second alarm arrives while the first is on screen, it still shows the first medication's name, and the second `ongoing` notification is never cancelled.
    - This is common, because a 0 h 0 m interval is allowed.
    - *Done:* the alarm screen keeps a queue. `onNewIntent` adds new alarms; it shows the newest one with "N more medications waiting" (plural, EN + IT), and each one is confirmed, snoozed or handled from its own notification. Verified on Android 16, unlocked and locked: two doses due together are handled one after the other, and both notifications and the sound end with the last one.
11. [x] **ANR risk in the receiver.** `AlarmReceiver.kt:41-42` and `:101` read DataStore with `runBlocking` on the main thread. Use `goAsync()`, as the boot and reset branches already do.
    - *Done (with P1-9):* every branch of `AlarmReceiver` now reads DataStore inside `goAsync()` on `Dispatchers.IO`; `runBlocking` is gone.
12. [x] **The receiver is exported.** `AlarmReceiver` has `exported="true"` (`AndroidManifest.xml:48`), so any installed app can trigger a fake alarm or the daily reset.
    - Fix: set `exported="false"`. `BOOT_COMPLETED` comes from the system, so it is still delivered.
    - Also check `intent.action` explicitly.
    - *Done:*
      - `AlarmReceiver` is `exported="false"`. Dose alarms carry an explicit `ACTION_DOSE_ALARM` (cancellation uses the same action so it still matches), and `onReceive` ignores unknown actions.
      - Verified on Android 16: spoofed reset and dose broadcasts from adb had no effect. The permission broadcast, the app's own alarms, delete-cancellation and a real reboot (BOOT_COMPLETED restored the schedule and the 03:00 reset) all still work.
13. [x] **`scheduleAlarm` opens Settings from any context** (`AlarmScheduler.kt:21-28`).
    - When it's called from the boot receiver, that launch is blocked and the alarm is silently dropped.
    - Fix: return a result so the UI can react.
    - Also reschedule all alarms on `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
    - *Done (with P0 permissions):* `scheduleAlarm` returns `false` instead of opening Settings, and the setup screen and Home banner surface the missing permission. The receiver reschedules everything on the permission broadcast.
14. [x] **Consider `setAlarmClock()` for doses.** It is the API meant for user-visible alarms (it also needs the exact-alarm permission). Doze doesn't throttle it the way it throttles `setExactAndAllowWhileIdle`, and the system shows the next alarm in the status bar.
    - *Done:*
      - Dose alarms and snoozes use `setAlarmClock()`; tapping the "next alarm" opens the app. Early reminders stay on `setExactAndAllowWhileIdle()`, so a reminder isn't shown as the next alarm.
      - Verified on Android 16: the dose alarm is registered as an alarm clock (`flags=0x3`) and shown as the next alarm, and Android refuses to enter deep Doze before it.
      - With only a reminder pending, the device entered deep Doze (IDLE) and the reminder still fired on time.
15. [x] **Decide how the 03:00 reset should behave.** It cancels every dose scheduled after 3 AM, so a plan started in the evening loses its night doses (`AlarmReceiver.kt:59-82`).
    - *Decision and implementation:*
      - A day's schedule is cleared at the first 03:00 after it started, unless doses remain; then right after its last dose (`alarm/DailyReset.kt`, 7 tests including daylight saving).
      - The reset time is recomputed on every change: plan edits, first dose, reboot, permission grant. When the app opens it clears a schedule that ended while the phone was off. This replaces the late-reset guard from P1-1.
      - Home marks doses after midnight with "(tomorrow)" / "(yesterday)" (`dayOffset`, 4 tests).
      - Verified on Android 16: a daytime plan resets at 03:00; a dose 24 h later moves the reset to right after it; deleting that dose moves it back; yesterday's schedule is cleared on app start.

---

## P2 — Store-review risk and polish

- [x] **Launcher icon.**
  - `mipmap-anydpi/ic_launcher.xml:3-5` (and `ic_launcher_round.xml`) uses the same full-colour, already-rounded image for the background, foreground and monochrome layers. Launchers then crop it unpredictably, and the themed icon on Android 13+ comes out as a blank shape.
  - `drawable/ic_launcher_foreground.xml` is still the Android Studio robot template, and low-density screens can pick it (lint `IconXmlAndPng`).
  - Fix: regenerate the icon with Image Asset Studio: a glyph inside the 66 dp safe zone, a solid-colour background layer, and a single-colour monochrome layer.
  - *Done:*
    - The original artwork turned out to have the glyph **cut out** (transparent), so launchers showed the wallpaper through the clock and pill. The glyph was traced into a vector (`drawable/ic_launcher_foreground.xml`, inside the safe zone) on the artwork's blue gradient (`ic_launcher_background.xml`); the same glyph is the monochrome layer.
    - The robot template and the legacy mipmap bitmaps are removed (never used with minSdk 26). The original artwork is kept as `docs/play-store/icon-artwork.webp`, the source for the 512 px store icon.
    - Verified in the Android 16 app drawer next to the old icon. Themed icons were not checked on a device.
- [x] **Notification icon.** `setSmallIcon(R.drawable.ic_launcher_foreground)` (`AlarmReceiver.kt:194`) renders as a solid white square. Add a white-on-transparent `ic_stat_*` vector.
  - *Done:* `drawable/ic_notification.xml` (the glyph, 24 dp). Verified in the status bar and the heads-up.
- [x] **Home top-bar icon.** Tinting the full-colour image (`HomeScreen.kt:36-39`) paints it as a solid coloured square. Use the monochrome glyph instead.
  - *Done:* uses `ic_notification`, tinted with the theme colour.
- [x] **Localization.**
  - The English strings say "Ciao" (`values/strings.xml:26-27`), and the Italian default greeting is "Ciao User".
  - `SettingsScreen.kt:74` strips "Ciao " off the greeting with `removePrefix`.
  - Hard-coded text to move into string resources:
    - "Minutes" (`SettingsScreen.kt:183`)
    - `"$reminderOffset min"` (`SettingsScreen.kt:88`)
    - the channel name "Medication Alarms" (`AlarmReceiver.kt:152`)
    - the fallback name "Medication" (`AlarmActivity.kt:46`, `AlarmReceiver.kt:36`)
  - The language dialog has no "System default" option, even though the `lang_system` string exists.
  - *Done:*
    - The English greeting is "Hello"/"Hello %s" (Italian "Ciao"), and the user-name row shows "Not set" instead of trimming "Ciao ".
    - The offset is a localized plural ("15 minutes before", "Off" for 0); the channel names and "Minutes" were already fixed in P1.
    - The language dialog offers "System default".
    - Verified in Italian on Android 16. Switching to English uncovered the language-switching bug below.
- [x] **Language switching (all Android versions).**
  - Below API 33, `AppCompatDelegate.setApplicationLocales` (`SettingsScreen.kt:245`) only takes effect in an `AppCompatActivity` that uses an AppCompat theme. To persist the choice it also needs `AppLocalesMetadataHolderService` (with `autoStoreLocales`) declared in the manifest.
  - `MainActivity` is a plain `ComponentActivity` and the theme is platform Material, so none of this is in place.
  - Test on an API 31 emulator.
  - *Worse than expected:* on Android 16 the switch did nothing either. `setApplicationLocales()` finds the system `LocaleManager` through a registered `AppCompatActivity`, and the app had none, so the system app locale stayed `[]`.
  - *Done:*
    - `MainActivity` and `AlarmActivity` extend `AppCompatActivity`, with an AppCompat DayNight theme and `AppLocalesMetadataHolderService` (`autoStoreLocales`).
    - Below Android 13, alarm notification texts use the saved language. Settings shows the language actually applied, including one picked in the system's app settings.
    - Release AABs keep all languages (`bundle { language { enableSplit = false } }`). Lint pointed out that Play would otherwise install only the device language's strings, which breaks in-app switching.
    - Verified on Android 16: switching to English updates the UI and the system app locale `[en]`, survives a restart, and the alarm notification is in English. "System default" clears it, and a change from the system setting is reflected.
    - **Not verified on Android 8–12** (no emulator image installed).
- [x] **Dark mode.** `Theme.MedReminder` extends `Theme.Material.Light` (`values/themes.xml:4`), so the app flashes white on launch in dark mode. Use a DayNight parent.
  - *Done (with language switching):* `Theme.AppCompat.DayNight.NoActionBar`. Verified on Android 16 in night mode: the launch splash is dark, with no white flash.
- [x] **Time format.** 24-hour time is forced in two places: the time picker (`HomeScreen.kt:97`) and the `"HH:mm"` formatter (`HomeScreen.kt:183`). Respect `DateFormat.is24HourFormat(context)` and the locale's time format.
  - *Done:* the picker and Home follow the system 12/24-hour setting and locale (`DateFormat.getTimeFormat`). Verified on Android 16: 12-hour mode shows AM/PM in the picker and "5:35 PM … 1:35 AM (domani)" on Home; 24-hour mode shows "17:35 … 01:35 (domani)".
- [x] **Accessibility.** The font scale is capped at 1.3 (`Theme.kt:57`), and many users of a medication app need large text. Remove the cap and make the screens scroll instead.
  - *Done (reverses the cap added in commit `6d74e80`; revert this one commit if you prefer the cap):*
    - The system font size is honoured up to 200%. Home is now a single scrolling list; the alarm screen and the add-medication dialog scroll when needed; top-bar titles and navigation labels ellipsize instead of breaking words.
    - Verified on Android 16 at 200% on every screen, including a long medication name on the alarm screen.
    - Known cosmetic issue: when the alarm screen takes over while the phone is in use, the system heads-up (with the same Taken/Snooze buttons) covers its title.
- [x] **Settings → About:** add the privacy policy link, the medical disclaimer and the app version.
  - *Done:* Settings has "Privacy policy" (opens `https://dallanoce.github.io/MedReminder/privacy-policy.html`) and "About" (version + medical disclaimer), EN + IT. Verified on Android 16. The link only works once GitHub Pages is enabled (P0).
- [x] **Confirmation before RESET** (already in `app/TODO.md`).
  - *Done:* the dialog explains that today's remaining alarms are cancelled (EN + IT). Verified on Android 16: Cancel keeps the schedule and its alarms; confirming clears both.
- [x] **Backup.**
  - `backup_rules.xml` and `data_extraction_rules.xml` are untouched templates, so everything gets backed up. That includes the device-specific ringtone URI and a stale first-dose time.
  - Alarms are not rescheduled after a restore.
  - Decide explicitly: either exclude those two keys or turn backup off. Make the Data safety answers match.
  - *Decision and implementation:*
    - The plan (health data) is excluded from cloud backups, which keeps the Data safety answer at "no data collected", and it moves with a direct phone-to-phone transfer (Android 12+, `data_extraction_rules.xml`). Android 11 and below back up nothing (`backup_rules.xml`), since one rule set covers both cloud and transfer there.
    - A stale ringtone or first dose after a transfer is handled by the P1-7 fallback and the P1-15 reset on app start.
    - Verified on Android 16 with the local backup transport: after device transfer the plan is restored; a cloud backup stores nothing (the transport rejects the empty backup).
- [x] **Separate notification channels** for "upcoming reminder" and "time to take it", so users can adjust each one independently.
  - *Done (with P1-8):* "Dose alarms" and "Upcoming dose reminders" channels, both localized.

---

## P3 — Build and maintenance

- [x] **Enable R8.**
  - Set `isMinifyEnabled = true` and `isShrinkResources = true` (`app/build.gradle.kts:24`).
  - Add `-keepattributes SourceFile,LineNumberTable` so stack traces in Play Console stay readable (the AAB carries the mapping file).
  - This matters here because `material-icons-extended` is very large unless shrunk.
  - Smoke-test the minified release build on a real device.
  - *Done:* minify + resource shrinking, with line numbers kept (the AAB carries the mapping). The release APK went from 12.8 MB to 2.0 MB. Verified the signed, minified release on the Android 16 emulator: setup screen, plan, a real alarm on the locked screen with sound, snooze and the language switch work, with no crashes. Not yet tried on a physical device.
- [x] **Upgrade dependencies** along with AGP. Lint reports 28 outdated-dependency warnings (Compose BOM 2024.11, Room 2.6.1, …). Also migrate `kotlinOptions` to `kotlin { compilerOptions { … } }`.
  - *Done (`kotlinOptions` was migrated in P0):*
    - Upgraded to the newest versions that compile against API 36: Compose BOM 2026.06.01 (Compose 1.11), core-ktx 1.18.0, lifecycle 2.10.0, navigation 2.9.8, activity-compose 1.13.0, appcompat 1.8.0, DataStore 1.3.0, coroutines 1.11.0, androidx.test junit 1.3.0 / espresso 3.7.0.
    - Fixed the one new Compose lint error (a string read through `LocalContext` during composition).
    - Re-ran the full suite: 25 unit tests, 4 instrumented tests, every emulator scenario, and the minified release build.
    - [ ] **Later:** Compose 1.12, core 1.19, lifecycle 2.11 and navigation 2.10 need `compileSdk = 37`. Install "Android SDK Platform 37" in the SDK Manager first; targetSdk can stay 36.
- [x] **Room schema.** Set `exportSchema = true` (`AppDatabase.kt:8`) and commit the schema directory to git. Every later schema change can then ship with a tested migration. Never use destructive migration, because the database holds users' medication plans.
  - *Done:*
    - Room Gradle plugin with `schemaDirectory("app/schemas")`. The version-1 schema is committed, and `AppDatabase` documents the migration rule.
    - New `MigrationTest` (Room `MigrationTestHelper`) checks the exported schema; future migration tests go there.
    - kotlinx.serialization is aligned to 1.8.1, since room-testing needs it and test APKs share the app's versions. 5/5 instrumented tests pass.
- [x] **Versioning.** Increase `versionCode` on every upload and pick a `versionName` scheme.
  - *Done:* `versionCode` goes up by 1 per upload, and `versionName` follows MAJOR.MINOR.PATCH, starting at 1.0.0. Both are documented in `app/build.gradle.kts` and `docs/RELEASE.md`.
- [x] **Tests.**
  - Replace the template tests. The instrumented test asserts the `com.example` package name, so it will fail after the rename.
  - Add unit tests for the schedule function from P1-5.
  - Test manually on Android 8, 12, 14 and 16, in each of these states:
    - phone locked
    - phone unlocked
    - another app in the foreground
    - in Doze (`adb shell dumpsys deviceidle force-idle`)
    - after a reboot
    - with each permission denied in turn
  - *Done:*
    - Template tests removed. There are now 24 unit tests (`DoseScheduleTest`, `DailyResetTest`, `InputValidationTest`, `DayOffsetTest`) and 4 instrumented tests (`MedicationDaoTest`, `MigrationTest`).
    - `docs/TESTING.md` describes how to run them, plus the manual alarm matrix with adb commands.
    - The whole matrix passed on the Android 16 emulator during this branch: locked, PIN, in use with and without overlay, notifications off, full screen revoked, two doses at once, reboot, permission revoke/re-grant, Doze, 200% text, 12 h clock, both languages, minified release.
    - [ ] **You:** run the matrix in `docs/TESTING.md` on Android 8, 12 and 14 (no emulator images for them were installed) and on at least one physical phone.
- [ ] **Lint cleanup:**
  - delete 11 unused resources: the template colours, `ic_account_box`, `ic_favorite`, `ic_home`, `ic_launcher_background`
  - replace the deprecated `Icons.Default.Assignment` with its `Icons.AutoMirrored` version
  - remove the redundant activity label
- [ ] **16 KB page size.** The current dependencies include no native libraries, so the app should already comply. Recheck the final AAB in APK Analyzer after upgrading dependencies.
- [ ] **Repo hygiene.** Remove or git-ignore `progress.txt`, `app/TODO.md` and the tracked `.idea/` files.

---

## Suggested order

1. Application ID, target API 36 with the AGP upgrade, and signing. This lets you upload to internal testing.
2. P1 crash and silent-alarm fixes (1, 2, 7, 8, 9), together with the permission cleanup.
3. Privacy policy, the Console forms and the listing assets. Then start the 12-tester closed test.
4. Remaining P1 and P2 items during the 14-day test, then promote to production.

## Sources

- [Target API level requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Full-screen intent limits (AOSP)](https://source.android.com/docs/core/permissions/fsi-limits)
- [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [Testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Schedule alarms / exact alarm permissions](https://developer.android.com/training/scheduling/alarms)
