# BodyBlock — Android prototype

A working, independent Android prototype of configurable body-region censorship, based on the feature inventory in [SPEC.md](SPEC.md). Android 15+; Kotlin, native Android views, ONNX Runtime and bundled ML Kit face landmarks. No account or backend.

## Install

The local deliverable is `artifacts/BodyBlock-0.1-prototype.apk` (debug signed, for evaluation). Enable installation from your file manager if Android asks. Do not install the separate test APK on your phone.

1. Open **Settings** in BodyBlock and choose categories and appearance.
2. Tap **Start protection**. Follow the explanation to enable the **BodyBlock Accessibility service**. On some devices a sideloaded app first needs **App info → menu → Allow restricted settings**.
3. Return to BodyBlock, tap **Start protection**, then choose **A single app** in Android's capture prompt and select your gallery or other app.
4. Keep the selected app fullscreen. Stop using the app's Home control or the notification.
5. Use **Browser** for web content, **Export** for permanent image outputs, and **Help** for private browsing and limitations.

The Accessibility service draws masks and reads window geometry. It does not dispatch taps or gestures. Captured frames are processed in memory; they are not saved by the live engine. A visible foreground notification accompanies capture.

## Implemented

| Area | Prototype behaviour |
| --- | --- |
| Detection | All 18 NudeNet body/face categories; all-faces shortcut; bundled eye landmarks; confidence and padding controls |
| Live app mode | MediaProjection single-app capture, opaque Accessibility overlays, category filtering, tracking, calibration, resize/visibility handling, notification stop |
| Effects | Solid, fast blur, pixelation, custom images, static, glitch, tape, error popup; colours, borders, text and animation |
| Reverse mode | Obscure the background and leave selected detected regions visible; strength control; no detection covers the whole content area |
| Profiles | Named configurations, save/load/delete, custom image pool, phrase sets and timing |
| Packs | BodyBlock JSON + image ZIP import/export with version, path, entry-count and decompression-size validation |
| Browser | Up to eight tabs, bookmarks, private window, basic ad-domain blocking, live viewport filtering, censored HTTPS image downloads with destination cookies |
| Images | Multi-select and immediate-child folder export, permanent PNG masks, cancellation and per-image errors; optional source deletion only after output decoding and publication succeed |
| Dashboard | Session timer, encountered-region count, lifetime statistics, progress and eight local achievement milestones |
| Performance | Four capture-size/scan-cadence presets and browser diagnostics; bundled 320-pixel detector |

“Blocks” counts new tracked regions, not frames. A new region after a disappearance counts again. Statistics are a prototype approximation of the original's unpublished rules.

## Important differences and limits

- This is a functional prototype, not a pixel-perfect reproduction or an official Beta Blocker app. Original branding, artwork and text are not bundled.
- The original app has not been installed or exhaustively compared. Its pack format, full achievement catalogue and precise reverse/effect semantics remain unverified. Our packs are not advertised as compatible.
- UI is English. Full localisation, recording/video export and the original seasonal achievements are not implemented.
- Detection is reactive and fallible. A new image can appear before its mask; there is no zero-exposure guarantee. No broad category-accuracy benchmark has been performed.
- Android-protected content can be uncapturable. Fullscreen capture is the tested geometry. OEM behaviour, split-screen, floating windows, keyboards, and rotation need broader physical-device validation.
- Browser filtering captures the WebView viewport. DRM, hardware video surfaces, canvas, animations and rapidly changing pages are not guaranteed. “Censor visible images now” requests another viewport scan, not a complete DOM image rewrite.
- The browser ad blocker is a short domain list, not a full filter-list engine. Non-HTTP(S) navigation is blocked. HTTPS-only saved-image fetching does not support blob/data image downloads or every authentication scheme.
- Private browsing uses an isolated WebView data directory and clears cookies, cache, storage and history on exit and reopening. It is not forensic erasure; a crash can leave private-process storage until the next private session cleanup.
- Blur uses filtered downsampling, not the original app's unknown blur kernel. Overlay animation updates at detection cadence, not an independent 60 FPS renderer.
- Presets adjust capture dimensions and cadence; all use the same 320n model. Restart live capture after changing capture dimensions. Inference runs on CPU with two threads.
- Exports cap the longest edge at 4096 pixels and omit metadata. Animated images become stills. Replacement can therefore discard source resolution/metadata/animation; it is off by default and explicitly confirmed. Providers may refuse deletion, in which case the original is retained.
- Export work is cancelled when its Activity is destroyed; it is not a resumable background job. Slow download networking uses a separate executor so it does not block live browser inference.
- No app-authored analytics or frame uploading is added. Third-party SDK telemetry and browser network behaviour have not undergone a network audit.
- Physical-device battery, heat, latency and long-session stability are unverified.

## Build

Prerequisites: JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` containing `sdk.dir=/your/android/sdk`.

```sh
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. The universal APK includes native libraries for multiple ABIs and bundled models, so it is relatively large. Release signing is intentionally not configured.

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb -s YOUR_TEST_EMULATOR install -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_TEST_EMULATOR install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_TEST_EMULATOR shell pm grant dev.bodyblock.prototype android.permission.POST_NOTIFICATIONS
adb -s YOUR_TEST_EMULATOR shell am instrument -w dev.bodyblock.prototype.test/androidx.test.runner.AndroidJUnitRunner
```

Run device tests only on a disposable test emulator: the live-flow test enables this application's Accessibility service there. The test APK contains a separate gallery with a public NASA image to exercise actual cross-app capture, alignment and touch-through behaviour. Screenshots are written to the target application's external files directory.

See [VALIDATION.md](VALIDATION.md) for the observed results and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependencies and test-image attribution.
