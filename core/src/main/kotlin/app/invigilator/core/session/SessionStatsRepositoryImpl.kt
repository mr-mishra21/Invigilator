package app.invigilator.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionStatsRepositoryImpl @Inject constructor() : SessionStatsRepository {
    private val _stats = MutableStateFlow(SessionStats())
    override val stats: StateFlow<SessionStats> = _stats.asStateFlow()

    override fun recordDistractionEvent(event: DistractionEvent) {
        _stats.update { current ->
            current.copy(
                distractionCount = current.distractionCount + 1,
                distractionEvents = current.distractionEvents + event,
            )
        }
    }

    override fun reset() {
        _stats.value = SessionStats()
    }
}
