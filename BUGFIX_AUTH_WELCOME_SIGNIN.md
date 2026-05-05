# Bug Fix — Auth Flow with Welcome Screen and Sign-In Path

Time estimate: 1.5h Claude Code + 30min testing.

What's broken: A registered user who logs out cannot log back in. After
OTP success, they're forced through onboarding (DOB, Name) and the
duplicate `/users/{uid}` write fails with "Something went wrong."

The fix: Add a Welcome screen with two paths. Returning users go
Phone → OTP → auto-route to home. New users go RoleSelect → DOB →
Name → Phone → OTP → Consent. After OTP, the routing decision is
based on whether a Firestore user doc exists.

═══════════════════════════════════════════════════════════════════════
ARCHITECTURE OVERVIEW
═══════════════════════════════════════════════════════════════════════

New flow:

```
Splash (decides based on Firebase Auth state + Firestore lookup)
  ├─ signed in + accountStatus=active → StudentHome / ParentHome
  ├─ signed in + accountStatus=pending_consent → resume consent flow
  └─ signed out → Welcome screen
       ├─ "Sign in" → PhoneEntry → OtpEntry → [auto-route by Firestore lookup]
       └─ "I am new" → RoleSelect → DOBEntry → NameEntry → PhoneEntry → OtpEntry → Consent
```

The key insight: Phone+OTP screens are SHARED between the two paths.
What differs is what happens AFTER OTP success:

- Sign-in path: look up Firestore doc, route based on role + accountStatus
- New-user path: create Firestore doc with the role + DOB + name we
  already collected, then route to consent

This is encoded in a "flow context" that travels with the user through
Phone and OTP screens.

═══════════════════════════════════════════════════════════════════════
DELIVERABLES
═══════════════════════════════════════════════════════════════════════

New files:
1. app/src/main/kotlin/app/invigilator/ui/onboarding/WelcomeScreen.kt
2. app/src/main/kotlin/app/invigilator/ui/onboarding/WelcomeRoute.kt
   (no ViewModel — Welcome is stateless)

Modified files:
3. app/src/main/kotlin/app/invigilator/ui/nav/Routes.kt
4. app/src/main/kotlin/app/invigilator/ui/nav/InvigilatorNavHost.kt
5. app/src/main/kotlin/app/invigilator/ui/auth/OtpEntryViewModel.kt
6. app/src/main/kotlin/app/invigilator/ui/auth/OtpEntryRoute.kt
7. app/src/main/kotlin/app/invigilator/ui/onboarding/OnboardingViewModel.kt
8. core/src/main/kotlin/app/invigilator/core/auth/UserRepository.kt
9. core/src/main/kotlin/app/invigilator/core/auth/UserRepositoryImpl.kt
10. app/src/main/res/values/strings.xml (+ as/hi/bn variants)

═══════════════════════════════════════════════════════════════════════
EXACT SPECIFICATIONS
═══════════════════════════════════════════════════════════════════════

──── Routes.kt — add ────

```kotlin
@Serializable data object Welcome : Route

// Phone and OTP routes get a "flow" parameter that tells them whether
// they're in sign-in or new-user mode.
@Serializable
data class PhoneEntry(val flow: AuthFlow) : Route

@Serializable
data class OtpEntry(
    val flow: AuthFlow,
    val phoneE164: String,
) : Route

enum class AuthFlow {
    SIGN_IN,        // existing user; just route to home after OTP
    NEW_USER,       // new user; create Firestore doc after OTP
}
```

If `Route.PhoneEntry` and `Route.OtpEntry` already exist with different
shapes, modify them to add the `flow` parameter. If they currently
take other params, those stay. Just add `flow` as a required param.

──── UserRepository.kt — add method ────

```kotlin
interface UserRepository {
    // ... existing methods ...

    /**
     * Returns true if a user document exists at /users/{uid}.
     * Used by sign-in flow to detect orphaned auth (Firebase user
     * exists but no Firestore profile).
     */
    suspend fun userDocExists(uid: String): Result<Boolean>

    /**
     * Reads the user document. Returns null if not found.
     */
    suspend fun getUser(uid: String): Result<UserDoc?>
}
```

If `getUser` already exists, leave it alone. If not, add it.

──── UserRepositoryImpl.kt ────

