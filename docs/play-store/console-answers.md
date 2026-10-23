# Play Console: suggested answers

Suggested answers for the *Policy → App content* section, based on what the code does on this branch.
Two answers rely on checklist items in P2 (the backup rules and the in-app About screen); finish those first.
Re-check the answers if the app gains network access, analytics, ads or accounts.

## Privacy policy

1. The contact address in `docs/privacy-policy.md` is `dallanoce.fd@gmail.com`; change it there if needed.
2. Enable GitHub Pages: repository *Settings → Pages → Deploy from a branch → `main` / `docs`*.
   Pages on a private repository needs a paid GitHub plan; otherwise host the page elsewhere.
3. The policy is then at `https://dallanoce.github.io/MedReminder/privacy-policy.html`.
   Enter that URL in *App content → Privacy policy*. The app links to the same URL from *Settings → About*.

## App access

All functionality is available without special access (no login).

## Ads

The app does not contain ads.

## Content rating

Category: *Utility, Productivity, Communication, or Other*. Answer "No" to the questions on violence,
sexuality, language, controlled substances, gambling and user interaction: the app only stores medication
names typed by the user and does not sell, promote or facilitate the purchase of medicines.

## Target audience and content

Target age group: **18 and over** only. The app is not designed for children, which keeps it out of the
Families policy requirements.

## News app / Government app / Financial features

No / No / "My app doesn't provide any financial features".

## Health apps declaration

Declare the medication/treatment-management category (wording in the form may differ, e.g. medication
management or treatment adherence). Do not tick categories that diagnose or monitor conditions.
The listing and the in-app About screen carry the "not a medical device" disclaimer the health policy asks for.

## Data safety

With the current code:

- The release build has **no INTERNET permission** and no third-party SDKs, so no data is transmitted off the device by the app.
- Android cloud backup is excluded in `data_extraction_rules.xml`; only device-to-device transfer is allowed.

Answers:

- *Does your app collect or share any of the required user data types?* → **No**.
- Because nothing is collected, the encryption-in-transit and data-deletion questions don't apply. There are no accounts, so no account-deletion URL is needed.

If you later add crash reporting, analytics or cloud backup, revisit this form before releasing that version.

## Full-screen intent declaration

*App content → Full-screen intent*: the app's core functionality is an **alarm** (dose alarms that must
interrupt the user at the scheduled time). Without the automatic grant, users on Android 14+ must enable
"Full screen notifications" themselves; the in-app setup screen guides them.

## Exact alarms

No declaration needed: the app uses `SCHEDULE_EXACT_ALARM` (user-granted), not `USE_EXACT_ALARM`.

## Foreground services

None used.
