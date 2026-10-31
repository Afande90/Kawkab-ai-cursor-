// Macrobenchmark for tab switching (task 23d). Runs on the CI emulator in the
// Instrumented workflow's benchmark job, never inside `check`.
//
// The target is the foss debug build: it is the build that installs on the
// x86_64 emulator (the release build ships arm64 only). Numbers from a debuggable
// build are slower than release, so a pass here is a conservative pass.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "app.kawkab.android.benchmark"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "foss")
        // The CI emulator and the debuggable foss build are both known compromises; see the header.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE,LOW-BATTERY,UNLOCKED"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}


// The benchmark installs the app's APK, which already contains its dependencies.
// Resolving them again here would pick their JVM variants, which the app never
// ships and the verification metadata does not cover, so the tested-APK
// configuration takes only the app project itself.
configurations.matching { it.name.endsWith("TestedApks") }.configureEach {
    isTransitive = false
}
