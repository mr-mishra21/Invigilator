package app.invigilator.core.consent

/**
 * Single source of truth for which consent version is current for each type.
 * When a consent text changes, bump the version here AND rename the asset file.
 * The [ConsentHashes] object (generated at build time) will update automatically.
 */
object ConsentVersions {
    val CURRENT: Map<ConsentType, String> = mapOf(
        ConsentType.ADULT_STUDENT_SELF to "v1.0",
        ConsentType.PARENT_FOR_MINOR to "v1.0",
        ConsentType.PARENT_TERMS_OF_SERVICE to "v1.0",
        // PARENT_MONITORING_ADULT_STUDENT: deferred to Sprint 3
    )

    fun currentVersionFor(type: ConsentType): String =
        CURRENT[type] ?: error("No current version registered for $type")
}