```kotlin
override suspend fun userDocExists(uid: String): Result<Boolean> {
    return try {
        val snap = firestore.collection("users").document(uid).get().await()
        Result.success(snap.exists())
    } catch (e: Exception) {
        Timber.e(e, "userDocExists failed for $uid")
        Result.failure(e)
    }
}

override suspend fun getUser(uid: String): Result<UserDoc?> {
    return try {
        val snap = firestore.collection("users").document(uid).get().await()
        if (snap.exists()) {
            Result.success(snap.toObject(UserDoc::class.java))
        } else {
            Result.success(null)
        }
    } catch (e: Exception) {
        Timber.e(e, "getUser failed for $uid")
        Result.failure(e)
    }
}
```

If `getUser` already exists, do not duplicate it.

──── WelcomeScreen.kt ────

Stateless screen. Two big buttons. Material3 layout, sentence case.

```kotlin
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.welcome_create_account))
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.welcome_sign_in))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
```

──── WelcomeRoute.kt ────

```kotlin
@Composable
fun WelcomeRoute(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    WelcomeScreen(
        onSignIn = onSignIn,
        onCreateAccount = onCreateAccount,
    )
}
```

──── strings.xml additions ────

```
welcome_title           → "Welcome to Invigilator"
welcome_subtitle        → "Stay focused. Study smarter."
welcome_create_account  → "Create an account"
welcome_sign_in         → "I have an account"
sign_in_unknown_number  → "We don't recognize this number. Try creating an account instead."
```

Add [DRAFT] versions to as/hi/bn files.

──── OtpEntryViewModel.kt — modify success handler ────

The OTP entry already verifies the OTP and signs in via Firebase Auth.
We need to branch the success behavior based on `flow`.

Add `flow: AuthFlow` to the SavedStateHandle reads at the top:
```kotlin
private val flow: AuthFlow = AuthFlow.valueOf(
    savedStateHandle.get<String>("flow") ?: AuthFlow.NEW_USER.name
)
```

Modify the OTP success handler. The current code probably calls
something like `verifyOtp(...).onSuccess { ... navigate ... }`. The
new logic: after successful verification, look up the user doc and
emit a new state field describing where to go.

Add to the UI state:
```kotlin
data class OtpEntryUiState(
    // ... existing fields ...
    val nextDestination: OtpDestination? = null,
)

sealed interface OtpDestination {
    /** New user path: continue with the data we already collected. */
    data object ProceedToCreateUser : OtpDestination
    /** Sign-in path, user is fully active — go straight to home. */
    data class GoToHome(val role: UserRole) : OtpDestination
    /** Sign-in path, user is mid-onboarding — resume consent. */
    data class ResumeConsent(val userDoc: UserDoc) : OtpDestination
    /** Sign-in path, but user has no Firestore doc — show error. */
    data object UnknownNumber : OtpDestination
}
```

Inside the OTP-success branch:

```kotlin
private suspend fun handleOtpSuccess(uid: String) {
    when (flow) {
        AuthFlow.NEW_USER -> {
            _state.update { it.copy(nextDestination = OtpDestination.ProceedToCreateUser) }
        }
        AuthFlow.SIGN_IN -> {
            val userResult = userRepository.getUser(uid)
            userResult
                .onSuccess { userDoc ->
                    when {
                        userDoc == null -> {
                            // Auth succeeded but no profile — orphaned auth.
                            // Sign out the orphan and show an error.
                            authRepository.signOut()
                            _state.update {
                                it.copy(
                                    nextDestination = OtpDestination.UnknownNumber,
                                    error = stringResource(R.string.sign_in_unknown_number),
                                )
                            }
                        }
                        userDoc.accountStatus == AccountStatus.ACTIVE -> {
                            _state.update {
                                it.copy(nextDestination = OtpDestination.GoToHome(userDoc.role))
                            }
                        }
                        else -> {
                            _state.update {
                                it.copy(nextDestination = OtpDestination.ResumeConsent(userDoc))
                            }
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(error = "Could not load your account. Please try again.")
                    }
                }
        }
    }
}
```

Note: `stringResource` doesn't work in ViewModels — replace with a
resource ID + Compose-side resolution, OR pass the message in a
`UiMessage(@StringRes val resId: Int)` wrapper. Use whichever pattern
already exists in the project's other ViewModels for error messages.
The existing error display in OtpEntryScreen probably handles raw
strings; if so, just use the raw string here for now.

──── OtpEntryRoute.kt — modify navigation ────

The Route observes `state.nextDestination` and navigates accordingly.
Replace the current "on success, navigate" logic:

