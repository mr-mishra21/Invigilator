# Sprint 3 Phase 3 — Distraction Classification and Dwell Tracking

Time estimate: 1.5h Claude Code + 30min testing.

What gets built: A classifier that maps package names to STUDY / ESSENTIAL /
DISTRACTING / UNKNOWN, dwell-time tracking that only counts a distraction
when the student stays for ≥30 seconds, an in-memory stats repository, and
classifier-aware Logcat output.

What's NOT built: Firestore writes (Phase 4), session timer expiry (Phase 4),
celebration / summary screen (Phase 4), on-screen distraction display (deferred).

═══════════════════════════════════════════════════════════════════════
DELIVERABLES
═══════════════════════════════════════════════════════════════════════

1. New files in :core:
   - core/src/main/kotlin/app/invigilator/core/session/AppCategory.kt
   - core/src/main/kotlin/app/invigilator/core/session/DistractionClassifier.kt
   - core/src/main/kotlin/app/invigilator/core/session/SessionStats.kt
   - core/src/main/kotlin/app/invigilator/core/session/SessionStatsRepository.kt
   - core/src/main/kotlin/app/invigilator/core/session/SessionStatsRepositoryImpl.kt

2. Modified:
   - feature-blocker/.../SessionMonitorService.kt — call classifier, track dwell
   - core/src/main/kotlin/.../core/di/CoreModule.kt — bind new repository

3. New test files:
   - core/src/test/kotlin/.../core/session/DistractionClassifierTest.kt
   - core/src/test/kotlin/.../core/session/SessionStatsRepositoryImplTest.kt

═══════════════════════════════════════════════════════════════════════
EXACT SPECIFICATIONS
═══════════════════════════════════════════════════════════════════════

──── AppCategory.kt ────

```kotlin
package app.invigilator.core.session

enum class AppCategory {
    STUDY,
    ESSENTIAL,
    DISTRACTING,
    UNKNOWN,
}
```

──── DistractionClassifier.kt ────

```kotlin
package app.invigilator.core.session

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DistractionClassifier @Inject constructor() {

    fun classify(packageName: String): AppCategory {
        return when {
            packageName == "app.invigilator" -> AppCategory.STUDY
            packageName in STUDY_APPS -> AppCategory.STUDY
            packageName in ESSENTIAL_APPS -> AppCategory.ESSENTIAL
            packageName.startsWith("com.android.systemui") -> AppCategory.ESSENTIAL
            packageName.startsWith("com.android.launcher") -> AppCategory.ESSENTIAL
            packageName.endsWith(".launcher") -> AppCategory.ESSENTIAL
            packageName in DISTRACTING_APPS -> AppCategory.DISTRACTING
            else -> AppCategory.UNKNOWN
        }
    }

    private companion object {
        // Study apps — Indian-context aware
        val STUDY_APPS = setOf(
            // Generic study tools
            "org.khanacademy.android",
            "com.google.android.apps.docs",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
            "com.google.android.keep",
            "org.wikipedia",
            // India-focused
            "com.byjus.thelearningapp",
            "com.unacademy.consumption.unacademyapp",
            "com.toppr.app",
            "com.vedantu.app",
            "com.physicswallah.app",
            // Reading
            "com.amazon.kindle",
            "com.google.android.apps.books",
        )

        val ESSENTIAL_APPS = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.settings",
            "com.google.android.deskclock",   // Clock / Alarm
            "com.android.deskclock",
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.android.calculator2",
            "com.google.android.calculator",
            "com.android.messaging",          // Stock SMS
            "com.google.android.apps.messaging",
            "com.android.emergency",
        )

        val DISTRACTING_APPS = setOf(
            // Social
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",       // TikTok
            "com.facebook.katana",
            "com.facebook.lite",
            "com.twitter.android",
            "com.reddit.frontpage",
            // Video / streaming
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "in.startv.hotstar",
            "com.amazon.avod.thirdpartyclient",
            // Messaging-as-distraction
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",              // Messenger
            "org.telegram.messenger",
            "com.discord",
            // Games (popular in India)
            "com.dts.freefireth",
            "com.tencent.ig",                 // PUBG / BGMI
            "com.activision.callofduty.shooter",
            "com.king.candycrushsaga",
            "com.supercell.clashofclans",
            "com.ludo.king",
            // Shopping / browsing distractions
            "in.amazon.mShop.android.shopping",
            "com.flipkart.android",
            "com.myntra.android",
        )
    }
}
```

