package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SecurityCryptoUtil
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SafeLockRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun parentAccount_createAndCrossUserIsolation_enforced() = runBlocking {
    val aliceUid = signInTestUser("alice_parent@test.com")
    val repo = SafeLockCloudRepository(firestore, auth)

    val salt = SecurityCryptoUtil.generateSalt()
    val pinHash = SecurityCryptoUtil.hashPinWithSalt("1234", salt)
    val createResult = withTimeout(5000L) {
      repo.createOrUpdateParentAccount(
        parentId = "LM-P4D81",
        name = "Alice Sharma",
        recoveryEmail = "alice@test.com",
        pinHash = pinHash,
        pinSalt = salt,
        connectedStudentIds = emptyList()
      )
    }
    assertTrue("Parent account creation should succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
    assertEquals("LM-P4D81", createResult.getOrNull()?.parentId)

    // Cross-user read attempt by Mallory must fail with PERMISSION_DENIED
    signInTestUser("mallory_parent@test.com")
    val malloryRead = withTimeout(5000L) {
      repo.getParentAccount(aliceUid)
    }
    assertTrue("Cross-user parent read must fail", malloryRead.isFailure)
    val ex = malloryRead.exceptionOrNull() as? FirebaseFirestoreException
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun studentPairingWorkflow_andRemoteLockCommand_succeedsWithAuthorization() = runBlocking {
    val studentUid = signInTestUser("leo_student@test.com")
    val repo = SafeLockCloudRepository(firestore, auth)
    val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(5).uppercase()
    val studentId = "SL-$uniqueSuffix"

    val regResult = withTimeout(5000L) {
      repo.registerStudentDevice(
        studentId = studentId,
        studentName = "Leo Sharma",
        deviceRegistrationId = "reg_$uniqueSuffix",
        approvedApps = listOf("com.android.dialer", "com.android.contacts"),
        proposedApps = listOf("com.whatsapp"),
        authorizedScheduleSlots = listOf("MON-FRI|16:00|18:00|Study"),
        proposedScheduleSlots = listOf("MON-FRI|16:00|19:00|Extended")
      )
    }
    assertTrue("Student device registration should succeed: ${regResult.exceptionOrNull()}", regResult.isSuccess)

    // Parent Alice requests pairing
    val parentUid = signInTestUser("alice_parent@test.com")
    val pairReq = withTimeout(5000L) {
      repo.requestPairingFromParent(
        studentId = studentId,
        parentId = "LM-P4D81",
        parentName = "Alice Sharma"
      )
    }
    assertTrue("Parent pairing request should succeed: ${pairReq.exceptionOrNull()}", pairReq.isSuccess)

    // Student Leo approves pairing
    signInTestUser("leo_student@test.com")
    val approvePair = withTimeout(5000L) {
      repo.respondToParentPairingAsStudent(
        studentId = studentId,
        approve = true,
        pendingParentUid = parentUid,
        pendingParentId = "LM-P4D81"
      )
    }
    assertTrue("Student pairing approval should succeed: ${approvePair.exceptionOrNull()}", approvePair.isSuccess)

    // Paired Parent Alice issues LOCK_NOW command
    signInTestUser("alice_parent@test.com")
    val cmdResult = withTimeout(5000L) {
      repo.issueRemoteCommandFromParent(
        studentId = studentId,
        studentUid = studentUid,
        commandType = "LOCK_NOW",
        durationMinutes = 0L
      )
    }
    assertTrue("Paired parent LOCK_NOW command should succeed: ${cmdResult.exceptionOrNull()}", cmdResult.isSuccess)
    val commandId = cmdResult.getOrThrow()

    // Student Leo confirms execution of LOCK_NOW command
    signInTestUser("leo_student@test.com")
    val confirmResult = withTimeout(5000L) {
      repo.confirmCommandExecutedByStudent(
        commandId = commandId,
        studentId = studentId,
        newSafeLockStatus = "RESTRICTED",
        newManualOverrideState = "LOCKED_NOW",
        emergencyUnlockUntilMillis = 0L
      )
    }
    assertTrue("Student command execution confirmation should succeed: ${confirmResult.exceptionOrNull()}", confirmResult.isSuccess)
  }

  @Test
  fun unauthenticatedCaller_cannotObserveRequests() = runBlocking {
    auth.signOut()
    val repo = SafeLockCloudRepository(firestore, auth)
    try {
      withTimeout(3000L) {
        repo.observeRequestsForParent("any_uid").first()
      }
      fail("Expected FirebaseFirestoreException for unauthenticated query")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
