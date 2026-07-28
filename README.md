# Shore Up

A full native Android client for the second-brain web app (Work/Organize/Mind/
MINDVERSE, bottom-tab navigation, REST API + Bearer auth), plus a Shield tab
that lets you set a daily time budget for specific apps and **hard-locks**
them — no skip button — once that budget is used up. App-limit tracking stays
on-device in a local Room database; everything else is synced live from the
account you log into.

Visual design is a direct port of the [second-brain](https://github.com/niloyinzamumulhoquecodesyrup-beep/second-brain)
web app's palette and type system: near-black `ink` backgrounds, serif
display headings over an `Inter`-style sans body, muted `mist` gray text,
and emerald/violet/gold accents with a soft radial "aura" glow.

## How it works

- **Dashboard** — every monitored app as a card with a progress bar (today's
  usage vs. its daily limit), colored emerald → gold → rose as it fills up.
- **Add a limit** — pick any installed app and set a daily budget in 5-minute
  steps.
- **Enforcement** — a foreground service polls Android's `UsageStatsManager`
  roughly every 1.5s for the current foreground app. Once its usage for the
  day reaches the configured limit, a full-screen overlay window is drawn
  on top of it: no skip button, back button is consumed, and it only clears
  when the app is no longer in the foreground or local midnight resets the
  counter. Leaving to the home screen is the only way out — the OS doesn't
  let any app intercept the home button/gesture, but the lock re-appears the
  instant the user reopens the same app.
- **Warning notification** — at 90% of the daily budget you get a one-time
  heads-up notification so the lock isn't a total surprise.
- **Reset** — automatic at local midnight, since the limit check always
  reads "usage since midnight" from the system's own tracking rather than
  a counter this app maintains itself.

## Project layout

```
app/src/main/java/com/secondbrain/lock/
  MainActivity.kt          Compose root: onboarding vs. dashboard/add-app
  LockApp.kt                Application class, sets up notification channels
  data/                      Room entity/DAO/DB, UsageStatsManager helpers,
                             installed-app listing
  ui/theme/                  Color/Type/Theme + reusable SbCard/SbLabel/
                             GradientText matching the web app's design system
  ui/screens/                Onboarding, Dashboard, Add-app Compose screens
  service/MonitorService.kt  Foreground service that polls usage and drives
                             the lock overlay
  service/LockOverlayManager.kt   Adds/removes the WindowManager overlay
  service/BlockingOverlayRoot.kt  View that swallows the BACK key
  receiver/BootReceiver.kt   Restarts the monitor service after a reboot
```

## Required permissions (requested via onboarding, no login involved)

| Permission | Why |
|---|---|
| `PACKAGE_USAGE_STATS` (Usage access) | See which app is foreground and how long it's been used today |
| `SYSTEM_ALERT_WINDOW` (Display over other apps) | Draw the hard-lock screen on top of a locked app |
| `POST_NOTIFICATIONS` | Show the 90%-used warning and the (silent, minimum-priority) "monitoring active" notification |

All three are OS-level toggles the user grants from Settings — there's no
sign-in of any kind.

## Opening the project

This was written directly as source (no Android Studio project wizard, no
network access in this environment to download Gradle or run a build), so
it hasn't been compiled here. To run it:

1. Open the `second-brain-lock/` folder in Android Studio (Koala or newer).
   Let it generate the Gradle wrapper jar on first sync if prompted, or run
   `gradle wrapper` once if you have a local Gradle install.
2. Sync Gradle — it will pull Compose BOM `2024.06.00`, Room `2.6.1`,
   Kotlin `1.9.24`, AGP `8.5.2`.
3. Run on a device or emulator with **API 26+**. The lock-screen overlay and
   usage-stats APIs need a real device (or emulator) — usage stats are
   unreliable on some emulator images, so a physical device is recommended
   for testing enforcement.
4. On first launch, grant the three permissions from the onboarding screen.
5. Tap **+**, pick an app, set a limit, and use that app past the limit to
   see the hard lock trigger.

## Known limitations

- Usage detection polls every ~1.5s rather than using `AccessibilityService`,
  so there can be a brief window (a couple seconds) after opening a
  limited app before the lock appears.
- Some OEM battery optimizers (Xiaomi/MIUI, Huawei, etc.) aggressively kill
  background foreground services; for reliable enforcement, exempt the app
  from battery optimization in system settings.
- The home button/gesture cannot be intercepted by any app (by OS design) —
  the lock is "hard" in that the *locked app itself* is unusable, not that
  the phone is unusable.
