# Invigilator — TODO Backlog

This file tracks known issues, technical debt, and deferred improvements
that we don't fix immediately but don't want to forget.

Each entry should have: a short title, when it was observed, where the
problem lives, what we tried (if anything), and what we'd do about it
when we get to it.

═══════════════════════════════════════════════════════════════════════
ACTIVE ITEMS
═══════════════════════════════════════════════════════════════════════

## TODO-001 — Pixel 8 permission check flakiness on session start

**Observed:** End of Sprint 3 (May 2026), during Test 1 verification on
a second Android Studio virtual device (Pixel 8, Android 17, API 37).

**Symptom:** As a student, attempting to start a session — even after
PACKAGE_USAGE_STATS was granted in system settings — sometimes routed
the user back to SessionTypeSelectScreen instead of starting the
foreground service. After 2-3 failed attempts the issue cleared on its
own and sessions started normally.

**Where the bug likely lives:**
- `core/src/main/kotlin/app/invigilator/core/permissions/PermissionChecker.kt`
- Specifically the `hasUsageStatsPermission()` function which uses
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, ...)`

**Hypotheses (untested):**
1. `unsafeCheckOpNoThrow` may have unreliable behavior on newer Android
   versions or Pixel 8 hardware. The non-unsafe variant `checkOpNoThrow`
   may be more reliable.
2. Permission propagation latency in newer Android — the OS may report
   the permission as not-yet-granted for a few seconds after the user
   toggles it on, even though the toggle is visually on.
3. App standby / power management on Pixel 8 may interfere with AppOp
   readings under certain conditions (cold start, after sleep, etc.).

**Why it's not urgent:** The issue auto-recovers within 2-3 attempts.
A real student would notice this as "I had to tap Start a couple times"
which is bad UX but not blocking. We've shipped on devices that work
reliably (Medium Phone emulator + the original test device).

**What to do when picked up:**
1. Add structured logging in PermissionChecker — print the exact
   AppOpsManager mode integer returned by every check. Capture data
   from a Pixel 8 in the wild before changing anything.
2. Compare `checkOpNoThrow` vs `unsafeCheckOpNoThrow` behavior on the
   same device. Switch to whichever is consistent.
3. Consider a brief retry loop (3 attempts with 200ms delay) if the
   first check returns MODE_IGNORED but the system grant flag is true.
4. Test on at least 3 physical Indian Android phones — Samsung Galaxy
   M-series, Xiaomi Redmi, Realme — since these are the most common
   handsets in the APSC student demographic.

**Won't fix in:** Sprint 4 (intervention features take priority)
**Earliest candidate sprint:** Sprint 6 or later

═══════════════════════════════════════════════════════════════════════
ARCHITECTURAL DEBT
═══════════════════════════════════════════════════════════════════════

## TODO-002 — Stray `backend/firestore.rules` file from OneDrive recovery

**Observed:** Mid-Sprint 2, surfaced again during the auth-flow debugging
in late Sprint 3.

**Symptom:** The OneDrive disaster left a `backend/firestore.rules` file
in addition to the project-root `firestore.rules`. We are not 100% sure
which one is currently the source of truth for `firebase deploy`.

**What to do:** Audit `firebase.json` to see which rules file the deploy
points at. Delete the unused one. Add a CI check (in the future) that
validates only one rules file exists.

**Won't fix in:** Sprint 4
**Earliest candidate sprint:** Whenever rules need to change next.

═══════════════════════════════════════════════════════════════════════
UI POLISH (low priority)
═══════════════════════════════════════════════════════════════════════

## TODO-003 — App name display fallback for unknown packages

**Observed:** Sprint 3, Phase 4 testing.

**Symptom:** When AppNameResolver can't resolve a package name (e.g. the
app was uninstalled between session and summary view), it falls back to
the last segment of the package name with first letter capitalized. For
something like `com.android.youtube`, you'd see "Youtube" instead of
"YouTube" — minor cosmetic glitch.

**What to do:** Maintain a small handcrafted lookup table for the most
common Indian Android apps where the heuristic produces ugly results.
Apply lookup before falling back to the package-name heuristic.

**Won't fix in:** Sprint 4
**Earliest candidate sprint:** Sprint 6+ when we polish the summary UI.

═══════════════════════════════════════════════════════════════════════
HOW TO USE THIS FILE
═══════════════════════════════════════════════════════════════════════

- Add new items at the bottom of the appropriate section.
- When picking up an item to fix, move it to a Sprint spec — don't fix
  inline. The Sprint spec can reference the TODO ID.
- When fixing an item, mark it RESOLVED and date-stamp it. Don't delete
  resolved items — they're a useful history.

═══════════════════════════════════════════════════════════════════════
RESOLVED
═══════════════════════════════════════════════════════════════════════

(none yet)
