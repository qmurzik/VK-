plugins {
    id("com.android.library")
}

android {
    namespace = "com.qmurzik.tokskip.xposed"
    // libxposed API 102 must be compiled against Android 17 (SDK 37).
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
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("org.luckypray:dexkit:2.3.0")
    testImplementation("junit:junit:4.13.2")
}
