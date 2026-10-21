package com.example.lockmonitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.auth.authStateFlow
import com.example.data.model.SecurityCryptoUtil
import com.example.data.model.StudentRequest
import com.example.device.DeviceManagementController
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SafeLockSystemUiState
import com.example.viewmodel.SafeLockSystemViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Standalone Activity for Application B: Lock Monitor (Parent Application).
 */
class LockMonitorActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val currentUser by Firebase.auth.authStateFlow()
          .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
        val uid = currentUser?.uid ?: "local_parent_session"
        val vm: SafeLockSystemViewModel = viewModel(
          key = uid,
          factory = SafeLockSystemViewModel.factory(uid)
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        LockMonitorParentAppScreen(
          state = state,
          viewModel = vm
        )
      }
    }
  }
}

private enum class ParentModalType {
  NONE,
  REQUESTS_PAGE,
  PAIR_NEW_STUDENT,
  NOTIFICATIONS_DRAWER,
  PIN_RECOVERY
}

@Composable
fun LockMonitorParentAppScreen(
  state: SafeLockSystemUiState,
  viewModel: SafeLockSystemViewModel
) {
  val context = LocalContext.current
  var activeModal by remember { mutableStateOf(ParentModalType.NONE) }

  BackHandler(enabled = activeModal != ParentModalType.NONE) {
    activeModal = ParentModalType.NONE
  }

  // 4.2 & 4.3 Parent Registration & App-Access PIN Gate (Parent App Only — No Student PIN)
  if (!state.parentPinUnlocked) {
    ParentPinAndRegistrationGate(
      state = state,
      onRegisterParent = { name, email, pin ->
        viewModel.registerOrUpdateParentAccount(name, email, pin)
      },
      onVerifyPin = { pin ->
        viewModel.verifyParentPin(pin)
      },
      onResetWithRecoveryEmail = { email, newPin ->
        viewModel.resetParentPinWithRecoveryEmail(email, newPin)
      }
    )
    return
  }

  val allCatalogApps = remember { DeviceManagementController.queryInstalledAndSupportedApps(context) }
  val activeStudent = state.connectedStudents.getOrNull(state.selectedStudentIndex)
  val displayedStudentName = activeStudent?.studentName ?: state.studentName
  val displayedStudentId = activeStudent?.studentId ?: state.studentId
  val displayedApprovedApps = activeStudent?.approvedApps ?: state.approvedApps
  val displayedSchedules = activeStudent?.authorizedScheduleSlots ?: state.authorizedScheduleSlots

  val parentNotifications = remember(state.notifications) {
    state.notifications.filter { it.targetRole == "PARENT" }
  }
  val unreadParentNotifs = parentNotifications.count { !it.isRead }

  var schedDay by remember { mutableStateOf("MON-FRI") }
  var schedStart by remember { mutableStateOf("16:00") }
  var schedEnd by remember { mutableStateOf("18:00") }
  var copiedParentId by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF070B14))
      .testTag("lock_monitor_parent_root")
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Top Parent Header Bar with Parent ID & Copy ID Button + Real-Time Notification Bell
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          border = BorderStroke(1.dp, Color(0x22FFFFFF)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFF312E81),
                  modifier = Modifier.size(40.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF818CF8))
                  }
                }
                Column {
                  Text(
                    text = stringResource(R.string.lock_monitor_parent_app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                  )
                  val parentIdText = state.parentAccount?.parentId ?: "LM-P4D81"
                  Text(
                    text = "Parent: ${state.parentAccount?.name ?: "Parent"} • ID: $parentIdText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = {
                    viewModel.markAllNotificationsRead()
                    activeModal = ParentModalType.NOTIFICATIONS_DRAWER
                  },
                  modifier = Modifier.testTag("parent_notifications_button")
                ) {
                  BadgedBox(
                    badge = {
                      if (unreadParentNotifs > 0) {
                        Badge(containerColor = Color(0xFFF43F5E)) {
                          Text(unreadParentNotifs.toString())
                        }
                      }
                    }
                  ) {
                    Icon(
                      imageVector = Icons.Default.Notifications,
                      contentDescription = stringResource(R.string.cd_notifications_bell),
                      tint = Color(0xFF818CF8)
                    )
                  }
                }
                IconButton(
                  onClick = { viewModel.lockParentMonitorApp() },
                  modifier = Modifier.testTag("lock_parent_app_pin_button")
                ) {
                  Icon(Icons.Default.Lock, contentDescription = "Lock Parent App with PIN", tint = Color(0xFFCBD5E1))
                }
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  val parentIdText = state.parentAccount?.parentId ?: "LM-P4D81"
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                  clipboard?.setPrimaryClip(ClipData.newPlainText("Parent ID", parentIdText))
                  copiedParentId = true
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("copy_parent_id_button")
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (copiedParentId) "Copied ID!" else "Copy Parent ID", fontSize = 12.sp)
              }
              Button(
                onClick = { activeModal = ParentModalType.PAIR_NEW_STUDENT },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                modifier = Modifier
                  .weight(1f)
                  .testTag("open_pair_student_button")
              ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pair Student ID", color = Color.White, fontSize = 12.sp)
              }
            }
          }
        }
      }

      // 4.4 Multiple Students Selector List
      item {
        Text(
          text = "CONNECTED STUDENTS (${state.connectedStudents.size.coerceAtLeast(1)})",
          style = MaterialTheme.typography.labelLarge,
          color = Color(0xFF94A3B8),
          fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (state.connectedStudents.isEmpty()) {
          FilterChip(
            selected = true,
            onClick = {},
            label = { Text("${state.studentName} (${state.studentId})") }
          )
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.connectedStudents, key = { _, s -> s.studentId }) { idx, student ->
              FilterChip(
                selected = idx == state.selectedStudentIndex,
                onClick = { viewModel.selectConnectedStudent(idx) },
                label = { Text("${student.studentName} (${student.studentId})") }
              )
            }
          }
        }
      }

      // 4.5 Dashboard Header Card: Student Name, Student ID, Connection Status, SafeLock Status
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          border = BorderStroke(1.dp, Color(0xFF312E81)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("parent_dashboard_status_card")
        ) {
          Column(
            modifier = Modifier
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF1E1B4B), Color(0xFF111827))
                )
              )
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = displayedStudentName,
                  style = MaterialTheme.typography.headlineMedium,
                  color = Color.White
                )
                Text(
                  text = "Student ID: $displayedStudentId • Pairing: ${activeStudent?.pairingStatus ?: state.pairingStatus}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFFC7D2FE),
                  fontSize = 12.sp
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Connection Status (Connected/Online, Disconnected/Offline, Synchronization Pending)
              val connText = when {
                state.studentSimulatedOffline -> "Disconnected / Offline (Stale)"
                (activeStudent?.safeLockStatus ?: state.safeLockStatus) == "COMMAND_PENDING" -> "Synchronization Pending"
                else -> "Connected / Online"
              }
              val connColor = when {
                state.studentSimulatedOffline -> Color(0xFFFDA4AF)
                connText.contains("Pending") -> Color(0xFFFCD34D)
                else -> Color(0xFF6EE7B7)
              }
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x88070B14),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("CONNECTION STATUS", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(connText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = connColor)
                }
              }

              // SafeLock Status (Restricted/Active, Normal-Use Period, Command Pending, Status Unknown)
              val rawLockStatus = activeStudent?.safeLockStatus ?: state.safeLockStatus
              val lockLabel = when {
                state.studentSimulatedOffline && rawLockStatus == "COMMAND_PENDING" -> "Command Pending (Queued)"
                state.studentSimulatedOffline -> "Status Unknown (Device Offline)"
                rawLockStatus == "COMMAND_PENDING" -> "Command Pending"
                rawLockStatus == "NORMAL_USE" -> "Normal-Use Period"
                rawLockStatus == "EMERGENCY_UNLOCKED" -> "Emergency Unlocked"
                else -> "Restricted / Active"
              }
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x88070B14),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text("SAFELOCK STATUS", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(lockLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF67E8F9))
                }
              }
            }
          }
        }
      }

      // 4.6 Lock Now Button, 4.7 Immediate Unlock Button, 4.8 Separate Requests Button
      // (No large pending-request card or count directly on the main dashboard!)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = { viewModel.sendParentLockOrUnlockCommand(lockNow = true) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("parent_lock_now_button")
          ) {
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.cd_lock_now), tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Lock Now", fontWeight = FontWeight.Bold, color = Color.White)
          }

          Button(
            onClick = { viewModel.sendParentLockOrUnlockCommand(lockNow = false) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("parent_unlock_now_button")
          ) {
            Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.cd_unlock_now), tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Unlock", fontWeight = FontWeight.Bold, color = Color.White)
          }

          Button(
            onClick = { activeModal = ParentModalType.REQUESTS_PAGE },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("parent_open_requests_button")
          ) {
            Icon(Icons.Default.Inbox, contentDescription = "Open Requests Section", tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Requests", fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }

      // 4.9 Approved Applications Controls (No Parent Wallpaper Control per Policy)
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Approved Applications for $displayedStudentName",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White
            )
            Text(
              text = "Toggle which installed applications are available while SafeLock is active. Wallpaper remains strictly student-controlled.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF94A3B8),
              fontSize = 12.sp
            )

            allCatalogApps.forEach { app ->
              val isApproved = displayedApprovedApps.contains(app.packageName)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(app.appName, fontWeight = FontWeight.SemiBold, color = Color.White)
                  Text(app.packageName, fontSize = 11.sp, color = Color(0xFF64748B))
                }
                Switch(
                  checked = isApproved,
                  onCheckedChange = { viewModel.toggleApprovedAppForSelectedStudent(app.packageName) },
                  modifier = Modifier.testTag("parent_toggle_app_${app.packageName}")
                )
              }
            }
          }
        }
      }

      // 4.9 Authorized Schedule Management
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Authorized Normal-Use Schedule",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White
            )
            displayedSchedules.forEach { rawSlot ->
              val parsed = SecurityCryptoUtil.parseScheduleSlot(rawSlot)
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = "${parsed.daysLabel} • ${parsed.startTime} – ${parsed.endTime}",
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                      fontSize = 13.sp
                    )
                    Text(parsed.title, fontSize = 11.sp, color = Color(0xFF94A3B8))
                  }
                  IconButton(onClick = { viewModel.removeAuthorizedScheduleSlotFromParent(rawSlot) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Schedule Slot", tint = Color(0xFFFDA4AF))
                  }
                }
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("MON-FRI", "SAT-SUN", "DAILY").forEach { d ->
                FilterChip(
                  selected = schedDay == d,
                  onClick = { schedDay = d },
                  label = { Text(d, fontSize = 11.sp) }
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = schedStart,
                onValueChange = { schedStart = it },
                label = { Text("Start") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = schedEnd,
                onValueChange = { schedEnd = it },
                label = { Text("End") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
            }

            OutlinedButton(
              onClick = {
                viewModel.addAuthorizedScheduleSlotFromParent(
                  daysLabel = schedDay,
                  start = schedStart,
                  end = schedEnd,
                  label = "Parent Authorized Window"
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("parent_add_schedule_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add Authorized Schedule Window")
            }
          }
        }
      }
    }

    // Separate Modals for Lock Monitor
    when (activeModal) {
      ParentModalType.REQUESTS_PAGE -> {
        ParentRequestsSectionModal(
          requests = state.parentRequests.ifEmpty { state.studentRequests },
          onDecideRequest = { req, approve, durMins ->
            viewModel.decideStudentRequestFromParent(req, approve, durMins)
          },
          onClose = { activeModal = ParentModalType.NONE }
        )
      }
      ParentModalType.PAIR_NEW_STUDENT -> {
        ParentPairStudentModal(
          onSendPairingRequest = { studentId ->
            viewModel.sendParentPairingRequest(studentId)
            activeModal = ParentModalType.NONE
          },
          onClose = { activeModal = ParentModalType.NONE }
        )
      }
      ParentModalType.NOTIFICATIONS_DRAWER -> {
        ParentNotificationsModal(
          notifications = parentNotifications,
          onClose = { activeModal = ParentModalType.NONE }
        )
      }
      else -> Unit
    }
  }
}

