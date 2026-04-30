# Invigilator — Architecture

This document is the single source of truth for how Invigilator is structured. Every code change should be consistent with what's written here. If a change requires deviating, update this document first.

## 1. Architectural principles

These come before any code.

1. **Privacy is structural, not configurable.** Camera frames must never have a code path that leaves the device. There is no "upload video" feature toggled by a flag — the upload code does not exist.
2. **Local-first.** Anything that *can* run on the device, *should* run on the device. The cloud is for sync, notification fan-out, and the AI tutor only.
3. **Multi-module from day one.** A monolithic `app/` module would couple the app blocker, the camera ML pipeline, and the AI tutor — three things that have nothing to do with each other and will be developed by different (eventually human) engineers. Separate modules prevent this rot.
4. **Compose-only UI.** No XML layouts, no view-binding, no fragments. Single-activity architecture with Navigation-Compose.
5. **Coroutines + Flow everywhere.** No RxJava, no callbacks, no LiveData in new code. ViewModels expose `StateFlow`. Data layers expose suspending functions or cold `Flow`s.
6. **Dependency injection with Hilt.** Manual constructor injection until a module has 3+ collaborators, then Hilt.
7. **No Java in production code.** Kotlin only. Generated code (e.g. Room, Hilt) is fine.
8. **Test the domain, not the framework.** Unit tests on use cases and view models. Instrumented tests only for the app blocker (which depends on Android internals).

## 2. Module boundaries

```
                ┌─────────────────────────────────────┐
                │              :app                   │
                │  (Activity, NavHost, DI graph root) │
                └────────────────┬────────────────────┘
                                 │ depends on
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
       :feature-blocker   :feature-voice     :feature-tutor (later)
              │                  │                  │
              └──────────────────┼──────────────────┘
                                 ▼
                              :core
                  (auth, repos, models, Firebase, Hilt)
```

**Rule:** Feature modules may depend on `:core`. Feature modules must not depend on each other. The `:app` module is the only place where features compose into a navigation graph.

This rule is enforced by Gradle dependency declarations and reviewed on every PR.

### What lives where

| Module | Responsibility | Key types |
|---|---|---|
| `:app` | Activity, navigation, theme, DI graph root | `MainActivity`, `InvigilatorNavHost`, `InvigilatorTheme` |
| `:core` | Auth, models, repositories, Firebase wiring, DPDP consent state | `User`, `Session`, `AuthRepository`, `SessionRepository`, `ConsentRepository` |
| `:feature-blocker` | Foreground app detection, blocking service, escalation engine | `AppMonitorService`, `BlockerAccessibilityService`, `EscalationEngine` |
| `:feature-voice` | TTS, voice nudge playback, language selection | `NudgeEngine`, `IndicTtsClient` |
| `:feature-tutor` (post-MVP) | AI tutor screen and Claude API client | `TutorViewModel`, `ClaudeClient` |
| `:feature-vision` (post-MVP) | Camera, MediaPipe pipeline, derived event emission | `VisionService`, `PostureDetector`, `AttentionDetector` |

## 3. Data flow

### MVP: smartphone mode session

```
[Parent starts session for student]
            │
            ▼
[FCM push to student device]
            │
            ▼
[AppMonitorService starts in foreground]
            │
            ▼
   Every 2s, check foreground app via UsageStatsManager
            │
   ┌────────┴────────┐
   ▼                 ▼
[Allowed app]   [Distracting app detected]
                       │
                       ▼
              [EscalationEngine.onDistraction()]
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
   Stage 1:        Stage 2:        Stage 3:
   Voice nudge     Force-close     FCM to parent
   (15s grace)     via Accessib.   + Firestore log
                   Service
```

The `EscalationEngine` is a state machine. Its state is persisted across process death so that closing the service does not reset the warning count. A student can't beat the system by force-stopping the app — the parent gets a "Service was terminated" alert if it dies.

### Event log writes

Every distraction, nudge, block, and parent alert writes a row to:

```
firestore: /users/{studentUid}/sessions/{sessionId}/events/{eventId}
```

The writes are **batched and queued offline-first** via Firestore's local cache. A student in poor connectivity will not break the session; events sync when connectivity returns.

## 4. Firestore schema

```
/users/{uid}
  - role: "student" | "parent" | "institute_admin"
  - displayName: string
  - phoneNumber: string  (E.164)
  - createdAt: Timestamp

/users/{parentUid}/linkedStudents/{studentUid}
  - relationship: "father" | "mother" | "guardian"
  - consentSignedAt: Timestamp     // DPDP audit field
  - consentVersion: string          // version of consent text accepted

/users/{studentUid}/sessions/{sessionId}
  - startedAt: Timestamp
  - endedAt: Timestamp?
  - plannedDurationMin: number
  - status: "active" | "completed" | "abandoned" | "terminated_by_student"
  - distractionCount: number
  - parentAlertsTriggered: number

/users/{studentUid}/sessions/{sessionId}/events/{eventId}
  - timestamp: Timestamp
  - type: "session_started" | "distraction_detected" | "nudge_played"
        | "app_blocked" | "parent_alerted" | "session_ended" | "service_died"
  - payload: map<string, any>      // type-specific (e.g. {"app": "youtube"})

/blocklist/{templateId}            // server-managed app blocklist templates
  - name: "default" | "exam_prep" | "school"
  - blockedPackages: array<string> // e.g. ["com.whatsapp", "com.google.android.youtube"]
  - version: number
```

