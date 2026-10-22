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
4. [ ] **Integer overflow and no input validation.**
   - `(intervalHours * 3600 + intervalMinutes * 60) * 1000` is computed as `Int` and overflows from about 597 h. Locations: `MedViewModel.kt:120`, `AlarmReceiver.kt:124`, `HomeScreen.kt:176`.
   - `offsetMin * 60 * 1000` has the same problem (`MedViewModel.kt:126`, `AlarmReceiver.kt:139`).
   - The hours/minutes fields accept any number of digits (`MedicationPlanScreen.kt:116,123`), and the offset field accepts negative values (`SettingsScreen.kt:191`).
   - Fix: use `Long` arithmetic (or `kotlin.time.Duration`) and validate ranges, e.g. minutes 0–59, hours 0–24, offset 0–120.
   - *Overflow done (with P1-5):* `DoseSchedule` computes in `Long` (unit test with a 700 h interval). Input validation is still open.
5. [x] **The schedule is computed in three places, and they disagree.**
   - `MedViewModel.scheduleAllAlarms` skips the first medication.
   - `AlarmReceiver.rescheduleAlarms` (after a reboot) schedules it (`AlarmReceiver.kt:121`).
   - `HomeScreen.calculateScheduledTime` is a third copy.
   - Fix: extract one pure function (meds + first-dose time → list of `(medId, time, isReminder)`), use it in all three places, and unit-test it.
   - *Done:*
     - `alarm/DoseSchedule.kt` is the single source of truth, covered by 8 tests in `DoseScheduleTest`. The Home screen, the ViewModel and the scheduler (boot, permission grant, app start) all use it.
     - Decision: the first medication gets an alarm too when its time is still ahead; a reminder offset of 0 turns reminders off.
     - Snoozes now have their own request codes, so a reschedule doesn't drop them (verified).
6. [ ] **Editing the plan during an active day breaks the schedule.**
   - Adding or deleting a medication doesn't reschedule the doses that follow it.
   - `orderIndex` isn't renumbered after a delete, so a later add can create a duplicate (`MedViewModel.kt:48`).
   - The UI decides which medication is first from `orderIndex > 0` (`HomeScreen.kt:161`, `MedicationPlanScreen.kt:75`), but scheduling uses list position, so the two can disagree.
   - `MedicationDao.updatePlan` already renumbers correctly but is never called.
7. [ ] **The alarm sound can crash or stop after one play.**
   - `RingtoneManager.getRingtone()` returns null when the saved URI no longer exists (e.g. a deleted ringtone, or a backup restored on a new phone). That causes an NPE in `AlarmActivity.kt:171`, which crashes the alarm screen.
   - The ringtone isn't looped, so it plays once and goes quiet.
   - Fix: fall back to the default alarm sound, and set `isLooping = true` (API 28+) or use a looping `MediaPlayer`.
8. [ ] **Several paths end in a silent alarm or no alarm.**
   - The notification channel has sound and vibration turned off (`AlarmReceiver.kt:155-157`), so all audio comes from the full-screen activity. On Android 14+, if the full-screen intent isn't allowed (`NotificationManager.canUseFullScreenIntent()`), the user only gets a silent heads-up.
   - If `POST_NOTIFICATIONS` is denied, the app shows no rationale and gives no way to re-enable it (`MainActivity.kt:80`). Verified on Android 16: with the phone locked and notifications denied, the screen stays off and nothing appears.
   - Fix part 1: create a new channel ID with alarm sound (`USAGE_ALARM`) and vibration, and set `FLAG_INSISTENT` on the notification.
   - Fix part 2: add Taken / Snooze action buttons to the notification.
   - Fix part 3: show a persistent warning on Home, with a Settings deep link, whenever notifications, full-screen intents or exact alarms are disabled. *(Done with P0 permissions: Home banner + setup screen.)*
