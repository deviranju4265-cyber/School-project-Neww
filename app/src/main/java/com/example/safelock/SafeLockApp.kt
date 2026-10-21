package com.example.safelock

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.authStateFlow
import com.example.device.DeviceManagementController
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SafeLockSystemUiState
import com.example.viewmodel.SafeLockSystemViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Standalone Activity for Application A: SafeLock (Student Application).
 * Configured with CATEGORY_HOME support in AndroidManifest.xml so it can act as the
 * restricted student home screen launcher on Android devices.
 */
class SafeLockActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val currentUser by Firebase.auth.authStateFlow()
          .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
        val uid = currentUser?.uid ?: "local_student_session"
        val vm: SafeLockSystemViewModel = viewModel(
          key = uid,
          factory = SafeLockSystemViewModel.factory(uid)
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        SafeLockStudentAppScreen(
          state = state,
          viewModel = vm,
          onSwitchToParentMonitor = { finish() }
        )
      }
    }
  }
}

private enum class StudentSheetType {
  NONE,
  WALLPAPER_PICKER,
  FIRST_TIME_SETUP,
  REQUESTS_AND_SCHEDULE,
  EMERGENCY_UNLOCK,
  NOTIFICATIONS_CENTER
}

data class WallpaperPresetOption(
  val id: String,
  val title: String,
  val brush: Brush
)

val STUDENT_WALLPAPER_PRESETS = listOf(
  WallpaperPresetOption(
    id = "AURORA_MIDNIGHT",
    title = "Midnight Aurora",
    brush = Brush.verticalGradient(
      colors = listOf(Color(0xFF0E3A53), Color(0xFF0B1528), Color(0xFF070B14))
    )
  ),
  WallpaperPresetOption(
    id = "CYBER_INDIGO",
    title = "Cyber Indigo",
    brush = Brush.linearGradient(
      colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF064E3B))
    )
  ),
  WallpaperPresetOption(
    id = "OBSIDIAN_SLATE",
    title = "Deep Obsidian",
    brush = Brush.verticalGradient(
      colors = listOf(Color(0xFF1E293B), Color(0xFF090D16), Color(0xFF030712))
    )
  ),
  WallpaperPresetOption(
    id = "CRIMSON_DUSK",
    title = "Crimson Dusk",
    brush = Brush.linearGradient(
      colors = listOf(Color(0xFF3B0764), Color(0xFF090D16), Color(0xFF4C0519))
    )
  )
)

