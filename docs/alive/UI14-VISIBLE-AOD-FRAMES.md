# UI14: visible AOD frame scheduling and first-sleep image handoff

Candidate: `0.5-alive.7-ui.14`, versionCode `29`, compile/target SDK 37.

## Diagnosis

- UI13 only allowed a 100 ms missing-vsync fallback for three seconds after an
  external invalidation. Continuous AOD animation did not extend that budget.
  Thus a visible, timed AOD could freeze before its system-managed timeout.
- That same fallback limited a transition to roughly 10 fps when vsync stopped.
- The first visible lock renderer decoded the same immutable photo again even
  when the preview/home renderer had just decoded it. Hidden companion renderers
  intentionally do not initialize EGL, so moving all initialization into hidden
  engines would reintroduce startup GPU contention.

## Changes

- Forward actual Display state and PowerManager interactivity to each GL worker
  at creation, visibility, surface, power-broadcast, ambient and display changes.
- DOZE and DOZE_SUSPEND are still visible display states. ON with noninteractive
  power also covers the OEM's transition into doze. In these states the fallback
  remains available for as long as the wallpaper is visible: 16 ms for active
  transitions, 33 ms for settled continuous motion. Vsync and timer still compete
  for one frame ticket; a settled static scene still schedules no continuation.
- Actual OFF cancels frame tickets, retries, sensors and minute placement ticks,
  even if WallpaperService visibility remains true. Hidden, detached and closed
  engines retain their cancellation/generation protections. Restoring a visible
  display restarts only eligible render work.
- Preview, unknown-state and interactive-display fallback remains bounded to the
  original three-second recovery window. Refresh-rate-only events do not rearm it.
- Add a CPU-only, single-image handoff cache keyed by file identity/size/mtime and
  decode sample. It holds at most 32 MiB for 30 seconds after use. Preview/home/lock
  can reuse the decode but still own independent GL textures and contexts. Expiry,
  replacement and oversize images are recycled safely after upload. No extra
  hidden GPU rendering, disk cache, image-quality change or photo persistence.
- Diagnostic per-engine status includes display state and interactivity.

No wake locks, brightness changes, forced refresh rate, AOD duration changes,
wallpaper binding or system/AOD hook changes are introduced by this revision.
The UI13 image-picker creation flow is retained.

## Validation and limits

- 233 JVM/Robolectric tests, zero failures/errors/skips. Added coverage for doze
  past three seconds, doze suspend, true OFF, bounded awake/unknown/preview
  behavior, cache reuse, replacement, expiry, oversize and failed decode/upload.
- Xiaomi 15 / API37 isolated EGL fault-injection tests cover all ten representative
  renderer variants. The 601 case deliberately removes vsync and verifies continued
  drawing past the old budget, DOZE_SUSPEND, hidden/OFF stop and resumed drawing.
  Tests use consumed 240x520 surfaces and isolated data, not the user's wallpaper.
- Optimized-APK runtime smoke, APK signature and 16 KiB ZIP alignment are checked
  separately. Full lint retains previously documented unrelated findings.
- This removes identified scheduling stalls and redundant decoding, but it does
  not prove the first visible sleep is free of jank. Real compositor pacing,
  native-resolution cold shader/texture work and the user's chosen photograph
  still need manual acceptance. No numeric first-sleep latency improvement is
  claimed from the offscreen tests.
- Release optimization is enabled; signing uses the existing matching debug
  certificate fallback because no production keystore is configured.
