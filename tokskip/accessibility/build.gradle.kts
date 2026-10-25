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
    testImplementation("junit:junit:4.13.2")
}
