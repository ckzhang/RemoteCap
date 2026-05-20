# Remote Cap - AI Agent Handoff Document

## 1. Project Overview & Location
- **Project Name**: Remote Cap
- **Location**: `C:\Users\ckzhang\clawd\RemoteCap`
- **Goal**: A cross-device (Android Phone + Wear OS) camera shutter remote and live preview tool.
- **Core Concept**: Unlike traditional camera remotes that only work with the default camera, Remote Cap uses a "Floating Target + Accessibility Service" architecture to press *any* button on the screen. This allows it to work with third-party apps like Instagram, TikTok, or Snapchat.

## 2. Architecture & Components
The project consists of two modules: `app` (Phone) and `wear` (Watch). Communication is handled via **Wear Data Layer API**.

### Phone App (`/app`)
- **MainActivity.kt**: UI dashboard. Handles permission requests (Overlay, Accessibility, MediaProjection), starts services, and checks watch connection status.
- **FloatingTargetService.kt**: Renders a floating crosshair (🎯) using `SYSTEM_ALERT_WINDOW`. The user drags this over the camera shutter button of any app. It records the (X, Y) coordinates. Supports `ACTION_POSITION_TARGET` for ADB/automation (see §7).
- **ShutterAccessibilityService.kt**: An `AccessibilityService`. When the watch sends `/shutter`, it **immediately** dispatches a gesture (2px slide / 150ms) at saved (X, Y). **Countdown runs on the watch only** — the phone must not delay again (fixed: was double-countdown bug).
- **ScreenCaptureService.kt**: Uses `MediaProjection` to capture the phone's screen, scales it down (200x200), compresses it (JPEG 15%), and streams it to the watch (max 4 FPS).
- **TargetManager.kt**: Singleton holding shared state (coordinates, countdown settings) in `RemoteCapPrefs`.
- **WatchMessageListenerService.kt**: Listens for `/shutter`, `/wake_shutter_only`, `/wake_preview`, `/get_countdown` from the watch.

### Wear OS App (`/wear`)
- **MainActivity.kt**: Watch UI — shutter button, preview `ImageView`, **countdown UI + vibrations**. When countdown > 0, the watch counts down locally, then sends `/shutter`.
- **WatchPreviewListenerService.kt**: Listens for `/preview` images and `/shutter_done` from the phone.

## 3. Key Features
1. **Universal Shutter**: Works on any app via Accessibility Gesture injection.
2. **Live View (Preview)**: Phone screen mirrored to the watch (requires MediaProjection consent on phone).
3. **Countdown Timer**: 0 / 3 / 5 / 10 seconds — configured on phone, synced to watch via `/set_countdown/{sec}`. **Only the watch runs the countdown UI** before sending `/shutter`.
4. **Anti-Ghost-Touch Bypass**: Micro-swipe gesture at target coordinates.

## 4. Build Instructions
**Important**: The user prefers CLI (PowerShell/ADB). Do NOT use or suggest Android Studio UI methods.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\ckzhang\clawd\RemoteCap
.\gradlew assembleDebug
```

**Install:**
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s 10AEBC0M38001DC install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s 192.168.8.107:38127 install -r wear\build\outputs\apk\debug\wear-debug.apk
```

## 5. Debugging Guide
- **Log tags**: `WatchListener`, `ShutterAccessibility`, `ScreenCapture`, `WearShutter`
- **Wear Data Layer**: Both modules use `applicationId = com.ckzhang.remotecap`.
- **Accessibility**: Must be enabled once by user (`enabled_accessibility_services` must include `ShutterAccessibilityService`). Agent cannot enable via ADB on most OEM ROMs.

## 6. Known Issues & Fixes
| Issue | Status |
|-------|--------|
| Double countdown (watch + phone both waited N seconds) | **Fixed** — phone clicks immediately on `/shutter` |
| Duplicate `ScreenCaptureService` in manifest | **Fixed** |
| Preview lag over Bluetooth | Mitigated via 200×200 / 15% JPEG / 4 FPS |

## 7. Autonomous Agent Testing Protocol (DO NOT ask user to tap)

**Principle**: The agent runs full E2E tests via ADB. The user should not need to open camera, drag the crosshair, or close camera manually.

