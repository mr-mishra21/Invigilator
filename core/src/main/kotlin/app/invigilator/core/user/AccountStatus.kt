package app.invigilator.core.user

enum class AccountStatus(val firestoreValue: String) {
    PENDING_CONSENT("pending_consent"),
    ACTIVE("active"),
    SUSPENDED("suspended");

    companion object {
        fun fromFirestore(value: String): AccountStatus? =
            entries.find { it.firestoreValue == value }
    }
}
