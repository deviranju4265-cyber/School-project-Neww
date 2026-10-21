package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.GoogleSignInButton
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogle
import com.example.lockmonitor.LockMonitorParentAppScreen
import com.example.notifications.SafeLockNotificationHelper
import com.example.safelock.SafeLockStudentAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SafeLockSystemViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow()
    .collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthGateScreen()
  } else {
    AuthenticatedDualAppHub(currentUserId = user.uid, userEmail = user.email ?: "")
  }
}

@Composable
private fun AuthGateScreen() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var authError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {},
      onUnauthenticated = {},
      scope = scope
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(Color(0xFF0E3A53), Color(0xFF090D16), Color(0xFF070B14))
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(22.dp)
      .testTag("auth_gate_screen"),
    contentAlignment = Alignment.Center
  ) {
    Card(
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xE6111827)),
      border = BorderStroke(1.dp, Color(0x3306B6D4)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = Color(0xFF0B1120),
          border = BorderStroke(1.dp, Color(0xFF06B6D4)),
          modifier = Modifier.size(72.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.cd_safelock_logo),
            modifier = Modifier.fillMaxSize()
          )
        }

        Text(
          text = "SafeLock & Lock Monitor",
          style = MaterialTheme.typography.headlineMedium,
          color = Color.White,
          textAlign = TextAlign.Center
        )

        Text(
          text = "Student Phone Management & Parental Control System.\nAuthenticate with Google Sign-In to synchronize SafeLock (Student) and Lock Monitor (Parent) via Cloud Firestore.",
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFCBD5E1),
          textAlign = TextAlign.Center
        )

        if (authError != null) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x33F43F5E),
            border = BorderStroke(1.dp, Color(0xFFF43F5E)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = authError ?: "",
              color = Color(0xFFFDA4AF),
              fontSize = 12.sp,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        GoogleSignInButton(
          onAuthSuccess = { authError = null },
          onAuthError = { msg -> authError = msg }
        )
      }
    }
  }
}

private enum class ActiveAppRole {
  SAFELOCK_STUDENT,
  LOCK_MONITOR_PARENT,
  PROTOTYPES_AND_ARCHITECTURE
}

