plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.bodyblock.prototype"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.bodyblock.prototype"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-prototype"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "onnx" }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
