# Claude Code Kickoff Prompt — Invigilator

Copy everything inside the fenced block below and paste it as your first message to Claude Code in VS Code. Claude Code will read it, ask any clarifying questions it has, then scaffold the entire project.

If Claude Code asks "should I proceed?" — say yes. If it asks for specific values (Firebase project ID, package name), use the defaults suggested at the bottom of this file or your own preferences.

---

```
We are starting a new Android project called Invigilator. I have already
saved README.md and ARCHITECTURE.md in this folder — read both of them in
full before writing any code. Those documents are authoritative; do not
deviate from them.

Your task in this session is to scaffold a buildable, runnable, empty
project skeleton that matches the architecture document. Do NOT implement
features yet. The goal is: I should be able to build the app, install it
on my phone, and see a "Hello, Invigilator" screen by the end of this
session.

Specifically, do the following, in order:

STEP 1 — Repository hygiene
- Run `git init` if this folder is not yet a git repo.
- Create a `.gitignore` appropriate for an Android + Kotlin + Gradle
  project (include /build, /.gradle, /local.properties, .idea, *.iml,
  *.keystore, google-services.json, .DS_Store, captures, .externalNativeBuild,
  .cxx, /node_modules for the parent-dashboard folder).
- Create an initial commit titled "chore: initial repository setup".

STEP 2 — Android project scaffolding
- Create a new Android Studio Gradle project (Kotlin DSL) at the root of
  this folder. Use `applicationId = "app.invigilator"`. Min SDK 26 (Android
  8.0). Target SDK 34. Compile SDK 34. Java 17.
- Use Kotlin + Jetpack Compose. Activity should be a single
  `MainActivity` extending `ComponentActivity`. No XML layouts anywhere.
- Set up a version catalog at `gradle/libs.versions.toml` with these
  initial entries (use latest stable versions you know of as of April 2026,
  or the most recent you're aware of):
    - kotlin, agp, compose-bom, compose-compiler-extension
    - androidx-core-ktx, androidx-lifecycle, androidx-activity-compose
    - androidx-navigation-compose
    - hilt, hilt-navigation-compose
    - kotlinx-coroutines
    - firebase-bom, firebase-auth, firebase-firestore, firebase-messaging,
      firebase-functions, firebase-crashlytics, firebase-analytics
    - timber (logging)
    - junit, mockk, kotlinx-coroutines-test, turbine

STEP 3 — Multi-module structure
Create these modules with empty `build.gradle.kts` and a placeholder
`README.md` in each, following the dependency graph in ARCHITECTURE.md
section 2:
  :app
  :core
  :feature-blocker
  :feature-voice

Do NOT create :feature-tutor or :feature-vision yet — those are post-MVP.

The :app module is the only one that depends on feature modules. Feature
modules depend only on :core and external libs. Enforce this by setting up
the dependency declarations now — even though the modules are empty.

STEP 4 — Hilt setup
- Add Hilt to the project. Annotate `MainActivity` with @AndroidEntryPoint
  and create a top-level `@HiltAndroidApp class InvigilatorApplication`.
- Register the application in AndroidManifest.

STEP 5 — Compose theme
- Create an `InvigilatorTheme` composable in :app (Material 3, light/dark
  support). Use a placeholder color palette — we'll redesign visuals later.
- Set the activity content to:
    `Scaffold { Text("Hello, Invigilator", modifier = Modifier.padding(it)) }`

STEP 6 — Firebase wiring (placeholder, no real keys yet)
- Apply the Google Services Gradle plugin in :app.
- Add Firebase BOM and the SDKs listed above.
- Create a placeholder `google-services.json` with a clear comment in a
  README that the real one needs to be downloaded from the Firebase console
  and added by the developer (i.e. me) before first build.
- DO NOT commit any real google-services.json. Add it to .gitignore (it
  already should be) and leave a `google-services.json.example` instead.

STEP 7 — Documentation seeds
- Create `docs/adr/_template.md` with a simple ADR template (Status,
  Context, Decision, Consequences).
- Create the three initial ADRs as per ARCHITECTURE.md section 10, but
  leave them as stubs with just the title and "TBD — to be written".
- Create `docs/dpdp-compliance.md` as a stub.

STEP 8 — CI starter
- Create `.github/workflows/android-ci.yml` with a job that runs
  `./gradlew lint testDebugUnitTest assembleDebug` on every push and PR.
  Use actions/checkout@v4 and actions/setup-java@v4 with temurin 17.

STEP 9 — Verify it builds
- Run `./gradlew assembleDebug` and confirm there are no errors.
- If there are errors, fix them. Do not stop at this step until the build
  is green. Use `./gradlew --stacktrace` if needed.

STEP 10 — Final commit
- Stage everything. Make a second commit titled
  "feat: scaffold multi-module Android project with Hilt and Firebase".
- Print a summary of:
    - the modules you created
    - the dependencies in libs.versions.toml
    - any decisions you made that weren't specified above
    - any errors you ran into and how you resolved them
    - exactly what I need to do next (create Firebase project, download
      google-services.json, install on phone, etc.)

CONSTRAINTS
- Do not implement any actual feature logic. No app blocking, no auth
  screens, no service classes beyond what's required for the build to
  succeed. We are scaffolding only.
- Do not commit any secrets or real config files.
- If a step fails, stop and tell me. Do not improvise around the spec.
- If something in this prompt conflicts with ARCHITECTURE.md, follow
  ARCHITECTURE.md and flag the conflict.

When finished, list the next session's expected scope so we can plan it.
```

