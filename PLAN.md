# Overnight Development Plan (Loop until 07:00 AM)

## Constraints & Rules
- **Development Loop:** For every task: 1) Write code, 2) Build & Deploy, 3) Test & Verify on physical device, 4) git add . and git commit the working feature, 5) Move to the next task.
- **CRITICAL HARDWARE SAFETY:** ONLY open the camera app (com.android.camera) when specifically running an E2E test. IMMEDIATELY force-stop it (db shell am force-stop com.android.camera) after the test. DO NOT leave it running in the background. Overheating risk.
- **Manager (Main Agent):** Oversees progress, checks in hourly, assigns tasks to sub-agents.
- **Worker Agents:** 
  - Builder (Writes code, compiles)
  - Tester (Runs ADB tests, verifies logs, reports back)

## Bug Backlog
- [ ] **Fix Accessibility Click Bug (Null Context):** The recent SharedPreferences change broke the click because TargetManager.init(this) is only called in MainActivity and onServiceConnected. When the app is swiped away and memory is cleared, WatchMessageListenerService starts ShutterAccessibilityService, but prefs is null, so it reads (0,0). 
  - *Fix:* Call TargetManager.init(applicationContext) inside ShutterAccessibilityService.onStartCommand() and WatchMessageListenerService.

## Feature Roadmap
1. [ ] **Countdown Timer:** Add selectable countdown options (3 / 5 / 10 seconds) on the watch or phone UI. Wait this amount of time before executing the shutter click.
2. [ ] **Two-Way Haptic Feedback:**
   - Watch vibrates *once* when the Phone Service successfully receives the /shutter intent.
   - Watch vibrates *again* (or differently) when the actual photo is taken (after the countdown finishes).
3. [ ] **Image Transmission:** Fetch the newly generated photo from /sdcard/DCIM/Camera and transmit the image back to the Watch over the Wear OS Data Layer (Asset/Channel) so the user can preview what was just captured.
4. [ ] **UI Beautification:** Refactor the Phone and Watch UI to look polished, modern, and delightful. 

