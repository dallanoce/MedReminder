# Play Store listing texts

Ready to paste into Play Console → *Grow users → Store presence → Main store listing*.
Add Italian as a translation of the English (default) listing.

| Field | Limit | English (default) | Italiano |
|---|---|---|---|
| App name | 30 | MedReminder – Dose Alarms | Promemoria Medicine |
| Short description | 80 | Dose alarms that ring on time, even when your phone is locked. | Allarmi per le dosi che suonano in orario, anche a telefono bloccato. |

## Full description – English

MedReminder helps you take every dose of your daily therapy at the right time.

HOW IT WORKS
• Enter your therapy plan once: your medications in the order you take them, and the interval from one to the next.
• Each day, record when you take the first dose. MedReminder works out the time of every following dose.
• At each dose time an alarm rings and takes over the screen, even when the phone is locked or you are using another app, until you confirm you took it or snooze it for 5 minutes.
• An optional early reminder lets you know a few minutes before each dose.

PRIVATE BY DESIGN
• No account, no ads, no internet access.
• Your therapy plan stays on your phone and is never sent to anyone.

PERMISSIONS
MedReminder asks for notifications, alarms & reminders, full screen notifications and display over other apps. They are used only to make sure the dose alarm reaches you at the right time.

Available in English and Italian.

IMPORTANT
MedReminder is a reminder tool. It is not a medical device and does not diagnose, treat, cure or prevent any medical condition. Always follow the instructions of your doctor or pharmacist, and consult a healthcare professional for medical advice.

## Descrizione completa – Italiano

Promemoria Medicine ti aiuta a prendere ogni dose della tua terapia giornaliera all'ora giusta.

COME FUNZIONA
• Inserisci una volta il tuo piano terapeutico: i farmaci nell'ordine in cui li prendi e l'intervallo tra uno e il successivo.
• Ogni giorno indica quando prendi la prima dose: l'app calcola l'orario di tutte le dosi successive.
• All'ora di ogni dose suona un allarme a schermo intero, anche con il telefono bloccato o mentre usi un'altra app, finché non confermi di averla presa o la rimandi di 5 minuti.
• Un promemoria facoltativo ti avvisa qualche minuto prima di ogni dose.

PRIVACY
• Nessun account, nessuna pubblicità, nessun accesso a internet.
• Il tuo piano terapeutico resta sul telefono e non viene mai inviato a nessuno.

PERMESSI
L'app chiede i permessi per notifiche, sveglie e promemoria, notifiche a schermo intero e visualizzazione sopra altre app. Servono solo a far arrivare l'allarme della dose al momento giusto.

Disponibile in italiano e inglese.

IMPORTANTE
Promemoria Medicine è uno strumento di promemoria. Non è un dispositivo medico e non diagnostica, cura, guarisce né previene alcuna condizione medica. Segui sempre le indicazioni del tuo medico o farmacista e rivolgiti a un professionista sanitario per consigli medici.

## Other listing fields

- **App category:** Medical
- **Contact email:** required, shown publicly.
- **Website:** optional, e.g. `https://dallanoce.github.io/MedReminder/`
- **Privacy policy:** `https://dallanoce.github.io/MedReminder/privacy-policy.html` (see `docs/play-store/console-answers.md`)

## Graphics

Ready to upload, in `docs/play-store/graphics/`:

- App icon: `icon-512.png` (512 × 512, 32-bit PNG), the launcher icon's vectors.
- Feature graphic: `feature-graphic-en.png` for the default listing, `feature-graphic-it.png` for the Italian
  translation (1024 × 500, 24-bit PNG).
- Phone screenshots: `screenshots/en/` and `screenshots/it/`, upload in file order (Home, the alarm, the plan,
  Settings). 1080 × 1920, 24-bit PNG; Play rejects screenshots more than twice as tall as they are wide.

The icon and feature graphics are rendered from `graphics/src/` by `graphics/render.ps1`. The screenshots come from
the release build on the Android 16 emulator, with sample data and the system demo mode status bar.
