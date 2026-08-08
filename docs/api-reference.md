# Backend API reference

The Android app talks to the second-brain **Next.js web repo** (`pages/api/*`), authenticating with a
`Bearer <token>` header (see [ApiClient.kt](../app/src/main/java/com/secondbrain/lock/network/ApiClient.kt)),
which the web app's cookie-based session also accepts as a fallback.

This is the full backend surface as spec'd against the web repo. Endpoints actually called from
native Android code (`ApiClient.kt` + the `data/repo/*` repositories) are marked ✅, verified by
grep — not by assuming a section was "ported" just because it exists. Unmarked endpoints exist on
the backend but have no native call site today (some older screens may still reach them through
the in-app WebView instead).

## Auth (`/api/auth/*`)

| Endpoint | Body | Notes |
|---|---|---|
| `POST /login`, `POST /register` | `{email, password}` | web-only (cookie session) |
| ✅ `POST /mobile` | `{email, password}` | returns `{email, token}`, also sets cookie — native login |
| `POST /logout` | — | clears cookie |
| ✅ `GET /me` | — | current user |
| ✅ `PATCH /profile` | `{name}` | |
| ✅ `POST /password` | `{currentPassword, newPassword}` | |
| ✅ `GET/POST /avatar` | POST `{mime_type, data(base64)}` | GET is a raw image response, loaded via Coil in [TopBar.kt](../app/src/main/java/com/secondbrain/lock/ui/nav/TopBar.kt) / [AccountSettingsScreen.kt](../app/src/main/java/com/secondbrain/lock/ui/screens/AccountSettingsScreen.kt) with a cache-busting `?v=` param. `DELETE` exists on the backend but native has no "remove avatar" button (removed deliberately — replaced by mandatory crop-on-select). |
| ✅ `POST /deactivate` | `{password}` | |

## Notes (`/api/notes/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET/POST /` | GET filters `para,tag,q,status,graduated`; POST `{title,content,tags,para,source_url}` |
| ✅ `GET/PUT /[id]`, ✅ `DELETE /[id]` | PUT `{title,content,para,executive_summary,distilled,status,tags,pinned,source_url,graduated}` — native's PUT calls only ever send a `graduated` patch today |
| ✅ `GET /[id]/links`, ✅ `GET /[id]/related` | pgvector similarity |
| `POST /embeddings`, `GET /embeddings/pending` | not called natively |
| ✅ `GET /graph` | mind-map nodes/edges |

## Tasks (`/api/tasks/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET/POST /` | POST `{title,note_id,due_date}` |
| ✅ `PUT/DELETE /[id]` | `{title,done,due_date,start_min,duration_min,pieces[]}` — also used by [SyncQueue.kt](../app/src/main/java/com/secondbrain/lock/data/SyncQueue.kt) to replay offline task edits once connectivity returns |
| ✅ `POST /breakdown` | `{task}` → `{task, subtasks:[{topic,estimated_minutes}], remaining_today}` — AI subtask suggestions, long-press a task row in [TasksPanel.kt](../app/src/main/java/com/secondbrain/lock/ui/screens/work/TasksPanel.kt); `remaining_today` is a server-enforced daily quota, native surfaces "0 task breakdowns left today" once it hits 0 |

## Planner (`/api/planner/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET/POST /` | GET `?from,days`; POST `{title,plan_date,start_min,duration_min,category,routine_id,status}` |
| ✅ `PATCH /[id]` | block edits; `DELETE /[id]` not called natively |
| ✅ `POST /prompts` | free-text answer to the standing "what's your routine?" prompt |
| `POST /prompts/[id]` | not called natively |
| ✅ `GET /routines` | schedule auto-block windows + the sectograph widget |
| ✅ `POST /routines`, ✅ `PATCH/DELETE /routines/[id]` | |

## Reminders (`/api/reminders/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET /` | polled every 60s, backs the top bar's notification bell — `POST` not called natively |
| ✅ `PATCH /[id]` | `{action:'snooze'\|'done'\|'dismiss'\|'accept', minutes}` |