### Security rules (sketch)

- A student can read/write only their own `/users/{uid}` and their own sessions.
- A parent can read (not write) sessions of any student in their `linkedStudents` collection.
- A parent can write a "force end session" command that the student app reads and obeys.
- Cloud Functions do all writes that span users (notifications, weekly report generation).

Full rules will live in `firestore.rules` and be tested with the Firebase emulator.

## 5. App blocking — how it actually works on Android

Three Android primitives, used together:

1. **`UsageStatsManager.queryEvents()`** — gives us the foreground app every ~2 seconds. Requires `PACKAGE_USAGE_STATS` permission, which the user grants from system Settings (cannot be granted programmatically — we will guide them through it on first run).
2. **`AccessibilityService`** — gives us the ability to dispatch a global "back" or "home" action when a blocked app is detected, effectively kicking the user out. Also user-granted via Settings.
3. **Foreground service with persistent notification** — keeps `AppMonitorService` alive during a session. Without this, Android will kill the service after a few minutes.

**Important:** Recent Android versions (12+) restrict foreground service starts. We use `FOREGROUND_SERVICE_TYPE_DATA_SYNC` and request the permission explicitly. The notification cannot be dismissed during a session by design.

**What we will *not* do:**
- We will not request Device Admin or Device Owner privileges. They're heavyweight, scary on the install prompt, and unnecessary for our use case.
- We will not use overlay-based blocking (drawing on top of other apps). Google Play has tightened policies on `SYSTEM_ALERT_WINDOW` and apps that abuse it for blocking get rejected.

## 6. DPDP Act 2023 compliance plan

This is a **legal requirement before commercial launch**, not a marketing item.

| Requirement | How we satisfy it |
|---|---|
| Verifiable parental consent for minors | Two-step: parent enters phone, receives OTP, signs consent text in-app, signature timestamped + stored. |
| Purpose limitation | Camera frames processed in-memory only; not retained. Event log retained 90 days, then auto-deleted. |
| Data minimization | We collect: phone, name, role, session events. We do not collect: location, contacts, photos, browsing history, biometrics. |
| Right to access | Parent dashboard exposes "Download my data" for any linked student. |
| Right to erasure | One-tap "Delete account" clears all user docs and triggers a Cloud Function to purge subcollections. |
| Data Protection Officer | Founder is named DPO until we hire one. Contact email: `dpo@invigilator.app` (to register). |
| Notice in regional language | Consent screens render in Assamese, Hindi, Bengali, English at minimum. |
| Breach notification | Sentry + Firebase alerts wired to founder's phone within 24h. |

A separate `docs/dpdp-compliance.md` will hold the full legal text, consent versions, and audit log structure.

## 7. The "no upload video" guarantee — how we enforce it

The vision module (post-MVP) is the highest privacy-risk surface. Its design:

- The camera feed is consumed by an `ImageAnalysis` use case in CameraX.
- Frames are passed directly to MediaPipe and TFLite interpreters, all of which run in a background thread on the device.
- The frame `Bitmap` is recycled immediately after analysis. There is no `File.write`, no `OutputStream`, no upload client, no Firebase Storage reference, **anywhere in the `:feature-vision` module's dependencies**.
- This is enforced by a Gradle rule: `:feature-vision` may not declare a dependency on Firebase Storage or any HTTP client. CI fails the build if it does.
- Only derived `VisionEvent` objects (sealed class with no media fields, just enums and timestamps) cross the module boundary.

This is what we mean by "structural, not configurable."

## 8. Build and release

- Single Gradle project, Kotlin DSL (`build.gradle.kts`).
- Version catalog (`gradle/libs.versions.toml`) so versions are managed in one place.
- Three build flavors: `dev` (Firebase debug project, verbose logging), `staging` (real Firebase, internal testers), `prod` (Play Store).
- CI: GitHub Actions runs lint + unit tests on every PR. Bigger items (instrumentation tests, screenshot tests) run nightly.
- Releases: signed AAB uploaded to Play Console internal testing track; promoted to closed/open tracks manually.

## 9. What's deliberately not decided yet

These are open questions to revisit at the relevant milestone, not now:

- Whether to write our own TFLite posture classifier or fine-tune an existing one (decide in vision-module sprint).
- Whether the AI tutor uses streaming or one-shot Claude API calls (decide in tutor-module sprint).
- Whether iOS will be MDM-only or use Screen Time API (decide after Android v1 ships).
- Pricing model details (decide after first 5 paying institutes).

## 10. Decisions log location

All non-trivial architectural decisions get a short ADR (Architecture Decision Record) in `docs/adr/`. The first three will be:

- `001-multi-module-structure.md`
- `002-firebase-as-backend.md`
- `003-no-upload-video-policy.md`

ADR template lives in `docs/adr/_template.md`.
