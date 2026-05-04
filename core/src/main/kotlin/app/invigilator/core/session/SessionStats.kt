package app.invigilator.core.session

data class SessionStats(
    val distractionCount: Int = 0,
    val distractionEvents: List<DistractionEvent> = emptyList(),
)

data class DistractionEvent(
    val packageName: String,
    val category: AppCategory,        // DISTRACTING or UNKNOWN
    val enteredAtMillis: Long,
    val exitedAtMillis: Long,
    val dwellSeconds: Long,
)