```kotlin
LaunchedEffect(state.nextDestination) {
    when (val dest = state.nextDestination) {
        null -> {} // not yet
        OtpDestination.ProceedToCreateUser -> {
            // Caller (NavHost) handles this — we propagate via callback
            onNewUserOtpComplete()
        }
        is OtpDestination.GoToHome -> {
            onSignInComplete(dest.role)
        }
        is OtpDestination.ResumeConsent -> {
            onResumeConsent(dest.userDoc)
        }
        OtpDestination.UnknownNumber -> {
            // Stay on OTP screen, error message is shown via state.error
            // The viewModel already cleared the auth session.
        }
    }
}
```

Add the new callbacks to OtpEntryRoute's parameters:

```kotlin
@Composable
fun OtpEntryRoute(
    onNewUserOtpComplete: () -> Unit,
    onSignInComplete: (UserRole) -> Unit,
    onResumeConsent: (UserDoc) -> Unit,
    viewModel: OtpEntryViewModel = hiltViewModel(),
) { ... }
```

──── OnboardingViewModel.kt — defensive change ────

In `createUser()`, before writing, check if the doc already exists.
If it does, log a warning and skip the write — that's a sign of an
unexpected state we should handle gracefully rather than crash on.

```kotlin
fun submitName() {
    viewModelScope.launch {
        // ... existing validation ...
        val uid = auth.currentUser?.uid ?: return@launch

        // DEFENSIVE: check if user doc already exists.
        // This shouldn't happen in normal flows, but if it does,
        // we don't want to throw "Something went wrong."
        val existsResult = userRepository.userDocExists(uid)
        existsResult.onSuccess { exists ->
            if (exists) {
                Timber.w("OnboardingViewModel: user doc already exists for $uid; skipping createUser")
                _state.update { it.copy(isComplete = true) }
                return@onSuccess
            }
            // Normal path: create the doc.
            userRepository.createUser(...)
                .onSuccess { _state.update { it.copy(isComplete = true) } }
                .onFailure { e ->
                    Timber.e(e, "createUser failed")
                    _state.update { it.copy(error = "Something went wrong. Please try again.") }
                }
        }
    }
}
```

──── InvigilatorNavHost.kt — modifications ────

This is the wiring core of the fix. Three key changes:

1. **SplashViewModel route to Welcome (not RoleSelect) when signed out.**
   In the splash routing logic, the "signed out" branch should now
   navigate to Route.Welcome, not Route.RoleSelect.

2. **Welcome route wired up:**

```kotlin
composable<Route.Welcome> {
    WelcomeRoute(
        onSignIn = {
            navController.navigate(Route.PhoneEntry(flow = AuthFlow.SIGN_IN))
        },
        onCreateAccount = {
            navController.navigate(Route.RoleSelect)
        },
    )
}
```

3. **Modify Phone and OTP composables to pass and read the flow:**

```kotlin
composable<Route.PhoneEntry> { backStackEntry ->
    val args = backStackEntry.toRoute<Route.PhoneEntry>()
    PhoneEntryRoute(
        flow = args.flow,
        onCodeSent = { phoneE164 ->
            navController.navigate(Route.OtpEntry(flow = args.flow, phoneE164 = phoneE164))
        },
    )
}

composable<Route.OtpEntry> { backStackEntry ->
    val args = backStackEntry.toRoute<Route.OtpEntry>()
    OtpEntryRoute(
        onNewUserOtpComplete = {
            // Continue creating the user — go to NameEntry (the screen that
            // collects the last bit of new-user data and writes the doc).
            // If the new-user flow already collected name BEFORE OTP, then
            // this should navigate to NameEntry. If it collected name AFTER
            // OTP, then this should navigate to whatever the post-OTP step
            // is. Use whatever pattern matches Phase 3's existing flow.
            navController.navigate(Route.NameEntry)
        },
        onSignInComplete = { role ->
            val home = when (role) {
                UserRole.STUDENT -> Route.StudentHome
                UserRole.PARENT -> Route.ParentHome
            }
            navController.navigate(home) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        },
        onResumeConsent = { userDoc ->
            // Route based on accountStatus, role, age — same logic as
            // SplashViewModel uses. Easiest: navigate to a "resolver"
            // route that looks at userDoc and picks the right next screen.
            // OR: replicate SplashViewModel's branching here.
            // Simplest: route to Splash, which will re-evaluate and route
            // correctly. Splash sees signed-in + pending_consent and does
            // its existing thing.
            navController.navigate(Route.Splash) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        },
    )
}
```

The `onNewUserOtpComplete` lambda needs to navigate to whatever the
NEXT step is in the new-user flow after OTP. Look at Sprint 2 Phase 3
to see if Name was collected before or after OTP. If before (likely),
then onNewUserOtpComplete should navigate to consent; if after, to
NameEntry.

