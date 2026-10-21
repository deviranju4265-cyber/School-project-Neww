package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class RealtimeNotificationItem(
  val id: String,
  val targetRole: String, // "PARENT" or "STUDENT"
  val title: String,
  val body: String,
  val category: String, // "REQUEST_NEW", "REQUEST_DECISION", "REMOTE_COMMAND"
  val timestampLabel: String = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date()),
  val isRead: Boolean = false
)

/**
 * Native Firebase Cloud Messaging (FCM) Service for receiving real-time push notifications
 * when the app is in the foreground or background.
 *
 * Handles:
 * - Parent alerts (`targetRole = "PARENT"`): New student requests (app access, schedule change, emergency unlock)
 *   and pairing responses.
 * - Student alerts (`targetRole = "STUDENT"`): Request approvals/rejections and parent-initiated
 *   device Lock Now / Direct Unlock / Emergency Unlock commands.
 */
class SafeLockFirebaseMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.i("SafeLockFCM", "Refreshed FCM Registration Token: ${token.take(12)}...")
    SafeLockNotificationHelper.cachedFcmToken = token
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    val data = remoteMessage.data
    val targetRole = data["targetRole"] ?: "PARENT"
    val category = data["category"] ?: "REMOTE_COMMAND"
    val title = remoteMessage.notification?.title
      ?: data["title"]
      ?: if (targetRole == "PARENT") "Lock Monitor Alert" else "SafeLock Update"
    val body = remoteMessage.notification?.body
      ?: data["body"]
      ?: "New parental control update received."

    val channelId = if (targetRole == "PARENT") {
      SafeLockNotificationHelper.CHANNEL_PARENT_ALERTS
    } else {
      SafeLockNotificationHelper.CHANNEL_STUDENT_ALERTS
    }

    SafeLockNotificationHelper.dispatchSystemNotification(
      context = applicationContext,
      channelId = channelId,
      title = title,
      message = body
    )
    SafeLockNotificationHelper.onFcmMessageCallback?.invoke(
      RealtimeNotificationItem(
        id = remoteMessage.messageId ?: "fcm_${System.currentTimeMillis()}",
        targetRole = targetRole,
        title = title,
        body = body,
        category = category
      )
    )
  }
}

object SafeLockNotificationHelper {
  const val CHANNEL_PARENT_ALERTS = "safelock_parent_alerts_channel"
  const val CHANNEL_STUDENT_ALERTS = "safelock_student_alerts_channel"
  private val notificationIdCounter = AtomicInteger(2000)

  @Volatile
  var cachedFcmToken: String = ""

  @Volatile
  var onFcmMessageCallback: ((RealtimeNotificationItem) -> Unit)? = null

  suspend fun fetchFcmRegistrationToken(): String {
    if (cachedFcmToken.isNotBlank()) return cachedFcmToken
    return try {
      val token = FirebaseMessaging.getInstance().token.await()
      cachedFcmToken = token
      token
    } catch (e: Exception) {
      Log.w("SafeLockFCM", "FCM token fetch fallback: ${e.message}")
      ""
    }
  }

  fun subscribeToRoleTopics(studentId: String, parentId: String) {
    try {
      val fcm = FirebaseMessaging.getInstance()
      if (studentId.isNotBlank()) {
        fcm.subscribeToTopic("safelock_student_${studentId.replace("-", "_")}")
      }
      if (parentId.isNotBlank()) {
        fcm.subscribeToTopic("lockmonitor_parent_${parentId.replace("-", "_")}")
      }
    } catch (e: Exception) {
      Log.w("SafeLockFCM", "Topic subscription skipped: ${e.message}")
    }
  }

  fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

      val parentChannel = NotificationChannel(
        CHANNEL_PARENT_ALERTS,
        "Lock Monitor — Parent Alerts (FCM)",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Real-time FCM & Cloud alerts when a student submits an app permission, schedule change, or emergency unlock request."
      }

      val studentChannel = NotificationChannel(
        CHANNEL_STUDENT_ALERTS,
        "SafeLock — Student Alerts (FCM)",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Real-time FCM & Cloud alerts when a parent approves/rejects a request or issues a Lock/Unlock command."
      }

      manager.createNotificationChannel(parentChannel)
      manager.createNotificationChannel(studentChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun dispatchSystemNotification(
    context: Context,
    channelId: String,
    title: String,
    message: String
  ) {
    ensureNotificationChannels(context)
    if (!hasNotificationPermission(context)) return

    try {
      val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
      val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, pendingFlags)

      val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

      NotificationManagerCompat.from(context)
        .notify(notificationIdCounter.incrementAndGet(), builder.build())
    } catch (_: SecurityException) {
      // Handled gracefully if POST_NOTIFICATIONS is revoked
    }
  }
}