---

## Defaults to use if Claude Code asks

| If asked for... | Answer |
|---|---|
| App name | `Invigilator` |
| Package / applicationId | `app.invigilator` |
| Min SDK | 26 |
| Target / Compile SDK | 34 |
| JDK | 17 (Temurin) |
| Kotlin version | latest stable (2.0+) |
| Build system | Gradle Kotlin DSL |
| Default branch name | `main` |
| First commit author | Your real name + email |
| Firebase region | `asia-south1` (Mumbai — closest to your users) |

## What to do *before* pasting this prompt

1. **Make sure your `Invigilator` folder is empty** (or only has the README/ARCHITECTURE files we'll add). Claude Code is more reliable on clean ground.
2. **Open the folder in VS Code.** Confirm Claude Code is active (you should see it in the sidebar or via your usual command).
3. **Save README.md and ARCHITECTURE.md into the folder root first.** I'm providing both files for you to download in the next message — drop them in, then start Claude Code.
4. **Verify Java 17 is installed** — Claude Code will need it. Quick check in terminal: `java --version`. If it's not 17, install Temurin 17 from `adoptium.net` first.
5. **Have your phone ready** with USB debugging enabled. We'll want to run the scaffolded "Hello, Invigilator" on real hardware before declaring success.

## What success looks like at end of this session

- Project builds with `./gradlew assembleDebug` (zero errors).
- App installs on your phone and shows a "Hello, Invigilator" screen.
- Two commits in git history.
- Four module folders exist (`app/`, `core/`, `feature-blocker/`, `feature-voice/`), each with a `build.gradle.kts` and a stub `README.md`.
- `docs/` folder has ADR template and stubs.
- A clear list of "what to do next" printed by Claude Code.

If any of those are missing — bring the output back to me here. I'll diagnose.

## After the scaffold session — what comes next

Once the skeleton builds and runs, the next session with Claude Code (which we'll plan together here) implements the **auth foundation**: phone-OTP login for parent and student, role-based routing to placeholder dashboards, and the DPDP consent flow. That's roughly Sprint 2 — about a week of work.

Then the sprints look like this:

| Sprint | Focus | Outcome |
|---|---|---|
| 1 | Scaffold (this one) | Buildable empty app |
| 2 | Auth + consent | Parent and student can log in; consent recorded |
| 3 | Session timer + foreground app detection | App knows what's on screen during a session |
| 4 | Escalation engine (stages 1+2) | Voice nudge + force-close working |
| 5 | Parent push alerts (stage 3) + parent web dashboard | End-to-end MVP loop |
| 6 | Polish, testing, internal pilot with 5 students | First real users |

That's the path to a usable smartphone-mode product in roughly 6 weeks of focused work.
