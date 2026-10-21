package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.DeviceCommand
import com.example.data.model.ParentAccount
import com.example.data.model.StudentProfile
import com.example.data.model.StudentRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
  val auth = FirebaseAuth.getInstance()
  val currentUser = auth.currentUser

  val providerInfoList = currentUser?.providerData?.map { provider ->
    JSONObject().apply {
      put("providerId", provider.providerId)
      put("email", provider.email)
    }
  } ?: emptyList()

  val authInfoJson = JSONObject().apply {
    put("userId", currentUser?.uid)
    put("email", currentUser?.email)
    put("emailVerified", currentUser?.isEmailVerified)
    put("tenantId", currentUser?.tenantId)
    put("providerInfo", JSONArray(providerInfoList))
  }

  val errorInfoJson = JSONObject().apply {
    put("error", exception.message ?: exception.toString())
    put("operationType", operationType.value)
    put("path", path)
    put("authInfo", authInfoJson)
  }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

class SafeLockCloudRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- 1. Parent Account Operations ---
  suspend fun createOrUpdateParentAccount(
    parentId: String,
    name: String,
    recoveryEmail: String,
    pinHash: String,
    pinSalt: String,
    connectedStudentIds: List<String>
  ): Result<ParentAccount> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("parents").document(uid)
    try {
      val existingSnap = docRef.get().await()
      if (!existingSnap.exists()) {
        val payload = mapOf(
          "parentUid" to uid,
          "parentId" to parentId,
          "name" to name,
          "recoveryEmail" to recoveryEmail,
          "pinHash" to pinHash,
          "pinSalt" to pinSalt,
          "connectedStudentIds" to connectedStudentIds,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
      } else {
        val updatePayload = mapOf(
          "name" to name,
          "recoveryEmail" to recoveryEmail,
          "pinHash" to pinHash,
          "pinSalt" to pinSalt,
          "connectedStudentIds" to connectedStudentIds,
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(updatePayload).await()
      }
      val saved = docRef.get().await()
      checkNotNull(saved.toObject(ParentAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun getParentAccount(parentUid: String = requireUserId()): Result<ParentAccount?> = runCatching {
    val docRef = db.collection("parents").document(parentUid)
    try {
      val snap = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
      snap.toObject(ParentAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  fun observeParentAccount(parentUid: String): Flow<ParentAccount?> {
    val docRef = db.collection("parents").document(parentUid)
    return docRef.snapshots()
      .map { snap -> snap.toObject(ParentAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  // --- 2. Student Device Profile & Pairing Operations ---
  suspend fun registerStudentDevice(
    studentId: String,
    studentName: String,
    deviceRegistrationId: String,
    approvedApps: List<String>,
    proposedApps: List<String>,
    authorizedScheduleSlots: List<String>,
    proposedScheduleSlots: List<String>
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("students").document(studentId)
    try {
      val payload = mapOf(
        "studentId" to studentId,
        "studentUid" to uid,
        "studentName" to studentName,
        "deviceRegistrationId" to deviceRegistrationId,
        "pairedParentUid" to "",
        "pairedParentId" to "",
        "pendingParentUid" to "",
        "pendingParentId" to "",
        "pendingParentName" to "",
        "pairingStatus" to "UNPAIRED",
        "connectionStatus" to "ONLINE",
        "safeLockStatus" to "RESTRICTED",
        "approvedApps" to approvedApps,
        "proposedApps" to proposedApps,
        "authorizedScheduleSlots" to authorizedScheduleSlots,
        "proposedScheduleSlots" to proposedScheduleSlots,
        "emergencyUnlockUntilMillis" to 0L,
        "manualOverrideState" to "NONE",
        "lastCommandId" to "",
        "lastCommandStatus" to "NONE",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      studentId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun requestPairingFromParent(
    studentId: String,
    parentId: String,
    parentName: String
  ): Result<Unit> = runCatching {
    val parentUid = requireUserId()
    val docRef = db.collection("students").document(studentId)
    try {
      docRef.update(
        mapOf(
          "pendingParentUid" to parentUid,
          "pendingParentId" to parentId,
          "pendingParentName" to parentName,
          "pairingStatus" to "PENDING_APPROVAL",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun respondToParentPairingAsStudent(
    studentId: String,
    approve: Boolean,
    pendingParentUid: String,
    pendingParentId: String
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("students").document(studentId)
    try {
      val updates = if (approve) {
        mapOf(
          "pairedParentUid" to pendingParentUid,
          "pairedParentId" to pendingParentId,
          "pairingStatus" to "PAIRED",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      } else {
        mapOf(
          "pendingParentUid" to "",
          "pendingParentId" to "",
          "pendingParentName" to "",
          "pairingStatus" to "UNPAIRED",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      }
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeStudentProfile(studentId: String): Flow<StudentProfile?> {
    val docRef = db.collection("students").document(studentId)
    return docRef.snapshots()
      .map { snap -> snap.toObject(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  fun observePairedStudentsForParent(parentUid: String): Flow<List<StudentProfile>> {
    val path = "students"
    return db.collection(path)
      .whereEqualTo("pairedParentUid", parentUid)
      .snapshots()
      .map { snap -> snap.toObjects(StudentProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun updateParentAuthorizedPolicy(
    studentId: String,
    approvedApps: List<String>,
    authorizedScheduleSlots: List<String>
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("students").document(studentId)
    try {
      docRef.update(
        mapOf(
          "approvedApps" to approvedApps,
          "authorizedScheduleSlots" to authorizedScheduleSlots,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // --- 3. Student Requests to Parent ---
  suspend fun submitStudentRequest(
    studentId: String,
    studentName: String,
    parentUid: String,
    requestType: String,
    details: String,
    payloadValue: String
  ): Result<String> = runCatching {
    val studentUid = requireUserId()
    val docRef = db.collection("student_requests").document()
    try {
      val payload = mapOf(
        "studentId" to studentId,
        "studentUid" to studentUid,
        "studentName" to studentName,
        "parentUid" to parentUid,
        "requestType" to requestType,
        "details" to details,
        "payloadValue" to payloadValue,
        "status" to "PENDING",
        "approvedDurationMinutes" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun decideStudentRequestAsParent(
    requestId: String,
    approve: Boolean,
    approvedDurationMinutes: Long = 0L
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("student_requests").document(requestId)
    try {
      docRef.update(
        mapOf(
          "status" to if (approve) "APPROVED" else "REJECTED",
          "approvedDurationMinutes" to if (approve) approvedDurationMinutes else 0L,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeRequestsForStudent(studentUid: String): Flow<List<StudentRequest>> {
    val path = "student_requests"
    return db.collection(path)
      .whereEqualTo("studentUid", studentUid)
      .snapshots()
      .map { snap -> snap.toObjects(StudentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeRequestsForParent(parentUid: String): Flow<List<StudentRequest>> {
    val path = "student_requests"
    return db.collection(path)
      .whereEqualTo("parentUid", parentUid)
      .snapshots()
      .map { snap -> snap.toObjects(StudentRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  // --- 4. Remote Commands (Lock Now / Direct Unlock / Emergency Unlock) ---
  suspend fun issueRemoteCommandFromParent(
    studentId: String,
    studentUid: String,
    commandType: String,
    durationMinutes: Long = 0L
  ): Result<String> = runCatching {
    val parentUid = requireUserId()
    val docRef = db.collection("device_commands").document()
    val commandId = docRef.id
    try {
      val payload = mapOf(
        "commandId" to commandId,
        "studentId" to studentId,
        "studentUid" to studentUid,
        "parentUid" to parentUid,
        "commandType" to commandType,
        "durationMinutes" to durationMinutes,
        "status" to "PENDING",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      // Record command as PENDING on student profile until student device confirms execution
      db.collection("students").document(studentId).update(
        mapOf(
          "safeLockStatus" to "COMMAND_PENDING",
          "lastCommandId" to commandId,
          "lastCommandStatus" to "PENDING",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      commandId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun confirmCommandExecutedByStudent(
    commandId: String,
    studentId: String,
    newSafeLockStatus: String,
    newManualOverrideState: String,
    emergencyUnlockUntilMillis: Long
  ): Result<Unit> = runCatching {
    requireUserId()
    val cmdRef = db.collection("device_commands").document(commandId)
    val studentRef = db.collection("students").document(studentId)
    try {
      cmdRef.update(
        mapOf(
          "status" to "EXECUTED",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      studentRef.update(
        mapOf(
          "safeLockStatus" to newSafeLockStatus,
          "manualOverrideState" to newManualOverrideState,
          "emergencyUnlockUntilMillis" to emergencyUnlockUntilMillis,
          "lastCommandId" to commandId,
          "lastCommandStatus" to "EXECUTED",
          "connectionStatus" to "ONLINE",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, cmdRef.path)
      throw e
    }
  }

  fun observeCommandsForStudent(studentUid: String): Flow<List<DeviceCommand>> {
    val path = "device_commands"
    return db.collection(path)
      .whereEqualTo("studentUid", studentUid)
      .snapshots()
      .map { snap -> snap.toObjects(DeviceCommand::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }
}
