plugins {
    id("com.android.library")
}

android {
    namespace = "com.qmurzik.tokskip.accessibility"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    // Offline OCR of the video frames (burned-in subtitles, ad banners). Russian + English
    // tessdata_fast models are bundled in assets; nothing is downloaded at runtime.
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android-openmp:4.8.0")
    testImplementation("junit:junit:4.13.2")
}
