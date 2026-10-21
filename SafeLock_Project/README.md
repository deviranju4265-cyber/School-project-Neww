# SafeLock & Lock Monitor — Student Phone Management and Parental Control System

## 1. Package Overview & File Structure
```text
SafeLock_Project/
├── SafeLock.html       # Student-side interactive HTML5 prototype
├── LockMonitor.html    # Parent-side interactive HTML5 prototype (Default Demo PIN: 1234)
├── img.png             # Official SafeLock shield & lock logo
├── README.md           # Architecture, limitations, and testing instructions
└── assets/             # Supporting prototype assets
```

In addition to the standalone HTML prototypes in `SafeLock_Project/`, this project contains the **production native Android applications** written in Kotlin with Jetpack Compose, Room + DataStore offline persistence, Android DevicePolicyManager / LockTask APIs, and Cloud Firestore + Google Sign-In backend integration:
- **Application A (`SafeLockApp.kt` / `SafeLockActivity`)**: Student-side native Android launcher & restricted environment.
- **Application B (`LockMonitorApp.kt` / `LockMonitorActivity`)**: Parent-side native Android control dashboard with salted SHA-256 PIN verification, rate limiting, multi-student management, Lock Now, Direct Unlock, and Requests management.

---

## 2. Honest Technical Distinction: HTML Prototype vs. Native Android vs. Google Cloud

| Capability | HTML Prototype (`SafeLock.html` / `LockMonitor.html`) | Native Android (`SafeLockApp.kt` / `LockMonitorApp.kt`) | Google Cloud (`Cloud Firestore` + `Firebase Auth`) |
| :--- | :--- | :--- | :--- |
| **Digital Clock & Responsive UI** | Works fully in browser (`setInterval` 1s clock, CSS Grid). | Works natively in Jetpack Compose. | N/A |
| **Student Wallpaper Picker** | Works with built-in themes & `<input type="file">` preview in `localStorage`. | Uses Android zero-permission Photo Picker (`PickVisualMedia`) + DataStore/Room. | Strictly local to student phone (never exposed to parent). |
| **Launching Approved Apps** | Uses `intent://` & `tel:` links (simulated in desktop browser). | Queries real installed apps via `PackageManager` and launches real intents (`getLaunchIntentForPackage`). | Syncs parent-authorized package allowlist (`approvedApps`). |
| **Blocking Disallowed Apps & Home Button** | **Cannot** enforce OS-level app blocking from an HTML page. | Enforces OS restrictions via Launcher role (`CATEGORY_HOME`) and `Activity.startLockTask()` / `DevicePolicyManager.setPackagesSuspended()` when provisioned as Device Owner. | Stores and syncs the authoritative restriction policy. |
| **Uninstall Protection (Option B)** | Impossible in HTML. | Enforced via `DevicePolicyManager.setUninstallBlocked()` under a managed Device Owner configuration; removable by authorized parent/admin. | Tracks `deviceRegistrationId` and revokes old installations if reinstalled. |
| **Real-Time Two-Phone Sync & Offline Queue** | Uses separate per-browser `localStorage` (includes demo simulation toggles). | Caches authorized policy in local Room/DataStore and executes/confirms remote commands when online. | Real-time Firestore listeners + hardened Zero-Trust Security Rules (`firestore.rules`). |

---

## 3. How to Test the HTML Prototypes on an Android Phone or Browser
1. Open `SafeLock_Project/SafeLock.html` in Chrome on your Android phone (or inspect it directly inside the Android app's built-in **HTML Prototype Viewer** tab).
   - Verify the live Digital Clock (`08:30 PM` format) updates automatically without a next-schedule countdown on the main screen.
   - Tap **Wallpaper** to preview and apply built-in dark wallpapers or pick a photo from your phone gallery.
   - Tap **Setup & ID** to generate/copy your unique Student ID (`SL-XXXXX`), propose a schedule, and request apps.
   - Tap **Requests** or **Emergency** to submit an Application Permission, Schedule Change, or Emergency Unlock request, or dial `112`.
2. Open `SafeLock_Project/LockMonitor.html`:
   - Enter the default demo PIN **`1234`** (or test 5 wrong PIN attempts to verify the 30-second rate-limit lockout, or use **Forgot PIN** with `alice@example.com`).
   - Switch between connected students (`Leo Sharma SL-A7K92` and `Maya Sharma SL-B8M41`).
   - Toggle **"Simulate Student Phone Offline"** at the top and press **Lock Now** or **Unlock Immediately** to verify that offline commands are recorded as **Pending / Queued** rather than falsely claiming immediate execution.
   - Tap the separate **Requests** button to approve or reject requests, including selecting an Emergency Unlock duration (`5m`, `15m`, `30m`, `Custom`).

---

## 4. Native Android Device Management & Enrollment Notes
- **Ordinary Android Phone Mode (Default Home Launcher + Lock Task Pinning)**:
  - Works on Android 7.0+ (API 24–36) without factory reset.
  - SafeLock registers as a `CATEGORY_HOME` launcher and uses screen pinning (`startLockTask()`), filtering the app drawer exclusively to parent-approved packages while keeping emergency calling (`Intent.ACTION_DIAL`) accessible at all times.
- **Fully Managed Device / Device Owner Mode (Strict OS Enforcement & Option B Uninstall Protection)**:
  - Provisioned via QR code or `dpm set-device-owner com.aistudio.safelock.vxmpzq/com.example.receiver.SafeLockDeviceAdminReceiver` on a freshly setup device.
  - Enables `setLockTaskPackages()`, `setPackagesSuspended()` to block disallowed apps at the OS level, and `setUninstallBlocked()` so the student cannot uninstall SafeLock until the parent disables protection or revokes management.
