# Remote Cap - CLI Build & ADB Testing Workflow

This document outlines the standard command-line workflow for building, deploying, and testing the Remote Cap Android application. 
**Note for AI Assistants:** The user strictly prefers running CLI (PowerShell/ADB) for building and deploying. Do NOT suggest Android Studio UI methods. Always format ADB commands natively (e.g., `adb -s <serial> ...`) without the `.\` prefix.

## 1. Environment Setup & Build APK

Before building, ensure the correct Java environment is used (Android Studio's bundled JBR is recommended to avoid version mismatches).

**Build Command (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\ckzhang\clawd\RemoteCap
.\gradlew clean assembleDebug
```

**Output Location:**
The generated APK will be located at:
`C:\Users\ckzhang\clawd\RemoteCap\app\build\outputs\apk\debug\app-debug.apk`

---

## 2. ADB Environment Setup

If the `adb` command is not recognized, temporarily add the Android SDK platform-tools to the PATH, or restart the ADB server if it is unresponsive.

**Setup / Restart ADB (PowerShell):**
```powershell
$env:PATH += ";C:\Users\ckzhang\AppData\Local\Android\Sdk\platform-tools"
adb kill-server
adb start-server
adb devices
```

---

## 3. Install APK via ADB

Always specify the device serial if working in a multi-device environment. Use the `-r` flag to replace/update the existing app.

**Install Command:**
```powershell
adb -s <device_serial> install -r C:\Users\ckzhang\clawd\RemoteCap\app\build\outputs\apk\debug\app-debug.apk
```

---

## 4. Granting Permissions via ADB (For Automated Testing)

To bypass UI interactions when testing, you can grant required permissions directly via ADB.

**Grant Media / Photo Permissions:**
*(For Android 13 / API 33+)*
```powershell
adb -s <device_serial> shell pm grant com.ckzhang.remotecap android.permission.READ_MEDIA_IMAGES
```
*(For Android 12 and below)*
```powershell
adb -s <device_serial> shell pm grant com.ckzhang.remotecap android.permission.READ_EXTERNAL_STORAGE
```

**Note on System Alert Window & Accessibility:**
Some special permissions (like `SYSTEM_ALERT_WINDOW` or Accessibility Services) usually require user UI interaction or more complex root/ADB commands. For standard runtime permissions (like camera, storage, media), `pm grant` is the standard approach.

---

## 5. Launching the App via ADB (Optional)

To start the app immediately after installation:
```powershell
adb -s <device_serial> shell am start -n com.ckzhang.remotecap/.MainActivity
```