──── SessionStats.kt ────

```kotlin
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
```

──── SessionStatsRepository.kt ────

```kotlin
package app.invigilator.core.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks distraction events during the current session.
 * Cleared when the session ends.
 */
interface SessionStatsRepository {
    val stats: StateFlow<SessionStats>

    /** Record that a distraction event has completed (student left the distracting app). */
    fun recordDistractionEvent(event: DistractionEvent)

    /** Reset stats to zero. Called at session start and end. */
    fun reset()
}
```

──── SessionStatsRepositoryImpl.kt ────

```kotlin
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
```

──── CoreModule.kt — add binding ────

In the existing `@Module @InstallIn(SingletonComponent::class) abstract class CoreModule`,
add:

```kotlin
@Binds
@Singleton
abstract fun bindSessionStatsRepository(
    impl: SessionStatsRepositoryImpl
): SessionStatsRepository
```

(DistractionClassifier doesn't need a binding — it's already `@Singleton @Inject`-annotated
and Hilt will provide it directly.)

──── SessionMonitorService.kt — modifications ────

Add two new injected dependencies:

```kotlin
@Inject lateinit var classifier: DistractionClassifier
@Inject lateinit var sessionStats: SessionStatsRepository
```

Add new state fields at the top of the class:

```kotlin
companion object {
    private const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "session_monitor"
    private const val POLL_INTERVAL_MS = 2000L
    private const val DISTRACTION_DWELL_THRESHOLD_MS = 30_000L  // 30 seconds
    const val ACTION_STOP = "app.invigilator.action.STOP_SESSION"
}

private var currentApp: String? = null
private var currentAppCategory: AppCategory = AppCategory.UNKNOWN
private var currentAppEnteredAt: Long = 0L
```

Replace the existing `detectForegroundApp()` function with this version:

```kotlin
private fun detectForegroundApp() {
    val now = System.currentTimeMillis()
    val events = usageStatsManager.queryEvents(now - POLL_INTERVAL_MS - 1000, now)
    var lastEventPackage: String? = null
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            lastEventPackage = event.packageName
        }
    }

    if (lastEventPackage != null && lastEventPackage != currentApp) {
        // The student switched apps. Close the previous app's dwell window.
        finalizePreviousApp(now)

        // Start tracking the new app.
        currentApp = lastEventPackage
        currentAppCategory = classifier.classify(lastEventPackage)
        currentAppEnteredAt = now

        Timber.d("SessionMonitor: foreground app -> $lastEventPackage ($currentAppCategory)")
    }
}

/**
 * When the student leaves an app, decide whether the time spent there
 * counts as a distraction event. Only counts if:
 *   - the app was DISTRACTING or UNKNOWN
 *   - the dwell time was >= DISTRACTION_DWELL_THRESHOLD_MS
 */
private fun finalizePreviousApp(exitedAt: Long) {
    val previous = currentApp ?: return
    val dwellMs = exitedAt - currentAppEnteredAt
    val dwellSeconds = dwellMs / 1000

    val countsAsDistraction = (
        currentAppCategory == AppCategory.DISTRACTING ||
        currentAppCategory == AppCategory.UNKNOWN
    ) && dwellMs >= DISTRACTION_DWELL_THRESHOLD_MS

    if (countsAsDistraction) {
        sessionStats.recordDistractionEvent(
            DistractionEvent(
                packageName = previous,
                category = currentAppCategory,
                enteredAtMillis = currentAppEnteredAt,
                exitedAtMillis = exitedAt,
                dwellSeconds = dwellSeconds,
            )
        )
        Timber.d("SessionMonitor: distraction recorded — $previous ($currentAppCategory) for ${dwellSeconds}s")
    } else if (currentAppCategory == AppCategory.DISTRACTING || currentAppCategory == AppCategory.UNKNOWN) {
        Timber.d("SessionMonitor: brief check (not counted) — $previous for ${dwellSeconds}s")
    }
}
```

