package app.invigilator.core.linking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import app.invigilator.core.user.LinkedStudentDoc

@Singleton
internal class LinkingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : LinkingRepository {

    override suspend fun generateCode(studentUid: String): Result<String> = runCatching {
        val code = String.format("%06d", Random.nextInt(1_000_000))
        val now = Timestamp.now()
        val expiresAt = Timestamp(Date(now.toDate().time + 30 * 60 * 1000))
        val doc = mapOf(
            "code" to code,
            "studentUid" to studentUid,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "claimedBy" to null,
            "claimedAt" to null,
        )
        firestore.collection("linkingCodes").document(code).set(doc).await()
        code
    }

    override fun observeLinkedStudents(parentUid: String): Flow<List<LinkedStudentDoc>> = callbackFlow {
        val listener = firestore
            .collection("users").document(parentUid)
            .collection("linkedStudents")
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val docs = snap?.documents?.mapNotNull { it.toObject(LinkedStudentDoc::class.java) }
                    ?: emptyList()
                trySend(docs)
            }
        awaitClose { listener.remove() }
    }

    override fun observeLinkingCode(code: String): Flow<LinkingCodeDoc?> = callbackFlow {
        val listener = firestore.collection("linkingCodes").document(code)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(null)
                } else {
                    trySend(snap.toObject(LinkingCodeDoc::class.java))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun claimCode(code: String): Result<ClaimResult> = runCatching {
        val result = functions
            .getHttpsCallable("claimLinkingCode")
            .call(mapOf("code" to code))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any?>
        ClaimResult(
            studentUid = data["studentUid"] as String,
            studentDisplayName = data["studentDisplayName"] as String,
            studentDateOfBirthMillis = (data["studentDateOfBirthMillis"] as? Number)?.toLong(),
        )
    }.mapFailure { it.toLinkingError() }

    override suspend fun completeLinking(studentUid: String, consentId: String): Result<Unit> =
        runCatching {
            functions
                .getHttpsCallable("completeLinking")
                .call(mapOf("studentUid" to studentUid, "consentId" to consentId))
                .await()
            Unit
        }.mapFailure { it.toCompleteLinkingError() }

    private fun Throwable.toCompleteLinkingError(): LinkingError {
        val msg = message ?: ""
        return when {
            msg.contains("UNAUTHENTICATED", ignoreCase = true) -> LinkingError.NotSignedIn
            msg.contains("INVALID_ARGUMENT", ignoreCase = true) -> LinkingError.BadRequest
            msg.contains("FAILED_PRECONDITION", ignoreCase = true) &&
                msg.contains("expired", ignoreCase = true) -> LinkingError.SessionExpired
            msg.contains("ALREADY_EXISTS", ignoreCase = true) -> LinkingError.AlreadyLinked
            msg.contains("PERMISSION_DENIED", ignoreCase = true) -> LinkingError.NotAuthorized
            else -> LinkingError.Unknown(this)
        }
    }

    private fun Throwable.toLinkingError(): LinkingError {
        val msg = message ?: ""
        return when {
            this is LinkingError -> this
            msg.contains("NOT_FOUND", ignoreCase = true) -> LinkingError.CodeNotFound
            msg.contains("DEADLINE_EXCEEDED", ignoreCase = true) ||
                msg.contains("expired", ignoreCase = true) -> LinkingError.CodeExpired
            msg.contains("ALREADY_EXISTS", ignoreCase = true) ||
                msg.contains("already claimed", ignoreCase = true) -> LinkingError.CodeAlreadyClaimed
            msg.contains("INVALID_ARGUMENT", ignoreCase = true) -> LinkingError.InvalidCodeFormat
            msg.contains("network", ignoreCase = true) ||
                msg.contains("UNAVAILABLE", ignoreCase = true) -> LinkingError.Network(this)
            else -> LinkingError.Unknown(this)
        }
    }

    private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(transform(it)) })
}
