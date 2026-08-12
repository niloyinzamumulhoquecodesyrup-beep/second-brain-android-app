# CLAUDE.md

Guidance for working in this repo. For full feature/architecture detail see
[docs/app-overview.md](docs/app-overview.md); for the backend endpoint spec see
[docs/api-reference.md](docs/api-reference.md); for exact color/type tokens with real device
screenshots see [docs/design-system/index.html](docs/design-system/index.html).

**Naming note**: the Gradle root project is `"Shore Up"`, but the actual Android launcher label
(and what's rendered in the top bar wordmark) is `"Slay Task"`. The README describes an older,
Shield-only version of the app — treat `docs/app-overview.md` as current, not the README.

**What this is**: a native Android client (100% Jetpack Compose, no Retrofit — hand-rolled OkHttp)
for a Next.js "second-brain" personal knowledge-management + productivity web app, talking to the
same backend over a Bearer-token REST API. Four bottom-nav tabs — Work, Organize, Mind, Mindverse —
plus a fifth un-tabbed feature, Shield (app-usage hard lock).

## Repository singletons, not ViewModels

Every data domain (`TasksRepository`, `NotesRepository`, `MindRepository`, `PlannerRepository`,
`StatsRepository`, `MindverseRepository`, `RemindersRepository`, `ProfileRepository`,
`OnboardingRepository`, `MindQueueRepository`, ...) is a Kotlin `object`, not a ViewModel. Each
exposes `StateFlow`s directly, collected via `collectAsState()` in composables, and follows the same
shape:

- `restore()` — hydrate from local disk cache (`LocalCache`, a generic Room-backed `id → json`
  blob store).
- `refresh()` — hit the network, then update both the `StateFlow` **and** the disk cache.
- One method per mutation (`create`, `setDone`, `moveToPara`, ...) — not a generic "update" bag.

When adding a new repository, match this shape rather than reaching for a ViewModel or a different
state-management pattern.

## Local-first cold start — never introduce a loading spinner where cache could show

`LockApp.onCreate()` runs, in order: `ApiClient.init` → `Overlays.init` → `LocalCache.init` →
`SyncQueue.init`, then `runBlocking { }`s `.restore()` across **nine** repositories
(`MindRepository`, `MindQueueRepository`, `MindverseRepository`, `NotesRepository`,
`PlannerRepository`, `ProfileRepository`, `RemindersRepository`, `StatsRepository`,
`TasksRepository`) *before* the first screen composes. This is deliberate: the very first frame
shows last-known-good cached data, not a blank/spinner state, even before any network call
resolves. Every screen then independently fires its own `refresh()` calls in a `LaunchedEffect`.

Corollary: if you're adding a new screen or repository, don't gate the first render on a network
call. Show cached/`restore()`d state immediately; let `refresh()` update it underneath the user.

## Theme colors — always a `get()`, never a cached `val`

Every color in `ui/theme/Color.kt` is a Kotlin property with a **getter** branching on
`SbThemeState.mode` (e.g. `val Ink950: Color get() = if (isLight) ... else ...`). Reading one
inside a composable is tracked by Compose's snapshot system and auto-recomposes when the theme
toggles.

**Never cache one of these in a top-level `val` or a `remember {}` without keying on the theme** —
it freezes at whichever theme was active on first read and silently stops updating when the user
toggles light/dark. Some token families change *hue*, not just lightness, between themes (e.g.
`Emerald400` is teal in dark mode but violet in light mode) — don't assume a dark value is "the
light value, just brighter."

## "Ported from web, and it says so"

A large fraction of the Kotlin source carries doc comments explicitly naming the web file being
mirrored (`pages/work.js`, `RoutinePlanner.js`, `TodayCards.js`, `lib/plannerDay.js`,
`InsightCards.js`, `PathDiagram.js`, `UserMenu.js`, etc.), often also calling out exactly where the
Android port deliberately diverges (a simplification, a fixed bug, an Android-specific constraint)
versus where it's a literal behavior port.

**Treat these comments as load-bearing.** A comment saying "matches web exactly, even though it
reads oddly" (e.g. `NudgesStrip`'s routine-suggestion decline calling `action=done` instead of
`dismiss`) means: don't "fix" it without first checking the web app and flagging the discrepancy to
the user. Bit-for-bit JS-compatible behavior (e.g. `NoteRow.kt`'s hash function deliberately using
`Long` + `0xFFFFFFFFL` masking to replicate JS's unsigned-32-bit truncation) is also intentional,
not an oversight.

## Offline mutations: network-first, narrow queueing

The pattern (most fully built out in `TasksRepository`, also used by `FocusPomodoro`'s activity
logging):

1. Try the real network call first.
2. On failure, check `ApiClient.isOffline()` (via `ConnectivityManager` capabilities).
3. **Genuine server error (4xx/5xx) → never queued.** Just surface it through the repository's
   `_error` `StateFlow`.
4. **Connectivity failure → queue it.** Apply the change optimistically to local state (marked
   `pendingSync = true`), persist it to `LocalCache`, and enqueue a `PendingOp` onto `SyncQueue`
   for later replay.

`SyncQueue` persists `List<PendingOp>` under one `LocalCache` key (`"pending_ops"`), not a
dedicated Room table. Replay (`SyncQueue.flush()`) walks the queue in `createdAt` order and stops
entirely at the first failure, leaving it and everything after it for the next attempt — so an
update can never apply before its own create has landed. A locally-created-then-deleted task (still
has its `local-` id prefix) never round-trips to the server at all; it's just dropped locally and
its pending create cancelled.

Known gap worth remembering: `SyncQueueWorker.doWork()` always reports `Result.success()` to
WorkManager regardless of whether the flush actually succeeded, so WorkManager's own retry/backoff
never engages — only a fresh `enqueue()` or the next app launch re-triggers a flush attempt.

Not every mutation is queue-eligible — e.g. `TasksRepository.setPieces()` (the focus session's
"break it into pieces" checklist) is explicitly left requiring connectivity, not offline-queued.
Don't assume every mutator follows the offline-queue path; check the specific repository method.

## Two design eras coexist — don't assume by component name

- **StreakAccent system (current)**: flat `#FB4F40` accent, tinted `StreakSurface`/`StreakCard`
  cards, `StreakIconChip` bullets, pill buttons, dot-and-line timeline rows. Lives **only** in
  `WorkScreen`, `StreakDetailScreen`, `AllTasksScreen`.
- **Emerald/`SbCard` base theme (older)**: everything else — Organize, Shield, Mind, Mindverse,
  `JournalScreen` — still runs this.

A screen sharing a component name with the Work family (e.g. a generic `Card` or list row) is not
necessarily using the current design language. Verify by grepping the actual component usage in
that screen's file, not by assuming from `Components.kt`. See
[docs/design-system/index.html](docs/design-system/index.html) for exact tokens per era, and
`docs/app-overview.md` §4 for the full current/base-theme table.

## Nothing that scores the user renders above the fold

`WorkScreen`'s first screenful is an action (`TasksPanel`), not a number (P10). `RewardPanel`'s
full streak/level/badges card still exists and still backs `StreakDetailScreen` in full — it's
just no longer what `WorkScreen` mounts directly. `WorkScreen` instead mounts `StreakStrip`, a
one-line collapsed presentation (`🔥 5 days · Lv 4 · tap for stats`) built on the exact same
`RewardMath.from(stats)` computation `RewardPanel`/`StreakDetailScreen` use, so the collapsed and
full views can never disagree. Keep this ordering intent for any future addition to `WorkScreen`:
score-y content (streaks, levels, badges, completion counts) belongs low on the screen or behind
a tap, never as the first thing rendered.

## Other conventions worth knowing

- **Networking**: hand-rolled OkHttp + `kotlinx.serialization` typed DTOs (`ApiClient.getTyped`/
  `postTyped`/`putTyped`/`deleteRaw`), no Retrofit. `org.json` untyped helpers are kept alongside
  for a few legacy call sites (`FocusState`, `RoutineCache`) that predate the typed layer — not a
  pattern to extend.
- **Auth**: every request carries `Authorization: Bearer <token>` via an OkHttp interceptor reading
  `SecurePrefs` (`EncryptedSharedPreferences`). A 401 from anywhere clears the token and snaps the
  whole UI back to login via `ApiClient.onUnauthorized`.
- **`focus/state` vs `activity/focus-state`** are two genuinely distinct backend endpoints (shared
  cross-device Pomodoro session record vs. a lighter live do-not-disturb flag) that must be kept in
  sync manually if either changes — easy to conflate, called out explicitly in
  `docs/api-reference.md`.
- Before assuming a backend endpoint is called from native code, check `docs/api-reference.md`'s
  ✅ markers (verified by grep against `ApiClient.kt`/`data/repo/*`) rather than assuming a section
  is wired up just because it exists on the backend.
