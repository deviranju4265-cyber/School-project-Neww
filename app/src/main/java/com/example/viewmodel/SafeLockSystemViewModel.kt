package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.CachedStudentPolicyEntity
import com.example.data.local.SafeLockLocalDatabase
import com.example.data.local.StudentPolicyDao
import com.example.data.model.DeviceCommand
import com.example.data.model.ParentAccount
import com.example.data.model.SecurityCryptoUtil
import com.example.data.model.StudentProfile
import com.example.data.model.StudentRequest
import com.example.data.repository.SafeLockCloudRepository
import com.example.notifications.RealtimeNotificationItem
import com.example.notifications.SafeLockNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SafeLockSystemUiState(
  val currentUserUid: String = "",
  val currentTimeFormatted: String = "08:30 PM",
  // Student App (SafeLock) State
  val studentSetupDone: Boolean = false,
  val studentId: String = "",
  val studentName: String = "Leo Sharma",
  val deviceRegistrationId: String = "",
  val pairedParentUid: String = "",
  val pairedParentId: String = "",
  val pendingParentUid: String = "",
  val pendingParentId: String = "",
  val pendingParentName: String = "",
  val pairingStatus: String = "UNPAIRED",
  val studentSimulatedOffline: Boolean = false,
  val safeLockStatus: String = "RESTRICTED",
  val manualOverrideState: String = "NONE",
  val emergencyUnlockUntilMillis: Long = 0L,
  val approvedApps: List<String> = listOf(
    "com.android.dialer",
    "com.android.contacts",
    "com.android.camera2",
    "com.google.android.youtube"
  ),
  val proposedApps: List<String> = listOf("com.whatsapp"),
  val authorizedScheduleSlots: List<String> = listOf(
    "MON-FRI|16:00|18:00|After School Normal Use",
    "SAT-SUN|14:00|18:00|Weekend Normal Use"
  ),
  val proposedScheduleSlots: List<String> = listOf(
    "MON-FRI|16:00|18:00|After School Normal Use"
  ),
  val studentRequests: List<StudentRequest> = emptyList(),
  // Student-only wallpaper customization (never exposed to Lock Monitor)
  val selectedWallpaperPreset: String = "AURORA_MIDNIGHT",
  val customWallpaperUri: String = "",
  // Parent App (Lock Monitor) State
  val parentAccount: ParentAccount? = null,
  val parentPinUnlocked: Boolean = false,
  val parentFailedPinAttempts: Int = 0,
  val parentLockoutUntilMillis: Long = 0L,
  val parentPinError: String? = null,
  val connectedStudents: List<StudentProfile> = emptyList(),
  val selectedStudentIndex: Int = 0,
  val parentRequests: List<StudentRequest> = emptyList(),
  // Real-time FCM / Cloud Notification Stream
  val notifications: List<RealtimeNotificationItem> = emptyList(),
  val statusBannerMessage: String? = null
)