@Composable
fun SafeLockStudentAppScreen(
  state: SafeLockSystemUiState,
  viewModel: SafeLockSystemViewModel,
  onSwitchToParentMonitor: () -> Unit = {}
) {
  val context = LocalContext.current
  val activity = context as? Activity
  var activeSheet by remember {
    mutableStateOf(
      if (!state.studentSetupDone) StudentSheetType.FIRST_TIME_SETUP else StudentSheetType.NONE
    )
  }

  BackHandler(enabled = activeSheet != StudentSheetType.NONE) {
    activeSheet = StudentSheetType.NONE
  }

  val installedApps = remember { DeviceManagementController.queryInstalledAndSupportedApps(context) }
  val approvedAppItems = remember(state.approvedApps, installedApps) {
    installedApps.filter { app ->
      state.approvedApps.contains(app.packageName) || app.isSystemEssential
    }
  }
  val managementStatus = remember { DeviceManagementController.getDeviceManagementStatus(context) }

  // Apply native Android LockTask / DevicePolicyManager restriction state when active
  LaunchedEffect(state.safeLockStatus, state.approvedApps) {
    DeviceManagementController.applyRestrictionPolicyOnDevice(
      activity = activity,
      context = context,
      restricted = state.safeLockStatus == "RESTRICTED",
      approvedPackages = state.approvedApps
    )
  }

  val activePreset = STUDENT_WALLPAPER_PRESETS.find { it.id == state.selectedWallpaperPreset }
    ?: STUDENT_WALLPAPER_PRESETS.first()

  val studentNotifications = remember(state.notifications) {
    state.notifications.filter { it.targetRole == "STUDENT" }
  }
  val unreadStudentNotifications = studentNotifications.count { !it.isRead }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(activePreset.brush)
      .testTag("safelock_student_root")
  ) {
    // Optional student-chosen custom gallery photo wallpaper
    if (state.customWallpaperUri.isNotBlank()) {
      AsyncImage(
        model = state.customWallpaperUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0x99070B14))
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // 3.1 Top of SafeLock screen: App Logo + App Name: SafeLock
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xCC111827)),
          border = BorderStroke(1.dp, Color(0x26FFFFFF)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B1120),
                border = BorderStroke(1.dp, Color(0xFF06B6D4)),
                modifier = Modifier.size(40.dp)
              ) {
                Image(
                  painter = painterResource(id = R.drawable.ic_launcher_foreground),
                  contentDescription = stringResource(R.string.cd_safelock_logo),
                  modifier = Modifier.fillMaxSize()
                )
              }
              Column {
                Text(
                  text = stringResource(R.string.safelock_student_app_name),
                  style = MaterialTheme.typography.titleLarge,
                  color = Color.White
                )
                Text(
                  text = "${state.studentName} • ID: ${state.studentId}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFF94A3B8),
                  fontSize = 11.sp
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              // Real-time Notification Bell for Student
              IconButton(
                onClick = {
                  viewModel.markAllNotificationsRead()
                  activeSheet = StudentSheetType.NOTIFICATIONS_CENTER
                },
                modifier = Modifier.testTag("student_notifications_button")
              ) {
                BadgedBox(
                  badge = {
                    if (unreadStudentNotifications > 0) {
                      Badge(containerColor = Color(0xFFF43F5E)) {
                        Text(unreadStudentNotifications.toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications_bell),
                    tint = Color(0xFF67E8F9)
                  )
                }
              }

              // Restriction Status Pill
              val isRestricted = state.safeLockStatus == "RESTRICTED"
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isRestricted) Color(0x3306B6D4) else Color(0x3310B981),
                border = BorderStroke(
                  1.dp,
                  if (isRestricted) Color(0xFF06B6D4) else Color(0xFF10B981)
                )
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = if (isRestricted) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isRestricted) Color(0xFF67E8F9) else Color(0xFF6EE7B7),
                    modifier = Modifier.size(13.dp)
                  )
                  Text(
                    text = when (state.safeLockStatus) {
                      "EMERGENCY_UNLOCKED" -> "Emergency Unlocked"
                      "NORMAL_USE" -> "Normal Use"
                      "COMMAND_PENDING" -> "Sync Pending"
                      else -> "Restricted"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRestricted) Color(0xFF67E8F9) else Color(0xFF6EE7B7)
                  )
                }
              }
            }
          }
        }

        // Pending Parent Pairing Approval Card (Requirement 5: Student approves pairing request)
        AnimatedVisibility(visible = state.pairingStatus == "PENDING_APPROVAL" && state.pendingParentId.isNotBlank()) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xE61E1B4B)),
            border = BorderStroke(1.dp, Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Parent Connection Request Received",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
              )
              Text(
                text = "${state.pendingParentName.ifBlank { "Parent" }} (${state.pendingParentId}) wants to connect Lock Monitor to your SafeLock ID (${state.studentId}).",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC7D2FE),
                modifier = Modifier.padding(vertical = 6.dp)
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { viewModel.respondToParentPairing(approve = true) },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("student_approve_pairing_button")
                ) {
                  Text("Approve Pairing", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                  onClick = { viewModel.respondToParentPairing(approve = false) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("student_reject_pairing_button")
                ) {
                  Text("Decline", color = Color(0xFFFDA4AF))
                }
              }
            }
          }
        }

        // 3.1 Below Header: Digital Clock (Displays current time e.g. 08:30 PM, NO next-schedule countdown)
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0x77090D16)),
          border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("safelock_digital_clock_card")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = state.currentTimeFormatted,
              style = MaterialTheme.typography.displayLarge,
              color = Color.White,
              modifier = Modifier.testTag("safelock_digital_clock_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (state.pairingStatus == "PAIRED") {
                "Paired with Parent (${state.pairedParentId}) • ${if (state.studentSimulatedOffline) "Offline Policy Active" else "Cloud Synced"}"
              } else {
                "Share Student ID (${state.studentId}) with Parent to Pair"
              },
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFCBD5E1),
              fontSize = 12.sp
            )
          }
        }
      }

      // 3.2 & 3.3 Responsive Approved Applications Grid
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "APPROVED APPLICATIONS (${approvedAppItems.size})",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
          )
          Text(
            text = managementStatus.managementTierLabel.substringBefore(" ("),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF67E8F9),
            fontSize = 11.sp
          )
        }

        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 96.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(vertical = 4.dp),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("approved_apps_grid")
        ) {
          items(approvedAppItems, key = { it.packageName }) { app ->
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xCC111827)),
              border = BorderStroke(1.dp, Color(0x26FFFFFF)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  DeviceManagementController.launchApprovedRealApp(context, app.packageName)
                }
                .testTag("approved_app_${app.packageName}")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(app.accentHex)),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = app.appName.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                  )
                }
                Text(
                  text = app.appName,
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Center,
                  fontSize = 12.sp
                )
              }
            }
          }
        }
      }

      // Minimal Bottom Dock: Wallpaper, Requests, Setup & ID, Emergency Call/Unlock
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD9111827)),
        border = BorderStroke(1.dp, Color(0x28FFFFFF)),
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          StudentDockAction(
            icon = Icons.Default.Palette,
            label = "Wallpaper",
            tag = "dock_wallpaper_button",
            onClick = { activeSheet = StudentSheetType.WALLPAPER_PICKER }
          )
          StudentDockAction(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "Requests",
            tag = "dock_requests_button",
            onClick = { activeSheet = StudentSheetType.REQUESTS_AND_SCHEDULE }
          )
          StudentDockAction(
            icon = Icons.Default.Settings,
            label = "Setup & ID",
            tag = "dock_setup_button",
            onClick = { activeSheet = StudentSheetType.FIRST_TIME_SETUP }
          )
          StudentDockAction(
            icon = Icons.Default.Emergency,
            label = "Emergency",
            tag = "dock_emergency_button",
            tint = Color(0xFFFDA4AF),
            onClick = { activeSheet = StudentSheetType.EMERGENCY_UNLOCK }
          )
        }
      }
    }

    // Sub-Sheets / Modals for Student App
    when (activeSheet) {
      StudentSheetType.WALLPAPER_PICKER -> {
        StudentWallpaperSheet(
          state = state,
          onSaveWallpaper = { presetId, customUri ->
            viewModel.updateStudentWallpaper(presetId, customUri)
            activeSheet = StudentSheetType.NONE
          },
          onClose = { activeSheet = StudentSheetType.NONE }
        )
      }
      StudentSheetType.FIRST_TIME_SETUP -> {
        StudentFirstTimeSetupSheet(
          state = state,
          installedApps = installedApps,
          managementStatus = managementStatus,
          onCompleteSetup = { name, scheduleSlot, requestedApps, wpId ->
            viewModel.completeStudentInitialSetup(name, scheduleSlot, requestedApps, wpId)
            activeSheet = StudentSheetType.NONE
          },
          onSimulateReinstall = { viewModel.simulateReinstallNewStudentId() },
          onToggleOffline = { viewModel.toggleStudentOfflineSimulation(it) },
          onClose = { activeSheet = StudentSheetType.NONE }
        )
      }
      StudentSheetType.REQUESTS_AND_SCHEDULE -> {
        StudentRequestsSheet(
          state = state,
          installedApps = installedApps,
          onSubmitRequest = { type, details, payload ->
            viewModel.submitRequestFromStudent(type, details, payload)
          },
          onClose = { activeSheet = StudentSheetType.NONE }
        )
      }
      StudentSheetType.EMERGENCY_UNLOCK -> {
        StudentEmergencySheet(
          onDialEmergency = { DeviceManagementController.launchEmergencyDialer(context, "112") },
          onSubmitEmergencyUnlock = { reason ->
            viewModel.submitRequestFromStudent(
              requestType = "EMERGENCY_UNLOCK",
              details = "Emergency Unlock: $reason",
              payloadValue = "15"
            )
            activeSheet = StudentSheetType.NONE
          },
          onClose = { activeSheet = StudentSheetType.NONE }
        )
      }
      StudentSheetType.NOTIFICATIONS_CENTER -> {
        StudentNotificationsSheet(
          notifications = studentNotifications,
          onClose = { activeSheet = StudentSheetType.NONE }
        )
      }
      StudentSheetType.NONE -> Unit
    }
  }
}