@Composable
private fun AuthenticatedDualAppHub(
  currentUserId: String,
  userEmail: String
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val viewModel: SafeLockSystemViewModel = viewModel(
    key = currentUserId,
    factory = SafeLockSystemViewModel.factory(currentUserId)
  )
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var activeAppRole by remember { mutableStateOf(ActiveAppRole.SAFELOCK_STUDENT) }

  BackHandler(enabled = activeAppRole != ActiveAppRole.SAFELOCK_STUDENT) {
    activeAppRole = ActiveAppRole.SAFELOCK_STUDENT
  }

  // Request POST_NOTIFICATIONS runtime permission on Android 13+ for real-time parent/student alerts
  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ -> }

  LaunchedEffect(Unit) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !SafeLockNotificationHelper.hasNotificationPermission(context)
    ) {
      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      NavigationBar(
        containerColor = Color(0xFF0B1120),
        tonalElevation = 8.dp
      ) {
        NavigationBarItem(
          selected = activeAppRole == ActiveAppRole.SAFELOCK_STUDENT,
          onClick = { activeAppRole = ActiveAppRole.SAFELOCK_STUDENT },
          icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "SafeLock Student App") },
          label = { Text("App A: SafeLock") },
          modifier = Modifier.testTag("nav_safelock_student")
        )
        NavigationBarItem(
          selected = activeAppRole == ActiveAppRole.LOCK_MONITOR_PARENT,
          onClick = { activeAppRole = ActiveAppRole.LOCK_MONITOR_PARENT },
          icon = { Icon(Icons.Default.Security, contentDescription = "Lock Monitor Parent App") },
          label = { Text("App B: Lock Monitor") },
          modifier = Modifier.testTag("nav_lock_monitor_parent")
        )
        NavigationBarItem(
          selected = activeAppRole == ActiveAppRole.PROTOTYPES_AND_ARCHITECTURE,
          onClick = { activeAppRole = ActiveAppRole.PROTOTYPES_AND_ARCHITECTURE },
          icon = { Icon(Icons.Default.Code, contentDescription = "HTML Prototypes & Spec") },
          label = { Text("HTML & Cloud Spec") },
          modifier = Modifier.testTag("nav_prototypes_spec")
        )
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Live Real-Time Cloud / FCM Notification Banner
      AnimatedVisibility(visible = state.statusBannerMessage != null) {
        Surface(
          color = Color(0xFF1E1B4B),
          border = BorderStroke(1.dp, Color(0xFF06B6D4)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFF67E8F9),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = state.statusBannerMessage ?: "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
            IconButton(
              onClick = { viewModel.dismissBannerMessage() },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss Banner",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      Box(modifier = Modifier.weight(1f)) {
        when (activeAppRole) {
          ActiveAppRole.SAFELOCK_STUDENT -> {
            SafeLockStudentAppScreen(
              state = state,
              viewModel = viewModel,
              onSwitchToParentMonitor = { activeAppRole = ActiveAppRole.LOCK_MONITOR_PARENT }
            )
          }
          ActiveAppRole.LOCK_MONITOR_PARENT -> {
            LockMonitorParentAppScreen(
              state = state,
              viewModel = viewModel
            )
          }
          ActiveAppRole.PROTOTYPES_AND_ARCHITECTURE -> {
            PrototypesAndArchitectureScreen(
              userEmail = userEmail,
              onSignOut = {
                signOutGoogle(context, onSignOutComplete = {}, scope = scope)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PrototypesAndArchitectureScreen(
  userEmail: String,
  onSignOut: () -> Unit
) {
  var selectedHtmlPreview by remember { mutableStateOf("NONE") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF090D16))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "HTML Prototypes & Cloud Architecture",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White
        )
        Text(
          text = "Signed in: ${userEmail.ifBlank { "Authenticated Google Account" }}",
          color = Color(0xFF94A3B8),
          fontSize = 12.sp
        )
      }
      IconButton(
        onClick = onSignOut,
        modifier = Modifier.testTag("sign_out_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = "Sign Out",
          tint = Color(0xFFFDA4AF)
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = selectedHtmlPreview == "NONE",
        onClick = { selectedHtmlPreview = "NONE" },
        label = { Text("Architecture Spec") }
      )
      FilterChip(
        selected = selectedHtmlPreview == "SAFELOCK_HTML",
        onClick = { selectedHtmlPreview = "SAFELOCK_HTML" },
        label = { Text("SafeLock.html") }
      )
      FilterChip(
        selected = selectedHtmlPreview == "LOCKMONITOR_HTML",
        onClick = { selectedHtmlPreview = "LOCKMONITOR_HTML" },
        label = { Text("LockMonitor.html") }
      )
    }

    if (selectedHtmlPreview == "SAFELOCK_HTML" || selectedHtmlPreview == "LOCKMONITOR_HTML") {
      val assetUrl = if (selectedHtmlPreview == "SAFELOCK_HTML") {
        "file:///android_asset/SafeLock.html"
      } else {
        "file:///android_asset/LockMonitor.html"
      }
      Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF06B6D4)),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        AndroidView(
          factory = { ctx ->
            WebView(ctx).apply {
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              webViewClient = WebViewClient()
              loadUrl(assetUrl)
            }
          },
          update = { webView ->
            if (webView.url != assetUrl) {
              webView.loadUrl(assetUrl)
            }
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          SpecCard(
            title = "1. Two Separate Applications (HTML & Native Kotlin)",
            body = "• SafeLock_Project/SafeLock.html & SafeLockApp.kt (Student App)\n" +
              "• SafeLock_Project/LockMonitor.html & LockMonitorApp.kt (Parent App)\n" +
              "• Packaged with img.png logo and README.md inside SafeLock_Project/ and SafeLock_Project.zip."
          )
        }
        item {
          SpecCard(
            title = "2. Google Cloud Backend & Real-Time Notifications",
            body = "• Firebase Authentication (Google Sign-In via Jetpack Credential Manager).\n" +
              "• Cloud Firestore Enterprise database with hardened Zero-Trust Security Rules (firestore.rules).\n" +
              "• Real-Time Cloud Notification Engine + Android System Notification Channels (POST_NOTIFICATIONS) alerting parents on new student requests and alerting students on request approvals/rejections and remote Lock/Unlock commands."
          )
        }
        item {
          SpecCard(
            title = "3. Native Android Restrictions vs. Ordinary Phones",
            body = "• Ordinary Android Phones: SafeLock registers as a CATEGORY_HOME launcher, discovers real installed packages via PackageManager, launches approved real apps, keeps Emergency Dialer (112) accessible, and supports screen pinning (startLockTask).\n" +
              "• Fully Managed / Device Owner Mode: Enforces OS-level package suspension, LockTask allowlisting, and Option B Uninstall Protection (setUninstallBlocked), removable only by authorized parent/admin."
          )
        }
      }
    }
  }
}

@Composable
private fun SpecCard(title: String, body: String) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF67E8F9), fontSize = 14.sp)
      Text(body, color = Color(0xFFE2E8F0), fontSize = 12.sp, lineHeight = 18.sp)
    }
  }
}
