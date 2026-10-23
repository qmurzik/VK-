plugins {
    id("com.android.application")
}

android {
    namespace = "com.qmurzik.vkenhancer"
    // libxposed API 102 requires compiling against Android 17 (SDK 37). The base platform ships as
    // "android-37.0", so the minor version is pinned to resolve it without an android-37 alias.
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.qmurzik.vkenhancer"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            // Hooks are resolved reflectively at runtime, so shrinking buys little and risks breakage.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        // DexKit's native library must be loadable straight from the module APK.
        jniLibs.useLegacyPackaging = false
        resources.excludes += setOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    // The container ships a JDK 21 only; compile to 17 bytecode with it rather than requiring a 17 toolchain.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("org.luckypray:dexkit:2.3.0")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