## Focus / Activity (`/api/activity/*`, `/api/focus/*`)

| Endpoint | Body | Notes |
|---|---|---|
| ✅ `POST /activity/focus-state` | `{active,ends_at}` | live "don't notify me" signal for the reminders evaluator |
| ✅ `POST /activity/focus` | `{mode,minutes,task_id,session_id}` | logs a *completed* session for streaks; idempotent via `session_id` |
| ✅ `GET/POST/DELETE /focus/state` | POST `{minutes,task_id,mode,label}`; DELETE `{status}` | cross-device Pomodoro state — what any client (Android included) can see/start/stop; drives the Shield lock/overlay |

`focus/state` vs `activity/focus-state` are distinct and both real: the former is the shared
session record (with a real `GET` for polling and a session id), the latter is a lighter-weight
do-not-disturb flag the web's `TodayCards.reportFocusState` also calls. Keep both in sync when
either changes.

## Mind (`/api/mind/*`)

| Endpoint | Notes |
|---|---|
| ✅ `GET /cycles`, ✅ `/insights`, ✅ `/library`, ✅ `/sections`, ✅ `/topics` | read-only aggregates, one repo function each in `MindRepository.kt` |
| ✅ `GET /queue` | "Your brain suggests" — pending `para_fun_queue` rows |
| ✅ `POST /queue/[id]/answer` | `{action,value}` — accept (`action="create_task"`, `value={title}`) or dismiss (`action="skip"`) |
| ✅ `POST /synthesize` | |

## Mindcord — chat/calls (`/api/mindcord/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET /rooms` | domains list |
| ✅ `POST /join` | `{domain}` |
| ✅ `POST /leave` | `{room_id}` |
| ✅ `POST /heartbeat` | `{room_id}` — every ~20s while in a room; server expires participants after 45s idle |
| ✅ `GET/POST /messages` | POST `{room_id,body}` |
| ✅ `GET /participants` | `?room_id=` |
| `POST /upload` | `{room_id,filename,mime_type,data(base64,≤5MB)}` |
| `GET /files/[id]` | |

## Other Brains — community (`/api/other-brains/*`)

| Endpoint | Body |
|---|---|
| ✅ `GET/POST /books` | `{title,note}` |
| ✅ `GET /clusters` | aggregate-only "who's studying what": domain + distinct-account headcount |
| ✅ `GET/POST /identity` | `{display_name}` |
| ✅ `GET/POST /messages` | |
| ✅ `GET/POST /suggestions` | |

## Misc

| Endpoint | Body | Notes |
|---|---|---|
| ✅ `GET/PUT /notification-prefs` | `{quiet_start_min,quiet_end_min}` | |
| ✅ `GET /stats` | | dashboard aggregates ([StatsRepository.kt](../app/src/main/java/com/secondbrain/lock/data/repo/StatsRepository.kt)) |
| ✅ `POST /para` | `{id,para}` | move-between-PARA-buckets, `NotesRepository.moveToPara` |
| ✅ `GET/POST /packets` | | `PUT/DELETE /packets/[id]` exist on the backend but aren't called natively — created packets have no edit/delete UI yet |
| ✅ `POST /onboarding/complete`, ✅ `GET /onboarding/status` | | |
| `POST /tour/complete`, `GET /tour/status` | | not called natively |

## Sources

- Full endpoint spec given 2026-07-31 (Shield tab / Account settings session), verified against the
  live Next.js route handlers in the web repo.
- Mobile-specific additions (`POST /api/auth/mobile`, `GET/POST/DELETE /api/focus/state`) added to the
  web repo 2026-07-25 (`migrations/030_focus_state.sql`) — additive only, no change to existing
  cookie/web client behavior.
- `POST /api/tasks/breakdown` documented 2026-08-06 from the native diff that added it (uncommitted at
  the time) — not independently re-verified against the web repo's route handler.
