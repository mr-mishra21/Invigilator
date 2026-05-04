package app.invigilator.core.session

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionSummaryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SessionSummaryRepository {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun saveSession(doc: SessionDoc) {
        ioScope.launch {
            try {
                firestore
                    .collection("users")
                    .document(doc.studentUid)
                    .collection("sessions")
                    .document(doc.sessionId)
                    .set(doc)
                    .await()
                Timber.d("SessionSummary: persisted ${doc.sessionId}")
            } catch (e: Exception) {
                Timber.e(e, "SessionSummary: write failed for ${doc.sessionId}")
                // Best-effort. We do not retry; could enqueue for later in Sprint 6.
            }
        }
    }

    override fun observeRecentSessions(studentUid: String, limit: Int): Flow<List<SessionDoc>> {
        return firestore
            .collection("users")
            .document(studentUid)
            .collection("sessions")
            .orderBy("endedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { snap -> snap.toObjects(SessionDoc::class.java) }
            .catch { e ->
                Timber.e(e, "SessionSummary: observe failed for $studentUid")
                emit(emptyList())
            }
    }
}
