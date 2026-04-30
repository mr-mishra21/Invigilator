package app.invigilator.core.user

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    private fun userRef(uid: String) = firestore.collection("users").document(uid)

    override suspend fun createUser(user: UserDoc): Result<Unit> = runCatching {
        userRef(user.uid).set(user).await()
    }.map { Unit }

    override suspend fun getUser(uid: String): Result<UserDoc?> = runCatching {
        val snap = userRef(uid).get().await()
        snap.toObject(UserDoc::class.java)
    }

    override fun observeUser(uid: String): Flow<UserDoc?> = callbackFlow {
        val listener = userRef(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(UserDoc::class.java))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateAccountStatus(uid: String, status: AccountStatus): Result<Unit> =
        runCatching {
            userRef(uid).update("accountStatus", status.firestoreValue).await()
        }.map { Unit }

    override suspend fun appendConsentRef(uid: String, consentPath: String): Result<Unit> =
        runCatching {
            val ref = firestore.document(consentPath)
            userRef(uid).update("consentRefs", FieldValue.arrayUnion(ref)).await()
        }.map { Unit }
}
