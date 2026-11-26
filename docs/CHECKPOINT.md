# Checkpoint (2026-11-27, ~00:55)

## Resume
- **Pane 1 (Android):** `cd ~/projects/BK && claude`, then:
  > Read docs/CHECKPOINT.md, docs/SPEC.md and docs/HANDOFF.md. You are pane 1. Continue from "Next for pane 1".
- **Pane 2 (everything else):** `cd ~/projects/BK && claude`, then:
  > Read docs/PANE2_BRIEF.md, docs/SPEC.md and docs/HANDOFF.md, then start with the shared contracts.

## Environment (already set up)
- Build: `export JAVA_HOME=$(ls -d ~/.jdks/jdk-17*/Contents/Home); ./gradlew :app:testDebugUnitTest :app:assembleDebug`
- JDK 17: `~/.jdks/jdk-17*` (Homebrew can't build on macOS 13). Android SDK: `~/Library/Android/sdk` (platform 35).
- adb: `~/Library/Android/sdk/platform-tools/adb`. scrcpy: `~/.local/scrcpy/scrcpy-macos-x86_64-v5.0.1/scrcpy`.
- BK is its own git repo. Note: `~` (home) is ALSO a git repo; never run git from outside BK.
- Mac: 8 GB RAM, about 14 GB free disk. Only pane 1 runs Gradle. No emulator yet.
- Secrets go in `.env` (gitignored): `HIGGSFIELD_API_KEY`, `HIGGSFIELD_API_SECRET`. Never print or commit them.

## Status by stage
| Stage | State |
|---|---|
| 1. Repo, modules, Demo Mode, voice loop, read-back, TTS bubble | Code done, builds. **Not yet run on a device.** |
| 2. Journey engine + accessibility overlay on Demo Pay | Code done, logic unit-tested. **Not yet run on a device.** |
| 3. Bill reader (ML Kit, boxes, read-aloud, links to Demo Pay journey) | Code done, parser tested. Needs `assets/demo/sample_bill.png` (pane 2). |
| 4. Udhaar ledger + reminder/QR + Rescue Circle | Redactor (tested), NameMatch (tested), encrypted Room ledger + PIN secrets written. **Still to do:** ledger screen + PIN UI, Tasks udhaar flows (add/repay/balance/remind, similar-name ask before read-back), QR (ZXing) + WhatsApp share + journey start, Rescue screen (summary from JourneyState, redact, edit, read aloud, "Bhej doon?", share WhatsApp/Gemini/Claude, save RescueCase). |
| 5. Schemes, error handling polish, onboarding, README, CI, Release | Not started (pane 2 owns README/CI). |
| 6. Web demo (Vercel), Wispr Flow API engine | Not started. `WisprFlowInput` is a stub. |

Tests: 8 classes, all passing (numbers, normalizer, intents, 20 debug phrases, journeys + guide logic, bill parser, redactor, name match).
Debug APK is 30.8 MB (unshrunk). Release with R8 + ABI splits should fit under 30 MB; measure in stage 5.

## Next for pane 1
1. Finish stage 4 (list above). `Assistant` needs a pre-confirm hook so name ambiguity ("Ramesh Kumar ya Ramesh Yadav?") is asked BEFORE the read-back.
2. Device: needs a phone with USB debugging, or an emulator (needs ~8 GB more disk). Ask the user which one.
   Once a device is connected: install both APKs, grant via adb (`pm grant` mic/camera/notifications, `appops set com.bataokaise.app SYSTEM_ALERT_WINDOW allow`,
   `settings put secure enabled_accessibility_services com.bataokaise.app/com.bataokaise.app.guide.GuideAccessibilityService`, `settings put secure accessibility_enabled 1`),
   then test Demo Pay guide end to end with `uiautomator dump` + `screencap`.
3. Stage 5: schemes (rules over assets/demo/schemes.json, Custom Tab to the demo form URL from HANDOFF.md), onboarding by voice, release build size.
4. After GitHub login: create public repo `batao-kaise`, push, tag `v0.1.0` for the Release once CI (pane 2) exists.

## Known risks (marked difficult)
- 🔴 Accessibility guide on a real device (positions, auto-advance). Untested on device.
- 🔴 WhatsApp Send-button targeting (unstable view IDs). Falls back to content-desc "Send", then spoken steps.
- 🔴 Chrome Custom Tab web-field highlighting. Matches by text/hint; positions may drift.
- 🔴 Wispr Flow API: gated access. Ship as "written, untested" unless approved.
- 🟠 Barge-in is tap-to-interrupt, not full-duplex. Silence timing relies on recognizer timeouts (~6 s / ~12 s).
