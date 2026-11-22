package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Producer must sign in with Google before accessing studio sessions.")
    }

    fun observeSessions(userId: String): Flow<List<StudioSession>> =
        db.collection("users").document(userId)
            .collection("sessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudioSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeMessages(userId: String, sessionId: String): Flow<List<StudioMessage>> =
        db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudioMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun createSession(
        title: String,
        dsp: DspParams = DspParams(),
        welcomeText: String? = null
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val sessionsRef = db.collection("users").document(uid).collection("sessions")
        val newSessionRef = sessionsRef.document()
        val sessionPath = newSessionRef.path
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Authenticated user changed." }
            val safeTitle = title.trim().ifBlank { "Midnight Session" }.take(80)

            val sessionPayload = mapOf(
                "userId" to activeUid,
                "title" to safeTitle,
                "bpm" to dsp.bpm.coerceIn(60, 180).toLong(),
                "musicalKey" to dsp.musicalKey.trim().ifBlank { "C minor" }.take(32),
                "grooveStyle" to dsp.grooveStyle.wireValue,
                "hpfHz" to dsp.hpfHz.coerceIn(20, 220).toLong(),
                "eqLowDb" to dsp.eqLowDb.coerceIn(-12f, 12f).toDouble(),
                "eqMidHz" to dsp.eqMidHz.coerceIn(250, 5000).toLong(),
                "eqMidDb" to dsp.eqMidDb.coerceIn(-12f, 12f).toDouble(),
                "eqHighDb" to dsp.eqHighDb.coerceIn(-12f, 12f).toDouble(),
                "compThresholdDb" to dsp.compThresholdDb.coerceIn(-40f, 0f).toDouble(),
                "saturation" to dsp.saturation.coerceIn(0f, 1f).toDouble(),
                "lastSpark" to "Boards calibrated and waiting for The Spark.",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(newSessionRef, sessionPayload)

            if (!welcomeText.isNullOrBlank()) {
                val msgRef = newSessionRef.collection("messages").document()
                val msgPayload = mapOf(
                    "userId" to activeUid,
                    "sessionId" to newSessionRef.id,
                    "speaker" to SpeakerRole.ENGINEER.wireValue,
                    "text" to welcomeText.trim().take(8000),
                    "sampleCredit" to "",
                    "stemPackIncluded" to false,
                    "stemBpm" to dsp.bpm.coerceIn(60, 180).toLong(),
                    "stemKey" to dsp.musicalKey.take(32),
                    "stemGroove" to dsp.grooveStyle.wireValue,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(msgRef, msgPayload)
            }

            batch.commit().await()
            Result.success(newSessionRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionPath)
            Result.failure(e)
        }
    }

    suspend fun updateSessionDsp(
        sessionId: String,
        dsp: DspParams,
        title: String? = null,
        lastSpark: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid/sessions/$sessionId"
        return try {
            val activeUid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val docRef = db.collection("users").document(activeUid)
                .collection("sessions").document(sessionId)

            val updates = mutableMapOf<String, Any>(
                "bpm" to dsp.bpm.coerceIn(60, 180).toLong(),
                "musicalKey" to dsp.musicalKey.trim().ifBlank { "C minor" }.take(32),
                "grooveStyle" to dsp.grooveStyle.wireValue,
                "hpfHz" to dsp.hpfHz.coerceIn(20, 220).toLong(),
                "eqLowDb" to dsp.eqLowDb.coerceIn(-12f, 12f).toDouble(),
                "eqMidHz" to dsp.eqMidHz.coerceIn(250, 5000).toLong(),
                "eqMidDb" to dsp.eqMidDb.coerceIn(-12f, 12f).toDouble(),
                "eqHighDb" to dsp.eqHighDb.coerceIn(-12f, 12f).toDouble(),
                "compThresholdDb" to dsp.compThresholdDb.coerceIn(-40f, 0f).toDouble(),
                "saturation" to dsp.saturation.coerceIn(0f, 1f).toDouble(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!title.isNullOrBlank()) {
                updates["title"] = title.trim().take(80)
            }
            if (!lastSpark.isNullOrBlank()) {
                updates["lastSpark"] = lastSpark.trim().take(300)
            }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        sessionId: String,
        speaker: SpeakerRole,
        text: String,
        sampleCredit: String = "",
        stemPackIncluded: Boolean = false,
        stemBpm: Int = 92,
        stemKey: String = "C minor",
        stemGroove: GrooveStyle = GrooveStyle.TRAP_SOUL
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val msgRef = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
            .collection("messages").document()
        val msgPath = msgRef.path
        return try {
            val activeUid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val safeText = text.trim().ifBlank { "..." }.take(8000)

            val payload = mapOf(
                "userId" to activeUid,
                "sessionId" to sessionId,
                "speaker" to speaker.wireValue,
                "text" to safeText,
                "sampleCredit" to sampleCredit.trim().take(300),
                "stemPackIncluded" to stemPackIncluded,
                "stemBpm" to stemBpm.coerceIn(60, 180).toLong(),
                "stemKey" to stemKey.trim().take(32),
                "stemGroove" to stemGroove.wireValue,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(payload).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val sessionRef = db.collection("users").document(uid)
            .collection("sessions").document(sessionId)
        return try {
            requireUserId()
            val msgs = sessionRef.collection("messages").get().await()
            val batch = db.batch()
            for (doc in msgs.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(sessionRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionRef.path)
            Result.failure(e)
        }
    }
}
