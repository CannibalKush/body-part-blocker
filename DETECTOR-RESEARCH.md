# Android detector starting point

Research checked 2026-09-21. Proposed implementation, not evidence of the reference app's internals.

## Recommendation

Start a device benchmark with NudeNet's `320n.onnx` and ONNX Runtime Android. Keep inference behind a small detector interface so the model can be replaced if accuracy, licensing, or performance fails the project requirements. Do not embed the Python package in Android.

Upstream provides 320×320 YOLOv8n-derived and 640×640 YOLOv8m-derived ONNX models. The smaller model is the default. Benchmark the larger model for still-image exports only after measuring it; its presence does not establish mobile real-time suitability. [Upstream README](https://raw.githubusercontent.com/notAI-tech/NudeNet/v3/README.md)

## Category coverage

The upstream label table covers all 18 distinct body/face classes shown in the supplied screenshot: exposed female and male genital regions; covered female genital region; exposed and covered female breasts, buttocks, anus, belly, feet and armpits; exposed male chest; and male/female face labels. **Covered anus is present**, despite its absence from some older descriptions of NudeNet. [Current label table](https://raw.githubusercontent.com/notAI-tech/NudeNet/v3/nudenet/nudenet.py)

“All faces” can aggregate both face labels. “Eyes” is not a NudeNet class and needs a second detector, such as bundled ML Kit face landmarks/contours, enabled only when required. Validate eye geometry at small sizes and rotated/profile faces; face classification labels are fallible model outputs. [ML Kit Android face detection](https://developers.google.com/ml-kit/vision/face-detection/android)

Category names matching does not establish accuracy or prove the reference app uses NudeNet. Evaluate photographs, animation, small regions, multiple people, occlusion and motion separately.

## Runtime and frame processing

Use the official `onnxruntime-android` package and a persistent inference session. ONNX Runtime recommends starting non-quantized models with XNNPACK; quantized models should start with CPU. Measure complete capture-to-overlay latency, memory, power and thermal behaviour on physical devices. Use the mobile usability checker before selecting execution providers. [Mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/), [model helpers](https://onnxruntime.ai/docs/tutorials/mobile/helpers/)

Do not make NNAPI the default acceleration strategy: Android deprecated it in Android 15 and warns that future devices may primarily use its CPU backend. Use CPU/XNNPACK first; investigate supported GPU/NPU paths only when benchmarks justify the complexity. [Android NNAPI documentation](https://developer.android.com/ndk/guides/neuralnetworks)

Match the upstream preprocessing and decode exactly before optimising: square padding, resizing, channel ordering, normalisation, confidence filtering, non-maximum suppression and reverse coordinate mapping. Validate Android outputs against fixed upstream fixtures. Then add per-class thresholds, box padding and short-lived tracking to reduce flicker. Never queue stale frames: process the latest available frame and discard superseded work. These tracking and scheduling choices are proposed design decisions.

## Licensing decision before distribution

The NudeNet repository currently contains an AGPL-3.0 license, and its README identifies Ultralytics YOLOv8 ancestry. Treat it as a licensing decision, not a permissively licensed drop-in dependency. Verify the exact selected weights' redistribution terms and provenance before shipping; the inspected README and repository license alone do not fully resolve all weight and training-data questions. If those terms do not fit the intended distribution, select separately licensed weights or train a replacement. [NudeNet license](https://raw.githubusercontent.com/notAI-tech/NudeNet/v3/LICENSE), [model ancestry](https://raw.githubusercontent.com/notAI-tech/NudeNet/v3/README.md)

## First acceptance gate

- Confirm category mapping and fixed-image output equivalence.
- Measure per-class misses and false positives against labelled representative images.
- Measure median and p95 capture-to-mask latency during scrolling and video.
- Measure 20-minute sustained operation on at least one mid-range and one high-end phone.
- Quantify new-content exposure time and tracker lag rather than promising perfect blocking.
- Select the model/runtime only after those results and the distribution terms are known.
