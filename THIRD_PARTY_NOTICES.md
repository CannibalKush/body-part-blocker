# Third-party components

## NudeNet detector

- Upstream: https://github.com/notAI-tech/NudeNet/tree/v3
- Packaged model: `320n.onnx`, extracted unchanged from the `nudenet==3.4.2` wheel distributed on PyPI.
- SHA-256: `c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f`
- Upstream repository licence: GNU Affero General Public License v3. The full text is in `LICENSE`; this prototype's application source is provided under AGPL-3.0-or-later.
- Architecture and labels originate from NudeNet's YOLOv8-based detector. The Android input/output adapter follows its public preprocessing and label definitions.
- Before publishing commercially, review the exact model-weight provenance and redistribution terms, including its upstream model ancestry; the repository licence alone does not resolve all training-data questions.

## Runtime dependencies

- ONNX Runtime Android 1.20.0: Microsoft, MIT licence. https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- Kotlin standard library: JetBrains, Apache 2.0. https://github.com/JetBrains/kotlin
- ML Kit face detection 16.1.7: Google SDK terms apply. Bundled model, no first-use model download required. https://developers.google.com/ml-kit/terms and https://developers.google.com/ml-kit/vision/face-detection/android
- AndroidX test libraries: Apache 2.0; test APK only. https://android.googlesource.com/platform/frameworks/support/
- JUnit 4: Eclipse Public License 1.0; local tests only. https://github.com/junit-team/junit4
- Gradle wrapper: Apache 2.0. https://github.com/gradle/gradle

## Test image

`app/src/androidTest/assets/astronaut.png` is the NASA photograph of astronaut Eileen Collins distributed as scikit-image's public-domain astronaut example. It is included only in the test APK, not the application APK.

- Source: https://github.com/scikit-image/scikit-image/blob/v0.25.2/skimage/data/astronaut.png
- Description and attribution: https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut

No original Beta Blocker artwork, APK, code, or proprietary assets are included.
