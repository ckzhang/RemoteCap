# Remote Cap - Product Roadmap & TODO Plan

*This document outlines the product management strategy, UX improvements, and technical roadmap for Remote Cap to transition from a prototype to a production-ready Google Play app.*

## Phase 6: UX Refinement & Onboarding (High Priority)
- [x] **Setup Wizard (Phone)**: Replace the current single-screen button list with a step-by-step onboarding flow.
    - Step 1: System Alert Window (Overlay)
    - Step 2: Accessibility Service (Shutter Control)
    - Step 3: MediaProjection (Screen Recording) - *Only request when Preview is activated.*
- [x] **Accessibility Watchdog**: Android often kills accessibility services. Add a check in `MainActivity` that detects if the service is dead and gently prompts the user to re-enable it.
- [x] **Auto-Fade Floating Target**: The 🎯 target should turn 20% transparent and shrink slightly after 3 seconds of being idle to avoid blocking the camera viewfinder. Tap to wake/unlock.

## Phase 7: Watch App Settings & Autonomy (New Feature)
- [x] **Wear OS Settings Screen**: Build a dedicated settings UI on the Watch app (accessible via swipe or a gear icon).
- [x] **Watch-side Countdown Control**: Allow the user to configure the countdown timer (0s, 3s, 5s, 10s) directly from the watch, rather than relying solely on the phone UI.
- [x] **Haptic Feedback Toggle**: Option on the watch to enable/disable vibration feedback during the countdown.

## Phase 8: Adaptive Streaming & Performance
- [x] **Smart FPS (Battery Saver)**: Stop the `ScreenCaptureService` transmission (0 FPS) immediately when the Watch enters Ambient Mode (screen off/dimmed). Resume when active.
- [ ] **Dynamic Quality**: Automatically adjust FPS and resolution based on connection bandwidth (e.g., 15 FPS / 400x400 for Wi-Fi direct, 4 FPS / 200x200 for Bluetooth).

## Phase 9: Monetization (Freemium Model)
- [ ] **Free Tier Setup**: Core universal remote shutter functionality (Floating Target + Accessibility Click).
- [ ] **Pro Tier (IAP)**: 
    - Unlocks "Live View" (Watch Preview).
    - Unlocks Advanced Countdown settings.
    - Unlocks "Invisible Target" (fully hide the 🎯 during capture).
- [ ] **Google Play Billing Integration**: Implement the billing client for a ~$2.99 USD one-time lifetime unlock.

---
*Note: Always apply `KARPATHY_CODING_GUIDELINES.md` when implementing these features (Simplicity first, Surgical changes, Goal-driven execution).*