@Composable
private fun StudentDockAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tag: String,
  tint: Color = Color(0xFF67E8F9),
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag(tag)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size(22.dp)
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = Color.White
    )
  }
}

@Composable
private fun StudentWallpaperSheet(
  state: SafeLockSystemUiState,
  onSaveWallpaper: (String, String) -> Unit,
  onClose: () -> Unit
) {
  var previewPresetId by remember { mutableStateOf(state.selectedWallpaperPreset) }
  var previewCustomUri by remember { mutableStateOf(state.customWallpaperUri) }

  // Zero-permission Android Photo Picker (Play Policy compliant!)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      previewCustomUri = uri.toString()
    }
  }

  val previewPreset = STUDENT_WALLPAPER_PRESETS.find { it.id == previewPresetId }
    ?: STUDENT_WALLPAPER_PRESETS.first()

  Surface(
    color = Color(0xF2090D16),
    modifier = Modifier
      .fillMaxSize()
      .testTag("student_wallpaper_sheet")
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
          text = "Student Wallpaper & Photo",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }

      Text(
        text = "Only you (the student) control your SafeLock wallpaper. The parent Lock Monitor app cannot change your wallpaper.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF94A3B8)
      )

      // Live Preview Box before applying
      Text(
        text = "LIVE PREVIEW BEFORE APPLYING",
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF67E8F9),
        fontSize = 11.sp
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(previewPreset.brush)
          .border(1.dp, Color(0xFF06B6D4), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
      ) {
        if (previewCustomUri.isNotBlank()) {
          AsyncImage(
            model = previewCustomUri,
            contentDescription = "Selected Gallery Wallpaper Preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0x77090D16))
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = state.currentTimeFormatted,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
          )
          Text(
            text = if (previewCustomUri.isNotBlank()) "Custom Gallery Photo" else previewPreset.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE2E8F0)
          )
        }
      }

      Text(
        text = "BUILT-IN DARK WALLPAPERS",
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF94A3B8),
        fontSize = 11.sp
      )

      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.height(150.dp)
      ) {
        items(STUDENT_WALLPAPER_PRESETS, key = { it.id }) { preset ->
          val isSelected = previewPresetId == preset.id && previewCustomUri.isBlank()
          Box(
            modifier = Modifier
              .height(66.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(preset.brush)
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF06B6D4) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp)
              )
              .clickable {
                previewPresetId = preset.id
                previewCustomUri = ""
              }
              .padding(10.dp),
            contentAlignment = Alignment.BottomStart
          ) {
            Text(
              text = preset.title,
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
              fontSize = 12.sp
            )
          }
        }
      }

      OutlinedButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("pick_gallery_photo_button")
      ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF67E8F9))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Pick Photo from Phone Gallery (Zero-Permission Picker)", color = Color.White)
      }

      Spacer(modifier = Modifier.weight(1f))

      Button(
        onClick = { onSaveWallpaper(previewPresetId, previewCustomUri) },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_wallpaper_button")
      ) {
        Text("Save & Apply Wallpaper", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun StudentFirstTimeSetupSheet(
  state: SafeLockSystemUiState,
  installedApps: List<com.example.data.model.InstalledAppItem>,
  managementStatus: com.example.device.DeviceManagementStatus,
  onCompleteSetup: (String, String, List<String>, String) -> Unit,
  onSimulateReinstall: () -> Unit,
  onToggleOffline: (Boolean) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  var nameInput by remember { mutableStateOf(state.studentName) }
  var scheduleDay by remember { mutableStateOf("MON-FRI") }
  var scheduleStart by remember { mutableStateOf("16:00") }
  var scheduleEnd by remember { mutableStateOf("18:00") }
  val selectedProposedApps = remember {
    mutableStateListOf<String>().apply { addAll(state.proposedApps) }
  }
  var copiedFeedback by remember { mutableStateOf(false) }

  Surface(
    color = Color(0xF5090D16),
    modifier = Modifier
      .fillMaxSize()
      .testTag("student_setup_sheet")
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "SafeLock Student Setup & ID",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
          )
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
          }
        }
      }

      // Unique Auto-Generated Student ID + Copy ID Button
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          border = BorderStroke(1.dp, Color(0xFF06B6D4))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "AUTO-GENERATED UNIQUE STUDENT ID",
              style = MaterialTheme.typography.labelLarge,
              color = Color(0xFF67E8F9),
              fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = state.studentId,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                modifier = Modifier.testTag("student_id_display_text")
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Student ID", state.studentId))
                    copiedFeedback = true
                  },
                  modifier = Modifier.testTag("copy_student_id_button")
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_copy_id), modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (copiedFeedback) "Copied!" else "Copy ID")
                }
                IconButton(
                  onClick = onSimulateReinstall,
                  modifier = Modifier.testTag("reinstall_new_id_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Simulate Reinstall / New Phone ID",
                    tint = Color(0xFFFCD34D)
                  )
                }
              }
            }
            Text(
              text = "Reinstalling SafeLock or switching phones generates a new Student ID and revokes the old device connection.",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF94A3B8),
              fontSize = 11.sp
            )
          }
        }
      }

      item {
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          label = { Text("Student Name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_name_input")
        )
      }

      item {
        Text(
          text = "Propose Initial Normal-Use Schedule (Submitted for Parent Approval)",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("MON-FRI", "SAT-SUN", "DAILY").forEach { d ->
            FilterChip(
              selected = scheduleDay == d,
              onClick = { scheduleDay = d },
              label = { Text(d) }
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = scheduleStart,
            onValueChange = { scheduleStart = it },
            label = { Text("Start (HH:MM)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = scheduleEnd,
            onValueChange = { scheduleEnd = it },
            label = { Text("End (HH:MM)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }
      }

      item {
        Text(
          text = "Select Applications to Request for Parent Approval",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White
        )
        Text(
          text = "Selected apps do NOT automatically become approved; they are submitted to your parent for approval.",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFF94A3B8),
          fontSize = 12.sp
        )
      }

      items(installedApps, key = { it.packageName }) { app ->
        val isApprovedAlready = state.approvedApps.contains(app.packageName)
        val isRequested = selectedProposedApps.contains(app.packageName)
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              if (isRequested) selectedProposedApps.remove(app.packageName)
              else selectedProposedApps.add(app.packageName)
            }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(app.appName, fontWeight = FontWeight.SemiBold, color = Color.White)
              Text(app.packageName, fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            Text(
              text = when {
                isApprovedAlready -> "Approved ✓"
                isRequested -> "Requested (Pending)"
                else -> "Tap to Request"
              },
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = when {
                isApprovedAlready -> Color(0xFF6EE7B7)
                isRequested -> Color(0xFFFCD34D)
                else -> Color(0xFF94A3B8)
              }
            )
          }
        }
      }

      // Offline Policy Resilience Toggle & Android Management Mode Info
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Simulate Student Phone Offline", fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                  "Tests offline schedule enforcement & pending remote command queue",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
              }
              Switch(
                checked = state.studentSimulatedOffline,
                onCheckedChange = onToggleOffline,
                modifier = Modifier.testTag("student_offline_switch")
              )
            }
            Text(
              text = managementStatus.managementExplanation,
              fontSize = 11.sp,
              color = Color(0xFFCBD5E1)
            )
          }
        }
      }

      item {
        Button(
          onClick = {
            val slot = "$scheduleDay|$scheduleStart|$scheduleEnd|Student Proposed Schedule"
            onCompleteSetup(
              nameInput.ifBlank { "Leo Sharma" },
              slot,
              selectedProposedApps.toList(),
              state.selectedWallpaperPreset
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("save_student_setup_button")
        ) {
          Text("Save Setup & Submit Proposals for Parent Approval", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun StudentRequestsSheet(
  state: SafeLockSystemUiState,
  installedApps: List<com.example.data.model.InstalledAppItem>,
  onSubmitRequest: (String, String, String) -> Unit,
  onClose: () -> Unit
) {
  var selectedType by remember { mutableStateOf("APP_PERMISSION") }
  var detailsInput by remember { mutableStateOf("") }
  var selectedPackage by remember {
    mutableStateOf(installedApps.firstOrNull { !state.approvedApps.contains(it.packageName) }?.packageName ?: "com.whatsapp")
  }
  var proposedDay by remember { mutableStateOf("MON-FRI") }
  var proposedStart by remember { mutableStateOf("17:00") }
  var proposedEnd by remember { mutableStateOf("19:00") }

  Surface(
    color = Color(0xF5090D16),
    modifier = Modifier
      .fillMaxSize()
      .testTag("student_requests_sheet")
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Submit Request to Parent",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
          )
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
          }
        }
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedType == "APP_PERMISSION",
            onClick = { selectedType = "APP_PERMISSION" },
            label = { Text("App Permission") }
          )
          FilterChip(
            selected = selectedType == "SCHEDULE_CHANGE",
            onClick = { selectedType = "SCHEDULE_CHANGE" },
            label = { Text("Schedule Change") }
          )
          FilterChip(
            selected = selectedType == "EMERGENCY_UNLOCK",
            onClick = { selectedType = "EMERGENCY_UNLOCK" },
            label = { Text("Emergency Unlock") }
          )
        }
      }

      if (selectedType == "APP_PERMISSION") {
        item {
          Text("Choose Application to Request:", fontSize = 12.sp, color = Color(0xFF94A3B8))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            installedApps.take(4).forEach { app ->
              FilterChip(
                selected = selectedPackage == app.packageName,
                onClick = { selectedPackage = app.packageName },
                label = { Text(app.appName) }
              )
            }
          }
        }
      } else if (selectedType == "SCHEDULE_CHANGE") {
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = proposedDay,
              onValueChange = { proposedDay = it },
              label = { Text("Days") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = proposedStart,
              onValueChange = { proposedStart = it },
              label = { Text("Start") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = proposedEnd,
              onValueChange = { proposedEnd = it },
              label = { Text("End") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      item {
        OutlinedTextField(
          value = detailsInput,
          onValueChange = { detailsInput = it },
          label = { Text("Reason / Request Details for Parent") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("student_request_details_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = {
            val payload = when (selectedType) {
              "APP_PERMISSION" -> selectedPackage
              "SCHEDULE_CHANGE" -> "$proposedDay|$proposedStart|$proposedEnd|Requested Window"
              else -> "15"
            }
            val summary = detailsInput.ifBlank {
              when (selectedType) {
                "APP_PERMISSION" -> "Requesting permission to use $selectedPackage"
                "SCHEDULE_CHANGE" -> "Proposing schedule change: $proposedDay $proposedStart–$proposedEnd"
                else -> "Requesting emergency unlock"
              }
            }
            onSubmitRequest(selectedType, summary, payload)
            detailsInput = ""
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("submit_student_request_button")
        ) {
          Text("Submit Request (Status: Pending Until Parent Approves)", fontWeight = FontWeight.Bold)
        }
      }

      item {
        Text(
          text = "CURRENT AUTHORIZED SCHEDULE",
          style = MaterialTheme.typography.labelLarge,
          color = Color(0xFF67E8F9),
          fontSize = 11.sp
        )
        state.authorizedScheduleSlots.forEach { raw ->
          val slot = com.example.data.model.SecurityCryptoUtil.parseScheduleSlot(raw)
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("${slot.daysLabel}: ${slot.startTime} – ${slot.endTime}", color = Color.White, fontWeight = FontWeight.SemiBold)
              Text(slot.title, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
          }
        }
      }

      item {
        Text(
          text = "SUBMITTED REQUESTS STATUS (${state.studentRequests.size})",
          style = MaterialTheme.typography.labelLarge,
          color = Color(0xFF94A3B8),
          fontSize = 11.sp
        )
      }

      if (state.studentRequests.isEmpty()) {
        item {
          Text("No requests submitted yet.", color = Color(0xFF64748B), fontSize = 13.sp)
        }
      } else {
        items(state.studentRequests, key = { it.id.ifBlank { it.details } }) { req ->
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(req.requestType.replace("_", " "), fontWeight = FontWeight.Bold, color = Color.White)
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
              Text(req.details, color = Color(0xFFCBD5E1), fontSize = 13.sp)
              Text(
                text = "Student ID: ${req.studentId}",
                color = Color(0xFF64748B),
                fontSize = 11.sp
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudentEmergencySheet(
  onDialEmergency: () -> Unit,
  onSubmitEmergencyUnlock: (String) -> Unit,
  onClose: () -> Unit
) {
  var reason by remember { mutableStateOf("") }

  Surface(
    color = Color(0xF5090D16),
    modifier = Modifier
      .fillMaxSize()
      .testTag("student_emergency_sheet")
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
        Text(
          text = "Emergency Calling & Unlock Request",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }

      Text(
        text = "Emergency calling remains accessible at all times regardless of SafeLock restriction state.",
        color = Color(0xFFFDA4AF),
        style = MaterialTheme.typography.bodyMedium
      )

      Button(
        onClick = onDialEmergency,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("emergency_dial_112_button")
      ) {
        Icon(Icons.Default.Call, contentDescription = stringResource(R.string.cd_emergency_call))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open Emergency Phone Dialer (112 / 911)", fontWeight = FontWeight.Bold, color = Color.White)
      }

      OutlinedTextField(
        value = reason,
        onValueChange = { reason = it },
        label = { Text("Emergency Unlock Reason for Parent") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("emergency_unlock_reason_input")
      )

      Button(
        onClick = { onSubmitEmergencyUnlock(reason.ifBlank { "Urgent family/school call needed" }) },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("submit_emergency_unlock_button")
      ) {
        Text("Request Emergency Unlock from Parent", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun StudentNotificationsSheet(
  notifications: List<com.example.notifications.RealtimeNotificationItem>,
  onClose: () -> Unit
) {
  Surface(
    color = Color(0xF5090D16),
    modifier = Modifier
      .fillMaxSize()
      .testTag("student_notifications_sheet")
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
          text = "SafeLock Real-Time Alerts",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }
      Text(
        text = "Instant alerts for parent request decisions (Approved/Rejected) and remote Lock/Unlock commands.",
        color = Color(0xFF94A3B8),
        fontSize = 12.sp
      )
      if (notifications.isEmpty()) {
        Text("No student alerts yet.", color = Color(0xFF64748B), fontSize = 13.sp)
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
                  Text(item.title, fontWeight = FontWeight.Bold, color = Color(0xFF67E8F9), fontSize = 13.sp)
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