Also modify `stopMonitoring()` to finalize the last app before clearing state:

```kotlin
private fun stopMonitoring() {
    finalizePreviousApp(System.currentTimeMillis())  // ADD THIS LINE
    monitorJob?.cancel()
    sessionState.endSession()
    sessionStats.reset()                              // ADD THIS LINE
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    Timber.d("SessionMonitor: stopped")
}
```

And modify `startMonitoring()` to reset stats when session begins:

```kotlin
private fun startMonitoring() {
    sessionStats.reset()                              // ADD THIS LINE
    currentApp = null                                  // ADD THIS LINE
    monitorJob?.cancel()
    monitorJob = scope.launch {
        while (isActive) {
            detectForegroundApp()
            delay(POLL_INTERVAL_MS)
        }
    }
    Timber.d("SessionMonitor: started polling")
}
```

═══════════════════════════════════════════════════════════════════════
TESTS
═══════════════════════════════════════════════════════════════════════

──── DistractionClassifierTest.kt ────

```kotlin
package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class DistractionClassifierTest {

    private val classifier = DistractionClassifier()

    @Test
    fun invigilator_itself_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("app.invigilator"))
    }

    @Test
    fun khan_academy_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("org.khanacademy.android"))
    }

    @Test
    fun byjus_is_study() {
        assertEquals(AppCategory.STUDY, classifier.classify("com.byjus.thelearningapp"))
    }

    @Test
    fun phone_dialer_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.google.android.dialer"))
    }

    @Test
    fun launcher_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.android.launcher3"))
    }

    @Test
    fun systemui_is_essential() {
        assertEquals(AppCategory.ESSENTIAL, classifier.classify("com.android.systemui"))
    }

    @Test
    fun instagram_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.instagram.android"))
    }

    @Test
    fun youtube_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.google.android.youtube"))
    }

    @Test
    fun whatsapp_is_distracting() {
        assertEquals(AppCategory.DISTRACTING, classifier.classify("com.whatsapp"))
    }

    @Test
    fun unknown_app_is_unknown() {
        assertEquals(AppCategory.UNKNOWN, classifier.classify("com.example.somerandomapp"))
    }
}
```

──── SessionStatsRepositoryImplTest.kt ────

```kotlin
package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsRepositoryImplTest {

    private val repo = SessionStatsRepositoryImpl()

    private fun makeEvent(pkg: String = "com.instagram.android", dwellSeconds: Long = 60) =
        DistractionEvent(
            packageName = pkg,
            category = AppCategory.DISTRACTING,
            enteredAtMillis = 1000L,
            exitedAtMillis = 1000L + dwellSeconds * 1000,
            dwellSeconds = dwellSeconds,
        )

    @Test
    fun initial_stats_are_zero() {
        assertEquals(0, repo.stats.value.distractionCount)
        assertTrue(repo.stats.value.distractionEvents.isEmpty())
    }

    @Test
    fun recordEvent_increments_count() {
        repo.recordDistractionEvent(makeEvent())
        assertEquals(1, repo.stats.value.distractionCount)
        assertEquals(1, repo.stats.value.distractionEvents.size)
    }

    @Test
    fun multiple_events_accumulate() {
        repo.recordDistractionEvent(makeEvent("com.instagram.android"))
        repo.recordDistractionEvent(makeEvent("com.google.android.youtube"))
        repo.recordDistractionEvent(makeEvent("com.whatsapp"))
        assertEquals(3, repo.stats.value.distractionCount)
    }

    @Test
    fun reset_clears_all_events() {
        repo.recordDistractionEvent(makeEvent())
        repo.recordDistractionEvent(makeEvent())
        repo.reset()
        assertEquals(0, repo.stats.value.distractionCount)
        assertTrue(repo.stats.value.distractionEvents.isEmpty())
    }
}
```