@Composable
private fun ParentPinAndRegistrationGate(
  state: SafeLockSystemUiState,
  onRegisterParent: (String, String, String) -> Unit,
  onVerifyPin: (String) -> Unit,
  onResetWithRecoveryEmail: (String, String) -> Boolean
) {
  var isCreatingAccount by remember(state.parentAccount) {
    mutableStateOf(state.parentAccount == null)
  }
  var showRecoveryMode by remember { mutableStateOf(false) }

  var nameInput by remember { mutableStateOf("Alice Sharma") }
  var emailInput by remember { mutableStateOf("alice@example.com") }
  var pinInput by remember { mutableStateOf("") }
  var recoveryEmailInput by remember { mutableStateOf("") }
  var recoveryNewPin by remember { mutableStateOf("") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(Color(0xFF1E1B4B), Color(0xFF070B14))
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(20.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
      border = BorderStroke(1.dp, Color(0x33FFFFFF)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("parent_pin_gate_card")
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = when {
            showRecoveryMode -> "Recover Parent App-Access PIN"
            isCreatingAccount -> "Lock Monitor — Parent Registration"
            else -> "Lock Monitor — Enter Parent PIN"
          },
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )

        if (showRecoveryMode) {
          Text(
            text = "Verify your registered recovery email to reset your app-access PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8)
          )
          OutlinedTextField(
            value = recoveryEmailInput,
            onValueChange = { recoveryEmailInput = it },
            label = { Text("Registered Recovery Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = recoveryNewPin,
            onValueChange = { if (it.length <= 6) recoveryNewPin = it },
            label = { Text("New 4–6 Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          if (state.parentPinError != null) {
            Text(state.parentPinError, color = Color(0xFFFDA4AF), fontSize = 12.sp)
          }
          Button(
            onClick = {
              if (onResetWithRecoveryEmail(recoveryEmailInput, recoveryNewPin)) {
                showRecoveryMode = false
              }
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Verify Email & Reset PIN", fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = { showRecoveryMode = false },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Back to PIN Entry")
          }
        } else if (isCreatingAccount) {
          Text(
            text = "Create your parent profile and salted SHA-256 app-access PIN. A unique Parent ID (LM-XXXXX) is generated automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8)
          )
          OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Parent Name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("parent_reg_name_input")
          )
          OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it },
            label = { Text("Recovery Email") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("parent_reg_email_input")
          )
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6) pinInput = it },
            label = { Text("Create 4–6 Digit App-Access PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("parent_reg_pin_input")
          )
          if (state.parentPinError != null) {
            Text(state.parentPinError, color = Color(0xFFFDA4AF), fontSize = 12.sp)
          }
          Button(
            onClick = {
              if (pinInput.length >= 4 && nameInput.isNotBlank() && emailInput.isNotBlank()) {
                onRegisterParent(nameInput, emailInput, pinInput)
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("parent_complete_registration_button")
          ) {
            Text("Register Parent Account & Generate LM-ID", fontWeight = FontWeight.Bold)
          }
        } else {
          Text(
            text = "Lock Monitor asks for your parent PIN every time it opens. Plain-text PINs are never stored; verified against salted SHA-256 hash with 5-attempt rate limiting.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8)
          )
          OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6) pinInput = it },
            label = { Text("Enter App-Access PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("parent_verify_pin_input")
          )
          if (state.parentPinError != null) {
            Text(state.parentPinError, color = Color(0xFFFDA4AF), fontSize = 12.sp)
          }
          Button(
            onClick = { onVerifyPin(pinInput) },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("parent_unlock_pin_button")
          ) {
            Text("Unlock Lock Monitor", fontWeight = FontWeight.Bold)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            OutlinedButton(onClick = { showRecoveryMode = true }) {
              Text("Forgot PIN?", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { isCreatingAccount = true }) {
              Text("Update Registration", fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ParentRequestsSectionModal(
  requests: List<StudentRequest>,
  onDecideRequest: (StudentRequest, Boolean, Long) -> Unit,
  onClose: () -> Unit
) {
  Surface(
    color = Color(0xF5070B14),
    modifier = Modifier
      .fillMaxSize()
      .testTag("parent_requests_modal")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Student Requests",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }

      if (requests.isEmpty()) {
        Text("No student requests received yet.", color = Color(0xFF94A3B8), fontSize = 13.sp)
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          items(requests, key = { it.id.ifBlank { it.details } }) { req ->
            var selectedDuration by remember { mutableLongStateOf(15L) }
            var customDurationInput by remember { mutableStateOf("45") }
            var isCustomDuration by remember { mutableStateOf(false) }

            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
              border = BorderStroke(1.dp, Color(0x22FFFFFF)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "${req.studentName} (${req.studentId}) — ${req.requestType.replace("_", " ")}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                  )
                  Text(
                    text = req.status,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = when (req.status) {
                      "APPROVED" -> Color(0xFF6EE7B7)
                      "REJECTED" -> Color(0xFFFDA4AF)
                      else -> Color(0xFFFCD34D)
                    }
                  )
                }

                Text(req.details, color = Color(0xFFE2E8F0), fontSize = 13.sp)

                // 3.8 & 4.8 Duration selector before approving Emergency Unlock requests (5m, 15m, 30m, Custom)
                if (req.status == "PENDING" && req.requestType == "EMERGENCY_UNLOCK") {
                  Text(
                    text = "Select Emergency Unlock Duration:",
                    fontSize = 12.sp,
                    color = Color(0xFF67E8F9),
                    fontWeight = FontWeight.SemiBold
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5L, 15L, 30L).forEach { mins ->
                      FilterChip(
                        selected = !isCustomDuration && selectedDuration == mins,
                        onClick = {
                          isCustomDuration = false
                          selectedDuration = mins
                        },
                        label = { Text("$mins min") }
                      )
                    }
                    FilterChip(
                      selected = isCustomDuration,
                      onClick = { isCustomDuration = true },
                      label = { Text("Custom") }
                    )
                  }
                  if (isCustomDuration) {
                    OutlinedTextField(
                      value = customDurationInput,
                      onValueChange = { customDurationInput = it },
                      label = { Text("Custom Duration (Minutes, 1–240)") },
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }

                if (req.status == "PENDING") {
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                      onClick = {
                        val finalMins = if (isCustomDuration) {
                          customDurationInput.toLongOrNull()?.coerceIn(1L, 240L) ?: 15L
                        } else {
                          selectedDuration
                        }
                        onDecideRequest(req, true, finalMins)
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("approve_request_${req.id}")
                    ) {
                      Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                      onClick = { onDecideRequest(req, false, 0L) },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("reject_request_${req.id}")
                    ) {
                      Text("Reject", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ParentPairStudentModal(
  onSendPairingRequest: (String) -> Unit,
  onClose: () -> Unit
) {
  var studentIdInput by remember { mutableStateOf("") }

  Surface(
    color = Color(0xF5070B14),
    modifier = Modifier
      .fillMaxSize()
      .testTag("parent_pair_student_modal")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Pair with Student Device", style = MaterialTheme.typography.titleLarge, color = Color.White)
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }
      Text(
        text = "Enter the unique Student ID (SL-XXXXX) displayed on the student's SafeLock app. The student must approve the connection request.",
        color = Color(0xFF94A3B8),
        fontSize = 13.sp
      )
      OutlinedTextField(
        value = studentIdInput,
        onValueChange = { studentIdInput = it.uppercase() },
        label = { Text("Student ID (e.g., SL-A7K92)") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pair_student_id_input")
      )
      Button(
        onClick = { if (studentIdInput.isNotBlank()) onSendPairingRequest(studentIdInput) },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("submit_pair_student_button")
      ) {
        Text("Send Pairing Request", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ParentNotificationsModal(
  notifications: List<com.example.notifications.RealtimeNotificationItem>,
  onClose: () -> Unit
) {
  Surface(
    color = Color(0xF5070B14),
    modifier = Modifier
      .fillMaxSize()
      .testTag("parent_notifications_modal")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Lock Monitor Real-Time Alerts", style = MaterialTheme.typography.titleLarge, color = Color.White)
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }
      Text(
        text = "Real-time cloud notifications whenever a connected student requests app access, schedule changes, or emergency unlock.",
        color = Color(0xFF94A3B8),
        fontSize = 12.sp
      )
      if (notifications.isEmpty()) {
        Text("No parent alerts yet.", color = Color(0xFF64748B), fontSize = 13.sp)
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(notifications, key = { it.id }) { item ->
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(item.title, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8), fontSize = 13.sp)
                  Text(item.timestampLabel, color = Color(0xFF64748B), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.body, color = Color.White, fontSize = 13.sp)
              }
            }
          }
        }
      }
    }
  }
}
