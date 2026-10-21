const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_alice_123";
const OTHER_PARENT_UID = "parent_mallory_999";
const STUDENT_UID = "student_leo_456";
const STUDENT_ID = "SL-A7K92";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const nowTimestamp = () => new Date(Date.now() - 1000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildStudentPayload(overrides = {}) {
  const ts = nowTimestamp();
  return {
    studentId: STUDENT_ID,
    studentUid: STUDENT_UID,
    studentName: "Leo Sharma",
    deviceRegistrationId: "reg_device_001",
    pairedParentUid: "",
    pairedParentId: "",
    pendingParentUid: "",
    pendingParentId: "",
    pendingParentName: "",
    pairingStatus: "UNPAIRED",
    connectionStatus: "ONLINE",
    safeLockStatus: "RESTRICTED",
    approvedApps: ["com.android.dialer", "com.android.contacts"],
    proposedApps: ["com.google.android.youtube", "com.whatsapp"],
    authorizedScheduleSlots: ["MON|16:00|18:00|Study Break"],
    proposedScheduleSlots: ["MON|16:00|19:00|Extended Break"],
    emergencyUnlockUntilMillis: 0,
    manualOverrideState: "NONE",
    lastCommandId: "",
    lastCommandStatus: "NONE",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("1. Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("parents").doc(PARENT_UID).get());
  await assertFails(unauthDb.collection("students").doc(STUDENT_ID).get());
  await assertFails(unauthDb.collection("student_requests").get());
  await assertFails(unauthDb.collection("device_commands").get());
});

test("2. Parent account PII isolation: owner can create/read, another parent cannot read", async () => {
  const aliceDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const ts = nowTimestamp();
  await assertSucceeds(
    aliceDb.collection("parents").doc(PARENT_UID).set({
      parentUid: PARENT_UID,
      parentId: "LM-P4D81",
      name: "Alice Sharma",
      recoveryEmail: "alice@example.com",
      pinHash: "a1b2c3d4e5f60718293a4b5c6d7e8f90",
      pinSalt: "salt_12345678",
      connectedStudentIds: [STUDENT_ID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const malloryDb = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  await assertFails(malloryDb.collection("parents").doc(PARENT_UID).get());
});

test("3. Student creates profile, Parent sends pairing request, Student approves, Parent updates approvedApps", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertSucceeds(
    studentDb.collection("students").doc(STUDENT_ID).set(buildStudentPayload())
  );

  // Unpaired parent Mallory tries to directly modify approvedApps without pairing -> FAILS
  const malloryDb = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  await assertFails(
    malloryDb.collection("students").doc(STUDENT_ID).update({
      approvedApps: ["com.whatsapp"],
      updatedAt: nowTimestamp(),
    })
  );

  // Parent Alice sends pairing request -> SUCCEEDS
  const aliceDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("students").doc(STUDENT_ID).update({
      pendingParentUid: PARENT_UID,
      pendingParentId: "LM-P4D81",
      pendingParentName: "Alice Sharma",
      pairingStatus: "PENDING_APPROVAL",
      updatedAt: nowTimestamp(),
    })
  );

  // Student Leo approves pairing -> SUCCEEDS
  await assertSucceeds(
    studentDb.collection("students").doc(STUDENT_ID).update({
      pairedParentUid: PARENT_UID,
      pairedParentId: "LM-P4D81",
      pairingStatus: "PAIRED",
      updatedAt: nowTimestamp(),
    })
  );

  // Now paired Parent Alice updates approvedApps -> SUCCEEDS
  await assertSucceeds(
    aliceDb.collection("students").doc(STUDENT_ID).update({
      approvedApps: ["com.android.dialer", "com.whatsapp"],
      updatedAt: nowTimestamp(),
    })
  );
});

test("4. Student Request & Terminal State Locking: only paired parent can approve once", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const ts = nowTimestamp();
  await assertSucceeds(
    studentDb.collection("student_requests").doc("req_001").set({
      studentId: STUDENT_ID,
      studentUid: STUDENT_UID,
      studentName: "Leo Sharma",
      parentUid: PARENT_UID,
      requestType: "EMERGENCY_UNLOCK",
      details: "Need phone for school pickup call",
      payloadValue: "15",
      status: "PENDING",
      approvedDurationMinutes: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Student cannot approve own request
  await assertFails(
    studentDb.collection("student_requests").doc("req_001").update({
      status: "APPROVED",
      approvedDurationMinutes: 15,
      updatedAt: nowTimestamp(),
    })
  );

  // Paired parent approves request with 15 min duration
  const aliceDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("student_requests").doc("req_001").update({
      status: "APPROVED",
      approvedDurationMinutes: 15,
      updatedAt: nowTimestamp(),
    })
  );

  // Duplicate approval on already APPROVED request fails (Terminal State Locking)
  await assertFails(
    aliceDb.collection("student_requests").doc("req_001").update({
      status: "APPROVED",
      approvedDurationMinutes: 30,
      updatedAt: nowTimestamp(),
    })
  );
});

test("5. Device Command authorization & student execution confirmation", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertSucceeds(
    studentDb.collection("students").doc(STUDENT_ID).set(
      buildStudentPayload({
        pairedParentUid: PARENT_UID,
        pairedParentId: "LM-P4D81",
        pairingStatus: "PAIRED",
      })
    )
  );

  const ts = nowTimestamp();
  // Unpaired parent cannot issue LOCK_NOW command
  const malloryDb = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  await assertFails(
    malloryDb.collection("device_commands").doc("cmd_bad").set({
      commandId: "cmd_bad",
      studentId: STUDENT_ID,
      studentUid: STUDENT_UID,
      parentUid: OTHER_PARENT_UID,
      commandType: "LOCK_NOW",
      durationMinutes: 0,
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Paired parent issues LOCK_NOW command -> SUCCEEDS
  const aliceDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("device_commands").doc("cmd_001").set({
      commandId: "cmd_001",
      studentId: STUDENT_ID,
      studentUid: STUDENT_UID,
      parentUid: PARENT_UID,
      commandType: "LOCK_NOW",
      durationMinutes: 0,
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent cannot mark command as EXECUTED (only student device can confirm execution)
  await assertFails(
    aliceDb.collection("device_commands").doc("cmd_001").update({
      status: "EXECUTED",
      updatedAt: nowTimestamp(),
    })
  );

  // Student device confirms command execution -> SUCCEEDS
  await assertSucceeds(
    studentDb.collection("device_commands").doc("cmd_001").update({
      status: "EXECUTED",
      updatedAt: nowTimestamp(),
    })
  );
});
