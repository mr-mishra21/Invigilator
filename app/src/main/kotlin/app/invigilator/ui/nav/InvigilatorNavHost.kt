package app.invigilator.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.invigilator.core.consent.ConsentType
import app.invigilator.core.user.UserRole
import app.invigilator.ui.auth.OtpEntryRoute
import app.invigilator.ui.auth.PhoneEntryRoute
import app.invigilator.ui.consent.ConsentRoute
import app.invigilator.ui.home.ParentHomeRoute
import app.invigilator.ui.home.StudentHomeRoute
import app.invigilator.ui.linking.ConfirmStudentScreen
import app.invigilator.ui.linking.EnterCodeRoute
import app.invigilator.ui.linking.LinkingCompletionRoute
import app.invigilator.ui.linking.StudentShareCodeRoute
import app.invigilator.ui.onboarding.DobEntryRoute
import app.invigilator.ui.onboarding.NameEntryRoute
import app.invigilator.ui.onboarding.OnboardingEvent
import app.invigilator.ui.onboarding.OnboardingViewModel
import app.invigilator.ui.onboarding.RoleSelectRoute
import app.invigilator.ui.splash.SplashRoute

@Composable
fun InvigilatorNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
    ) {
        // ── Splash ────────────────────────────────────────────────────────────
        composable<Route.Splash> {
            SplashRoute(
                onNavigateToOnboarding = {
                    navController.navigate(Route.OnboardingGraph) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToParentHome = {
                    navController.navigate(Route.ParentHome) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToStudentHome = {
                    navController.navigate(Route.StudentHome) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToAdultStudentConsent = {
                    navController.navigate(
                        Route.Consent(ConsentType.ADULT_STUDENT_SELF.firestoreValue)
                    ) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToStudentShareCode = {
                    navController.navigate(Route.StudentShareCode) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
                onNavigateToParentConsent = {
                    navController.navigate(
                        Route.Consent(ConsentType.PARENT_TERMS_OF_SERVICE.firestoreValue)
                    ) {
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
            )
        }

        // ── Onboarding graph (OnboardingViewModel is scoped to this graph) ───
        navigation<Route.OnboardingGraph>(startDestination = Route.RoleSelect) {

            composable<Route.RoleSelect> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Route.OnboardingGraph>()
                }
                val onboardingVm: OnboardingViewModel = hiltViewModel(parentEntry)

                RoleSelectRoute(
                    onRoleSelected = { role ->
                        if (role == UserRole.STUDENT.firestoreValue) {
                            navController.navigate(Route.DobEntry(role))
                        } else {
                            navController.navigate(Route.PhoneEntry(role))
                        }
                    },
                    onboardingViewModel = onboardingVm,
                )
            }

            composable<Route.DobEntry> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.DobEntry>()
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Route.OnboardingGraph>()
                }
                val onboardingVm: OnboardingViewModel = hiltViewModel(parentEntry)

                DobEntryRoute(
                    role = route.role,
                    onDobConfirmed = {
                        navController.navigate(Route.PhoneEntry(route.role))
                    },
                    onboardingViewModel = onboardingVm,
                )
            }

            composable<Route.PhoneEntry> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.PhoneEntry>()

                PhoneEntryRoute(
                    role = route.role,
                    onOtpSent = { normalizedPhone ->
                        navController.navigate(Route.OtpEntry(route.role, normalizedPhone))
                    },
                )
            }

            composable<Route.OtpEntry> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.OtpEntry>()
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Route.OnboardingGraph>()
                }
                val onboardingVm: OnboardingViewModel = hiltViewModel(parentEntry)

                OtpEntryRoute(
                    role = route.role,
                    phone = route.phone,
                    onVerified = { uid ->
                        onboardingVm.onEvent(OnboardingEvent.UidReceived(uid))
                        navController.navigate(Route.NameEntry)
                    },
                    onWrongNumber = {
                        navController.popBackStack(Route.PhoneEntry(route.role), inclusive = false)
                    },
                )
            }

            composable<Route.NameEntry> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Route.OnboardingGraph>()
                }
                val onboardingVm: OnboardingViewModel = hiltViewModel(parentEntry)

                NameEntryRoute(
                    onAdultStudentConsent = {
                        navController.navigate(
                            Route.Consent(ConsentType.ADULT_STUDENT_SELF.firestoreValue)
                        ) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onMinorStudentShareCode = {
                        navController.navigate(Route.StudentShareCode) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onParentConsent = {
                        navController.navigate(
                            Route.Consent(ConsentType.PARENT_TERMS_OF_SERVICE.firestoreValue)
                        ) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onboardingViewModel = onboardingVm,
                )
            }
        }

        // ── Consent (shared, parameterized by type string) ────────────────────
        composable<Route.Consent> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Consent>()

            ConsentRoute(
                onComplete = { consentId ->
                    when (route.type) {
                        ConsentType.ADULT_STUDENT_SELF.firestoreValue -> {
                            navController.navigate(Route.StudentHome) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        ConsentType.PARENT_TERMS_OF_SERVICE.firestoreValue -> {
                            navController.navigate(Route.ParentHome) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        ConsentType.PARENT_FOR_MINOR.firestoreValue -> {
                            navController.navigate(
                                Route.ParentLinkingComplete(
                                    studentUid = route.studentUid,
                                    studentDisplayName = route.studentDisplayName,
                                    consentId = consentId,
                                )
                            ) { popUpTo(0) { inclusive = true } }
                        }
                        else -> {
                            navController.navigate(Route.ParentHome) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                },
            )
        }

        // ── Student share code (minor linking, student side) ──────────────────
        composable<Route.StudentShareCode> {
            StudentShareCodeRoute(
                onNavigateToStudentHome = {
                    navController.navigate(Route.StudentHome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    navController.navigate(Route.OnboardingGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.StudentLinkingPending> {
            ShareCodePlaceholder()
        }

        // ── Parent enter code (minor linking, parent side) ────────────────────
        composable<Route.ParentEnterCode> {
            EnterCodeRoute(
                onStudentConfirmed = { claim ->
                    navController.navigate(
                        Route.ConfirmStudent(
                            studentUid = claim.studentUid,
                            studentName = claim.studentDisplayName,
                            studentDobMillis = claim.studentDateOfBirthMillis ?: 0L,
                        )
                    )
                },
                onLoggedOut = {
                    navController.navigate(Route.OnboardingGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Confirm student (parent side) ─────────────────────────────────────
        composable<Route.ConfirmStudent> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ConfirmStudent>()
            ConfirmStudentScreen(
                studentName = route.studentName,
                studentDateOfBirthMillis = route.studentDobMillis.takeIf { it > 0L },
                onConfirmed = {
                    navController.navigate(
                        Route.Consent(
                            type = ConsentType.PARENT_FOR_MINOR.firestoreValue,
                            studentUid = route.studentUid,
                            studentDisplayName = route.studentName,
                        )
                    )
                },
                onNotMyChild = { navController.popBackStack() },
            )
        }

        // ── Linking completion (batch write after PARENT_FOR_MINOR consent) ───
        composable<Route.ParentLinkingComplete> {
            LinkingCompletionRoute(
                onNavigateToParentHome = {
                    navController.navigate(Route.ParentHome) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable<Route.ParentHome> {
            ParentHomeRoute(
                onLoggedOut = {
                    navController.navigate(Route.OnboardingGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEnterCode = {
                    navController.navigate(Route.ParentEnterCode)
                },
            )
        }

        composable<Route.StudentHome> {
            StudentHomeRoute(
                onLoggedOut = {
                    navController.navigate(Route.OnboardingGraph) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