### Device serials (update if changed)
| Device | Serial |
|--------|--------|
| Phone (vivo V2413) | `10AEBC0M38001DC` |
| Watch (SM-R945F, Wi-Fi adb) | `192.168.8.107:38127` |

### One-command E2E shutter test
```powershell
cd C:\Users\ckzhang\clawd\RemoteCap
.\scripts\e2e-shutter-test.ps1 -Install   # optional: build + install first
.\scripts\e2e-shutter-test.ps1 -CountdownSec 0
```

**Script flow (agent must follow):**
1. Verify `ShutterAccessibilityService` is enabled (fail fast with clear message if not).
2. **Open camera** — vivo/V2413 stock camera: `com.android.camera/.CameraActivity` (STILL_IMAGE_CAMERA intent may return "No activity found"). Fallback package list in `scripts/e2e-shutter-test.ps1`.
3. **Find shutter** — `uiautomator dump`, prefer `resource-id` containing `shutter_button` (vivo: bounds ~[504,2269][756,2521] → center 630,2395), then text/bottom-clickable fallbacks.
4. **Position crosshair** — `am startservice` from adb is blocked on API 34; use activity automation intent:
   ```powershell
   adb -s <phone> shell am start -n com.ckzhang.remotecap/.MainActivity `
     -a ACTION_AUTO_POSITION_TARGET --ei SCREEN_X <x> --ei SCREEN_Y <y>
   ```
   (`FloatingTargetService` also accepts `ACTION_POSITION_TARGET` when started from inside the app.)
5. **Sync countdown** — `adb shell am start -n ...MainActivity -a ACTION_AUTO_SET_COUNTDOWN --ei COUNTDOWN_SEC <0|3|5|10>`
6. **Fire shutter from watch** — `input tap` on Shutter Only + shutter button (240,229) then (240,390).
7. **Assert** — primary pass signal: watch logcat tag `WearShutter` (shutter_done → double vibration). Phone logs may be sparse on some ROMs.
8. **Always close camera when done** — `KEYCODE_HOME` + `am force-stop <camera_pkg>` + `am force-stop com.ckzhang.remotecap` (stops overlay).

### Manual ADB snippets
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# Open / close camera
& $adb -s 10AEBC0M38001DC shell am start -a android.media.action.STILL_IMAGE_CAMERA
& $adb -s 10AEBC0M38001DC shell input keyevent KEYCODE_HOME
& $adb -s 10AEBC0M38001DC shell am force-stop com.android.camera   # use resolved package

# Crosshair automation
& $adb -s 10AEBC0M38001DC shell am startservice -n com.ckzhang.remotecap/.FloatingTargetService
& $adb -s 10AEBC0M38001DC shell am startservice -n com.ckzhang.remotecap/.FloatingTargetService `
  -a ACTION_POSITION_TARGET --ei SCREEN_X 686 --ei SCREEN_Y 2368

# Watch: Shutter Only → shutter
& $adb -s 192.168.8.107:38127 shell input tap 240 229
& $adb -s 192.168.8.107:38127 shell input tap 240 390

# Logs
& $adb -s 10AEBC0M38001DC logcat -d -t 300 | findstr ShutterAccessibility WatchListener
& $adb -s 192.168.8.107:38127 logcat -d -t 100 | findstr WearShutter
```

### Prerequisites (one-time, user)
- USB/Wi-Fi ADB for both devices
- Overlay permission allowed for Remote Cap
- Accessibility enabled for Remote Cap
- Watch app installed, Data Layer connected (phone UI shows ✅)

### Timing expectations (after double-countdown fix)
| Countdown | Watch waits | Phone waits after `/shutter` | Total |
|-----------|-------------|------------------------------|-------|
| 0s | ~0s | ~0s | ~1s |
| 3s | 3s | ~0s | ~3s |
| 10s | 10s | ~0s | ~10s |

## 8. Current Status & Next Steps
- Shutter, overlay, preview, countdown sync: working.
- Autonomous E2E script: `scripts/e2e-shutter-test.ps1`.
- Future: preview latency tuning, UI/UX polish, optional `WearMessageListenerService.kt` dead code removal.
