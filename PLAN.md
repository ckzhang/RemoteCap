# Remote Cap Implementation Plan
Date: 2026-05-20

## 🎯 Objectives
1. **Tiered Permissions (Phone App):** Add Accessibility (basic), MediaProjection (preview), and CAMERA (flashlight countdown). Create a UI/WebView to explain these.
2. **Camera Preview (Phone App):** Restore preview functionality via WebSocket/Wear Data Layer.
3. **Connection Status (Phone App):** 2-layer check (OS level `NodeClient`, App level `CapabilityClient`) displayed in UI.
4. **Startup Flow & Prompts (Watch App):** 2-layer connection check on watch startup. Prompt user: 'Shutter Only' (N) or 'With Preview' (Y). N wakes phone background service silently; Y wakes phone MainActivity.
5. **Countdown & Flashlight:** Watch app shows 3...2...1 countdown. Phone flashes torch during countdown.

## 🛠️ Execution Strategy (Manual Mode)
*Since external coding agents (Claude Code, Codex) are currently unconfigured or missing login on this host, I will implement this incrementally during system heartbeats.*

**Phase 1: Manifest & App Architecture**
- Read `AndroidManifest.xml` (Phone & Watch).
- Add `android.permission.CAMERA` and necessary UI activities.

**Phase 2: Phone UI & Permissions**
- Create/modify `MainActivity` or a new `PermissionActivity`.
- Implement tiered permission explanations and request prompts.

**Phase 3: Connection Status & Wear Layer**
- Implement `NodeClient` & `CapabilityClient` checks on both devices.
- Setup Watch startup prompt (Shutter vs Preview) and signal sending.
- Handle signals on Phone: Wake Service (silent) vs Wake Activity.

**Phase 4: Preview & Flashlight Countdown**
- Add countdown UI on Watch.
- Implement `CameraManager.setTorchMode` on Phone for flashlight strobing.
- Re-integrate MediaProjection screen capture and send frames via Data Layer/WebSocket.

**Phase 5: Build & Commit**
- `./gradlew clean assembleDebug`
- Fix any compilation errors.
- `git commit`

---
*Progress will be tracked and updated automatically via HEARTBEAT.md.*