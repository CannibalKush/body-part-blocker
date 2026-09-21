# Body Part Blocker — Android product specification

Status: specification only; no implementation started.
Date: 2026-09-21.

## Objective and scope

Build an Android app that reproduces the functionality of Beta Blocker Android: identify user-selected body regions in visual content and obscure them in real time, with configurable effects, an integrated browser, image export, profiles, and local session statistics.

The target is functional parity with the Android edition, including documented updates through version 1.67. The supplied Android screenshot shows version 1.0. Implement independently; source code, branding, artwork, and model assets are not supplied. Desktop-only features are outside the initial scope unless later confirmed as Android features.

## Evidence and uncertainty

- **Documented:** described by the developer on the product page or Android release notes; not independently tested.
- **Screenshot:** visible in the two screenshots supplied by the user; the body-category screen's exact Android parity is unconfirmed.
- **Proposed:** a requirement for this implementation, not a verified property of the reference app.
- **Unresolved:** requires inspection, testing, or a later product decision. Do not silently treat it as confirmed behaviour.

The supplied YouTube demo could not be accessed during research. No APK, source code, or runtime behaviour has been inspected. This document covers known features and explicitly records gaps; it is not proof of exhaustive parity.

## Main user flow

1. Open Home and select or configure a profile.
2. Choose detection categories and censor appearance in Settings.
3. Tap Start Protection and complete the necessary Android permission and capture prompts.
4. Select one target app using One App capture mode.
5. Detect matching regions and keep censor overlays aligned as content changes.
6. Display protection state and session statistics.
7. Stop protection from an obvious control and release capture resources.

For web content, open the integrated Browser. For permanent output, use image export or save a censored browser image.

## Functional requirements

### 1. Live app protection

- Process the selected app's visible content, including images and video frames, on-device.
- Support the intended social-media and gallery use cases, subject to Android capture restrictions and measured compatibility.
- Cover all detected regions whose categories are enabled, including multiple simultaneous regions.
- Track regions between detections; smooth motion and reduce flicker during scrolling, movement, and size changes.
- Correctly transform detection coordinates for captured content size, display size, orientation, and aspect ratio.
- Use One App mode as the documented operating mode; the reference developer warns that other capture modes may be glitchy.
- Provide active, stopped, permission-required, and error states. These explicit state definitions are proposed.
- Android Accessibility support is mentioned in the reference release notes for stronger One App coverage. Its exact role and required permissions remain unresolved.

### 2. Detection categories

Provide independent toggles for the following screenshot categories. These are intended parity targets, pending confirmation of Android availability and model support.

| Group | Categories |
| --- | --- |
| Exposed intimate regions | Female genitals; male genitals; female breasts; buttocks; anus |
| Covered intimate regions | Female genitals covered; breasts covered; buttocks covered; anus covered |
| Faces and eyes | All faces; female face; male face; eyes |
| Other body regions | Belly/stomach; belly covered; male chest; feet; feet covered; armpits; armpits covered |

Category names describe reference UI labels, not verified classification accuracy. Define overlap behaviour for All Faces and face subcategories before implementation. The meaning and availability of covered-region classes require validation.

### 3. Censor appearance

Documented styles:

- Solid box.
- Blur and pixelation, each with a 1–100 intensity control.
- Custom images: import an image pool, enable individual images, and randomly select an active image for each detected region; size and clip it to the region.
- Static, glitch, tape, and error-popup effects.

Support configurable colours, border effects, animated effects, text, phrase categories, custom phrases, and text-change timing. Exact effect lists, text rules, parameter ranges, and randomisation persistence are unresolved.

Apply configured styles consistently across live protection, browser content, and image exports where documented. Measure performance impact: the reference describes solid boxes as cheaper than blur, pixelation, and custom images.

### 4. Reverse censoring

- Provide a Reverse Censor toggle and Reverse Strength slider.
- Show setup guidance when enabling the feature, without repeatedly interrupting subsequent use.
- Support reverse-mode effects, including the documented full-screen glitch behaviour.
- Resolve the precise mask semantics before implementation: which regions remain visible, how the background is obscured, how strength operates, and what happens with zero detections. The available documentation does not specify these rules fully.

### 5. Profiles and configuration packs

- Create, name, select, save, and delete profiles.
- Store detection categories, censor style and intensity, border effect, colour, enabled phrase categories, custom phrases, and custom images with a profile.
- Persist profiles and restore their complete configuration after restarting the app.
- Support configuration packs containing Android settings, as referenced by the release notes.
- Pack schema, import/export workflow, asset packaging, version compatibility, and compatibility with the separate Pack Creator tool remain unresolved.

### 6. Integrated browser

- Navigation, tabs, bookmarks, incognito mode, and ad blocking.
- Apply the active detection and appearance configuration to browser content.
- Refresh overlays during scrolling, page changes, and tab switches.
- Provide Censor All Images: process every visible image on the current page through the detection/censor pipeline. This does not establish that every image must be fully obscured regardless of detections.
- Save downloaded images with censorship permanently applied before writing the output to the gallery.
- Support custom phrases and phrase categories in browser overlays.
- Keep browser navigation controls usable while overlays are active.
- Define incognito storage behaviour, supported download formats, video coverage, and ad-blocking rules before implementation.

### 7. Batch image export

- Select a folder or collection of images and process them with the active censor configuration.
- Use the same detection categories and effects as live mode and permanently render censorship into output images.
- Offer an optional replace-originals mode. Reference behaviour deletes originals permanently rather than moving them to a recycle bin.
- Proposed: default to preserving originals, explicitly explain destructive replacement, and delete an original only after its output has been successfully written and validated.
- Proposed: show progress, cancellation, successful outputs, and per-file failures.
- Supported formats, metadata handling, output naming, animation support, and interruption recovery remain unresolved.
- Android video export and recording are not established as complete workflows by the reviewed documentation. Track them as a parity investigation item rather than promising desktop-equivalent export.

