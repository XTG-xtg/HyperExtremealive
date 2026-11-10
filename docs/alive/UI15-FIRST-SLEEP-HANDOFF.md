# UI15: prepare the new photo's lock buffer before first sleep

Candidate: `0.5-alive.7-ui.15`, versionCode 30, compile/target SDK 37.

## Target-device evidence

The user performed a new-photo/apply/home/sleep sequence on Xiaomi 15 while the
UI14 logs were streamed. The capture is stored locally under
`build/xiaomi15-ui15/manual-capture/` and is not included in the source archive.

- 15:49:06.210: apply completed.
- 15:49:07.035: the new home surface submitted its first frame.
- 15:49:09.034: the first sleep event started the AOD transition.
- 15:49:09.079: the system hid the home wallpaper.
- 15:49:09.130: the lock renderer completed its 71 ms initialization.
- 15:49:09.175: the lock surface finally submitted its first frame, already in
  the AOD transition: 141 ms after sleep, 96 ms after home became hidden.
- Subsequent sleep events reused that renderer, without another initialization.

The logs establish a first-frame handoff gap, not a measurement of the exact
visible black interval. UI14's decoded-image cache did not eliminate EGL/shader/
texture initialization at the first sleep. The exposure presentation pass was
also lazily allocating its framebuffer/shader on first AOD use.

## Change and boundaries

- After the current home generation has submitted a frame and home is visible,
  the registry may ask the hidden photo lock companion (601-606) to prepare.
- Preparation waits 150 ms to avoid competing with the home first frame. It
  requires the awake ON display, real PowerManager interactivity, HOME state,
  matching requested event sequence and a current, still-hidden Surface.
- Initialize a settled HOME composition for this new photo and submit one buffer
  into the hidden lock Surface. EGL/textures remain local to that engine. A mask
  arrival may complete this one buffer within a 500 ms window; it never starts
  hidden continuous animation or polling. Exposure resources initialize with the
  photo renderer rather than at the first sleep.
- The subsequent sleep consumes its original registry timestamp and reuses the
  same renderer. There is no local animation-clock reset or AOD delay.
- Requested state/display/visibility are recorded before posting to the GL queue.
  They are rechecked before initialization and before publishing a prepared buffer,
  including a real interactivity check. A sleep request cancels eligibility even
  while the display still says ON. Detach/reload/close cancel the pending job and
  retain generation checks. A normal hidden engine still does not initialize just
  because it was attached: the explicit home-ready handshake is required.
- Expose `prepared_generation` and mark prepared first-frame logs so device
  acceptance can distinguish a prefilled lock buffer from a late cold first frame.

This is a narrow exception to UI11's hidden-GPU rule: one explicitly requested
photo buffer during awake desktop display, not ongoing hidden rendering. UI14's
visible-AOD scheduler, true-OFF stop and all system-owned AOD settings are retained.
No wake lock, screen-power command, wallpaper visibility override, duration change,
direct-boot photo exposure or screenshot substitution is added.

## Validation

The unit suite includes preparation eligibility/exclusion tests. An isolated
1200x2670 consumed-Surface probe covers both lens and framed photos: lazy hidden
attachment, one prepared buffer with no hidden loop, unchanged renderer identity
on first sleep, an intermediate (not settled) AOD first frame, cancellation by
sleep and invalidation by detach. The probe never binds a wallpaper or changes
the physical display power, and requires the test phone to remain awake.

Existing ten-family recovery/missing-vsync probes and optimized-APK runtime tests
remain separate from visual acceptance. Release signing continues to use the
existing matching debug-certificate fallback; release optimization is enabled.
Full lint retains previously documented unrelated findings.

Manual acceptance still requires replacing a photo then sleeping once. A user
who sleeps before the desktop-ready preparation can finish, a destroyed Surface,
memory pressure, or a ROM compositor transition may still take the cold path.
Do not equate isolated GPU submission timing with panel-visible smoothness.