──── Phone Entry — pass flow through ────

PhoneEntryRoute already exists. Modify it to accept `flow: AuthFlow`
and pass it through to its onCodeSent navigation. PhoneEntryViewModel
itself doesn't need to know about flow — it just collects and validates
the phone number, requests OTP, and reports back.

In PhoneEntryRoute:
```kotlin
@Composable
fun PhoneEntryRoute(
    flow: AuthFlow,
    onCodeSent: (phoneE164: String) -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    // ... existing logic, just pass flow to navigation in the LaunchedEffect ...
}
```

═══════════════════════════════════════════════════════════════════════
TESTS
═══════════════════════════════════════════════════════════════════════

Unit tests to update / add:

OtpEntryViewModelTest:
- new_user_flow_routes_to_create_user_after_otp_success
- sign_in_flow_with_active_user_routes_to_go_to_home
- sign_in_flow_with_pending_consent_user_routes_to_resume_consent
- sign_in_flow_with_no_user_doc_signs_out_and_shows_error

OnboardingViewModelTest:
- existing_user_doc_does_not_throw_error (the defensive change)
- createUser_only_called_when_doc_does_not_exist

Don't add new tests for WelcomeScreen — it's stateless.

═══════════════════════════════════════════════════════════════════════
BUILD AND VERIFY
═══════════════════════════════════════════════════════════════════════

```bash
./gradlew lint
./gradlew testDevDebugUnitTest
```

Both must pass before commit.

═══════════════════════════════════════════════════════════════════════
COMMIT AND PUSH (MANDATORY)
═══════════════════════════════════════════════════════════════════════

Commit message: "fix(auth): welcome screen with separate sign-in path for returning users"

Then: `git push origin main`

Push is non-negotiable. Same OneDrive lesson still applies.

═══════════════════════════════════════════════════════════════════════
WHAT THE HUMAN WILL TEST (5 scenarios on real phone)
═══════════════════════════════════════════════════════════════════════

After APK install, run all five tests in order. Record pass/fail for each.

**Test 1 — Fresh install, new student signup (regression check):**
1. Sign out / clear app data
2. Launch app → see Welcome screen
3. Tap "Create an account" → RoleSelect
4. Pick Student → DOB (any date) → Phone → OTP → Name → Continue
5. Should land on Consent screen (or whichever post-name screen the new-user flow goes to)
6. Complete onboarding fully

**Test 2 — Active student signs out, signs back in:**
1. Use the user from Test 1, now active
2. Tap logout → returns to Welcome
3. Tap "I have an account"
4. Phone → enter same number → OTP → enter code
5. Should immediately route to StudentHome (no DOB, no Name, no error)

**Test 3 — Active parent signs out, signs back in:**
1. Use a known active parent account
2. Logout → Welcome
3. Tap "I have an account" → Phone → OTP
4. Should immediately route to ParentHome

**Test 4 — Pending-consent user signs out, signs back in:**
1. Create a new user up to OTP entry but DON'T complete consent
2. From the consent screen, force-quit the app
3. Re-launch → SplashViewModel should detect pending_consent and resume
4. Now: sign out from wherever you are
5. Welcome → "I have an account" → Phone → OTP
6. Should resume the consent flow (not start onboarding from scratch)

**Test 5 — Sign-in attempt with unknown number:**
1. From Welcome, tap "I have an account"
2. Enter a phone number that has never registered
3. Get the OTP somehow (test phone numbers configured in Firebase)
4. Enter OTP → should show error "We don't recognize this number..."
5. Should NOT silently create a new user
6. Should NOT crash
7. Tapping back / "Wrong number?" should return to a usable state

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- DO NOT modify consent flow, linking flow, or session features.
- DO NOT modify Firestore schema or rules.
- DO NOT remove RoleSelectScreen or DOBEntryScreen — they're still
  used in the new-user path.
- DO NOT change the OTP-verification logic itself, only the
  post-success branching.
- The `userDocExists` check in OnboardingViewModel.submitName() is
  defensive only; do not remove it even if it seems redundant.
- If the existing PhoneEntry/OtpEntry routes already have parameters
  (e.g. for phone number passing), preserve those and ADD the flow
  parameter alongside. Don't replace, augment.
- Use stateIn(SharingStarted.Eagerly) on any new ViewModel that uses
  combine.
- Run `./gradlew lint` BEFORE running tests.

When done, print:
  - Files modified (list)
  - Test count and pass/fail
  - Lint output (clean or what was fixed)
  - Confirmation git push succeeded
  - Any deviations from this prompt and why
