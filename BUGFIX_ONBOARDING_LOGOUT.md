# Bug Fix Session — Logout on Onboarding Waiting Screens

This is a small, scoped fix. Do exactly what's described below and
nothing else. Do not start Sprint 3 work. Do not refactor unrelated code.

═══════════════════════════════════════════════════════════════════════
CONTEXT
═══════════════════════════════════════════════════════════════════════

Phase 4 added logout to ParentHomeScreen and StudentHomeScreen, but a
user in `pending_consent` state never reaches those screens — they sit
on onboarding screens (StudentShareCode, EnterCode, etc.) with no way
to log out. The only current escape is to delete the user from Firebase
console manually. This blocks all Phase 4 testing.

The splash routing itself is working correctly (verified by Logcat in
the previous session). The fix is purely about adding logout entry
points to three onboarding screens.

═══════════════════════════════════════════════════════════════════════
STEP 1 — Identify the three target screens
═══════════════════════════════════════════════════════════════════════

Read the codebase and confirm these three screens exist. Print the
file paths so we can verify alignment:

  1. StudentShareCodeScreen — student displays the 6-digit code,
     waits for parent. May appear under a route called something like
     StudentShareCode or StudentShareCodeResume (or both).

  2. EnterCodeScreen — parent enters the 6-digit code from the student.

  3. Any "linking pending" screen — student waiting for parent to
     finish signing the consent. May or may not exist as a separate
     screen depending on Phase 4 implementation. If StudentShareCode
     handles both states (showing code AND waiting after claim), then
     there is only one student-side waiting screen.

Print the file paths. Do not modify anything yet.

═══════════════════════════════════════════════════════════════════════
STEP 2 — Add a small reusable composable for the logout overflow menu
═══════════════════════════════════════════════════════════════════════

Avoid copy-pasting the menu into 3 screens. Create one shared composable
in :app under ui/common/:

```kotlin
// File: app/src/main/kotlin/app/invigilator/ui/common/OnboardingLogoutMenu.kt

@Composable
fun OnboardingLogoutMenu(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_logout)) },
                onClick = {
                    menuOpen = false
                    confirmOpen = true
                },
            )
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_onboarding_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.action_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
```

Add the matching string resources (use the EXISTING ones if they
already exist for home-screen logout; create if not):

  cd_more_options                    → "More options"
  action_logout                      → "Log out"
  action_cancel                      → "Cancel"
  logout_confirm_title               → "Log out?"
  logout_confirm_onboarding_body     → "Your progress so far will be saved. You can sign back in with the same phone number to continue."

Add the same keys to values-as, values-hi, values-bn with reasonable
translations marked [DRAFT] if uncertain.

═══════════════════════════════════════════════════════════════════════
STEP 3 — Wire the menu into the three target screens
═══════════════════════════════════════════════════════════════════════

For each of the three screens identified in Step 1, add a TopAppBar
(if one doesn't exist) with the OnboardingLogoutMenu in its actions slot.

Each screen's ViewModel needs a `logout()` function:

```kotlin
fun logout() {
    viewModelScope.launch {
        authRepository.signOut()
        // No need to navigate — SplashViewModel handles routing on
        // next composition because authState becomes SignedOut.
        // But navigation isn't automatic from this screen; the caller
        // (Route composable) listens for authState change.
    }
}
```

The cleanest way to handle navigation after logout: in each Route
composable, observe `authState` from AuthRepository and call
`onLoggedOut()` when it becomes `SignedOut`. That callback in the nav
graph does:

```kotlin
navController.navigate(Route.RoleSelect) {
    popUpTo(navController.graph.id) { inclusive = true }
}
```

This pops the entire onboarding back stack so the user starts fresh
from RoleSelect.

If the existing home-screen logout already uses this pattern, COPY IT
exactly. Do not invent a new pattern. Consistency matters more than
elegance for a 6-line piece of glue code.

═══════════════════════════════════════════════════════════════════════
STEP 4 — Verify no Firestore deletion on logout
═══════════════════════════════════════════════════════════════════════

Confirm that AuthRepository.signOut() does NOT delete the user's
Firestore document. It should ONLY clear Firebase Auth state. Read the
implementation and verify. The user's /users/{uid} document must
remain intact so they can resume on next login.

If signOut() does delete Firestore data anywhere, REMOVE that deletion
code. We do not want logout to destroy progress. Account deletion is a
separate Sprint 6 feature.

═══════════════════════════════════════════════════════════════════════
STEP 5 — Manual verification on device (you, not Claude Code)
═══════════════════════════════════════════════════════════════════════

After the fix is committed, the human will:

1. Launch the app on a device with the existing minor-student account
   (the one from the Logcat — uid 8pS8mTpNyGhnUzKyb5t6VX9831g2).
2. Splash should route to StudentShareCode (existing state).
3. Tap the overflow menu in the top-right.
4. Tap "Log out" → confirm in dialog.
5. App should navigate back to RoleSelect.
6. Verify in Firebase console that:
   - Auth: user is signed out (no active session for that uid)
   - Firestore: /users/{uid} document still exists
7. Sign back in with the same phone number + OTP.
8. Splash should route back to StudentShareCode (resumed state).

If any of those fail, paste the result in the conversation.

═══════════════════════════════════════════════════════════════════════
COMMIT MESSAGE
═══════════════════════════════════════════════════════════════════════

"fix: logout entry point on onboarding waiting screens"

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- Do NOT add logout to ConsentScreen. The user can press back; we'll
  handle "cancel mid-consent" in a later sprint.
- Do NOT modify ParentHomeScreen or StudentHomeScreen. Their existing
  logout works.
- Do NOT change splash routing logic. It works correctly.
- Do NOT delete any Firestore data on logout.
- Remove all leftover // DEBUG Timber logs from the previous diagnostic
  session — they served their purpose and should not ship.

When done, print:
  - file paths modified
  - whether the leftover DEBUG logs were removed (yes/no)
  - any deviations from this prompt and why
  - confirmation that home-screen logout still works (do not break it)
