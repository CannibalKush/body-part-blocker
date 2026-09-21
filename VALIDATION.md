# Prototype validation

Date: 2026-09-21. Device environment: clean Android 15 / API 35 ARM64 emulator (`BodyBlock_Test_API35`). This is emulator evidence, not physical-phone certification or a detection-accuracy benchmark.

## Build and static checks

- `:app:assembleDebug`: successful, installable universal debug APK.
- `:app:testDebugUnitTest`: 3 tests covering output decoding, padding/coordinate mapping, confidence/non-finite rejection, duplicate suppression, and tracked encounter counting.
- `:app:lintDebug`: 0 errors; 19 warnings, primarily untranslated prototype UI strings and available test-library updates.
- `:app:assembleDebugAndroidTest`: successful.

## Instrumented checks

Eight device tests cover:

1. Bundled ONNX model detects a face in the NASA fixture; bundled ML Kit produces eye landmarks.
2. All eight effects produce opaque masks inside selected regions and transparent pixels outside; reverse mode preserves selected holes.
3. Exported PNGs retain masks when reopened; pixels outside the mask retain their source colour.
4. Profile packs round-trip; path-traversal ZIP entries are rejected.
5. Home, Settings, Export, Help and Browser navigation; private browser closes and reopens without crashing.
6. Actual single-app MediaProjection capture of a separate test-gallery APK, nonzero detections, correct selected package, a pixel assertion that the mask covers the known face location, touch-through operation, and clean stop.
7. Browser viewport detection of an embedded face fixture.
8. Repeated captured-content size callbacks retain the existing ImageReader; an actual orientation change replaces it.

The four engine tests are also run with emulator Wi-Fi and mobile data disabled to check offline operation. This is not a full network audit of third-party SDKs.

## Visual inspection

Screenshots under ignored `artifacts/screenshots/latest/` show Home, Settings, Browser and actual face masks in both cross-app capture and browser filtering. The first capture screenshot revealed an initial-frame cutout/inset offset; moving screen-to-overlay translation into drawing and explicitly handling display cutouts fixed it. The regression test now checks a known masked face pixel.

## Review findings resolved

### Implementation review

- Private WebView suffix configured once per process, allowing repeated private-window launches.
- Tracker resets on capture-coordinate or browser-page generation changes.
- Capture target acquisition waits for confirmed captured-content visibility.
- Window alignment uses the largest application window for the target package, avoiding focused-dialog bounds.
- Overlay translation uses its actual post-layout screen location.

### Spec review

- Exported files are reopened, decoded, dimension-checked and published before source deletion is attempted.
- Untracked export regions receive image-selection IDs; custom image choices are stable per region.
- All imported assets remain visible in the pool manager.
- Browser image downloads carry destination-specific cookies and user agent, constrain redirects to HTTPS, and avoid sending cookies blindly across hosts.

## Not established by these tests

- Per-category recall/precision, moving-body coverage, rapid scrolling, every censor/effect setting combination, or every content type.
- Authenticated downloads against a real logged-in website; third-party site compatibility; every SAF provider's deletion semantics.
- OEM permission flows, all orientation/multi-window/dialog combinations, protected video or DRM behaviour.
- Sustained battery/thermal performance or latency guarantees on physical devices.
- Original-app binary/pack compatibility or exact achievement/effect parity.

See README.md for explicit feature gaps and installation instructions.

## Android 16 physical-device capture fix (0.1.1)

On a Samsung SM-S711B running Android 16, Reddit capture repeatedly recreated an unchanged 1024x2219 ImageReader: 73 new abandoned readers in three seconds. The accessibility service and MediaProjection were connected, but no inference frames completed. Duplicate resize callbacks now leave the existing capture surface intact.

The regression test failed before the fix and passed afterward. All eight emulator instrumentation tests, JVM tests, debug build, and lint passed. The updated APK was installed over the existing phone app, preserving its data. Physical-device retesting requires restarting protection after installation; successful Reddit masking is not yet confirmed.

The read-only `scripts/check-capture-buffer.py --serial SERIAL --adb PATH` checks buffer churn without capturing screen pixels.

## Mask text selection (0.1.2)

Replaced the text toggle with Yes / No / Part. Legacy boolean preferences and profiles migrate to the matching mode. Part uses the shared renderer for capture, browser, and exports, with detector confidence as a percentage; eye landmarks omit confidence because none is supplied. Reverse mode has no individual masked category, so Part adds no label there.

Debug APK build, JVM tests, lint, and a dedicated emulator check passed. The check covers legacy preference migration, profile round-trip, category/confidence labels, and differing rendered pixels for all three modes.

## Detection quality improvements (0.1.3)

Detailed (default) combines a full-frame pass with overlapping square crops (at most six extra passes). Fast keeps a single pass. Crop results map back into screen coordinates and same-category duplicates are merged; different categories no longer suppress each other. Capture width is selected by quality (640 Fast / 1024 Detailed), independently of scan frequency. Restart live protection after changing quality.

Eye inference waits at most 500 ms, retains its own bitmap until completion, and does not queue additional requests while busy. Failure or timeout preserves successful body detections. No stale eye coordinates are reused.

On the emulator, a public astronaut fixture inset into a 640x1400 canvas yielded face confidence 0.224 in Fast versus 0.706 in Detailed (31 vs 110 ms, one warmed measurement). This is a targeted regression example, not an accuracy benchmark or phone latency claim. The ten-test instrumentation suite, five JVM tests, build and lint passed; the quality test additionally checks that a pending eye task preserves body detections.

The model remains 320n. A larger model, calibrated per-category thresholds, and temporal persistence require broader labelled evaluation before selecting defaults. The previously reported 16 KB native-library compatibility warning remains unresolved in this build.
