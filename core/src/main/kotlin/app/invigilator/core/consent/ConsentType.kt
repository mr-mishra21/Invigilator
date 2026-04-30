package app.invigilator.core.consent

enum class ConsentType(
    val firestoreValue: String,
    /** Prefix used to look up consent asset files, e.g. "AdultStudentSelfConsent_v1.0_en.txt". */
    val assetPrefix: String,
) {
    ADULT_STUDENT_SELF(
        firestoreValue = "AdultStudentSelfConsent",
        assetPrefix = "AdultStudentSelfConsent",
    ),
    PARENT_FOR_MINOR(
        firestoreValue = "ParentForMinorConsent",
        assetPrefix = "ParentForMinorConsent",
    ),
    /** Deferred to Sprint 3 — no asset file yet, but the data model supports it. */
    PARENT_MONITORING_ADULT_STUDENT(
        firestoreValue = "ParentMonitoringAdultStudent",
        assetPrefix = "ParentMonitoringAdultStudent",
    ),
    PARENT_TERMS_OF_SERVICE(
        firestoreValue = "ParentTermsOfService",
        assetPrefix = "ParentTermsOfService",
    );

    companion object {
        fun fromFirestore(value: String): ConsentType? =
            entries.find { it.firestoreValue == value }
    }
}