═══════════════════════════════════════════════════════════════════════
LINT — RUN BEFORE TESTS
═══════════════════════════════════════════════════════════════════════

After writing all the production code (before writing tests), run:

```bash
./gradlew lint
```

Fix anything that surfaces. Common Phase 3 lint risks:
- An unused import in SessionMonitorService.kt → remove it
- A missing import for AppCategory or DistractionClassifier → add it
- A "field could be made local" warning → ignore or refactor

If lint passes, then run tests. If lint fails, fix it before tests
(test compilation failures will mask lint output).

═══════════════════════════════════════════════════════════════════════
TESTS RUN
═══════════════════════════════════════════════════════════════════════

```bash
./gradlew :core:testDebugUnitTest
./gradlew testDevDebugUnitTest lint
```

Both must pass.

═══════════════════════════════════════════════════════════════════════
COMMIT AND PUSH (MANDATORY)
═══════════════════════════════════════════════════════════════════════

Commit message: "feat(session): distraction classifier with dwell tracking"

Then: git push origin main

Push is non-negotiable.

═══════════════════════════════════════════════════════════════════════
WHAT THE HUMAN WILL TEST (on real phone)
═══════════════════════════════════════════════════════════════════════

After APK install:

1. Open Logcat in Android Studio. Filter: `SessionMonitor`.
2. Sign in as student, start a Timed 25-min session.
3. Press home, open Chrome. Check Logcat:
   "SessionMonitor: foreground app -> com.android.chrome (UNKNOWN)"
4. Within 30 seconds, switch to YouTube. Check Logcat:
   "SessionMonitor: brief check (not counted) — com.android.chrome for Xs"
   "SessionMonitor: foreground app -> com.google.android.youtube (DISTRACTING)"
5. Stay in YouTube for >30 seconds (e.g. 45s).
6. Switch to phone dialer. Check Logcat:
   "SessionMonitor: distraction recorded — com.google.android.youtube (DISTRACTING) for 45s"
   "SessionMonitor: foreground app -> com.google.android.dialer (ESSENTIAL)"
7. Stay in dialer for 60 seconds.
8. Switch to Instagram. Check Logcat:
   (nothing about dialer counting — ESSENTIAL never counts)
   "SessionMonitor: foreground app -> com.instagram.android (DISTRACTING)"
9. Stay in Instagram >30 seconds, then return to Invigilator.
10. End session.
11. Last detected events should also finalize (Instagram counted, even though
    student returned to Invigilator before "exiting" Instagram explicitly).

Edge cases to verify:
- A 5-second YouTube check counts as "brief check (not counted)"
- A 45-second YouTube stay counts as a distraction event
- Phone dialer for any duration is logged but never counted
- Returning to Invigilator (STUDY) before threshold = brief check
- Ending session immediately after entering distracting app = the partial
  dwell still gets evaluated (if >=30s, counts; if <30s, brief)

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- DO NOT write distraction events to Firestore (Phase 4).
- DO NOT add a session timer or auto-end behavior (Phase 4).
- DO NOT create a SummaryScreen (Phase 4).
- DO NOT show distraction count to the student in the active session
  UI (we soft-hide; user reviews stats post-session).
- DO NOT add new strings to strings.xml — no user-visible text in this phase.
- DO NOT add the "DEPRECATION" suppress unless lint specifically demands it.
- DO NOT change SessionStateRepository or SessionMonitorService's lifecycle
  beyond the additions specified above.
- DO NOT commit any service-account JSON or sensitive files.
- Use stateIn(SharingStarted.Eagerly) if any new ViewModel uses combine
  (this phase doesn't introduce one, but for future reference).
- Run `./gradlew lint` BEFORE running tests, and fix any lint issues there.

When done, print:
  - Files created (list)
  - Files modified (list)
  - Test count and pass/fail status
  - Lint output (clean or what was fixed)
  - Confirmation git push succeeded
  - Any deviations from this prompt and why
