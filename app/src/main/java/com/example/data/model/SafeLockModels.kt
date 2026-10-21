package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale

@IgnoreExtraProperties
data class ParentAccount(
  @DocumentId val id: String = "",
  val parentUid: String = "",
  val parentId: String = "",
  val name: String = "",
  val recoveryEmail: String = "",
  val pinHash: String = "",
  val pinSalt: String = "",
  val connectedStudentIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentProfile(
  @DocumentId val id: String = "",
  val studentId: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val deviceRegistrationId: String = "",
  val pairedParentUid: String = "",
  val pairedParentId: String = "",
  val pendingParentUid: String = "",
  val pendingParentId: String = "",
  val pendingParentName: String = "",
  val pairingStatus: String = "UNPAIRED",
  val connectionStatus: String = "ONLINE",
  val safeLockStatus: String = "RESTRICTED",
  val approvedApps: List<String> = listOf(
    "com.android.dialer",
    "com.android.contacts",
    "com.android.camera2"
  ),
  val proposedApps: List<String> = emptyList(),
  val authorizedScheduleSlots: List<String> = listOf(
    "MON-FRI|16:00|18:00|After School Normal Use",
    "SAT-SUN|14:00|18:00|Weekend Normal Use"
  ),
  val proposedScheduleSlots: List<String> = emptyList(),
  val emergencyUnlockUntilMillis: Long = 0L,
  val manualOverrideState: String = "NONE",
  val lastCommandId: String = "",
  val lastCommandStatus: String = "NONE",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentRequest(
  @DocumentId val id: String = "",
  val studentId: String = "",
  val studentUid: String = "",
  val studentName: String = "",
  val parentUid: String = "",
  val requestType: String = "APP_PERMISSION",
  val details: String = "",
  val payloadValue: String = "",
  val status: String = "PENDING",
  val approvedDurationMinutes: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeviceCommand(
  @DocumentId val id: String = "",
  val commandId: String = "",
  val studentId: String = "",
  val studentUid: String = "",
  val parentUid: String = "",
  val commandType: String = "LOCK_NOW",
  val durationMinutes: Long = 0L,
  val status: String = "PENDING",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

data class InstalledAppItem(
  val packageName: String,
  val appName: String,
  val categoryLabel: String,
  val accentHex: Long,
  val isSystemEssential: Boolean = false
)

data class ScheduleSlotUi(
  val rawSlot: String,
  val daysLabel: String,
  val startTime: String,
  val endTime: String,
  val title: String
)

object SecurityCryptoUtil {
  private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  private val secureRandom = SecureRandom()

  fun generateStudentId(): String {
    val sb = StringBuilder("SL-")
    repeat(5) {
      sb.append(ID_ALPHABET[secureRandom.nextInt(ID_ALPHABET.length)])
    }
    return sb.toString()
  }

  fun generateParentId(): String {
    val sb = StringBuilder("LM-")
    repeat(5) {
      sb.append(ID_ALPHABET[secureRandom.nextInt(ID_ALPHABET.length)])
    }
    return sb.toString()
  }

  fun generateSalt(): String {
    val bytes = ByteArray(12)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun hashPinWithSalt(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = "$salt:${pin.trim()}".toByteArray(Charsets.UTF_8)
    val hashed = digest.digest(input)
    return hashed.joinToString("") { "%02x".format(it) }
  }

  fun parseScheduleSlot(raw: String): ScheduleSlotUi {
    val parts = raw.split("|")
    return ScheduleSlotUi(
      rawSlot = raw,
      daysLabel = parts.getOrNull(0) ?: "DAILY",
      startTime = parts.getOrNull(1) ?: "16:00",
      endTime = parts.getOrNull(2) ?: "18:00",
      title = parts.getOrNull(3) ?: "Normal-Use Window"
    )
  }

  fun isCurrentlyInAuthorizedSchedule(
    slots: List<String>,
    calendar: Calendar = Calendar.getInstance()
  ): Boolean {
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
    val dayCode = when (dayOfWeek) {
      Calendar.MONDAY -> "MON"
      Calendar.TUESDAY -> "TUE"
      Calendar.WEDNESDAY -> "WED"
      Calendar.THURSDAY -> "THU"
      Calendar.FRIDAY -> "FRI"
      Calendar.SATURDAY -> "SAT"
      Calendar.SUNDAY -> "SUN"
      else -> "MON"
    }
    val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    for (raw in slots) {
      val parsed = parseScheduleSlot(raw)
      val dayMatches = when (parsed.daysLabel.uppercase(Locale.US)) {
        "DAILY" -> true
        "MON-FRI" -> !isWeekend
        "SAT-SUN" -> isWeekend
        else -> parsed.daysLabel.uppercase(Locale.US).contains(dayCode)
      }
      if (!dayMatches) continue
      val startMins = parseTimeMinutes(parsed.startTime) ?: continue
      val endMins = parseTimeMinutes(parsed.endTime) ?: continue
      if (currentMinutes in startMins..endMins) {
        return true
      }
    }
    return false
  }

  private fun parseTimeMinutes(hhmm: String): Int? {
    val split = hhmm.split(":")
    if (split.size != 2) return null
    val h = split[0].toIntOrNull() ?: return null
    val m = split[1].toIntOrNull() ?: return null
    return h * 60 + m
  }
}