9. [ ] **When the phone is unlocked and in use, the alert is lost completely (verified on Android 16).**
   - When the phone is unlocked, `AlarmReceiver.kt:97` calls `startActivity` from the background.
   - Android blocked the launch (`Background activity launch blocked … BAL_BLOCK`) without throwing an exception. The `catch` fallback never ran, so there was no screen, no notification and no sound.
   - With "Display over other apps" granted, the same launch was allowed (`BAL_ALLOW_SAW_PERMISSION`) and the alarm took over the screen.
   - Fix part 1: always post the full-screen-intent notification with sound. Locked, it opens full screen; unlocked, it shows as a heads-up.
   - Fix part 2: additionally call `startActivity` when `Settings.canDrawOverlays()` is true, so the alarm takes over the screen even while the phone is in use.
   - Fix part 3: request the overlay permission during onboarding (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`). *(Done with P0 permissions.)*
10. [ ] **Two doses due at the same time.**
    - `AlarmActivity` is `singleInstance` (`AndroidManifest.xml:41`) but doesn't override `onNewIntent`.
    - So if a second alarm arrives while the first is on screen, it still shows the first medication's name, and the second `ongoing` notification is never cancelled.
    - This is common, because a 0 h 0 m interval is allowed.
11. [ ] **ANR risk in the receiver.** `AlarmReceiver.kt:41-42` and `:101` read DataStore with `runBlocking` on the main thread. Use `goAsync()`, as the boot and reset branches already do.
12. [ ] **The receiver is exported.** `AlarmReceiver` has `exported="true"` (`AndroidManifest.xml:48`), so any installed app can trigger a fake alarm or the daily reset.
    - Fix: set `exported="false"`. `BOOT_COMPLETED` comes from the system, so it is still delivered.
    - Also check `intent.action` explicitly.
13. [x] **`scheduleAlarm` opens Settings from any context** (`AlarmScheduler.kt:21-28`).
    - When it's called from the boot receiver, that launch is blocked and the alarm is silently dropped.
    - Fix: return a result so the UI can react.
    - Also reschedule all alarms on `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
    - *Done (with P0 permissions):* `scheduleAlarm` returns `false` instead of opening Settings, and the setup screen and Home banner surface the missing permission. The receiver reschedules everything on the permission broadcast.
14. [ ] **Consider `setAlarmClock()` for doses.** It is the API meant for user-visible alarms (it also needs the exact-alarm permission). Doze doesn't throttle it the way it throttles `setExactAndAllowWhileIdle`, and the system shows the next alarm in the status bar.
15. [ ] **Decide how the 03:00 reset should behave.** It cancels every dose scheduled after 3 AM, so a plan started in the evening loses its night doses (`AlarmReceiver.kt:59-82`).

---

## P2 — Store-review risk and polish

- [ ] **Launcher icon.**
  - `mipmap-anydpi/ic_launcher.xml:3-5` (and `ic_launcher_round.xml`) uses the same full-colour, already-rounded image for the background, foreground and monochrome layers. Launchers then crop it unpredictably, and the themed icon on Android 13+ comes out as a blank shape.
  - `drawable/ic_launcher_foreground.xml` is still the Android Studio robot template, and low-density screens can pick it (lint `IconXmlAndPng`).
  - Fix: regenerate the icon with Image Asset Studio: a glyph inside the 66 dp safe zone, a solid-colour background layer, and a single-colour monochrome layer.
- [ ] **Notification icon.** `setSmallIcon(R.drawable.ic_launcher_foreground)` (`AlarmReceiver.kt:194`) renders as a solid white square. Add a white-on-transparent `ic_stat_*` vector.
- [ ] **Home top-bar icon.** Tinting the full-colour image (`HomeScreen.kt:36-39`) paints it as a solid coloured square. Use the monochrome glyph instead.
- [ ] **Localization.**
  - The English strings say "Ciao" (`values/strings.xml:26-27`), and the Italian default greeting is "Ciao User".
  - `SettingsScreen.kt:74` strips "Ciao " off the greeting with `removePrefix`.
  - Hard-coded text to move into string resources:
    - "Minutes" (`SettingsScreen.kt:183`)
    - `"$reminderOffset min"` (`SettingsScreen.kt:88`)
    - the channel name "Medication Alarms" (`AlarmReceiver.kt:152`)
    - the fallback name "Medication" (`AlarmActivity.kt:46`, `AlarmReceiver.kt:36`)
  - The language dialog has no "System default" option, even though the `lang_system` string exists.
- [ ] **Language switching on Android 8–12.**
  - Below API 33, `AppCompatDelegate.setApplicationLocales` (`SettingsScreen.kt:245`) only takes effect in an `AppCompatActivity` that uses an AppCompat theme. To persist the choice it also needs `AppLocalesMetadataHolderService` (with `autoStoreLocales`) declared in the manifest.
  - `MainActivity` is a plain `ComponentActivity` and the theme is platform Material, so none of this is in place.
  - Test on an API 31 emulator.
- [ ] **Dark mode.** `Theme.MedReminder` extends `Theme.Material.Light` (`values/themes.xml:4`), so the app flashes white on launch in dark mode. Use a DayNight parent.
- [ ] **Time format.** 24-hour time is forced in two places: the time picker (`HomeScreen.kt:97`) and the `"HH:mm"` formatter (`HomeScreen.kt:183`). Respect `DateFormat.is24HourFormat(context)` and the locale's time format.
- [ ] **Accessibility.** The font scale is capped at 1.3 (`Theme.kt:57`), and many users of a medication app need large text. Remove the cap and make the screens scroll instead.
- [ ] **Settings → About:** add the privacy policy link, the medical disclaimer and the app version.
- [ ] **Confirmation before RESET** (already in `app/TODO.md`).
- [ ] **Backup.**
  - `backup_rules.xml` and `data_extraction_rules.xml` are untouched templates, so everything gets backed up. That includes the device-specific ringtone URI and a stale first-dose time.
  - Alarms are not rescheduled after a restore.
  - Decide explicitly: either exclude those two keys or turn backup off. Make the Data safety answers match.
- [ ] **Separate notification channels** for "upcoming reminder" and "time to take it", so users can adjust each one independently.

---

## P3 — Build and maintenance

- [ ] **Enable R8.**
  - Set `isMinifyEnabled = true` and `isShrinkResources = true` (`app/build.gradle.kts:24`).
  - Add `-keepattributes SourceFile,LineNumberTable` so stack traces in Play Console stay readable (the AAB carries the mapping file).
  - This matters here because `material-icons-extended` is very large unless shrunk.
  - Smoke-test the minified release build on a real device.
- [ ] **Upgrade dependencies** along with AGP. Lint reports 28 outdated-dependency warnings (Compose BOM 2024.11, Room 2.6.1, …). Also migrate `kotlinOptions` to `kotlin { compilerOptions { … } }`.
- [ ] **Room schema.** Set `exportSchema = true` (`AppDatabase.kt:8`) and commit the schema directory to git. Every later schema change can then ship with a tested migration. Never use destructive migration, because the database holds users' medication plans.
- [ ] **Versioning.** Increase `versionCode` on every upload and pick a `versionName` scheme.
- [ ] **Tests.**
  - Replace the template tests. The instrumented test asserts the `com.example` package name, so it will fail after the rename.
  - Add unit tests for the schedule function from P1-5.
  - Test manually on Android 8, 12, 14 and 16, in each of these states:
    - phone locked
    - phone unlocked
    - another app in the foreground
    - in Doze (`adb shell dumpsys deviceidle force-idle`)
    - after a reboot
    - with each permission denied in turn
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
