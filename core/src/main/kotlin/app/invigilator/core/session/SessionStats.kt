package app.invigilator.core.session

data class SessionStats(
    val distractionCount: Int = 0,
    val distractionEvents: List<DistractionEvent> = emptyList(),
    val interventions: List<InterventionRecord> = emptyList(),
) {
    val nudgeCount: Int
        get() = interventions.count { it.type == "FIRST_NUDGE" || it.type == "SECOND_NUDGE" }

    val nagCount: Int
        get() = interventions.count { it.type == "NAG" }
}

data class DistractionEvent(
    val packageName: String,
    val category: AppCategory,        // DISTRACTING or UNKNOWN
    val enteredAtMillis: Long,
    val exitedAtMillis: Long,
    val dwellSeconds: Long,
)
