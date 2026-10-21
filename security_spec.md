# SafeLock & Lock Monitor — Security Specification (Payload-First Security TDD)

## 1. Data Invariants
1. **Parent Account Isolation (`/parents/{parentUid}`)**:
   - Contains parent PII (`recoveryEmail`) and salted SHA-256 PIN verifier (`pinHash`, `pinSalt`).
   - Only `request.auth.uid == parentUid` can read (`get`, `list`), create, update, or delete `/parents/{parentUid}`.
   - Plain-text PINs are never stored; `pinHash` must be a hex string (16..128 chars).
2. **Student Device Profile & Pairing Authorization (`/students/{studentId}`)**:
   - A Student Device Profile can only be created by the authenticated student (`incoming().studentUid == request.auth.uid`).
   - Merely knowing a `studentId` (`SL-XXXXX`) or `parentId` (`LM-XXXXX`) never grants device control.
   - A parent can only request pairing when `existing().pairingStatus == 'UNPAIRED'`, setting `pendingParentUid == request.auth.uid` and `pairingStatus == 'PENDING_APPROVAL'`.
   - Only the student (`existing().studentUid == request.auth.uid`) can approve the pairing request (setting `pairedParentUid == existing().pendingParentUid` and `pairingStatus == 'PAIRED'`).
   - Only the explicitly paired parent (`existing().pairedParentUid == request.auth.uid && existing().pairingStatus == 'PAIRED'`) can modify `approvedApps`, `authorizedScheduleSlots`, or `manualOverrideState`.
3. **Student Requests (`/student_requests/{requestId}`)**:
   - Can only be created by the authenticated student (`incoming().studentUid == request.auth.uid`) with initial `status == 'PENDING'`.
   - Terminal State Locking: Can only be updated when `existing().status == 'PENDING'` AND only by the authorized parent (`existing().parentUid == request.auth.uid`), transitioning `status` to `'APPROVED'` or `'REJECTED'`. Once approved or rejected, no further updates are permitted (preventing duplicate approvals).
4. **Remote Device Commands (`/device_commands/{commandId}`)**:
   - Can only be created by an authenticated parent (`incoming().parentUid == request.auth.uid`) who is verified via `get()` on `/students/$(incoming().studentId)` to be the paired parent (`pairedParentUid == request.auth.uid`).
   - Terminal State Locking: Can only be updated while `existing().status == 'PENDING'` by the target student device (`existing().studentUid == request.auth.uid`) to confirm execution (`'EXECUTED'` or `'FAILED'`).

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read on `/parents/{parentUid}`**: `auth = null` -> Rejected by `isSignedIn()`.
2. **Cross-Parent PII Read on `/parents/parent_bob` by `parent_alice`**: Rejected by `request.auth.uid == parentUid`.
3. **Identity Spoofing on `/students/SL-12345` Create**: `alice` attempts to create student profile with `studentUid: "bob"` -> Rejected by `incoming().studentUid == request.auth.uid`.
4. **Unauthorized Parent Control without Student Approval**: `parent_mallory` tries to update `approvedApps` on `/students/SL-12345` while `pairingStatus == 'PENDING_APPROVAL'` -> Rejected because `existing().pairedParentUid == request.auth.uid && existing().pairingStatus == 'PAIRED'` is false.
5. **Shadow Update / Ghost Field Injection**: Student updates `/students/SL-12345` with `{ "isAdmin": true }` -> Rejected by `incoming().keys().hasOnly(...)` and `affectedKeys().hasOnly(...)`.
6. **Student Self-Approving Request**: Student tries to update `/student_requests/req_1` to `status: "APPROVED"` -> Rejected because update requires `existing().parentUid == request.auth.uid`.
7. **Duplicate Approval of Terminal Request**: Parent tries to update `/student_requests/req_1` after `status` is already `"APPROVED"` -> Rejected by terminal state gate `existing().status == 'PENDING'`.
8. **Unauthorized Remote Lock Command**: Unpaired user `mallory` creates `/device_commands/cmd_1` targeting `SL-12345` -> Rejected by relational parent gate checking `/students/SL-12345.data.pairedParentUid == request.auth.uid`.
9. **Parent Faking Device Execution Confirmation**: Parent tries to update `/device_commands/cmd_1` to `status: "EXECUTED"` -> Rejected because only `existing().studentUid == request.auth.uid` can mark a command executed.
10. **Resource / Value Poisoning**: Sending a 10,000-character `studentName` or 500-item `approvedApps` list -> Rejected by strict `.size()` bounds in `isValidStudentProfile()`.
11. **Immortal Field Mutation**: Attempting to mutate `createdAt` or `studentUid` during an update -> Rejected by `incoming().createdAt == existing().createdAt` and `incoming().studentUid == existing().studentUid`.
12. **Unfiltered Query Scraping (`allow list`)**: Calling `db.collection("students").get()` without filtering by `studentUid` or `pairedParentUid` -> Rejected by Query Enforcer rule on `allow list`.
