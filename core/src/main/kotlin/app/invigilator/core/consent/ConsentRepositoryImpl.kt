package app.invigilator.core.consent

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConsentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val textProvider: ConsentTextProvider,
) : ConsentRepository {

    override fun getConsentText(type: ConsentType, language: String): ConsentTextResult? =
        textProvider.resolve(
            type = type,
            version = ConsentVersions.currentVersionFor(type),
            language = language,
        )

    override suspend fun recordConsent(doc: ConsentDoc): Result<String> = runCatching {
        val id = doc.consentId.ifBlank { UUID.randomUUID().toString() }
        val finalDoc = if (doc.consentId.isBlank()) doc.copy(consentId = id) else doc
        firestore.collection("consents").document(id).set(finalDoc.toClientWriteMap()).await()
        id
    }

    override fun awaitServerTimestamp(consentId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("consents").document(consentId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists() && snap.getTimestamp("signedAt") != null) {
                    trySend(true)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun withdrawConsent(consentId: String, uid: String): Result<Unit> =
        runCatching {
            firestore.collection("consents").document(consentId).update(
                mapOf(
                    "withdrawn" to true,
                    "withdrawnAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
            ).await()
        }.map { Unit }
}
