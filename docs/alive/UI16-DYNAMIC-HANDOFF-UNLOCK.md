# UI16: dynamic first-sleep preparation and earlier unlock animation

Candidate: `0.5-alive.7-ui.16`, versionCode 31, min SDK 33, compile/target SDK 37.

## Evidence from the UI15 device session

The user switched Cosmic variants 3, 4 and 1 on Xiaomi 15 and reported both
first-sleep flicker and delayed lock-to-home animation. Raw logs are local under
`build/xiaomi15-dynamic-capture-20261110-161311/` (not in the source archive).

- Variant 3: AOD starts at 16:13:28.309, home is hidden at 16:13:28.355,
  and the lock's first buffer is submitted at 16:13:28.380 after 30 ms of
  renderer initialization. The buffer is 71 ms after the state event and
  25 ms after the home-hide callback.
- Variant 4: lock initialization takes 43 ms; its first buffer at
  16:13:50.175 is 14 ms after home becomes hidden.
- Variant 1: lock initialization takes 17 ms; its first buffer at
  16:14:06.829 is 8 ms after home becomes hidden.
- At 16:14:08.775 SystemUI accepts fingerprint unlock and begins keyguard exit.
  Home becomes visible at 16:14:08.787 and lock hides at 16:14:08.814, but Alive
  only enters HOME at 16:14:09.579 with `reason=user-present`: about 804 ms late.

These are app/system callback and GPU-submission timestamps, not measurements of
panel-visible black frames. They identify cold handoff work and late state input;
they do not establish that every visual artifact has the same cause.

## Changes

UI15 only allowed photo variants 601-606 to prepare the hidden lock buffer.
UI16 extends that explicit home-ready handshake to shader-rendered Cosmic
1-19 (excluding nonexistent 2/5), Phoenix 101-105, Bubble 201-205, Gradient
301-303 and Stereo 501-503. Video 401-403 and Vivo 701-703 remain excluded.

The existing guard remains: home must have drawn the current generation, home
must be visible, lock must be hidden, and the display must be ON and physically
interactive in HOME state. Preparation waits 150 ms, creates one hidden buffer,
and also prepares the exposure framebuffer/shader before first sleep. It neither
starts a hidden render loop nor delays AOD. Sleep, visibility, sequence and
generation are rechecked before publication. Cosmic/Gradient retain the shared
CPU motion session; there is no shared EGL context or animation reset. UI15's
self-photo flow and UI14's visible-AOD/true-OFF scheduling remain intact.

For unlock, observational SystemUI hooks send HOME when
`notifyKeyguardGoingAway(true)` succeeds, with a
`startKeyguardExitAnimation` fallback. The event carries the hook-entry uptime
through asynchronous provider IPC. An exit overtaken by a visibility event can
advance to the latest timestamp, but never crosses a newer power, completed
unlock or cancellation boundary. A bounded 2-second fence prevents late ordinary
visibility/scrim hints from switching back to LOCK. Sleep/noninteractivity and
explicit re-lock remain authoritative. USER_PRESENT remains a fallback and does
not restart an already running HOME animation.

### ROM contract review

Archived `.reference/rom/MiuiSystemUI.apk` SHA256:
`a0e738e41fe599b97950cbf52a9e2ddc6ae2ceff986efbacb1c9840bea78768d`.
DEX inspection confirms:

- `KeyguardStateControllerImpl.notifyKeyguardGoingAway(Z)V`.
- `KeyguardViewMediator.startKeyguardExitAnimation(SurfaceTransition$Params)V`.
- `KeyguardViewMediator.finishSurfaceBehindRemoteAnimation(Z)V`; its true
  branch explicitly requests `showKeyguard()`, while false does not.

Do not treat `handleCancelKeyguardExitAnimation()` or `mShowing` alone as proof
of re-lock: this ROM has a cancellation branch that ends unlocked, and showing
can remain true until the exit finishes. The re-lock hook therefore observes only
the explicit true branch of `finishSurfaceBehindRemoteAnimation`. Original
methods always execute; the module does not write authentication/keyguard state.
After reconnection the live SystemUI APK hash matches the archive exactly.
The user's subsequent reboot confirms all three new hooks registered in SystemUI.

