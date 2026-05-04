# Bug Fix Session — Logout and Splash Routing

Two bugs are blocking all Phase 4 testing. Fix them in order.
Do NOT touch any other code. Do NOT start Sprint 3 work.

Read the existing code carefully before writing any fix.
The goal is to understand what's actually wrong before changing anything.

═══════════════════════════════════════════════════════════════════════
STEP 1 — DIAGNOSE (read code and report, do not change anything yet)
═══════════════════════════════════════════════════════════════════════

Read the following files and answer each question explicitly.
Print your answers before writing any fix.

1.1 Open InvigilatorNavHost.kt (or wherever the nav graph lives).
    Answer:
    - What is the START destination of the nav graph?
    - After OTP + name entry for a PARENT, what route does the graph
      navigate to as the final home screen?
    - After consent completion for an ADULT STUDENT, what route does
      the graph navigate to as the final home screen?
    - Are there TWO different composables that could be called
      "StudentHome" or "ParentHome"? (old placeholder + new one)
      If yes, name both.

1.2 Open SplashViewModel.kt.
    Answer:
    - What is the exact sequence of async operations it performs
      before deciding where to navigate?
    - Does it read the /users/{uid} Firestore document? If yes, which
      repository method does it call?
    - What happens in the code if that Firestore read returns an error
      or returns null? Does it log? Does it fall back? Does it hang?
    - Is there a timeout on the Firestore read?

1.3 Open the repository method that SplashViewModel calls to read the
    user document (likely UserRepository.getUser() or similar).
    Answer:
    - Does it return Result<UserDoc?> or Flow<UserDoc?> or something else?
    - If the Firestore document doesn't exist yet (e.g. a user who just
      completed OTP but crashed before name entry), what does it return?
    - If the Firestore security rules block the read, what exception
      would be thrown, and is it caught?

1.4 Open ParentHomeScreen.kt and StudentHomeScreen.kt (Phase 4 versions).
    Answer:
    - Is there a TopAppBar with an actions parameter containing a
      three-dot or settings icon?
    - If yes, what composable renders the overflow menu?
    - Is there a Route in Routes.kt that maps to these screens?
    - In InvigilatorNavHost.kt, are these screens actually wired into
      the nav graph, or are they defined but never reached?

Print all four answers clearly labeled 1.1 / 1.2 / 1.3 / 1.4.
Do not start Step 2 until you have printed the answers.

═══════════════════════════════════════════════════════════════════════
STEP 2 — ADD DIAGNOSTIC LOGGING (run on device, collect output)
═══════════════════════════════════════════════════════════════════════

Add temporary Timber.d() logs to the following places.
Keep them clearly marked // DEBUG — REMOVE BEFORE SPRINT 3 so we
can strip them cleanly.

2.1 In SplashViewModel, at the very start of the splash resolution:
    Timber.d("SPLASH: currentUser = ${FirebaseAuth.getInstance().currentUser?.uid}")

2.2 After the Firestore user doc read attempt:
    Timber.d("SPLASH: userDoc result = $result")
    (where $result is whatever Result<> or value came back)

2.3 At every branch point in the routing logic:
    Timber.d("SPLASH: routing to [destination] because role=$role status=$status")

2.4 In ParentHomeScreen and StudentHomeScreen composables,
    at the top of the function body:
    Timber.d("SCREEN: ParentHomeScreen composed") // or StudentHomeScreen
    This tells us whether the Phase 4 screens are being reached at all.

2.5 Build and install the app: ./gradlew :app:installDebug
    Then open Android Studio → Logcat, filter by "SPLASH" and "SCREEN".
    Do NOT delete any Firebase user. Just kill the app and relaunch.
    Paste the Logcat output for me to read.

After you paste the output in the conversation, I will tell you exactly
what the fix is. Do not guess at a fix from the logs yourself —
wait for instruction.

═══════════════════════════════════════════════════════════════════════
STEP 3 — FIX (only after diagnostic output is reviewed)
═══════════════════════════════════════════════════════════════════════

[Claude Code: leave this section blank for now. The human will paste
the Logcat output and I will fill in the specific fix instructions
based on what the logs show. Do not proceed past Step 2.]

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- Do not change any logic in Step 1. Read only.
- Do not change any nav graph wiring in Step 2. Add logs only.
- Do not attempt fixes until the human confirms the Logcat output
  has been reviewed.
- Do not touch consent flow, linking, or any Sprint 3 code.
- Keep all diagnostic logs clearly marked for later removal.
