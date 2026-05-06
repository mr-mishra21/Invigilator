package app.invigilator.core.intervention

/**
 * Per-app-visit intervention escalation state. Resets to NONE when
 * the user switches to a different app, or when the session ends.
 *
 * Order matters: levels can only escalate forward in a single visit.
 */
enum class InterventionLevel {
    NONE,                    // < 30s on the app, or app is essential/study
    DISTRACTION_RECORDED,    // ≥ 30s on a distractor — Sprint 3 behavior
    NUDGED_ONCE,             // ≥ 60s — first voice nudge fired
    NUDGED_TWICE,            // ≥ 120s — second voice nudge fired
    NAGGED,                  // ≥ 180s — visible nag fired (Phase 3)
    ;
}
