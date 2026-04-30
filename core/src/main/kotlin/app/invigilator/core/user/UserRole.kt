package app.invigilator.core.user

enum class UserRole(val firestoreValue: String) {
    PARENT("parent"),
    STUDENT("student");

    companion object {
        fun fromFirestore(value: String): UserRole? =
            entries.find { it.firestoreValue == value }
    }
}
