# Prototype validation

Date: 2026-09-21. Device environment: clean Android 15 / API 35 ARM64 emulator (`BodyBlock_Test_API35`). This is emulator evidence, not physical-phone certification or a detection-accuracy benchmark.

## Build and static checks

- `:app:assembleDebug`: successful, installable universal debug APK.
- `:app:testDebugUnitTest`: 3 tests covering output decoding, padding/coordinate mapping, confidence/non-finite rejection, duplicate suppression, and tracked encounter counting.
- `:app:lintDebug`: 0 errors; 19 warnings, primarily untranslated prototype UI strings and available test-library updates.
- `:app:assembleDebugAndroidTest`: successful.

## Instrumented checks

Seven device tests cover:

1. Bundled ONNX model detects a face in the NASA fixture; bundled ML Kit produces eye landmarks.
2. All eight effects produce opaque masks inside selected regions and transparent pixels outside; reverse mode preserves selected holes.
3. Exported PNGs retain masks when reopened; pixels outside the mask retain their source colour.
4. Profile packs round-trip; path-traversal ZIP entries are rejected.
5. Home, Settings, Export, Help and Browser navigation; private browser closes and reopens without crashing.
6. Actual single-app MediaProjection capture of a separate test-gallery APK, nonzero detections, correct selected package, a pixel assertion that the mask covers the known face location, touch-through operation, and clean stop.
7. Browser viewport detection of an embedded face fixture.

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
