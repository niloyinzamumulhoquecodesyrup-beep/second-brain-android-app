# Feature Testing Checklist (for non-technical testers)

Plain-language testing pointers for the ADHD-redesign work, grouped by feature area. If
anything crashes, freezes, or looks visually broken, note exactly which step caused it.

## Offline & syncing

1. **Offline task changes stick** — turn on Airplane Mode, create a task or mark one done,
   turn Airplane Mode back off. The change should still be there (no data loss).
2. **Offline note/project capture** — with Airplane Mode on, capture a note or make a new
   project. It should save right away (with a small "syncing" dot) and turn into a normal
   note once you're back online.
3. **Logging out with unsynced changes** — make an offline change, then log out before
   reconnecting. *(Known partial gap: the change gets discarded rather than lost silently,
   but there's no warning shown yet. Just confirm the app doesn't crash.)*

## Reminders & alarms

4. **Alarm permission warning** — in Settings, turn on "Wake alarm." If the phone hasn't
   given the app permission for exact alarms, you should see a warning with a "Fix this"
   button that takes you straight to the right phone settings screen.
5. **Task reminder notifications** — schedule a task for a few minutes from now, lock the
   phone or close the app, and wait. A notification should pop up right at that time, even
   with the app closed.
6. **Reminder notification buttons** — when that notification shows up, try each button:
   - **Done** → task should show as completed in the app.
   - **10 min** → notification should disappear and come back 10 minutes later.
   - **Too big** → should turn into a checklist of smaller steps.
7. **Buttons work with no internet** — turn on Airplane Mode, force-close the app, wait for
   a reminder notification, tap "Done." Turn Airplane Mode back off, open the app — the
   task should show as done (it saved offline and synced once reconnected).
8. **Repeated snoozes escalate** *(not yet tester-verified — worth checking anyway)* —
   snooze the same reminder 3 times in a row. It should eventually take over the whole
   screen with three options: "Do it now," "Break it down," or "Not today." This should NOT
   happen if you're in Focus mode or during quiet hours.
9. **Notification still works if the app is fully killed** *(FCM backstop)* — force-stop the
   app from phone Settings and confirm a scheduled reminder still arrives.

## Capturing thoughts

10. **Quick capture is fast** — from the app, tap the center button. Keyboard should pop up
    immediately, type something, hit Save — it should feel close to instant, and the sheet
    should stay open so you can capture several things in a row without extra taps.
11. **Capture button gestures** — a normal **tap** on the center button opens typing mode;
    **press and hold** should start voice capture instead (it should start listening
    immediately, not require you to keep holding).
12. **Voice capture accuracy** *(needs a real human voice)* — say a full sentence out loud
    into the mic. Words should appear on screen as you're still talking, not all at once at
    the end.
13. **Launcher shortcut** — from your home screen, long-press the app icon. A "Capture"
    shortcut should appear and jump straight into capture mode, even if the app was already
    open in the background.

## Tasks & scheduling

14. **Overdue tasks aren't punished** — a task overdue by a few days should now show up in
    Today's list (not hidden away), with a neutral grey "waiting since [date]" label — no
    red, no exclamation marks.
15. **Returning after time away** — if you haven't opened the app in 3+ days, you should see
    a "Welcome back" screen offering either one small task to start with, or a "Clear the
    whole list" option. No guilt-tripping numbers, no red, no exclamation points.
16. **"Clear the whole list" is instant** — tapping it should clear immediately with no
    waiting/spinner, and there's a ~10 second undo option right after.
17. **Task breakdown menu** — tap the **⋮** icon on any task row → "Break it down." The
    first suggestion should have a bold "Start this now" button, the rest a small **+**.
    Suggestions over 20 minutes should offer "Break this down further →". The list should
    show a quota caption ("N breakdowns left today") plus Add all / Discard all. This
    should also work from "See all" → Today tab, and should NOT appear on routines or
    completed tasks. Press-and-hold on a task row should still work as a shortcut to the
    same flow.

## Layout & navigation

18. **Today tab leads with action, not stats** — opening the Work/Today tab, the first thing
    you see should be your task list, not a big streak/score card. The streak info is now a
    small one-line strip lower down.
19. **Mindverse (community chat) is hidden by default** — on a fresh install, you should
    only see 3 tabs, no Mindverse tab. Turning on "Community rooms" in Settings should
    bring it back immediately, no restart needed.

---

**Known lower-confidence spots, flagged rather than hidden:**
- Item 8 (escalation ladder) hasn't been tester-verified yet.
- Item 12 (voice accuracy) genuinely needs a human — it can't be verified through automated
  on-device testing tools.
- A residual bug exists where a minority of tasks don't reliably stay cleared after
  bankruptcy (item 16) despite the server confirming the clear succeeded — root cause not
  yet found; report it if seen, but it's a known, tracked issue, not new.
