package app.invigilator.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.invigilator.core.user.UserRole
import app.invigilator.ui.auth.OtpEntryRoute
import app.invigilator.ui.auth.PhoneEntryRoute
import app.invigilator.ui.home.ParentHomeScreen
import app.invigilator.ui.home.StudentHomeScreen
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
                        navController.navigate(Route.Consent("AdultStudentSelfConsent")) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onMinorStudentShareCode = {
                        navController.navigate(Route.StudentShareCode) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onParentConsent = {
                        navController.navigate(Route.Consent("ParentTermsOfService")) {
                            popUpTo(Route.OnboardingGraph) { inclusive = true }
                        }
                    },
                    onboardingViewModel = onboardingVm,
                )
            }
        }

        // ── Phase 4 placeholders ──────────────────────────────────────────────
        composable<Route.Consent> {
            PlaceholderScreen("Phase 4: consent flow not yet implemented")
        }

        composable<Route.StudentShareCode> {
            PlaceholderScreen("Phase 4: student share-code screen not yet implemented")
        }

        composable<Route.StudentLinkingPending> {
            PlaceholderScreen("Phase 4: linking pending screen not yet implemented")
        }

        composable<Route.ParentEnterCode> {
            PlaceholderScreen("Phase 4: parent enter-code screen not yet implemented")
        }

        // ── Home ──────────────────────────────────────────────────────────────
        composable<Route.ParentHome> { ParentHomeScreen() }
        composable<Route.StudentHome> { StudentHomeScreen() }
    }
}

@Composable
private fun PlaceholderScreen(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}