## Validation and delivery status

- Local Debug/Release build and 244 unit/Robolectric tests pass. Tests cover
  early exit, late visibility, out-of-order IPC, sleep authority, explicit
  cancellation, bounded hints and preparation family eligibility.
- Offline SDK 37 verification passes: 1,381 structure/asset checks, 118 Java
  sources compiled, 18 state, 454 power, 57 permission/crop, 23 shared-motion
  and 6 stereo assertions. The verifier now resolves `android-37.0` and checks
  UI14's display-aware fallback instead of the obsolete universal 3-second cap.
- Release uses R8 optimization and the existing matching debug-certificate
  fallback, not a new production signing key. Full lint has preexisting unrelated
  findings; passing Release vital checks is not a full-lint clean claim.
- APK signature verification and 16 KiB zip alignment pass; badging confirms
  versionCode 31, min SDK 33 and target SDK 37 without a debuggable flag. APK SHA256:
  `8274cf48f9660fc810ad765d3e78283165591afd90177d7e8bc4d84b2b999088`.
- After reconnection all nine isolated companion probe cases pass: 1, 3, 4,
  101, 201, 301, 501, 601 and 602 at 1200x2670. They check one hidden buffer,
  shared motion identity, renderer reuse and cancellation. Cosmic 1/3/4 first
  prepared takeovers measure 14/29/19 ms in the isolated harness, not panel time.
- Ten-family recovery/missing-vsync tests pass for 1, 101, 201, 301, 501, 601,
  602, 701, 702 and 703, including the prolonged-visible-doze/true-OFF probe.
  The optimized APK passes all 42 representative offscreen renderer cases.
  These are not visual acceptance or live-hook/power-transition tests.
- UI16 was cover-installed at 2026-11-10 17:39:25 +08:00 on serial 197fb721.
  Installed versionCode 31, target SDK 37 and APK hash match. The APK was also
  copied to Download as `HyperExtreme-Alive-0.5-alive.7-ui.16-sdk37.apk` and its
  hash matches. App/files directory inodes and both saved Alive preference file
  hashes remain unchanged across installation.
- Real service startup for variant 1 records a visible home first frame at
  17:39:28.184 and the hidden lock first frame with `prepared=true` at
  17:39:28.366. The status query reports both engines and an empty render error.
  This confirms preparation in the installed service, not first-sleep visual
  acceptance. No reboot, input injection, power command or settings/data reset
  has been issued for UI16.

## Post-reboot session, 2026-11-10

The user rebooted and performed the requested changes/sleep/unlock sequence.
Capture under `build/xiaomi15-ui16-postreboot-20261110-193251/` was stopped after
the user reported completion; raw device logs remain local, not in this release.

| Variant | Prepared buffer | First sleep | Preparation lead | Apply-to-sleep |
| --- | --- | --- | --- | --- |
| Photo 601 | 19:37:30.633 | 19:37:31.735 | 1102 ms | 1432 ms |
| Cosmic 1 | 19:37:46.325 | 19:37:47.573 | 1248 ms | 1624 ms |
| Cosmic 3 | 19:38:09.365 | 19:38:09.458 | 93 ms | 270 ms |

The current generation's prepared buffer precedes each first sleep, with no
cold renderer initialization during those sleep intervals. All eight unlocks
enter HOME with `reason=keyguard-going-away`. Relative to SystemUI's
`call fw keyguardGoingAway` log, delays are 37, 12, 18, 7, 13, 5, 9 and 5 ms;
the five dynamic-wallpaper unlocks are 5-13 ms. No late LOCK interrupts those
HOME states before the next real sleep. Seven sleep transitions are captured,
with no Alive error-level/render-failure logs and an empty final render error.

These are event/submission timings, not panel flicker or end-to-end animation
latency measurements. The user reported completion, not explicit visual
acceptance. Sleeping before preparation completes, Surface destruction, memory
pressure and compositor behavior can still take a cold path.
