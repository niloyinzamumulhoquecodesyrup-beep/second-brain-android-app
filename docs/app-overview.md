# Shore Up / "Slay Task" — Complete App Reference

This is the single comprehensive reference for the native Android client: every feature, every
screen, the design system, and the technical architecture underneath. Where a narrower doc already
covers a topic in more depth, this file links out to it instead of duplicating it
([docs/api-reference.md](api-reference.md) for the full backend endpoint spec,
[docs/design-system/index.html](design-system/index.html) for exact color/type tokens).

**A naming note, since it's confusing in the codebase itself:** the Gradle root project is named
`"Shore Up"` ([settings.gradle.kts](../settings.gradle.kts)) and the top-level [README.md](../README.md)
describes the app under that name — but the *actual* Android launcher label is
`"Slay Task"` (`app/src/main/res/values/strings.xml`'s `app_name` string), which also matches what's
rendered in the in-app top bar wordmark ([TopBar.kt](../app/src/main/java/com/secondbrain/lock/ui/nav/TopBar.kt))
and the title of the design system doc ("Slay Task / design"). The README also still describes an
older, single-feature version of the app (just the Shield app-lock feature) predating the
Work/Organize/Mind/Mindverse tabs documented below — treat this file, not the README, as current.

**What the app fundamentally is:** a full native Android client for a "second-brain" personal
knowledge-management + productivity web app (a Next.js product, referenced throughout the Kotlin
source's own comments as `pages/*.js`, confirming this is a deliberate port), talking to that same
backend over a REST API with Bearer-token auth. It's built around four bottom-nav tabs — **Work**
(tasks/focus/routines/streaks), **Organize** (PARA-method notes), **Mind** (AI-derived insights
about the user's own knowledge/behavior), **Mindverse** (cross-account chat/voice/video rooms,
branded "Mindcord") — plus a fifth, deliberately un-tabbed feature, **Shield**, which is the app's
original/founding feature: a hard app-usage lock with no skip button.

---

## 1. Tech stack & build

| | |
|---|---|
| Package / applicationId | `com.secondbrain.lock` |
| Gradle project name | `"Shore Up"` (stale vs. actual app name "Slay Task" — see naming note above) |
| Language | Kotlin 1.9.24, JVM target 17 |
| Build tooling | AGP 8.5.2, Gradle 8.7, KSP 1.9.24-1.0.20 |
| UI toolkit | 100% Jetpack Compose — Compose BOM `2024.06.00`, Material 3 + `material-icons-extended`, `navigation-compose:2.7.7` |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |
| Local DB | Room `2.6.1` (KSP codegen) |
| Background work | `androidx.work:work-runtime-ktx:2.9.0` (WorkManager) |
| Home-screen widget | `androidx.glance:glance-appwidget:1.1.1` |
| Networking | OkHttp `4.12.0` (hand-rolled REST client, no Retrofit) |
| Serialization | `kotlinx-serialization-json:1.6.3` (typed DTOs) + `org.json` (legacy untyped helpers, kept for FocusState/RoutineCache call sites that predate the typed layer) |
| Encrypted storage | `androidx.security:security-crypto:1.1.0-alpha06` (`EncryptedSharedPreferences`) |
| Prefs | `androidx.datastore:datastore-preferences:1.1.1` (alongside plain `SharedPreferences` for some stores) |
| Images | Coil `2.6.0` (`coil-compose`), wired to reuse `ApiClient`'s authenticated OkHttp client |
| Blur/glass effects | `dev.chrisbanes.haze:haze:1.0.0` — **pinned**, not latest: newer releases require compileSdk 36/AGP 8.9.1+, ahead of this project |
| Voice/video | `io.getstream:stream-webrtc-android:1.1.1` — GetStream's maintained repackage of Google's `org.webrtc.*` AAR (Google's own `org.webrtc:google-webrtc` was pulled from Maven years ago; same API surface) |
| Analytics | Firebase Analytics (`firebase-bom:33.1.2`) |
| Backend base URL | `https://second-brain-pi-six.vercel.app` (overridable via `-PSB_BASE_URL=`) |
| Realtime backend | Supabase Realtime — URL/anon key baked into `BuildConfig` (`SB_SUPABASE_URL`/`SB_SUPABASE_ANON_KEY`), overridable the same way. The anon key is intentionally public (RLS makes it read-only), same exposure as it already has embedded in the web app's client bundle |

### Permissions (`AndroidManifest.xml`)

| Permission | Feature it's for |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | all backend calls; the latter also drives `ApiClient.isOffline()` for the sync queue |
| `PACKAGE_USAGE_STATS` (special, granted via Settings) | Shield: see which app is foreground and its usage-today |
| `SYSTEM_ALERT_WINDOW` | Shield: draw the hard-lock/cooldown/focus-pill overlays |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | `MonitorService` (declared subtype `"app-usage-lock-enforcement"`) |
| `RECEIVE_BOOT_COMPLETED` | restart `MonitorService` + reschedule the wake alarm after reboot |
| `POST_NOTIFICATIONS` | 90%-budget warning, monitoring-active notification, wake alarm |
| `QUERY_ALL_PACKAGES` | enumerate installed apps in Shield's "add app" picker |
| `USE_FULL_SCREEN_INTENT` | the morning wake-alarm's full-screen launch over the lock screen |
| `VIBRATE` | haptic feedback (gesture ticks, long-press confirms) |
| `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Mindcord voice/video calls |

### Declared components

- **Activities:** `MainActivity` (`singleTask`, the entire app), `WakeFlowActivity` (`excludeFromRecents`, the morning alarm flow)
- **Services:** `MonitorService` (foreground, usage polling), `LockAccessibilityService` (`typeWindowStateChanged` only, `canRetrieveWindowContent="false"`)
- **Receivers:** `BootReceiver` (`ACTION_BOOT_COMPLETED`), `WakeAlarmReceiver`, `SectographWidgetReceiver`

---

## 2. Architectural patterns used everywhere

A handful of conventions repeat across nearly every feature area — worth naming once here rather
than re-explaining per screen:

- **Repository singletons (`object`), not ViewModels.** Every data domain (`TasksRepository`,
  `NotesRepository`, `MindRepository`, `PlannerRepository`, `StatsRepository`, `MindverseRepository`,
  `RemindersRepository`, `ProfileRepository`, `OnboardingRepository`, `MindQueueRepository`) is a
  Kotlin `object` exposing `StateFlow`s directly, collected via `collectAsState()` in composables.
  Each follows the same shape: `restore()` (hydrate from local disk cache), `refresh()` (hit the
  network, update the flow **and** the disk cache), and one method per mutation.
- **Local-first cold start.** `LockApp.onCreate()` calls `LocalCache.init()` then `runBlocking`s
  `.restore()` across nine repositories *before* the first screen composes — so the very first frame
  shows last-known-good cached data, not a blank/loading state, even before any network call
  resolves. Every screen then independently fires its own `refresh()` calls in a `LaunchedEffect`.
- **Generic disk cache (`LocalCache`/`CacheEntry`).** A trivial Room table (`cache_entry`: `id` →
  `json` blob) backs a typed `LocalCache.load<T>(key)`/`save<T>(key, value)` wrapper used by nearly
  every repository's `restore()`/`refresh()` pair. `LocalCache.clearAll()` runs on logout so a
  different account never sees stale cached data from the previous session.
- **Offline-first mutations, but only for genuine connectivity failures.** The pattern (most fully
  built out in `TasksRepository`, also used by `FocusPomodoro`'s activity logging): try the real
  network call first; on failure, check `ApiClient.isOffline()` (via `ConnectivityManager`
  capabilities); if it's a **real** server error (4xx/5xx), just surface it through an `_error`
  flow — nothing gets queued. If it's **connectivity**, apply the change optimistically to local
  state (marked `pendingSync = true`), persist it to `LocalCache`, and enqueue a `PendingOp` onto
  `SyncQueue` for later replay. See §9 for the full sync-queue mechanics.
- **"Ported from web, and it says so."** A very large fraction of the Kotlin source carries doc
  comments explicitly naming the web file being mirrored (`pages/work.js`, `RoutinePlanner.js`,
  `TodayCards.js`, `lib/plannerDay.js`, `InsightCards.js`, `PathDiagram.js`, `UserMenu.js`, etc.) and
  calling out exactly where the Android port deliberately diverges (a simplification, a fixed bug,
  an Android-specific constraint) versus where it's a literal behavior port. This is a genuinely
  useful signal when reading the code: a comment saying "matches web exactly, even though it reads
  oddly" means don't "fix" it without checking the web app first.
- **Bit-for-bit JS-compatible hashing where it matters.** E.g. `NoteRow.kt`'s `iconForNote(id)`
  deliberately widens its running hash to `Long` and masks `0xFFFFFFFFL` every iteration specifically
  to replicate JavaScript's per-step unsigned-32-bit truncation — Kotlin's native signed-`Int`
  overflow would diverge the moment the hash goes negative.

---

## 3. App shell: navigation, auth, onboarding

### 3.1 Top-level state machine (`MainActivity.kt`)

A single `ComponentActivity` wrapping one `RootApp()` composable. There is **no separate splash
screen** — the decision tree is:

1. `authToken` seeded from `SecurePrefs.getToken()`. A `LaunchedEffect` subscribes to
   `ApiClient.onUnauthorized` (emitted by the OkHttp interceptor on any HTTP 401) and nulls the
   token immediately, snapping the whole UI back to login.
2. If `authToken == null`: shows `LoginScreen` or `RegisterScreen` (toggled by local state) — this
   *is* the onboarding gate; the first frame is a login form if no token is cached.
3. Once authenticated: builds the `NavHostController`, a shared `topBar` composable (threaded into
   every tab so it scrolls with content, not pinned via `Scaffold`'s slot), and quick-add sheet
   state, then renders a `Scaffold` with `BottomBar` + `AppNavHost`.
4. `onLogout` (shared by the top bar's menu and account deactivation): `ApiClient.logout()` →
   `ProfileRepository.clear()` → `LocalCache.clearAll()` → null the token.

**Registration always chains into login**, since `POST /api/auth/register` only sets a cookie and
returns `{email}` with no bearer token — `RegisterScreen`'s success handler calls
`ApiClient.register(...).mapCatching { ApiClient.login(...).getOrThrow() }` to actually obtain the
token the native client persists.

### 3.2 `LockApp.kt` — process startup sequence

1. `ApiClient.init` → `Overlays.init` → `LocalCache.init` → `SyncQueue.init`
2. `runBlocking { }` restoring 9 repositories from local cache (justified inline as "cheap — a
   handful of local reads")
3. `SyncQueue.scheduleFlush()` — replay anything queued last session
4. Coil `ImageLoader` wired to `ApiClient`'s authenticated OkHttp client (so `AsyncImage` hitting
   e.g. `/api/auth/avatar` "just works")
5. Theme restored from `SecurePrefs` into the global `SbThemeState`
6. `SectographUpdateWorker.schedulePeriodic()` (the home-screen widget)
7. Three notification channels created: `monitor_service` (min importance), `usage_warning`
   (default importance, custom sound), `wake_alarm` (high importance, bypasses DND, alarm sound)

### 3.3 Navigation graph (`AppNavHost.kt`, `Destinations.kt`)

Start destination: `work`. Full route table:

| Route | Screen | Notes |
|---|---|---|
| `work` | `WorkScreen` | pushes to `streak`/`tasks` |
| `organize` | `OrganizeScreen` | stores a `"tag"` value on its own `savedStateHandle` so a tag tapped from deep inside a note (possibly several link-hops in) can pop back pre-filtered |
| `mind` | `MindScreen` | |
| `mindverse` | `MindverseScreen` | pushes to `mindverse_room` |
| `mindverse_room` | `MindverseRoomScreen` | full-screen call takeover, no nav args (reads `MindverseRepository.currentRoom` directly); bottom bar is hidden while this route is on top |
| `shield` | `MainActivity`'s inline `SettingsFlow` | see §7 — excluded from the bottom bar |
| `account_settings` | `AccountSettingsScreen` | reached only from the top bar's avatar menu |
| `streak` | `StreakDetailScreen` | |
| `tasks` | `AllTasksScreen` | |
| `note/{noteId}` | `NoteDetailScreen` | recursive — notes link to other notes, re-pushing this same route |

`Destination.bottomBarOrder = [WORK, ORGANIZE, MIND, MINDVERSE]` — **Shield is deliberately excluded**
so 4 tabs split evenly around the raised center "+", and is reached instead via a floating shield
icon (§3.4) or, per the design doc, the account menu area.

### 3.4 Bottom nav bar (`BottomBar.kt`) — the notched-pill shape

Four tabs (2 left + a reserved center gap + 2 right) inside a Material3 `NavigationBar`. Selected
color is `StreakAccent`, unselected `Mist300`, indicator fully transparent (deliberately no
selection "pill," matching the web app).

**The raised "+" quick-add button** floats independently (56dp circle, `StreakAccent`, white
plus-icon), centered over the bar's notch, opening `QuickAddChooserSheet`. **The Shield icon** is a
second independent floating overlay, top-right, a plain themed image icon (no background chip) —
hidden entirely when already on the Shield route.

**Tab-tap logic** first tries `popBackStack(route, inclusive=false)` (pop straight to an
already-visited tab's stack entry), falling back to `navigate(route){popUpTo(start){saveState=true}; launchSingleTop=true; restoreState=true}` only if there's no entry to pop to — worked around a
real bug where plain `navigate()` silently no-ops when called from a screen pushed on top of a tab
whose *id* happens to collide with the start destination's id.

**The notch shape (`NotchedTopBarShape`)** is a custom `Shape` — square bottom/sides, rounded top
corners (24dp), and a notch built from **three tangent-continuous arcs** (fillet → main notch arc →
mirrored fillet) so there's no visible seam:
- The main notch arc is **concentric with the "+" button itself** (same center point) — the only
  way to guarantee a uniform gap to the button's edge everywhere along the arc, not just directly
  below it.
- `dx = sqrt(r1² + 2·r1·f)` (r1 = notch radius, f = fillet radius) and
  `handoffAngle = atan2(f, dx)` — the standard "circle tangent to a line and to another circle"
  construction, computed so each arc pair shares both a point *and* a tangent direction where they
  meet (the code states this was verified algebraically, not just eyeballed).

### 3.5 Top bar (`TopBar.kt`)

Wordmark "Slay Task" → spacer → theme toggle (sun/moon, flips `SbThemeState.mode`, persists via
`SecurePrefs`) → 32dp circular avatar button. Avatar shows, in priority order: real photo (Coil,
`?v=avatarVersion` cache-busted) → initials-only circle → generic account icon while profile is
still loading. Tapping opens a dropdown: **Settings** → `account_settings`, **Log out** (Rose400).
Not pinned to `Scaffold`'s slot — deliberately the first item inside each tab's own scrolling
column, so it scrolls away with content. Also runs a background 60s `RemindersRepository.refresh()`
poll for as long as it's composed (feeding the Work tab's `NudgesStrip`, which reads passively).

### 3.6 Quick-add sheets (`QuickAddSheet.kt`)

Two-step bottom sheet flow from the "+" button: **chooser** ("Create a task" / "Capture") →
either `QuickAddTaskSheet` (title + optional due date/time/duration chips, creates via
`TasksRepository.create` then a follow-up `reschedule()` if a time was picked — the create endpoint
only accepts title/due-date/note, so a time requires a separate PUT) or `CaptureSheet` (from the
Organize package — full note/link/idea capture, see §5.2).

### 3.7 Auth screens

- **`LoginScreen.kt`**: email + password, "Use the same account you use on the web," submit disabled
  until both fields are non-blank.
- **`RegisterScreen.kt`**: email + password + confirm, client-side validated (≥8 chars, must match)
  *before* ever hitting the network, mirroring the same rule the backend enforces server-side.
- Both share `AuthHeader`/`authFieldColors()` styling helpers.
- **Auth endpoints**: `POST /api/auth/mobile` (login, returns `{token,email}`),
  `POST /api/auth/register` (register, `{email}` only — no token). Logout is purely local
  (`SecurePrefs.clearAuth()`) — there is no server-side logout call.

### 3.8 Session persistence

`SecurePrefs` wraps `EncryptedSharedPreferences` (AES256-GCM master key, AES256-SIV key encryption,
AES256-GCM value encryption). The token is written once at login, read on every app launch to
decide login-vs-main-app, and attached as `Authorization: Bearer <token>` to every HTTP request by
an OkHttp interceptor. It's invalidated only by explicit logout/deactivation, or automatically by
that same interceptor whenever any response comes back 401 (which also triggers the
`onUnauthorized` flow `MainActivity` listens for).

### 3.9 Onboarding — two different, non-overlapping things

**There is no general "who are you" profile-setup screen wired into the live UI**, despite a full
`OnboardingRepository` + `GET/POST /api/onboarding/{status,complete}` + a 7-option persona list
existing in the data layer — nothing currently calls it; it reads as parked/legacy infrastructure.

**What *is* wired up and user-facing is Shield's permission walkthrough** (`OnboardingScreen.kt` +
`MainActivity`'s inline `SettingsFlow`), shown the first time the Shield tab opens if
`!Permissions.allGranted()`. Four steps, three of them gating "Continue":

1. **Usage access** (required) — `Settings.ACTION_USAGE_ACCESS_SETTINGS`
2. **Display over other apps** (required) — `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
3. **Notifications** (required, API 33+ only) — runtime `POST_NOTIFICATIONS` dialog
4. **Instant blocking / Accessibility** (optional, doesn't gate Continue) — shows an explicit
   rationale dialog first (only reads foreground-app changes, never screen content/taps, can be
   turned off anytime) before opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`

Permission state is re-derived on every `ON_RESUME` (a `resumeTick` counter forces recomposition),
so checkmarks update live as the user returns from system Settings — but the screen deliberately
stays visible with checkmarks ticked until the user taps Continue, rather than auto-dismissing the
instant the last permission flips true in the background (an explicit fixed-bug comment). On
Continue: `MonitorService.start()` is called, and the flow reveals the Dashboard/AddApp/Settings
screens described in §7.

---

## 4. Design system & theming

Full color/type token reference lives in **[docs/design-system/index.html](design-system/index.html)**
— read directly from `Color.kt`/`Type.kt`/`Components.kt`, not reconstructed from memory, and
includes real device screenshots. Key facts worth knowing before touching any screen:

1. **Theme is manually switched, not OS-driven.** Every color is a Kotlin property with a getter
   branching on `SbThemeState.mode`. Compose's snapshot system means reading one inside a
   composable auto-recomposes on toggle — but **never cache one in a plain top-level `val`**, it
   freezes at whichever theme was active on first read.
2. **Some token families change hue, not just lightness, between themes.** "Emerald400" is teal in
   dark mode but violet in light mode; "Gold400" is pale champagne in dark but rust red in light.
   Don't assume a dark value is "the light value, just brighter."
3. **Two design eras coexist right now.** A newer `StreakAccent`-led system (flat `#FB4F40`, tinted
   `StreakSurface` cards, `StreakIconChip` bullets, pill buttons, dot-and-line timeline rows) exists
   **only** in the Work tab family (`Work`, `StreakDetailScreen`, `AllTasksScreen`). Every other
   screen — Organize, Shield, Mind, Mindverse — still runs the original Emerald-led base theme with
   plain `SbCard`. Don't assume a screen is "current" just because it shares a component name.

| Screen | Design status |
|---|---|
| Work / StreakDetailScreen / AllTasksScreen | ● current (StreakAccent system) |
| Organize | ○ base theme (predates the redesign) |
| Shield (DashboardScreen) | ○ base theme |
| Mind / Mindverse | ○ base theme |

Typography is a deliberate serif/sans split (`Type.kt`) — display sizes use a system serif standing
in for Cormorant Garamond (no embedded webfont, so the lock screen still renders fully offline).
`headlineLarge`/`titleLarge`/`bodyLarge` are defined but **not used anywhere in the Work family** —
don't reach for them when matching Work's look.

---

## 5. Organize tab — PARA-method notes

**PARA** = Projects / Areas / Resources / Archive, the personal-knowledge-management method this
whole tab implements. Every `Note` carries a literal `para` field (`inbox|project|area|resource|archive`,
`inbox` being an implicit pre-sort bucket), color-coded consistently: inbox=rose, project=emerald,
area=violet, resource=gold, archive=grey (`NoteRow.kt`'s `paraAccent()`).

### 5.1 Tab layout (`OrganizeScreen.kt`)

A single scrolling column (no internal tabs), explicitly a manual port of `pages/index.js`:
optional tag-filter chip → `ParaCubeView` → `GraduatedSection` → `PacketsSection` → `MindMapView` →
`GoalArrowChart` → `FieldInvestigationReport`.

### 5.2 Capture flow (`CaptureSheet.kt`)

A bottom sheet (title, 30-char capped with a live counter; multi-line content; 4-way PARA
`FilterChip` picker defaulting to **project**; optional source URL; optional CSV tags). **Deliberately
stays open after a successful save** (fields reset, a green confirmation appears) rather than
auto-closing — supports capturing several notes in a row; closed only by the X or back gesture.
`POST /api/notes`.

### 5.3 Note detail (`NoteDetailScreen.kt`) — view / edit / distill

Header: back arrow, "Distill" / "Edit" / "Delete" text actions (mutually exclusive modes). Loads
`GET /api/notes/{id}`, `.../links`, `.../related` in parallel.

- **View mode**: title, status pills (PARA bucket + Distilled/Graduated/Pinned), executive-summary
  card if present, raw content, tag chips, and a combined "Linked notes" card — **Links to/Linked
  from** (user-created typed links, capped 5 each + overflow) plus a **Related** list (AI-similarity
  matches, top 5, each showing a real `NN`% match percentage).
- **Edit mode**: plain form → `PUT /api/notes/{id}` (partial-update semantics — only non-null fields
  change).
- **Distill mode** — the note-refinement step: write an "Executive summary" →
  `PUT .../{id} {executiveSummary, distilled:true}`. Once `distilled == true`, a "Turn this into
  something real" section appears: **+ add task** (`TasksRepository.create(title, noteId=...)`,
  linking a task back to its origin note — repeatable) and **save as packet**
  (`POST /api/packets`, one-shot, button disables after use).
- **Delete**: confirm dialog → `DELETE /api/notes/{id}`.

### 5.4 The notes graph — "radial mind map" (`MindMapView.kt`)

Not a physics simulation (that's `ParaCubeView`'s job) — a **3-level radial tree**: a central "Notes"
root → one curved branch per PARA category, sized proportionally to note count → each note as a
leaf twig off its category's arc. This explicitly replaced an earlier "force-directed hairball" that
read as "a dense dot cluster, not a map you could read." Actual note-to-note relationships (typed
links + AI-inferred similarity) are drawn as thin curved cross-links *underneath* the tree — an
overlay, not the primary structure — with a toggle to hide AI-inferred connections.

- **Pan/zoom**: `detectTransformGestures`, zoom clamped `[fitZoom*0.35, fitZoom*14]`.
- **Auto-fit**: computed from actual bezier-curve bounding boxes (a branch's control-point bulge can
  extend past a straight endpoint-to-endpoint line), driven by `onSizeChanged` + `LaunchedEffect`
  rather than inside the gesture handler (the pointerInput coroutine can start before Canvas's first
  layout pass reports a size).
- **Tapered branch strokes**: Compose Canvas has no native variable-width stroke, so the curve is
  sampled at 14 segments and drawn as successive round-capped segments with linearly-interpolated
  width — faking a brush-stroke taper.
- **Radial layout algorithm**: each PARA category gets an angular sector proportional to its note
  count (6° gaps, starting at 12 o'clock, clockwise); leaves within a sector alternate between two
  radii so a dense category doesn't collapse into one unreadable ring.
- Endpoint: `GET /api/notes/graph` → `{nodes:[{id,title,para,tags,distilled}], edges:[{from,to,type,similarity}]}`.

### 5.5 The PARA card-stack (`ParaCubeView.kt`) — physics spin gesture

The tab's centerpiece interaction. Despite the filename, it's a **3D flippable card carousel** of 4
faces — Resources / Projects / Areas / Archive (**Inbox has no face here**; defaults to showing
Projects). Center face is fully interactive (2-column note-tile grid); neighbor faces "peek"
tilted/scaled/dimmed via `graphicsLayer` (`rotationY ±42°`, `scale` to 0.88, `alpha` to 0.55 —
approximating CSS `translateZ` depth, which Compose has no direct equivalent for) with their tiles'
click modifiers entirely **omitted** (not just disabled) so a touch falls through to the card's own
"tap to activate" handler.

**The "flick like a fidget spinner" gesture** — a continuous float `position` (not a discrete index),
via `detectHorizontalDragGestures`:
1. Live 1:1 finger tracking, with a **hand-rolled exponential-moving-average velocity** (`v = v*0.7 + inst*0.3`) — used *instead of* Compose's built-in `VelocityTracker`, which on-device testing found "reliably returning zero for otherwise-clean fast drags on this device."
2. On release: if the drag crossed <18% of a face-width **and** velocity is under 250px/s, it just springs to the nearest face (220ms tween, custom cubic-bezier easing).
3. Otherwise, `Animatable.animateDecay()` with `exponentialDecay(frictionMultiplier=1.1f)` — genuine screen-pixel friction, deliberately tuned low so a strong flick can **spin through several full loops** before landing (velocity clamped to 20,000px/s pre-decay).
4. A final snap-to-nearest locks it exactly onto the closest face.
5. **Every integer-boundary crossing** (a new face becoming "current") — during drag, coast, or settle — fires `FeedbackUtil.spinTick()`: a short tone + 8ms/amplitude-60 vibration, simulating a mechanical wheel's clicks.

Per-tile overflow menu: **Move to {other PARA bucket}** → `POST /api/para`, **Distill** (opens
`NoteDetailScreen`), **Graduate** (only shown if `!inbox && distilled`) → gates the next feature:

### 5.6 Graduated notes (`GraduatedSection.kt`)

"Graduated" = a note that's moved past active PARA management entirely — the terminal state of the
workflow **Capture → sort into PARA bucket → Distill (write summary) → Graduate**. `graduated: Boolean`
gates a note out of the PARA cube's own dataset (`refreshPara()` filters graduated notes out) and
into a separate `GET /api/notes?graduated=true` fetch. Read/browse only — a simple 2-column tile
grid, no further actions.

### 5.7 Packets (`PacketsSection.kt`)

A packet is **a distilled excerpt clipped from a note**, created via Distill's "save as packet"
action. The section itself was added specifically because packets previously had "no browsing UI at
all — they could be created but never listed anywhere." Hidden if empty; otherwise a dot-and-line
timeline list (title + one-line preview), tapping jumps back to the origin note (packets have no
detail screen of their own). `GET /api/packets`, creation via `POST /api/packets`.

### 5.8 AI insight visualizations (shared by Organize and Mind)

- **`GoalArrowChart.kt`** — renders `inferred_goal`-kind insights (the AI's read on what the user
  seems to be working toward) as a vertical central "spine" with alternating left/right notched
  ribbon banners, each linked to a numbered badge on the spine, terminating in a bullseye mark.
  Tapping a banner expands a details panel with the full summary + source citations.
- **`FieldInvestigationReport.kt`** — a paged "‹ i / N ›" carousel over `recommendation`-kind
  insights, whose body shape depends on the insight's `metadata`: a **dependency-graph "path"**
  view (levels computed by longest-path-from-a-root, with a cycle guard), a **concept card**
  (definition + contributor mini-avatars + related-concept pills), or a **mini bar chart** — plus
  always a "For you: {suggestion}" callout and a collapsible sources list.
- **`SourceRefsView.kt`** — the shared citation-list renderer both of the above use, plus
  `NoteDetailScreen`'s related-notes list. Handles polymorphic ref types (`note`/`mind_insight`/
  `resource`/generic stat) and deliberately types its numeric `value` field as a raw `JsonElement`
  (not `Double`) since the backend genuinely sends booleans for some stat refs — a strict-Double
  typing previously crashed the whole insights parse.

### 5.9 `NotesRepository.kt` — CRUD & offline pattern

Three `StateFlow`s (`paraNotes`, `graduatedNotes`, `packets`), each with the standard
restore/refresh/cache pattern (§2) — **except** a tag-filtered fetch is treated as transient UI
state and deliberately *not* persisted to disk (only the untagged base dataset is cached). Notably,
**PARA-bucket moves have their own dedicated endpoint** (`POST /api/para`) rather than going through
the generic note-update path used for graduation/editing/distillation — moving between buckets is a
distinct backend operation from a field update.

---

## 6. Work tab — tasks, focus, routines, streaks

### 6.1 Tab composition (`WorkScreen.kt`)

Explicitly "mirrors `pages/work.js`": `NudgesStrip` → `RewardPanel` → `TasksPanel` →
`RoutinePlanner`, plus a `CompletionCelebration` overlay. On first composition, five refreshes fire
in parallel (stats, tasks, routines, planner-today, mind-queue) while cached data already shows.

**The completion/celebration funnel (`handleCompletion(type)`)** — every completion (task done,
routine done, focus session finished) passes through this single function:
1. Always bumps local stats first (unconditionally, before any celebration decision — fixes a past
   bug where a bonus roll used to skip the real stats bump).
2. Checks for a level-up (comparing `RewardMath.level()` before/after) → if so, shows a "Level up!"
   celebration (longer-lived, 2s).
3. Else, a **15% random roll** shows one of 6 rare "surprise bonus" lines — a classic
   variable-ratio reinforcement schedule, explicitly named as such in the comments.
4. Else, a generic completion line from a fixed pool (task vs. focus have separate pools).

### 6.2 The "Today" timeline (`TasksPanel.kt`)

The largest, most logic-dense file in the app. Merges real tasks with routine occurrences into one
sorted `TodayItem` list (`buildTodayItems`, explicitly ported from `lib/plannerDay.js`'s
`dayEntries()`):

- A routine occurrence is either **materialized** (a real `planner_blocks` row exists for today) or
  purely **virtual** (computed client-side from the recurring schedule, no server row yet) — a
  materialized block always wins, so completing/dismissing today's occurrence never mutates the
  underlying recurring routine.
- **Overnight-wrap handling**: a routine like "Sleep 23:00–07:00" is only treated as "still running"
  in the early morning if it actually ran *yesterday* too (checked against `routine.days`) — so a
  Monday-only routine doesn't misleadingly appear in-progress on Tuesday morning.
- **"Next up" pointer**: if nothing is currently active, the next not-yet-done item gets the same
  "current" accent-dot styling, so the timeline never looks fully idle between blocks.
- A `LaunchedEffect` ticks every 30s to advance the "now" indicator without unrelated recomposition.

**Long-press AI task-breakdown** (long-press a task row → haptic tick → `POST /api/tasks/breakdown`):
- If the server reports 0 breakdowns remaining today (a daily quota), shows that message with no
  network call.
- On success, suggested subtasks are stacked sequentially in time — the first starts right after the
  parent's own scheduled end (or "now" if the parent is undated), each next one starting where the
  previous ended. A `fitsToday` flag catches any suggestion pushed past midnight (the tasks table's
  `start_min` DB check-constraint is `[0,1439]`) — shown, but its "+" is swapped for a "pick another
  day" calendar/clock icon instead of sending an invalid value.
- **Adding a suggestion** creates the task, reschedules it into its slot, then **cascades a
  reschedule of every other still-open task today at or after that slot**, shifting each later by
  the new subtask's duration — implementing "the rest of the task time will be adjusted." Anything
  that would shift past midnight is left alone rather than sent an invalid value.
- A suggestion that doesn't fit today opens the date/time pickers (today greyed out) instead.

**Cross-device focus polling**: while no focus dialog is locally open, polls `GET /api/focus/state`
every 15s — if another client (e.g. the web app) has an active session, tapping the matching task's
play button resumes that session directly instead of showing the duration picker.

**`TimelineRow`** (the shared row component): continuous dot-and-line timeline, 4-color background
cycle so consecutive rows read as distinct without borders, 🔔/🔁 leading icon, done/highlighted/
dimmed states, optional play/toggle/delete/long-press affordances.

### 6.3 Focus / Pomodoro (`FocusPomodoro.kt`)

A full-screen `Dialog` (deliberately takes over the whole screen, not a modal). Two-state machine:

- **PICK**: task title, duration chips (15/25/45min, default 25). Starting calls
  `POST /api/focus/state`; on success also fires `POST /api/activity/focus-state` (a *live*
  do-not-disturb signal the server's reminders evaluator reads — distinct from logging completed
  minutes). **Offline fallback**: if the start POST fails specifically due to no connectivity, the
  timer still runs as a **local-only session** (`sessionId = null`), just without cross-device
  sync until it completes.
- **RUNNING**: a `Canvas`-drawn circular progress ring + `mm:ss` countdown. If it's a real server
  session, a second poll every 8s checks for cross-device drift (another client stopped/replaced the
  session) and auto-dismisses the dialog if so, rather than silently counting down against reality.

**On natural completion**: logs the full duration, clears the server-side active signal, plays a
completion tone, bumps stats, and hands off to `WorkScreen`'s celebration funnel.

**Ending early**: computes elapsed minutes from wall-clock (not the ticked state, so it's correct
even before the first tick lands); credits minutes if ≥1 full minute elapsed but explicitly does
**not** count as a completed session (no celebration) since the pomodoro wasn't seen through; also
cancels the server session — explicitly to prevent `MonitorService`'s background poll from later
reporting the abandoned session as "completed" at its original end time, which would double-credit
minutes and could re-trigger the Shield focus-lock.

**A notable implementation detail**: the activity-logging call runs on a *fresh*
`CoroutineScope(Dispatchers.Default)`, not the composable's own scope — because every caller
dismisses the dialog immediately after invoking it, which would tear down a composable-scoped
coroutine mid-flight and lose both the network attempt and its offline-queue fallback.

**"Break it into pieces"**: a mini checklist persisted on the task's own `pieces` field
(`PUT /api/tasks/:id`) — not offline-queued.

**Lock tie-in**: starting a focus session here is the same state `MonitorService`'s poll reads to
drive Shield's `blockDuringFocus` lock overlay elsewhere in the app.

### 6.4 Recurring routines (`RoutinePlanner.kt`)

Ported field-for-field from `RoutinePlanner.js`. First-run: a free-text prompt ("What do you do on a
regular basis?") → `POST /api/planner/prompts` → fed to an async backend "mind cycle" that later
proposes routines (not parsed client-side). A row of **starter routine chips** (Sleep 23:00, Morning
yoga 07:00, Breakfast 08:00, Deep work 09:00 weekdays-only, Lunch 13:00, Evening reading 21:00) —
any already matching an existing routine title is filtered out.

Each routine row: category dot, title, a violet "from your brain" tag if `source == "cycle"`
(AI-generated rather than user-created), inline time/duration editor, 7 day-toggle chips (blocked
from removing the last day), pause/resume toggle, delete. New-routine form: title, category chips
(7 fixed categories with fixed colors — also reused by the Sectograph widget), time/duration, days.

### 6.5 Streak & rewards (`RewardPanel.kt` + `StreakDetailScreen.kt`)

`RewardMath` is the shared, pure-client-side gamification engine (no server calls) both files read:

- **Leveling curve**: a seed table `[0,3,8,15,25,40,60,85,120]` extended geometrically
  (`next = last + max(round((last-prev)*1.3), 25)`) out to 24 levels — **the same table is reused
  across every dimension** (streak days, notes, tasks done, focus sessions, raw focus *minutes*, not
  converted to hours), matching the web app verbatim.
- **Streak computation**: unions active-day sets across captures/focus-sessions/tasks-done (any
  activity counts) into one set; if today has no activity yet, counting starts from yesterday
  instead (so an outstanding streak doesn't zero out mid-day before you've done anything).
- **11 badges**, checked in a fixed order (first unsatisfied = "next"): first/ten captures,
  first/ten tasks, first focus/focus_builder(5)/deep_focus(25)/focus_hour(60min)/focus_marathon(300min),
  streak_3, streak_7.
- **Gauges**: 5 metrics, each targeted against the **median of the last 7 days** for that metric
  (floor 1) — a "typical day" goal rather than a fixed number.
- A time-bucketed (not random, not persisted) rotating headline phrase — deterministic per 6-hour
  window (`now / 6h % list.size`), and a day-of-year-bucketed daily quote.
- **Activity heatmap**: 5-week (35-day) grid, 4 activity levels, level-0 cells get a hand-drawn
  diagonal-stripe texture rather than flat color.

`StreakDetailScreen` presents all of the above in full: a ring gauge, a big milestone-progress card,
a 2×2+1 category-tile grid, the 35-day heatmap, this-week/this-month summary tiles, and the full
badge list.

### 6.6 "See all" (`AllTasksScreen.kt`)

Four segmented tabs: **Today** (same merged timeline, untruncated titles, no cap — plus a "Your
brain suggests" pill row of AI-queued task suggestions from `GET /api/mind/queue`, accept/dismiss
via `POST /api/mind/queue/:id/answer`), **This week**, **This month**, **More tasks** (Overdue +
Drafts). A separate always-visible **Done** section below the tabs. No completion celebration shown
here (explicitly reserved as a `WorkScreen`-only flourish).

### 6.7 Nudges (`NudgesStrip.kt`)

Reads `RemindersRepository.reminders`, shows exactly one due reminder at a time (locally dismissed
ones tracked in-memory only), with a "+N more waiting" line if more exist. Two variants:
`routine_suggestion` (AI-proposed routine: "Yes, add it" / "Not now" — the decline still calls
`action=done`, not `dismiss`, matching the web app literally even though the code admits "it reads a
little oddly") vs. everything else (✓ Done / Snooze 10m / conditionally Open / Learn more). All
actions go through `PATCH /api/reminders/:id` followed by a full refresh.

### 6.8 Sectograph home-screen widget (`widget/Sectograph*.kt`)

A Glance widget drawing a 24-hour circular "sectograph" — a clock face with colored arc segments
per active routine plus a hand pointing at the current time. Since Glance widgets compile down to
`RemoteViews` (no live Canvas), the whole face is **pre-rasterized to a plain Bitmap** ahead of time
by `SectographRenderer` (reading cached routines from Room — no network call at render time) and
displayed as a static `Image`. `SectographUpdateWorker` refreshes the cache + redraws every 30
minutes (periodic WorkManager job), plus an immediate-update path callable right after anything
changes what the widget should show.

### 6.9 Repositories

`TasksRepository` (offline-queue-backed, see §9), `PlannerRepository` (today's occurrences +
routine definitions, no offline queue), `RoutineRepository` (a **different, class-based** repo,
Room-backed, used by both Shield's schedule-block check and the widget), `StatsRepository` (holds
the single `/api/stats` snapshot plus **local-only optimistic bump methods** for instant UI feedback
before any server round-trip), `RemindersRepository`.

---

## 7. Shield — the app-usage hard lock

The app's original/founding feature (see the old README) — reachable via the floating shield icon,
not a bottom tab.

### 7.1 Dashboard (`DashboardScreen.kt`) / Add app (`AddAppScreen.kt`)

Dashboard: one row per monitored app — real icon (with a fallback glyph if the app was later
uninstalled), a traffic-light status dot (emerald/gold/StreakAccent for safe/near-limit/locked),
usage text, an optional "capped at N opens/day" line, a progress bar, and two per-app toggles:
**"Block during schedule windows"** and **"Block during focus sessions"**. Add-app: search installed
apps → set a daily minute budget (5-min steps, 5–480 range) and an optional opens/day cap (1–100).

### 7.2 The Room schema (`AppLimit`)

`app_limits` table: `packageName` (PK), `appName`, `dailyLimitMinutes`, `enabled`,
`lastWarnedEpochDay` (dedupes the 90%-warning), `openCountLimit`, `blockDuringSchedule`,
`blockDuringFocus`. The DB (`second_brain_lock.db`, v3) uses `fallbackToDestructiveMigration()` —
explicitly accepted as fine since this is "pre-release local config, safe to drop and recreate."

### 7.3 The dual detection mechanism (this is the architectural core)

Two independent detection paths both funnel into one decision function, and are explicitly designed
to be safe running simultaneously:

- **`MonitorService`** — always-on foreground service, polls `UsageStatsManager` (via
  `UsageStatsHelper.currentForegroundPackage`) every **1.5s**, scanning only the last 5 seconds of
  the event log each time (not the whole day) for efficiency. This is the **baseline path**,
  requiring no special permission beyond usage access.
- **`LockAccessibilityService`** — entirely **optional** (user must separately grant Accessibility),
  registered for `typeWindowStateChanged` events only, `canRetrieveWindowContent="false"` (cannot
  read screen content, only which window/package changed). Provides **instant** detection instead of
  polling latency.

**Both call the identical `ForegroundEvaluator.onForegroundPackage()`**, written to be idempotent per
package — re-evaluating the same foreground app repeatedly is a safe no-op if it's already
locked/unlocked correctly. There is no mutex between the two; both can fire for the same transition
and nothing breaks. **Granting Accessibility only changes how *fast* a lock is noticed, never what
gets blocked.**

`ForegroundEvaluator`'s decision order per foreground event: hide any overlay for a since-abandoned
package → **morning-routine check** (blocks *every* app, not just configured ones, if
`SleepPrefs.isMorningRoutineActive()`) → look up the package's `AppLimit` → **schedule block**
(`blockDuringSchedule` + an active matching `RoutineCache` window) → **focus block**
(`blockDuringFocus` + an active cross-device focus session) → **daily minute budget** → **open-count
budget** (checked only on an actual app-entry transition, not every poll tick) → otherwise unlock +
maybe fire the 90%-used warning.

### 7.4 The overlay stack

- **`LockOverlayManager`** — the actual hard lock. `TYPE_APPLICATION_OVERLAY`/`TYPE_PHONE` +
  `FLAG_SHOW_WHEN_LOCKED`, a reason label + non-punitive subtitle, an optional live midnight
  countdown (only for daily-budget locks — schedule/focus/morning-routine locks resolve when their
  own window ends, not at midnight). The **only** escape is a "Home" button.
- **`BlockingOverlayRoot`** — the view that makes it "hard": overrides `dispatchKeyEvent()` to
  consume `KEYCODE_BACK` without calling `super`, so the system back button/gesture never
  propagates past it. Explicitly documented: the home button/gesture itself *cannot* be intercepted
  this way (OS-owned) — going home is the one deliberate way out.
- **`CooldownOverlayManager`** — a 5-second **non-punitive pause screen shown once, before** the
  hard lock, with four "reason chips" (Habit/Bored/Checking something/Needed it) that only
  *shortcut* straight to the hard lock — tapping one never grants extra time.
- **`FocusOverlayManager`** — **not** a lock at all: a small floating, non-focusable "time
  remaining" pill shown during any active cross-device focus session, purely informational (which
  apps to hard-block *during* focus is called out as "an unmade product decision" in the code).

### 7.5 Morning wake alarm & "morning routine" lock

A separate feature (`AlarmScheduler`, `WakeAlarmReceiver`, `WakeFlowActivity`/`Screen`) that happens
to feed into Shield's lock via the morning-routine branch above. Uses `AlarmManager.setAlarmClock()`
specifically — "the one API meant to survive Doze without `SCHEDULE_EXACT_ALARM`" — wrapped in
`runCatching` since at least one OEM (Oplus/ColorOS) throws an undocumented `SecurityException` here
anyway.

`WakeFlowActivity` moves through three phases in one Activity (shown over the lock screen):
**RING** (looping alarm tone + vibration; Stop/Snooze/Set-for-later) → **WELLBEING** (on Stop,
immediately starts a 1-hour "morning routine" window that hard-locks *every other app* via
`SleepPrefs.isMorningRoutineActive` — "I've done those" / "Show this to me later" / "Skip for today")
→ **JOURNAL** (optional nudge into `JournalScreen`, a minimal composer that POSTs straight to
`/api/notes` bypassing `NotesRepository` entirely). An "Emergency call" button is present on every
phase.

### 7.6 Settings (`SettingsScreen.kt`)

Schedule auto-block category toggles (7 categories, feeds `blockDuringSchedule`), Accessibility
status + shortcut, and the sleep-alarm toggle (+ "use my planner sleep routine" sub-toggle, or a
manual wake-time stepper).

---

## 8. Mind tab — AI-derived insights about the user

### 8.1 Tab shell (`MindScreen.kt`)

A "Mind model" label + manual refresh button (`POST /api/mind/synthesize` — triggers a server-side
"mind cycle" run) and a 2-option segmented control: **Overview** / **Knowledge Library**.

### 8.2 Overview (`MindOverviewTab.kt`)

Stack: a news ticker (§8.3) → a **Cycle Health** card (status dot + last-run stats) → a **PARA
donut chart** ("the whole picture," with an expandable narrative summary) → a **Reminders** card
(reads `open_loop`-kind insights — a *different* data source than `RemindersRepository`'s due-date
reminders used elsewhere) → an **"Attention patterns"** line+dot chart of daily capture counts → the
**3D interest-cluster city map** (§8.4).

### 8.3 News ticker (`MindNewsTicker.kt`)

A single-line auto-scrolling marquee, speed proportional to content width (constant px/ms), looping
by snapping back to 0 rather than bouncing. Items colored by a keyword-based domain classifier
(science/technology/business/humanities/default) shared with the city-map's own domain coloring.

### 8.4 Interest cluster — the 3D isometric city map (`InterestClusterCityMap.kt`)

The single most technically distinctive Compose file in the app. Replaced an older flat "bubble
graph" (git history: commit `bb0c9c5`). **There is no real 3D API involved (no OpenGL/Filament) —
it's a hand-rolled pseudo-3D pipeline on one Compose `Canvas`.**

- **Data mapping**: each top-level knowledge domain becomes a "city" on a ring; each domain's
  descendant topics become "buildings" on concentric rings inside that city (ring count/slot count
  scale with `ceil(sqrt(n/5))`, not a fixed grid, so an uneven domain — 2 sub-topics vs. 40 — still
  lays out sensibly). Building height/color come from a `score()` heuristic reused verbatim from the
  old bubble graph (evidence-based: goal-match or library-match scoring, else a flat low baseline —
  there's no real per-topic "interest score" from the API). Cross-city connection lines are drawn
  only when two buildings' evidence actually cites a shared source note.
- **The 3D pipeline**: plain `Vec3` math, no matrix classes. A fixed dual-axis rotation (24°
  around Y, 56° around X — `toView`) produces the tilted "looking down at a city" look; a simple
  perspective-divide (`FOCAL/(FOCAL+depth)`) fakes depth scaling; a **painter's-algorithm** depth
  sort (collect every primitive with its view-depth, sort descending, draw back-to-front) substitutes
  for a z-buffer; **face culling** only draws faces whose rotated normal points at the camera; and a
  Lambertian directional-light term shades each visible face by its normal's dot product with a
  fixed light direction — the classic isometric shaded-cube look.
- **Interactions**: pinch-zoom/drag-pan (anchored at the pinch centroid), an auto-fit-on-first-layout
  pass (computed from the actual projected bounding box), tap-to-select nearest building (opens a
  detail panel with evidence + source citations), manual +/- zoom buttons. Building labels only
  render once zoomed in past a threshold, to avoid clutter at the default view.

### 8.5 Knowledge Library (`MindLibraryTab.kt` + `MindLibraryDetail.kt`)

A searchable/filterable card list (type filter: concept/roadmap/fact/method; domain filter via a
sidebar of distinct domains with accent-colored dots) plus a "recently reinforced" horizontal strip.
Each card shows a 1–5 star "mastery" rating **approximated from `cycleCount`** (no real mastery field
exists server-side). Tapping opens a detail dialog whose body renders one of the same
concept/path/chart visual shapes used by `FieldInvestigationReport` (shared parsers), or falls back
to plain summary text.

---

## 9. Mindverse / Mindcord — cross-account chat & calls

### 9.1 Entry gate (`MindverseScreen.kt`)

If no `OtherBrainsIdentity` is set yet, shows a one-time "pick a display name" card (explicitly
anonymous/cross-account — must not be the user's email/real name; a random avatar is auto-assigned).
**The web app's separate "Other Brains" sub-tab (community feed / suggestion box / currently-studying)
was deliberately dropped from this native build** — Mindverse here is Mindcord-only, though the
backing DTOs/repo methods for it still exist unused.

### 9.2 Room picker (`MindcordTab.kt`)

Lists every domain (sorted by live-count then total "brains"), each showing "{N} here now" or
"{brains} brains study this," with a Join button (`POST /api/mindcord/join`).

### 9.3 The room screen (`MindverseRoomScreen.kt`)

A full-screen, **forced-dark** takeover (ignores app theme, same rationale as Zoom/Meet/Discord's
call screens) that hides the bottom nav. Header (domain + live participant count + chat button with
unread badge) → a **non-lazy** 2-column participant grid (explicitly not `LazyVerticalGrid` — a
real-device bug was found where `SurfaceViewRenderer`'s hardware-compositor video "hole punch"
rendering didn't track Compose's lazy virtualization/scroll offsetting, so live camera video never
appeared inside a lazy grid on a real device despite working fine on an emulator; the roster is
always small so virtualization wasn't buying anything anyway) → a control bar (camera/mic toggle,
a stubbed "not available yet" screen-share, raise-hand, invite, leave) → a bottom-sheet with **Chat**
and **People** sub-tabs.

Each participant tile: live video via a `SurfaceViewRenderer` if the track is present/enabled,
otherwise an avatar circle that visibly changes (accent border, thicker outline, an animated
4-bar waveform) while `speaking == true` — driven by real audio-level data, not just "mic
unmuted" (see §9.5). Camera on/off is signaled explicitly and shown; **mic mute state is not shown
for remote participants** (no signal carries it).

Real-time updates arrive over Supabase Realtime (chat messages, participant roster) with a 15s
fallback poll covering anything the socket misses, plus a 20s presence heartbeat (the server expires
a participant after 45s of no heartbeat). Leaving clears local room state *before* the network
round-trip completes, specifically to avoid a race where popping the screen directly could beat the
state clearing and cause an "already in a room" re-navigation glitch.

### 9.4 WebRTC call architecture (`webrtc/MindcordCallManager.kt`) — the deepest technical piece in the app

- **Library/topology**: Google's official native WebRTC AAR (via GetStream's maintained repackage,
  since Google pulled its own Maven artifact years ago), in a **full mesh** — one `PeerConnection`
  per *other* participant, no SFU/media server.
- **Signaling** runs over a hand-rolled Supabase Realtime **broadcast** channel
  (`network/MindcordCallSignaling.kt`), deliberately on a *separate* topic from the chat/presence
  socket ("so a call-signaling bug can't wedge the message subscription and vice versa") — not the
  official `supabase-kt` client, to avoid a second HTTP stack (Ktor) alongside the app's OkHttp.
- **Offer/answer flow**: audio/video tracks are created once, up front, before the user even toggles
  mic/camera — later mute/camera-off is just `setEnabled()`/stopping the capturer on an
  already-negotiated track, deliberately avoiding a renegotiation round. **Offerer determination is
  deterministic** (whichever peer has the lexicographically higher user ID always offers) — glare-free
  by construction, no random backoff needed. ICE candidates are queued if they arrive before the
  remote description is set.
- **Offer-retry watchdog**: because broadcast delivery has no guaranteed replay (a peer can join a
  beat after another's offer already went out and silently miss it), a periodic check every 4s
  re-offers to any peer this client should be offering to but has had no remote description for >8s.
- **TURN**: `GET /api/mindcord/turn-credentials` resolves a room-scoped STUN+TURN mix from a
  **Metered** TURN account (the secret key never reaches the client) — falls back to public Google
  STUN only if the fetch fails. Fetched once per room join, shared across every peer connection in
  that session.
- **Speaking indicators are genuinely audio-level-driven**: a 300ms poll reads real WebRTC
  `RTCStats` (`media-source`'s `audioLevel` for local, each connection's `inbound-rtp` audio stat for
  that specific remote peer) — a level above a small threshold (tuned above typical ambient mic
  noise) flips a "speaking" flag that drives the waveform animation; this is not simply "mic
  unmuted."
- **Camera state has to be signaled explicitly** (`{kind:"video-state"}` broadcast) because a
  disabled video track just silently stops producing frames on the wire — there's no signal for
  *why* on the receiving end without it.
- **Hand-raise** is a pure broadcast signal with no server-side record — a peer with no established
  connection to you never learns you raised your hand.
- **Late-joiner state sync**: the moment a given peer's ICE state reaches CONNECTED, this client
  re-sends its current video/hand-raise state directly to that one peer, since broadcast delivery
  has no "join late, replay current state" mechanism.
- Screen share is a UI stub only (toast: "not available yet") — not implemented.

---

## 10. Backend integration summary

Full endpoint-by-endpoint spec, with ✅ marking exactly which are called from native code (verified
by grep, not assumed): **[docs/api-reference.md](api-reference.md)**. Architectural points worth
restating here:

- Every request carries `Authorization: Bearer <token>` via an OkHttp interceptor reading
  `SecurePrefs`; a 401 response anywhere clears the token and snaps the UI to login.
- Two genuinely different real-time transports are in play, both Supabase Realtime but on separate
  channel types: `postgres_changes` for Mindcord chat/participant updates, `broadcast` for WebRTC
  call signaling — kept apart so a bug in one can't wedge the other.
- TURN credentials are minted server-side (Metered) and only ever reach the client pre-resolved,
  per-room, short-lived — the secret key itself never ships to the app.
- `focus/state` (the shared cross-device Pomodoro session record, with a real GET/POST/DELETE) and
  `activity/focus-state` (a lighter live do-not-disturb flag) are two genuinely distinct endpoints
  that must be kept in sync manually when either changes — documented explicitly in the API
  reference as an easy thing to conflate.

---

## 11. Known gaps, parked features, and things that look wired up but aren't

Worth knowing before assuming any of these are load-bearing:

- **`OnboardingRepository`** (`GET/POST /api/onboarding/{status,complete}`, a 7-option persona list)
  has no consuming UI anywhere in the app shell — likely parked infrastructure for a profile-setup
  step that was superseded by Shield's permission walkthrough (a different "onboarding," see §3.9).
- **The web app's "Other Brains" community feed** (posts/suggestions/currently-studying) was
  deliberately dropped from the native Mindverse tab — the DTOs and repository methods still exist
  and compile, but no screen renders them.
- **Screen sharing** in Mindcord calls is a UI stub (toast only) — no real implementation.
- **File uploads** in Mindcord (`POST /api/mindcord/upload`, `GET /api/mindcord/files/:id`) exist
  server-side per the DTO comments but aren't surfaced in any native screen yet.
- **`PUT`/`DELETE /api/packets/:id`** exist on the backend but have no edit/delete UI natively —
  created packets can currently only be viewed, never modified or removed, from the app.
- The sync queue's WorkManager job (`SyncQueueWorker`) always reports success to WorkManager even
  when the underlying flush fails partway — so a flush failure with no subsequent local edit or app
  relaunch to re-trigger it can leave the queue sitting unflushed with no periodic retry backing it
  up (see §12 for the full mechanism and why).
- `JournalScreen` (used only by the morning wake-flow) still uses the older Emerald400/`SbLabel`
  visual language, not the newer StreakAccent system — consistent with it being an older, rarely
  touched screen, not a regression.

---

## 12. Offline-first sync queue — full mechanics

The most involved single mechanism in the app, so it earns its own section.

**Storage**: `PendingOp` (a flat `kotlinx.serialization` data class covering all four op types via
optional fields, rather than a sealed hierarchy — "just a plain data class like every other DTO
here") is persisted as a single `List<PendingOp>` under one `LocalCache` key (`"pending_ops"`), not
a dedicated Room table — justified as "just an ordered list, no querying needed."

**Enqueue path** (used by `TasksRepository`'s create/update/delete and `FocusPomodoro`'s activity
logging): try the network call first. On failure, check `ApiClient.isOffline()`
(`ConnectivityManager` capabilities). A **genuine server error** (4xx/5xx) is never queued — it just
surfaces via the repository's `_error` flow. A **connectivity failure** applies the change
optimistically to local state (marked `pendingSync = true`), persists it, and appends a `PendingOp`.

**A locally-created-then-deleted task never round-trips**: deleting a task whose id still has the
`local-` placeholder prefix just drops it locally and cancels its pending create (plus any
update/delete queued against that same placeholder) — since the server never knew about it, there's
nothing to tell it about.

**What triggers a replay**: `SyncQueue.enqueue()` schedules a `OneTimeWorkRequest<SyncQueueWorker>`
with a `NetworkType.CONNECTED` constraint, via `enqueueUniqueWork(..., ExistingWorkPolicy.KEEP)` — a
newly-queued op never spawns a duplicate job if one's already pending; it just waits to be picked up.
This deliberately leans on **WorkManager's own constraint-holding and retry semantics** rather than a
hand-rolled connectivity listener. `LockApp.onCreate()` also unconditionally calls
`scheduleFlush()` on every process start, guaranteeing at least one flush attempt per app launch.

**Replay (`SyncQueue.flush()`)**: loads the queue, sorts by `createdAt`, and walks it in order,
tracking a `localId → realId` map as creates succeed (so a later op in the *same* flush pass
targeting a still-placeholder id gets correctly redirected to the real server id).
**Ordering-preserving failure handling**: the flush stops entirely at the first op that fails,
leaving it and everything after it untouched for the next flush attempt — deliberately, so an update
can never apply before its own create has landed.

**The gap**: `SyncQueueWorker.doWork()` wraps the flush in `runCatching` and **always returns
`Result.success()`** to WorkManager, regardless of whether anything actually succeeded — so
WorkManager's own backoff/retry never engages on this job. The only things that trigger another
attempt are a fresh `enqueue()` call or the next app launch. In practice this is rarely hit (most
flushes succeed once connectivity returns), but it's worth knowing as a real, current limitation
rather than assuming there's a robust periodic retry underneath.

---

## 13. Directory reference

```
app/src/main/java/com/secondbrain/lock/
  MainActivity.kt, LockApp.kt          Compose root + application-level setup
  data/                                 Room entities/DAOs, prefs, generic cache, sync queue
    repo/                               Repository singletons (Tasks/Notes/Mind/Planner/Stats/...)
  network/                              ApiClient (OkHttp+kotlinx.serialization), Mindcord realtime/signaling
    dto/                                Typed request/response shapes per backend area
  webrtc/                               MindcordCallManager (WebRTC mesh + calls)
  service/                              MonitorService, overlay managers, accessibility service, alarms
  receiver/                             BootReceiver
  widget/                               Sectograph home-screen widget (Glance)
  ui/
    theme/                              Color/Type/Theme/Components — see design-system doc
    nav/                                AppNavHost, BottomBar, TopBar, Destinations, QuickAddSheet
    wake/                               WakeFlowActivity/Screen (morning alarm flow)
    screens/
      work/                             Tasks, Focus/Pomodoro, Routines, Streak/Rewards, widget-adjacent
      organize/                         Notes, PARA cube/graph, Capture, Packets, insight visualizations
      mind/                             Overview, Library, News ticker, 3D interest city map
      mindverse/                        Room picker, in-room chat/call screen, shared components
      *.kt (top-level)                  Dashboard/AddApp/Settings (Shield), Login/Register/Onboarding,
                                         AccountSettings, Journal
```

See [README.md](../README.md) for the (outdated, Shield-only) original project framing,
[docs/api-reference.md](api-reference.md) for the full backend endpoint spec, and
[docs/design-system/index.html](design-system/index.html) for exact colors/type/component tokens
with live screenshots.
