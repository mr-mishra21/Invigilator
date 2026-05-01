package app.invigilator.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {

    // ── Top-level ─────────────────────────────────────────────────────────────
    @Serializable data object Splash : Route

    // ── Onboarding graph (nested — OnboardingViewModel is scoped here) ────────
    @Serializable data object OnboardingGraph : Route
    @Serializable data object RoleSelect : Route
    @Serializable data class  DobEntry(val role: String) : Route
    @Serializable data class  PhoneEntry(val role: String) : Route
    @Serializable data class  OtpEntry(val role: String, val phone: String) : Route
    @Serializable data object NameEntry : Route

    // ── Post-onboarding (Phase 4) ─────────────────────────────────────────────
    @Serializable data class  Consent(val type: String) : Route
    @Serializable data object StudentShareCode : Route
    @Serializable data object StudentLinkingPending : Route
    @Serializable data object ParentEnterCode : Route

    // ── Home ──────────────────────────────────────────────────────────────────
    @Serializable data object ParentHome : Route
    @Serializable data object StudentHome : Route
}
