# Releasing MedReminder to Google Play

## One-time setup: upload key

Google Play signs the app that users install (Play App Signing, mandatory for new apps).
You sign each upload with your own **upload key**. Create it once and keep it outside the repository:

```bash
keytool -genkeypair -v -keystore ../keys/medreminder-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.example` to `keystore.properties` in the project root and fill in the
path, alias and both passwords. `keystore.properties`, `*.jks` and `*.keystore` are git-ignored.

Back up the `.jks` file and its passwords somewhere safe (e.g. a password manager). If the upload key
is lost, it can be reset through Play Console support, but releases are blocked until then.

## Every release

1. Increase `versionCode` (and update `versionName`) in `app/build.gradle.kts`.
   Play rejects an upload whose `versionCode` was already used.
2. Build the App Bundle:

   ```bash
   ./gradlew bundleRelease
   ```

   The signed bundle is `app/build/outputs/bundle/release/app-release.aab`.
3. Upload it in Play Console → *Test and release* → the track you want (internal / closed / production).

On the first upload, Play Console asks you to confirm Play App Signing: keep the default
("Let Google manage and protect your app signing key").

## First release on a personal developer account: closed test

Personal accounts created after 13 November 2023 can only apply for production access after a closed test
with **at least 12 testers opted in for 14 consecutive days**.

1. Play Console → *Test and release → Testing → Closed testing* → create a track and upload the first `.aab`.
2. Add the testers' Google account emails (an email list or a Google Group) and share the opt-in link.
3. Keep at least 12 testers opted in for 14 days; ask them to actually use the app (set a plan, let alarms ring)
   and send feedback. Fix what they report with new uploads to the same track.
4. After 14 days, *Dashboard → Apply for production* and answer the questions about the test.
