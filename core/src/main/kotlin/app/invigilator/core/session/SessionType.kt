package app.invigilator.core.session

sealed class SessionType {
    data class Timed(val durationMinutes: Int) : SessionType()
    data object OpenEnded : SessionType()
}
