# UI17 lossless resource packing

## Scope

- Version `0.5-alive.7-ui.17`, versionCode 32, minSdk 33 / targetSdk 37, arm64-v8a.
- Compress the native libraries at APK packaging time; PackageInstaller extracts them at installation.
- Preserve the small `libhome_bridge.so` uncompressed for the LSPosed native entry.
- Remove the global `dex` no-compress rule, which previously overrode main DEX compression.
- Gzip the Flyme runtime DEX and the two Vivo runtime native libraries in generated assets.
  Use `.agz`, not `.gz`: AGP automatically expands `.gz` files while merging assets.
- The generated asset tree replaces the source asset tree for packaging. Original and compressed
  copies must never both enter the APK. Imported source files and their provenance hashes stay intact.
- Keep videos, textures, photo masks, models, fonts, and nested resource APKs unchanged.
  Most already-compressed image/video assets do not benefit from another ZIP layer.

## Extraction and timing

`PackedAssets` streams gzip data directly into the existing runtime extractors. There is no new
startup service, installer broadcast receiver, network fetch, or whole-bundle in-memory decode.
NativeClockRuntime and VivoEngineRuntime keep their digest-pinned private cache paths, read-only
code protection, temporary output and verified publication behavior. A matching existing cache
continues to work without reading the compressed asset. Corrupt caches are checked and rebuilt.

Feature resources are prepared before loading that feature, not on every launch, frame, lock,
unlock, or AOD transition. No changes were made to wallpaper selection/application, rendering,
shared motion, hidden companion preparation, display power policy, or user-photo storage.
Nothing is moved into device-protected storage.

This reduces the APK/download size, not the expanded data size. Installing compressed native
libraries requires additional extracted storage alongside the smaller APK. A cold feature load
can perform one decompression, and clearing the code cache can require another extraction.

## Native entry alignment and signing

AGP legacy JNI packaging does not page-align explicitly stored native files. Each variant's
package task therefore runs `tools/finalize_packed_apk.py` before exposing the APK to consumers:

1. Verify the original signed APK and remember its signing certificate digest.
2. Align stored native entries to 16 KiB with the selected SDK's `zipalign`.
3. Sign with that same variant's key, passing passwords through environment variables, not files.
4. Verify the certificate is unchanged and verify alignment before atomically replacing the APK.

The existing V2 signing scheme is retained. In this workspace release still uses the existing
Android Debug certificate fallback; no production keystore was added or changed.

## Verification

The release APK is approximately 222.2 MiB, compared with UI16's 250.2 MiB: about 28.0 MiB / 11.2%
smaller without downsampling, transcoding, removing wallpaper choices, or changing decoded assets.
Exact size and digest are recorded in `build/packed-apk-verification.json` and the deliverable checksums.

- 249 JUnit/Robolectric tests pass, including five compressed-resource/cache tests.
- Four Python packing tests cover byte identity, deterministic output, stale generated files,
  missing inputs, and overlapping source/output rejection.
- `verify_packed_apk.py` compares 602 resource/native entries against UI16 byte-for-byte after
  decompression. Generated ART profiles are deliberately exempt because app bytecode changed.
- It also checks no duplicate originals, compressed main DEX/native libraries, stored videos,
  the stored and 16 KiB-aligned LSPosed entry, and all packed-resource digests.
- Offline verifier: 1,383 structural/asset checks and 119 Java sources compiled.
- Extension verifier: 328 checks against the packaged release.
- Release build, APK signature verification, SDK/ABI metadata and zip alignment checks pass.

No Android device was connected during this change, so this is not a claim of fresh-install,
native hook, runtime GPU, or power-transition validation on the Xiaomi 15. Full lint retains
pre-existing findings; the release-critical lint/build checks pass.

## Reproduce

```text
gradlew --offline --no-daemon -PlegacyUiSmoke=false :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
python tools/test_pack_alive_assets.py
python tools/verify_alive.py --compile-sdk 37 --build-tools 37.0.0
python tools/verify_alive_extensions.py --apk app/build/outputs/apk/release/app-release.apk
python tools/verify_packed_apk.py --baseline deliverables/HyperExtreme-Alive-0.5-alive.7-ui.16.apk
python tools/package_alive_candidate.py
```

On the next connected-device run, cover-install without clearing app data, check the extracted
native libraries, and exercise both first and cached loads of Flyme clock/editor and all three
Vivo wallpaper families. Then check the existing Alive lock/unlock/AOD transitions. No automatic
reboot or system-setting change is required by the packing code.
