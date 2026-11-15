# UI12: direct new-wallpaper preview

Version: `0.5-alive.7-ui.12`, versionCode `27`.

## Navigation correction

The Alive carousel's new card and its new-wallpaper button incorrectly shared
the dynamic-library callback. Both now use a separate creation callback that
opens `AlivePreviewActivity` directly in lock-screen preview mode, using the
last Alive scene and existing draft options. They do not open a saved preset,
erase drafts, bind a wallpaper, or apply settings merely by being opened.

The separate dynamic-wallpaper row still opens the library. Current-wallpaper
and saved-preset preview paths are unchanged. The carousel regression test
checks both new entry points and verifies that only the library row calls the
library callback.

Liquid, Vivo raster, and flash-card creation previously imported a photo into
a draft and stayed on the carousel. Successful new imports now open the same
preview editor after import/optional subject recognition releases the busy
state. Navigation waits for the activity to resume and is consumed once.
Cancelled pickers and failed imports do not navigate. Ordinary material
replacement remains in place. Old drafts/presets are still preserved, and
opening the imported draft does not apply it to the system wallpaper.

Regression tests cover waiting for busy to clear, single consumption, no
navigation without a completed import, and background completion/resume.

## Xiaomi 15 AOD investigation

After the UI11 installation, the old system-process hooks initially remained
loaded. Following the user's reboot, device logs confirmed that the UI11 AOD
wallpaper guard was installed and an AOD render frame was produced. Those
observations alone do not establish visible AOD output.

The user subsequently identified the disabled system option linking AOD
appearance to the lock screen. No AOD hook, renderer, scope, or system-setting
change is included in UI12. ADB input injection was denied; it was not bypassed.
The user manually controlled the screen. Visible output after enabling the
option remains a user acceptance check, not an automated test result.

## Build and acceptance

The production build retains compile/target SDK 37, minimum SDK 33, ARM64,
R8 optimization, and resource shrinking. It uses the existing local signing
certificate for data-preserving updates, not a separate release certificate.
Build/test evidence and installation logs are kept under
`build/xiaomi15-ui12/`; the versioned APK, source, and R8 mapping are retained
under `deliverables/` after validation.

On the phone, test the new card and new-wallpaper button, cancel the editor,
then verify that the dynamic-wallpaper row still opens the scene library.