class SafeLockSystemViewModel(
  private val appContext: Context,
  private val cloudRepo: SafeLockCloudRepository,
  private val localDao: StudentPolicyDao,
  private val currentUserId: String
) : ViewModel() {

  private val _uiState = MutableStateFlow(SafeLockSystemUiState(currentUserUid = currentUserId))
  val uiState: StateFlow<SafeLockSystemUiState> = _uiState.asStateFlow()

  private val seenRequestIdsForParent = mutableSetOf<String>()
  private val seenRequestStatesForStudent = mutableMapOf<String, String>()
  private val seenCommandIdsForStudent = mutableSetOf<String>()
  private var initialSnapshotsLoaded = false

  init {
    SafeLockNotificationHelper.ensureNotificationChannels(appContext)
    SafeLockNotificationHelper.onFcmMessageCallback = { item ->
      _uiState.update { state ->
        state.copy(
          notifications = (listOf(item) + state.notifications).take(30),
          statusBannerMessage = "${item.title} — ${item.body}"
        )
      }
    }
    startDigitalClockAndScheduleTicker()
    loadAndObserveLocalStudentPolicy()
    observeCloudStreamsForAuthenticatedUser()
  }

  private fun startDigitalClockAndScheduleTicker() {
    viewModelScope.launch {
      val clockFormat = SimpleDateFormat("hh:mm a", Locale.US)
      while (true) {
        val now = Date()
        val timeText = clockFormat.format(now)
        val currentState = _uiState.value

        // Evaluate emergency unlock expiry and schedule transitions
        val nowMillis = System.currentTimeMillis()
        val inEmergencyUnlock = currentState.emergencyUnlockUntilMillis > nowMillis
        val inScheduleWindow = SecurityCryptoUtil.isCurrentlyInAuthorizedSchedule(
          currentState.authorizedScheduleSlots
        )

        val evaluatedStatus = when {
          currentState.safeLockStatus == "COMMAND_PENDING" && !currentState.studentSimulatedOffline ->
            currentState.safeLockStatus
          inEmergencyUnlock -> "EMERGENCY_UNLOCKED"
          currentState.manualOverrideState == "LOCKED_NOW" && !inScheduleWindow -> "RESTRICTED"
          currentState.manualOverrideState == "UNLOCKED_NOW" -> "NORMAL_USE"
          inScheduleWindow -> "NORMAL_USE"
          else -> "RESTRICTED"
        }

        // Clear expired emergency unlock automatically
        val nextEmergencyMillis = if (
          currentState.emergencyUnlockUntilMillis > 0L &&
          nowMillis >= currentState.emergencyUnlockUntilMillis
        ) {
          0L
        } else {
          currentState.emergencyUnlockUntilMillis
        }

        _uiState.update {
          it.copy(
            currentTimeFormatted = timeText,
            safeLockStatus = evaluatedStatus,
            emergencyUnlockUntilMillis = nextEmergencyMillis
          )
        }
        delay(1000L)
      }
    }
  }

  private fun loadAndObserveLocalStudentPolicy() {
    viewModelScope.launch {
      val cached = localDao.getLocalPolicyOnce()
      if (cached == null) {
        val generatedStudentId = SecurityCryptoUtil.generateStudentId()
        val regId = "REG-" + UUID.randomUUID().toString().take(8).uppercase(Locale.US)
        val initialEntity = CachedStudentPolicyEntity(
          singletonId = 1,
          setupCompleted = false,
          studentId = generatedStudentId,
          studentName = "Leo Sharma",
          deviceRegistrationId = regId
        )
        localDao.saveLocalPolicy(initialEntity)
      }

      localDao.observeLocalPolicy().collect { entity ->
        if (entity != null) {
          _uiState.update { state ->
            state.copy(
              studentSetupDone = entity.setupCompleted,
              studentId = entity.studentId,
              studentName = entity.studentName,
              deviceRegistrationId = entity.deviceRegistrationId,
              pairedParentUid = entity.pairedParentUid.ifBlank { state.pairedParentUid },
              pairedParentId = entity.pairedParentId.ifBlank { state.pairedParentId },
              pairingStatus = if (entity.pairingStatus != "UNPAIRED") entity.pairingStatus else state.pairingStatus,
              approvedApps = entity.approvedAppsCsv.split(",").filter { it.isNotBlank() },
              proposedApps = entity.proposedAppsCsv.split(",").filter { it.isNotBlank() },
              authorizedScheduleSlots = entity.authorizedScheduleCsv.split(";;").filter { it.isNotBlank() },
              proposedScheduleSlots = entity.proposedScheduleCsv.split(";;").filter { it.isNotBlank() },
              selectedWallpaperPreset = entity.selectedWallpaperPreset,
              customWallpaperUri = entity.customWallpaperUri
            )
          }
          if (entity.studentId.isNotBlank()) {
            observeSpecificStudentDocument(entity.studentId)
          }
        }
      }
    }
  }

  private var observedStudentDocId: String = ""

  private fun observeSpecificStudentDocument(studentId: String) {
    if (observedStudentDocId == studentId) return
    observedStudentDocId = studentId
    viewModelScope.launch {
      cloudRepo.observeStudentProfile(studentId)
        .catch { e -> Log.w("SafeLockVM", "Student profile stream warning: ${e.message}") }
        .collect { profile ->
          if (profile != null && !_uiState.value.studentSimulatedOffline) {
            _uiState.update { state ->
              state.copy(
                studentName = profile.studentName,
                pairedParentUid = profile.pairedParentUid,
                pairedParentId = profile.pairedParentId,
                pendingParentUid = profile.pendingParentUid,
                pendingParentId = profile.pendingParentId,
                pendingParentName = profile.pendingParentName,
                pairingStatus = profile.pairingStatus,
                approvedApps = profile.approvedApps,
                proposedApps = profile.proposedApps,
                authorizedScheduleSlots = profile.authorizedScheduleSlots,
                proposedScheduleSlots = profile.proposedScheduleSlots,
                emergencyUnlockUntilMillis = profile.emergencyUnlockUntilMillis,
                manualOverrideState = profile.manualOverrideState
              )
            }
            // Persist updated authorized policy locally for offline resilience
            val currentLocal = localDao.getLocalPolicyOnce() ?: CachedStudentPolicyEntity()
            localDao.saveLocalPolicy(
              currentLocal.copy(
                studentId = profile.studentId,
                studentName = profile.studentName,
                pairedParentUid = profile.pairedParentUid,
                pairedParentId = profile.pairedParentId,
                pairingStatus = profile.pairingStatus,
                approvedAppsCsv = profile.approvedApps.joinToString(","),
                proposedAppsCsv = profile.proposedApps.joinToString(","),
                authorizedScheduleCsv = profile.authorizedScheduleSlots.joinToString(";;"),
                proposedScheduleCsv = profile.proposedScheduleSlots.joinToString(";;"),
                safeLockStatus = profile.safeLockStatus,
                manualOverrideState = profile.manualOverrideState,
                emergencyUnlockUntilMillis = profile.emergencyUnlockUntilMillis,
                lastCommandId = profile.lastCommandId,
                updatedAtMillis = System.currentTimeMillis()
              )
            )
          }
        }
    }
  }

  private fun observeCloudStreamsForAuthenticatedUser() {
    // 1. Observe Parent Account
    viewModelScope.launch {
      cloudRepo.observeParentAccount(currentUserId)
        .catch { e -> Log.w("SafeLockVM", "Parent account stream warning: ${e.message}") }
        .collect { account ->
          _uiState.update { it.copy(parentAccount = account) }
        }
    }

    // 2. Observe Paired Students for Parent Dashboard
    viewModelScope.launch {
      cloudRepo.observePairedStudentsForParent(currentUserId)
        .catch { e -> Log.w("SafeLockVM", "Paired students stream warning: ${e.message}") }
        .collect { students ->
          _uiState.update { state ->
            val safeIdx = state.selectedStudentIndex.coerceIn(0, (students.size - 1).coerceAtLeast(0))
            state.copy(
              connectedStudents = students,
              selectedStudentIndex = safeIdx
            )
          }
        }
    }

    // 3. Observe Student Requests for Parent (Trigger Real-Time Parent Notifications!)
    viewModelScope.launch {
      cloudRepo.observeRequestsForParent(currentUserId)
        .catch { e -> Log.w("SafeLockVM", "Parent requests stream warning: ${e.message}") }
        .collect { requests ->
          val sorted = requests.sortedByDescending { it.createdAt?.seconds ?: 0L }
          sorted.forEach { req ->
            if (req.id.isNotBlank() && !seenRequestIdsForParent.contains(req.id)) {
              seenRequestIdsForParent.add(req.id)
              if (initialSnapshotsLoaded && req.status == "PENDING") {
                pushNotificationAlert(
                  targetRole = "PARENT",
                  category = "REQUEST_NEW",
                  title = "New Request from ${req.studentName}",
                  body = "${req.requestType.replace("_", " ")}: ${req.details}"
                )
              }
            }
          }
          _uiState.update { it.copy(parentRequests = sorted) }
        }
    }

    // 4. Observe Student Requests for Student (Trigger Real-Time Student Decision Notifications!)
    viewModelScope.launch {
      cloudRepo.observeRequestsForStudent(currentUserId)
        .catch { e -> Log.w("SafeLockVM", "Student requests stream warning: ${e.message}") }
        .collect { requests ->
          val sorted = requests.sortedByDescending { it.createdAt?.seconds ?: 0L }
          sorted.forEach { req ->
            val prevStatus = seenRequestStatesForStudent[req.id]
            seenRequestStatesForStudent[req.id] = req.status
            if (prevStatus != null && prevStatus != req.status && req.status != "PENDING") {
              val durText = if (req.approvedDurationMinutes > 0) " (${req.approvedDurationMinutes} mins)" else ""
              pushNotificationAlert(
                targetRole = "STUDENT",
                category = "REQUEST_DECISION",
                title = "Request ${req.status}$durText",
                body = "Parent ${req.status.lowercase(Locale.US)}: ${req.details}"
              )
            }
          }
          _uiState.update { it.copy(studentRequests = sorted) }
        }
    }

    // 5. Observe Remote Commands for Student Device & Auto-Execute when Online!
    viewModelScope.launch {
      cloudRepo.observeCommandsForStudent(currentUserId)
        .catch { e -> Log.w("SafeLockVM", "Commands stream warning: ${e.message}") }
        .collect { commands ->
          initialSnapshotsLoaded = true
          if (_uiState.value.studentSimulatedOffline) return@collect
          val pendingCmds = commands.filter { it.status == "PENDING" }
          for (cmd in pendingCmds) {
            if (seenCommandIdsForStudent.add(cmd.commandId)) {
              executeAndConfirmIncomingCommand(cmd)
            }
          }
        }
    }
  }

  private suspend fun executeAndConfirmIncomingCommand(cmd: DeviceCommand) {
    val nowMillis = System.currentTimeMillis()
    val (newStatus, newOverride, unlockUntil, notifTitle, notifBody) = when (cmd.commandType) {
      "LOCK_NOW" -> Quintuple(
        "RESTRICTED",
        "LOCKED_NOW",
        0L,
        "🔒 Parent Activated Lock Now",
        "SafeLock restricted mode has been activated by your parent."
      )
      "UNLOCK_NOW" -> Quintuple(
        "NORMAL_USE",
        "UNLOCKED_NOW",
        0L,
        "🔓 Parent Unlocked Your Device",
        "Your parent unlocked SafeLock for normal use until the next scheduled transition."
      )
      "EMERGENCY_UNLOCK" -> {
        val mins = cmd.durationMinutes.coerceIn(1L, 240L)
        Quintuple(
          "EMERGENCY_UNLOCKED",
          "UNLOCKED_NOW",
          nowMillis + mins * 60_000L,
          "🚨 Emergency Unlock Approved ($mins min)",
          "Your phone is unlocked for $mins minutes. It will return to SafeLock when the timer expires."
        )
      }
      else -> Quintuple(
        "RESTRICTED",
        "NONE",
        0L,
        "🔄 SafeLock Policy Synchronized",
        "Updated schedule and approved apps have been synchronized from Lock Monitor."
      )
    }

    cloudRepo.confirmCommandExecutedByStudent(
      commandId = cmd.commandId,
      studentId = cmd.studentId,
      newSafeLockStatus = newStatus,
      newManualOverrideState = newOverride,
      emergencyUnlockUntilMillis = unlockUntil
    )

    pushNotificationAlert(
      targetRole = "STUDENT",
      category = "REMOTE_COMMAND",
      title = notifTitle,
      body = notifBody
    )
  }

  private data class Quintuple(
    val status: String,
    val overrideState: String,
    val unlockUntil: Long,
    val title: String,
    val body: String
  )

  fun pushNotificationAlert(
    targetRole: String,
    category: String,
    title: String,
    body: String
  ) {
    val channelId = if (targetRole == "PARENT") {
      SafeLockNotificationHelper.CHANNEL_PARENT_ALERTS
    } else {
      SafeLockNotificationHelper.CHANNEL_STUDENT_ALERTS
    }
    SafeLockNotificationHelper.dispatchSystemNotification(
      context = appContext,
      channelId = channelId,
      title = title,
      message = body
    )
    val item = RealtimeNotificationItem(
      id = UUID.randomUUID().toString(),
      targetRole = targetRole,
      title = title,
      body = body,
      category = category
    )
    _uiState.update { state ->
      state.copy(
        notifications = (listOf(item) + state.notifications).take(30),
        statusBannerMessage = "$title — $body"
      )
    }
  }

  fun markAllNotificationsRead() {
    _uiState.update { state ->
      state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
    }
  }

  fun dismissBannerMessage() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  // --- Student Actions (SafeLock) ---

  fun completeStudentInitialSetup(
    studentName: String,
    proposedScheduleSlot: String,
    requestedPackages: List<String>,
    wallpaperPreset: String
  ) {
    viewModelScope.launch {
      val current = _uiState.value
      val studentId = current.studentId.ifBlank { SecurityCryptoUtil.generateStudentId() }
      val regId = current.deviceRegistrationId.ifBlank {
        "REG-" + UUID.randomUUID().toString().take(8).uppercase(Locale.US)
      }
      val proposedSchedules = listOf(proposedScheduleSlot)

      val existingEntity = localDao.getLocalPolicyOnce() ?: CachedStudentPolicyEntity()
      localDao.saveLocalPolicy(
        existingEntity.copy(
          setupCompleted = true,
          studentId = studentId,
          studentName = studentName.trim(),
          deviceRegistrationId = regId,
          proposedAppsCsv = requestedPackages.joinToString(","),
          proposedScheduleCsv = proposedSchedules.joinToString(";;"),
          selectedWallpaperPreset = wallpaperPreset,
          updatedAtMillis = System.currentTimeMillis()
        )
      )

      val cloudResult = cloudRepo.registerStudentDevice(
        studentId = studentId,
        studentName = studentName.trim(),
        deviceRegistrationId = regId,
        approvedApps = current.approvedApps,
        proposedApps = requestedPackages,
        authorizedScheduleSlots = current.authorizedScheduleSlots,
        proposedScheduleSlots = proposedSchedules
      )
      observeSpecificStudentDocument(studentId)

      val msg = if (cloudResult.isSuccess) {
        "Setup saved & synced! Student ID $studentId ready for parent pairing."
      } else {
        "Setup saved locally. Will synchronize with cloud when online."
      }
      _uiState.update { it.copy(statusBannerMessage = msg) }
    }
  }

  fun simulateReinstallNewStudentId() {
    viewModelScope.launch {
      val newStudentId = SecurityCryptoUtil.generateStudentId()
      val newRegId = "REG-" + UUID.randomUUID().toString().take(8).uppercase(Locale.US)
      val current = _uiState.value
      val local = localDao.getLocalPolicyOnce() ?: CachedStudentPolicyEntity()
      localDao.saveLocalPolicy(
        local.copy(
          studentId = newStudentId,
          deviceRegistrationId = newRegId,
          pairedParentUid = "",
          pairedParentId = "",
          pairingStatus = "UNPAIRED",
          updatedAtMillis = System.currentTimeMillis()
        )
      )
      cloudRepo.registerStudentDevice(
        studentId = newStudentId,
        studentName = current.studentName,
        deviceRegistrationId = newRegId,
        approvedApps = listOf("com.android.dialer", "com.android.contacts"),
        proposedApps = current.proposedApps,
        authorizedScheduleSlots = current.authorizedScheduleSlots,
        proposedScheduleSlots = current.proposedScheduleSlots
      )
      observeSpecificStudentDocument(newStudentId)
      _uiState.update {
        it.copy(
          studentId = newStudentId,
          deviceRegistrationId = newRegId,
          pairedParentUid = "",
          pairedParentId = "",
          pairingStatus = "UNPAIRED",
          statusBannerMessage = "Reinstall simulated: New ID $newStudentId generated. Old control relationship revoked; parent must pair again."
        )
      }
    }
  }

  fun updateStudentWallpaper(presetId: String, customUri: String = "") {
    viewModelScope.launch {
      val currentLocal = localDao.getLocalPolicyOnce() ?: CachedStudentPolicyEntity()
      localDao.saveLocalPolicy(
        currentLocal.copy(
          selectedWallpaperPreset = presetId,
          customWallpaperUri = customUri,
          updatedAtMillis = System.currentTimeMillis()
        )
      )
      _uiState.update {
        it.copy(
          selectedWallpaperPreset = presetId,
          customWallpaperUri = customUri,
          statusBannerMessage = "Wallpaper saved locally on student device (Student-controlled only)."
        )
      }
    }
  }

  fun respondToParentPairing(approve: Boolean) {
    viewModelScope.launch {
      val current = _uiState.value
      if (current.pendingParentUid.isBlank()) return@launch
      val result = cloudRepo.respondToParentPairingAsStudent(
        studentId = current.studentId,
        approve = approve,
        pendingParentUid = current.pendingParentUid,
        pendingParentId = current.pendingParentId
      )
      if (result.isSuccess) {
        pushNotificationAlert(
          targetRole = "PARENT",
          category = "REMOTE_COMMAND",
          title = if (approve) "Student Approved Pairing" else "Student Declined Pairing",
          body = "${current.studentName} (${current.studentId}) ${if (approve) "accepted" else "declined"} your Lock Monitor connection."
        )
      }
    }
  }

  fun submitRequestFromStudent(
    requestType: String,
    details: String,
    payloadValue: String
  ) {
    viewModelScope.launch {
      val state = _uiState.value
      val targetParentUid = state.pairedParentUid.ifBlank { currentUserId }
      val res = cloudRepo.submitStudentRequest(
        studentId = state.studentId,
        studentName = state.studentName,
        parentUid = targetParentUid,
        requestType = requestType,
        details = details.trim(),
        payloadValue = payloadValue.trim()
      )
      if (res.isSuccess) {
        pushNotificationAlert(
          targetRole = "PARENT",
          category = "REQUEST_NEW",
          title = "New ${requestType.replace("_", " ")} from ${state.studentName}",
          body = details.trim()
        )
      } else {
        _uiState.update {
          it.copy(statusBannerMessage = "Could not submit request: ${res.exceptionOrNull()?.localizedMessage}")
        }
      }
    }
  }

  fun toggleStudentOfflineSimulation(isOffline: Boolean) {
    _uiState.update {
      it.copy(
        studentSimulatedOffline = isOffline,
        statusBannerMessage = if (isOffline) {
          "Student device offline: Enforcing last saved local schedule & app policy. Remote commands will queue as PENDING."
        } else {
          "Student device back online: Synchronizing pending commands & policy with Cloud Firestore."
        }
      )
    }
  }

  // --- Parent Actions (Lock Monitor) ---

  fun registerOrUpdateParentAccount(
    name: String,
    recoveryEmail: String,
    rawPin: String
  ) {
    viewModelScope.launch {
      val existing = _uiState.value.parentAccount
      val parentId = existing?.parentId?.takeIf { it.isNotBlank() } ?: SecurityCryptoUtil.generateParentId()
      val salt = SecurityCryptoUtil.generateSalt()
      val pinHash = SecurityCryptoUtil.hashPinWithSalt(rawPin, salt)
      val res = cloudRepo.createOrUpdateParentAccount(
        parentId = parentId,
        name = name.trim(),
        recoveryEmail = recoveryEmail.trim(),
        pinHash = pinHash,
        pinSalt = salt,
        connectedStudentIds = existing?.connectedStudentIds ?: emptyList()
      )
      if (res.isSuccess) {
        _uiState.update {
          it.copy(
            parentAccount = res.getOrNull(),
            parentPinUnlocked = true,
            parentFailedPinAttempts = 0,
            parentPinError = null,
            statusBannerMessage = "Parent account registered! Your Parent ID is $parentId."
          )
        }
      } else {
        _uiState.update {
          it.copy(parentPinError = res.exceptionOrNull()?.localizedMessage ?: "Registration failed")
        }
      }
    }
  }

  fun verifyParentPin(enteredPin: String) {
    val state = _uiState.value
    val now = System.currentTimeMillis()
    if (now < state.parentLockoutUntilMillis) {
      val remainingSec = ((state.parentLockoutUntilMillis - now) / 1000L).coerceAtLeast(1L)
      _uiState.update {
        it.copy(parentPinError = "Rate limit active: Too many failed attempts. Try again in ${remainingSec}s.")
      }
      return
    }
    val account = state.parentAccount
    if (account == null) {
      _uiState.update { it.copy(parentPinError = "Complete parent registration first.") }
      return
    }
    val candidateHash = SecurityCryptoUtil.hashPinWithSalt(enteredPin, account.pinSalt)
    if (candidateHash == account.pinHash) {
      _uiState.update {
        it.copy(
          parentPinUnlocked = true,
          parentFailedPinAttempts = 0,
          parentLockoutUntilMillis = 0L,
          parentPinError = null
        )
      }
    } else {
      val attempts = state.parentFailedPinAttempts + 1
      val lockoutUntil = if (attempts >= 5) now + 30_000L else 0L
      val msg = if (attempts >= 5) {
        "5 failed attempts! PIN entry locked for 30 seconds."
      } else {
        "Incorrect PIN ($attempts/5 attempts)."
      }
      _uiState.update {
        it.copy(
          parentFailedPinAttempts = attempts,
          parentLockoutUntilMillis = lockoutUntil,
          parentPinError = msg
        )
      }
    }
  }

  fun resetParentPinWithRecoveryEmail(enteredEmail: String, newPin: String): Boolean {
    val account = _uiState.value.parentAccount ?: return false
    if (!account.recoveryEmail.equals(enteredEmail.trim(), ignoreCase = true)) {
      _uiState.update { it.copy(parentPinError = "Recovery email does not match registered email.") }
      return false
    }
    registerOrUpdateParentAccount(
      name = account.name,
      recoveryEmail = account.recoveryEmail,
      rawPin = newPin
    )
    return true
  }

  fun lockParentMonitorApp() {
    _uiState.update { it.copy(parentPinUnlocked = false, parentPinError = null) }
  }

  fun selectConnectedStudent(index: Int) {
    _uiState.update { it.copy(selectedStudentIndex = index) }
  }

  fun sendParentPairingRequest(targetStudentId: String) {
    viewModelScope.launch {
      val state = _uiState.value
      val parentAccount = state.parentAccount
      val parentId = parentAccount?.parentId ?: SecurityCryptoUtil.generateParentId()
      val parentName = parentAccount?.name ?: "Parent"
      val cleanStudentId = targetStudentId.trim().uppercase(Locale.US)
      val res = cloudRepo.requestPairingFromParent(
        studentId = cleanStudentId,
        parentId = parentId,
        parentName = parentName
      )
      if (res.isSuccess) {
        _uiState.update {
          it.copy(statusBannerMessage = "Pairing request sent to $cleanStudentId. Waiting for student-side approval.")
        }
      } else {
        _uiState.update {
          it.copy(statusBannerMessage = "Could not find unpaired Student ID $cleanStudentId.")
        }
      }
    }
  }

  fun sendParentLockOrUnlockCommand(lockNow: Boolean) {
    viewModelScope.launch {
      val state = _uiState.value
      val targetStudent = state.connectedStudents.getOrNull(state.selectedStudentIndex)
      val studentId = targetStudent?.studentId ?: state.studentId
      val studentUid = targetStudent?.studentUid ?: state.currentUserUid
      val cmdType = if (lockNow) "LOCK_NOW" else "UNLOCK_NOW"

      val res = cloudRepo.issueRemoteCommandFromParent(
        studentId = studentId,
        studentUid = studentUid,
        commandType = cmdType,
        durationMinutes = 0L
      )
      if (res.isSuccess) {
        val offlineNote = if (state.studentSimulatedOffline) {
          "Student device is offline: Command recorded as PENDING and will execute when connectivity returns."
        } else {
          "$cmdType command sent and awaiting device confirmation."
        }
        _uiState.update { it.copy(statusBannerMessage = offlineNote) }
      }
    }
  }

  fun toggleApprovedAppForSelectedStudent(packageName: String) {
    viewModelScope.launch {
      val state = _uiState.value
      val targetStudent = state.connectedStudents.getOrNull(state.selectedStudentIndex)
      val studentId = targetStudent?.studentId ?: state.studentId
      val currentApproved = targetStudent?.approvedApps ?: state.approvedApps
      val currentSchedules = targetStudent?.authorizedScheduleSlots ?: state.authorizedScheduleSlots

      val updatedApps = if (currentApproved.contains(packageName)) {
        currentApproved - packageName
      } else {
        currentApproved + packageName
      }
      cloudRepo.updateParentAuthorizedPolicy(
        studentId = studentId,
        approvedApps = updatedApps,
        authorizedScheduleSlots = currentSchedules
      )
      pushNotificationAlert(
        targetRole = "STUDENT",
        category = "REMOTE_COMMAND",
        title = "App Policy Updated by Parent",
        body = "${packageName.substringAfterLast('.')} is now ${if (updatedApps.contains(packageName)) "Approved" else "Restricted"}."
      )
    }
  }

  fun addAuthorizedScheduleSlotFromParent(daysLabel: String, start: String, end: String, label: String) {
    viewModelScope.launch {
      val state = _uiState.value
      val targetStudent = state.connectedStudents.getOrNull(state.selectedStudentIndex)
      val studentId = targetStudent?.studentId ?: state.studentId
      val currentApproved = targetStudent?.approvedApps ?: state.approvedApps
      val currentSchedules = targetStudent?.authorizedScheduleSlots ?: state.authorizedScheduleSlots

      val formatted = "$daysLabel|$start|$end|${label.ifBlank { "Allowed Window" }}"
      val updatedSchedules = (currentSchedules + formatted).distinct().take(30)
      cloudRepo.updateParentAuthorizedPolicy(
        studentId = studentId,
        approvedApps = currentApproved,
        authorizedScheduleSlots = updatedSchedules
      )
      pushNotificationAlert(
        targetRole = "STUDENT",
        category = "REMOTE_COMMAND",
        title = "Authorized Schedule Updated",
        body = "Parent added allowed normal-use window: $daysLabel $start–$end"
      )
    }
  }

  fun removeAuthorizedScheduleSlotFromParent(rawSlot: String) {
    viewModelScope.launch {
      val state = _uiState.value
      val targetStudent = state.connectedStudents.getOrNull(state.selectedStudentIndex)
      val studentId = targetStudent?.studentId ?: state.studentId
      val currentApproved = targetStudent?.approvedApps ?: state.approvedApps
      val currentSchedules = targetStudent?.authorizedScheduleSlots ?: state.authorizedScheduleSlots

      val updatedSchedules = currentSchedules - rawSlot
      cloudRepo.updateParentAuthorizedPolicy(
        studentId = studentId,
        approvedApps = currentApproved,
        authorizedScheduleSlots = updatedSchedules
      )
    }
  }

  fun decideStudentRequestFromParent(
    request: StudentRequest,
    approve: Boolean,
    emergencyDurationMinutes: Long = 15L
  ) {
    viewModelScope.launch {
      if (request.status != "PENDING") {
        _uiState.update { it.copy(statusBannerMessage = "Request already processed (duplicate approval prevented).") }
        return@launch
      }
      val dur = if (approve && request.requestType == "EMERGENCY_UNLOCK") emergencyDurationMinutes else 0L
      val res = cloudRepo.decideStudentRequestAsParent(
        requestId = request.id,
        approve = approve,
        approvedDurationMinutes = dur
      )
      if (res.isSuccess && approve) {
        val state = _uiState.value
        val targetStudent = state.connectedStudents.find { it.studentId == request.studentId }
        val currentApproved = targetStudent?.approvedApps ?: state.approvedApps
        val currentSchedules = targetStudent?.authorizedScheduleSlots ?: state.authorizedScheduleSlots

        when (request.requestType) {
          "EMERGENCY_UNLOCK" -> {
            cloudRepo.issueRemoteCommandFromParent(
              studentId = request.studentId,
              studentUid = request.studentUid,
              commandType = "EMERGENCY_UNLOCK",
              durationMinutes = dur
            )
          }
          "APP_PERMISSION" -> {
            val pkg = request.payloadValue.ifBlank { "com.whatsapp" }
            cloudRepo.updateParentAuthorizedPolicy(
              studentId = request.studentId,
              approvedApps = (currentApproved + pkg).distinct(),
              authorizedScheduleSlots = currentSchedules
            )
          }
          "SCHEDULE_CHANGE" -> {
            val slot = request.payloadValue.ifBlank { "MON-FRI|17:00|19:00|Approved Request" }
            cloudRepo.updateParentAuthorizedPolicy(
              studentId = request.studentId,
              approvedApps = currentApproved,
              authorizedScheduleSlots = (currentSchedules + slot).distinct()
            )
          }
        }
      }
    }
  }

  companion object {
    fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val cloudRepository = SafeLockCloudRepository(firestore, Firebase.auth)
        val localDb = SafeLockLocalDatabase.getInstance(app)
        SafeLockSystemViewModel(
          appContext = app.applicationContext,
          cloudRepo = cloudRepository,
          localDao = localDb.studentPolicyDao(),
          currentUserId = currentUserId
        )
      }
    }
  }
}
