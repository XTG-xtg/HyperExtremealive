# UI11: Alive AOD and renderer recovery

Candidate: `0.5-alive.7-ui.11`, versionCode `26`. UI10 editor changes are retained.

## Report and confirmed code defects

The reported symptoms were missing Alive AOD effects, first-sleep failures after long gaming sessions, and a 3-5 second black interval following a reboot. The exact gaming/reboot scenarios have not been reproduced on a supported HyperOS phone in this session.

Code inspection and the archived MIUIAod DEX revealed:

1. A renderer exception released EGL permanently. Visibility/display callbacks could request another frame but could not recreate the renderer.
2. Frame delivery depended entirely on Choreographer. Suspended display vsync could strand a first frame or an AOD transition.
3. A normal AOD panel could disable wallpaper before asynchronous configuration arrived. Later configuration did not repair that panel.
4. MIUIAod also checks `aod_using_super_wallpaper` and both wallpaper slots' OEM `super_wallpaper` classification. Our ambient-capable WallpaperService is not necessarily recognized by those OEM checks.
5. A configuration notification failure was inside the rendering failure boundary. A successfully submitted frame could therefore be followed by unnecessary renderer destruction.

## Changes

### EGL ownership and recovery

- Retain the current borrowed Surface and its generation separately from disposable EGL objects.
- Retry a failed visible renderer with a bounded 100/350/1000 ms backoff. Initialization alone does not reset the failure budget. Sustained successful rendering or a later visibility cycle after a cooldown can rearm it.
- A detached/closed/replaced generation cannot submit frames or restart through an old queued retry. Hidden failures wait for visibility; they do not rebuild offscreen.
- Keep the shared EGLDisplay reference counting across wallpaper slots and previews.
- Isolate configuration notifications from rendering and dispatch them off the GL thread. Notification failure is logged, not treated as GPU failure.

### Frame delivery and startup

- Submit the first requested frame without requiring a display-vsync callback.
- Prefer vsync normally. A coalescing frame pump allows a 100 ms fallback only within a three-second window opened by external invalidation. A timer and vsync cannot submit the same ticket twice.
- Animation continuation does not extend that window. Hidden/detached workers cancel both paths. Clock, media and sensor invalidations remain separate legitimate inputs; no wake lock or AOD-session extension is added.
- Do not eagerly initialize a hidden companion slot. Avoid duplicate renderer reconstruction for unchanged Surface dimensions, while real destruction/recreation and explicit configuration reload remain supported.
- Consume main-thread power state before Surface attachment; seed long-idle history with its settled mode instead of replaying obsolete transitions.
- Refresh-rate/HDR-only display notifications no longer masquerade as wake events or reopen fallback draw budgets.
- Expose per-slot generation, drawn generation, frames, initialization time, attach-to-first-frame time, recovery count and last failure in the diagnostic status Bundle. Attach-to-first-frame time includes any time a slot was intentionally hidden; it is not an end-to-end reboot benchmark.

### AOD compatibility and late configuration

- Adapt OEM wallpaper classification only within the stock `showSecurityIdentityViewAt` creation call and only for enabled Alive, with native device/AOD-setting checks and the occlusion/fingerprint guards satisfied.
- Do not globally change OEM wallpaper classification, system settings, screen brightness, schedules or notification pulses.
- Track a normal-template denial or the exact native-created panel/window. A later valid configuration can restore that same attached, visible, dozing panel after rechecking native eligibility.
- When the cold creation omitted wallpaper layout flags, repair only that panel's matching `WindowManager.LayoutParams`; preserve unrelated flags. Disabling restores the flags/format changed by this adapter.
- Replacement windows, detached roots, unrelated native wallpaper denials and newer backgrounds do not inherit old restoration authority. Background color mutation is checked as well as Drawable identity.
- Missing ROM contracts disable this adapter rather than guessing field/method names.
- Configuration retries are bounded. Initial reads, retries, content changes and attachment all reconcile the panel; no Provider call is made synchronously from a system/UI hook.

## Verification

See `VALIDATION-UI11-STABILITY.json` for final counts and artifact hashes.

- Full JVM/Robolectric suite, including retry budgets, frame callback races, stale history, initial state ordering, configuration retries, AOD eligibility, late classification, window ownership, occlusion and fingerprint cases.
- Offline state/resource/Java checks and archived MIUIAod method/field contracts.
- Real EGL window fault injection on an isolated Redmi K30 / Android 12 process for variants 1, 101, 201, 301, 501, 601, 602, 701, 702 and 703. Tests cover EGL window loss, hidden failure, recovery, missing vsync, companion-slot independence, detach-before-retry and final display-reference cleanup.
- The missing-vsync probe disables continuous AOD/sensor animation and cancels its independent minute tick, so it tests the fallback scheduler without unrelated external invalidation.
- Separate optimized-APK renderer smoke, release signature and ZIP alignment checks.

The spare phone was not rebooted, no new APK was installed, and no wallpaper was bound or user preference changed by these probes. Test data is isolated under `/data/local/tmp`.

## Remaining device acceptance

The spare API31 phone is below the production API33 minimum and is not an AOD/HyperOS acceptance target. The ROM archive proves contract shape, not live hook execution on the user's firmware. The GPU probes use 240x520 consumed surfaces, not full-resolution reboot timing, and do not include video variants 401-403.

On the target phone, cover-install the same-signature release and reload the hooks with one normal reboot. Preserve existing LSPosed scopes/settings. Verify:

1. First sleep after boot, ordinary sleep/wake, and power pressed again during the opening transition.
2. First sleep after a long game, including recovery after memory pressure.
3. AOD schedule/disabled state, fingerprint interaction and keyguard occlusion remain stock-controlled.
4. Time from the visible lock screen to the first wallpaper frame after boot, distinguishing before-first-unlock credential storage availability from renderer initialization.

No direct-boot exposure of private photos, process split, root keep-alive, forced process restart, or copied cached screenshot was introduced. The changes remove identified failure paths and startup contention; they do not establish that every reported 3-5 second reboot interval is eliminated on the target device.

Full lint has pre-existing findings in DesktopWallpaperInjector, ParallaxBg and ScreenFlash. These must not be described as a clean lint result or suppressed as part of this repair.