### 8. Dashboard and achievements

- Home, Settings, Browser, and Help navigation, as shown in the Android screenshot.
- Start/Stop Protection control and clear protection status.
- Session duration, block count, current detection status, achievement badges, and next-milestone progress.
- Persistent achievement progression covering blocks, sessions, duration, streaks, peak detections, customisation, profiles, browser use, exports, and hidden seasonal triggers.
- The release notes describe 38 additional achievements; the full catalogue and unlock conditions are not published in the reviewed material.
- Define what counts as one block, when a session starts/ends, and how streaks and repeated detections are counted.

### 9. Performance controls

- Low, Medium, High, and Ultra presets balancing detection quality, smoothness, and battery use.
- Optional diagnostics for inference time, FPS, execution provider, numeric precision, and active box count.
- Tracking, display-synchronised overlay interpolation, and idle throttling.
- Benchmark detection latency, overlay alignment, sustained frame rate, memory, battery consumption, and thermal behaviour on representative devices.
- Concrete preset parameters and performance targets remain to be set from measurements. Do not claim zero latency or perfect detection.

### 10. Privacy, lifecycle, and localisation

- Run detection and image processing locally; filtering must work without network access.
- Do not upload captured frames or collect telemetry by default. The reference advertises zero data collection; this has not been independently verified.
- Browser access naturally requires networking; keep that separate from local inference.
- Proposed: keep transient captured frames in memory and avoid retaining them after processing.
- Stop capture and overlays cleanly on user request, permission revocation, or unrecoverable errors.
- Support translated UI, settings, and tooltips. Documented languages include English, French, Spanish, Portuguese, German, Japanese, Korean, Russian, Simplified Chinese, and Traditional Chinese.
- Provide Help covering setup, permissions, capture mode, performance choices, export behaviour, and troubleshooting.

## Platform and proposed architecture

Reference requirements: Android 15 or newer, at least 4 GB RAM, and a mid-range processor or better; a processor from 2020 onward is recommended.

Proposed processing flow:

`Capture → on-device detection → category filtering → region tracking → effect rendering → overlay`

Proposed components:

- Android capture and protection lifecycle.
- Detector/model runtime and category mapping.
- Region tracker and coordinate transforms.
- Shared effect renderer for live, browser, and saved-image paths.
- Browser and download processing.
- Batch export worker.
- Local profile, asset, settings, statistics, and achievement storage.
- Home, Settings, Browser, Export, and Help interfaces.

No language, framework, ML model, inference runtime, browser engine, database, or dependency has been selected. Investigate Android capture/overlay restrictions and suitable model licences before choosing them. Do not assume unrestricted compatibility with every app or protected content.

## Acceptance criteria

1. Every implemented category can be toggled independently and affects live, browser, and image-export results consistently.
2. Multiple regions remain covered and correctly positioned during scrolling, movement, rotation, and resizing, within measured tolerances.
3. Every documented style and profile setting survives saving, switching profiles, and restarting.
4. Browser tabs, bookmarks, incognito behaviour, ad blocking, and censored image saving work as specified.
5. Batch output contains permanent censorship; cancellation and failures do not destroy unprocessed originals.
6. Reverse censoring passes explicitly agreed mask and zero-detection cases.
7. Start, Stop, permission denial, permission revocation, and app lifecycle transitions leave no orphaned capture or overlay session.
8. Filtering and export work offline, with network inspection confirming no frame uploads or telemetry.
9. Statistics and achievement counters follow documented counting rules and persist correctly.
10. Performance presets show measured tradeoffs on supported devices; detection misses, false positives, latency, and battery use are reported honestly.
11. The final parity review resolves or explicitly defers every unresolved item below.

## Outstanding parity investigation

- Inspect the demo and Android settings screens to confirm complete category and effect lists.
- Verify capture permissions, Accessibility usage, lifecycle behaviour, and app compatibility.
- Identify the detector, confidence behaviour, category definitions, and coverage for photographs, illustrations, and animation.
- Establish reverse-censor semantics, including no detections and overlapping regions.
- Document profile defaults, pack format, and Pack Creator interoperability.
- Inventory all achievements and exact counting/unlock rules.
- Confirm recording, video export, animated-image support, and any features added after the reviewed release notes.
- Establish measurable latency, quality, alignment, battery, and thermal acceptance thresholds.

## Sources

Reviewed on 2026-09-21:

- [Android product page](https://isla2d.itch.io/beta-blocker-mobile).
- [Styles and localisation update](https://isla2d.itch.io/beta-blocker-mobile/devlog/1473134/devlog-1-new-languages-censor-styles-bug-fixes).
- [Batch export, profiles, custom images, achievements, and performance](https://isla2d.itch.io/beta-blocker-mobile/devlog/1501242/devlog-2-new-export-mode-custom-images-performance-increases-more).
- [Reverse censoring, new styles, Accessibility, and packs](https://isla2d.itch.io/beta-blocker-mobile/devlog/1551062/devlog-2-reverse-censoring-comes-to-android).
- [Version 1.67: replacement exports and overlay fixes](https://isla2d.itch.io/beta-blocker-mobile/devlog/1581068/devlog-3).
- User-supplied screenshots: `1000134291.jpg` (body-category selection) and `1000134290.jpg` (Android Home).
- [User-supplied demo](https://m.youtube.com/watch?v=XgUJnbRxx9E), not accessible during research.

## Implementation status

Specification only. No application code, dependencies, build configuration, remote repository, or implementation commitments are included.
