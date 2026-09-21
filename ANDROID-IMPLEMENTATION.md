# Android implementation approach

Research date: 2026-09-21. Proposal only; no application code added.

## Recommendation

Start with a native Kotlin Android application, Jetpack Compose for screens, and Android 15 as the minimum to match the reference scope. Keep one application module with ordinary packages until a concrete need for more modules appears. Use an on-device detector, shared detection/effect settings, and separate adapters for app capture, browser content, and exported images. No backend is needed for core functionality.

Model choice is provisional; see DETECTOR-RESEARCH.md for independently researched candidates and gaps. Benchmark an existing model before considering training.

## Prove the difficult path first

Build the first future experiment around one selected app, one category, and solid censor boxes on a physical phone. Establish capture, detection, touch behaviour, coordinate alignment, and lifecycle cleanup before implementing the full interface.

Android MediaProjection supplies captured content through a virtual display and surface. Use a mediaProjection foreground service, user consent, resize/visibility callbacks, and complete cleanup on stop. Single-app capture excludes system UI, so captured coordinates are not automatically screen coordinates. Resize callbacks give dimensions, not a complete solution for window placement. Initially constrain the experiment to fullscreen; explicitly test insets, rotation, keyboard, split-screen, and app switching before claiming wider support. [MediaProjection](https://developer.android.com/media/grow/media-projection), [app sharing](https://developer.android.com/about/versions/14/features/app-screen-sharing).

Evaluate application overlays against an explicitly enabled Accessibility overlay. Opaque application overlays can prevent touch pass-through under Android's untrusted-touch rules; FLAG_NOT_TOUCHABLE alone does not solve that. Accessibility windows have different trust treatment. Decide on permissions and overlay mechanism from physical-device tests, rather than assuming a full-screen transparent window with opaque drawings will preserve interaction. Any store distribution requires a separate review of the chosen Accessibility use. [Touch restrictions](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events).

Respect protected content: FLAG_SECURE can prevent capture, and apps can opt out of application overlays. Report unsupported operation rather than attempting to bypass these restrictions. [Protected activities](https://developer.android.com/security/fraud-prevention/activities).

## Detection and rendering loop

Proposed pipeline:

`Latest frame → resize/normalise → detector → category/confidence filters → tracking → coordinate mapping → renderer`

- Keep at most one pending frame; drop stale work rather than building latency through a queue.
- Begin with CPU inference as a reference; benchmark acceleration on actual target devices.
- Associate boxes between detections, smooth motion, add configurable padding, and use short expiry/hysteresis to limit flicker. Clear stale masks on scene or capture changes.
- Render on display ticks independently of inference rate. Smooth rendering does not mean a new detection every frame.
- Keep image choices and phrases stable per tracked region; avoid re-randomising every frame.
- Measure end-to-end appearance-to-mask latency, not just model inference time.

Solid boxes, images, text, tape, static, and popup graphics are straightforward drawing operations. Blur and pixelation require captured source pixels: process and draw region crops into the overlay. RenderEffect blurs its own RenderNode content; placing it on an empty transparent view does not supply pixels from another app. GPU rendering is a later optimisation if measurement warrants it. [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect).

Define reverse mode before building it: background treatment, visible holes, intensity, overlapping regions, and no-detection behaviour. It can share the renderer once those rules are known. Test that the capture excludes the app's own censor overlays; do not assume away feedback loops.

External-app protection is reactive: the pipeline sees content after rendering. It cannot guarantee that newly appearing content is never briefly visible. A controlled image/download pipeline can withhold its own output until processing finishes; arbitrary external apps cannot be delayed this way.

## Browser and exports

Start with Android WebView and native browser controls. Reuse detector and effect logic, but treat acquiring browser pixels and tracking scroll geometry as a separate integration task. Verify video, canvas, cross-origin frames, zoom, and rapidly changing layouts explicitly.

Request interception can help with images and ad blocking, but does not cover every content path: WebView's interception callback excludes blob and javascript URLs. Do not promise comprehensive filtering based on this callback alone. [WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient).

Use a separate disposable browsing profile where supported for incognito and test deletion of its cookies, storage, and history; clearing visible history alone is insufficient. Feature-check profile support. [ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore).

Process downloaded and batch-selected images at appropriate output resolution, then apply the same settings and render permanent outputs. Use Android's user-selected file access and output APIs. Preserve originals by default; verify a completed output before any explicitly requested deletion. Add progress and cancellation. Video export remains a discovery item in SPEC.md.

## Remaining product features

- Compose screens matching the reference navigation and configuration concepts.
- Local settings/profiles, initially using DataStore or versioned files as appropriate; a database only if structured history warrants it.
- Local imported images and versioned pack manifests. Native compatibility with the reference pack format requires a sample and format inspection.
- Event-based session and achievement counters. Define a block as a tracked encounter or another explicit rule, not an inference-frame count.
- Performance presets controlling input size, inference cadence, and effects based on measured tradeoffs.
- Localisation resources, Help, diagnostics, and reliable recovery from revoked permissions and stopped capture.

## Delivery sequence and gates

1. **Reference inventory:** inspect an authorised reference installation and record controls, defaults, effects, profiles, reverse mode, packs, and achievements.
2. **Feasibility:** capture one app, detect one category, draw solid masks, preserve expected interaction, and stop cleanly on real hardware.
3. **Detection coverage:** evaluate every category against labelled, appropriately sourced fixtures; record missing model classes separately from accuracy failures.
4. **Live engine:** add categories, tracking, profiles, all effects, reverse mode, and measured performance presets.
5. **Browser and exports:** verify content paths, permanent outputs, incognito isolation, and interruption recovery.
6. **Product parity:** add remaining dashboard, achievements, packs, localisation, and visual polish; close the reference comparison checklist.

The first useful deliverable should be a working live-engine feasibility result with latency, alignment, touch, and battery observations. That evidence determines whether the proposed architecture can support a faithful clone.
