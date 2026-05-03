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

    // ── Post-onboarding ───────────────────────────────────────────────────────
    @Serializable data class  Consent(
        val type: String,
        /** Set only for PARENT_FOR_MINOR; passed through so onComplete can start the batch write. */
        val studentUid: String = "",
        val studentDisplayName: String = "",
    ) : Route
    @Serializable data object StudentShareCode : Route
    @Serializable data object StudentLinkingPending : Route
    @Serializable data object ParentEnterCode : Route
    /** Shows student name + age for parent to confirm before consenting. */
    @Serializable data class  ConfirmStudent(
        val studentUid: String,
        val studentName: String,
        val studentDobMillis: Long,
    ) : Route
    /** Calls the completeLinking Cloud Function after parent consent. */
    @Serializable data class  ParentLinkingComplete(
        val studentUid: String,
        val studentDisplayName: String,
        val consentId: String,
    ) : Route

    // ── Session ───────────────────────────────────────────────────────────────
    @Serializable data object StartSession : Route
    @Serializable data object Permissions : Route

    // ── Home ──────────────────────────────────────────────────────────────────
    @Serializable data object ParentHome : Route
    @Serializable data object StudentHome : Route
}
