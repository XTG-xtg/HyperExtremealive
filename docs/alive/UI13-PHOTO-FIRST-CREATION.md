# UI13: consistent photo-first creation

Version: `0.5-alive.7-ui.13`, versionCode `28`.

The user clarified that Alive creation, like liquid wallpaper creation, must
ask for an image before entering the preview. This supersedes UI12's direct
entry for the Alive new card/button.

Both Alive new entry points now launch the system single-image picker. On
success, the image is imported into immutable private storage and passed
directly to `AlivePreviewActivity` as a new, unsaved editor snapshot. The
current photo scene is retained; otherwise the newest saved photo scene is
used, falling back to the circle scene (601). The selected image supplies
lock, following home, and frame content with fresh crop defaults, rather than
inheriting old independent media or a hidden-photo AOD mode.

Import runs off the main thread. The existing lifecycle-aware completion
effect opens the editor once, after work finishes and the activity resumes.
Cancelling selection does not create a new draft or navigate. Import failure
is reported without changing applied wallpaper or draft preferences. The
editor continues to save its own draft after it opens; applying remains an
explicit confirmation. No historical preset is added by this creation step.

The dynamic scene library, photo-style shortcuts, and saved/current preview
routes remain separate. UI12's automatic preview after successful liquid,
raster, or flash-card creation is retained. AOD hooks and system settings are
unchanged.

New tests cover picker cancellation, image delivery in the preview intent,
draft/applied isolation before navigation, fresh crop/media defaults,
non-photo scene fallback, and import failure. The import boundary uses a
synthetic PNG fixture because desktop native graphics cannot decode Android
file descriptors; this is not a new device ImageDecoder acceptance test. Existing
carousel and lifecycle navigation tests also remain in the full suite.

Build evidence is under `build/xiaomi15-ui13/`. The release retains SDK 37,
minimum SDK 33, ARM64, R8, and the same local certificate for cover-installing
without clearing data. The APK, source snapshot, and R8 mapping are archived
under `deliverables/` after validation. Phone picker interaction remains a
manual acceptance check because ADB input injection is unavailable.
