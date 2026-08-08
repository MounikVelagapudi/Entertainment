import org.gradle.kotlin.dsl.implementation
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val keysProperties = Properties().apply {
    val keysFile = rootProject.file("keys.properties")
    if (keysFile.exists()) {
        keysFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.entertainment"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.entertainment"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MOVIE_DB_BASE_URL", "\"https://api.themoviedb.org/\"")
        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"${keysProperties.getProperty("TMDB_API_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Compose Compiler Metrics & Reports.
// Kotlin 2.x folds the Compose compiler into the Kotlin plugin itself (the
// `kotlin.plugin.compose` plugin we apply above), so metrics are configured
// through this `composeCompiler {}` extension rather than the old
// `freeCompilerArgs += "-P plugin:androidx.compose.compiler..."` hack you'll
// still see in older tutorials/StackOverflow answers.
//
// Run a build (e.g. `./gradlew :app:assembleDebug`) and inspect:
//   app/build/compose_reports/*-composables.csv  -> every @Composable, and whether it's
//                                                     "restartable"/"skippable" or not
//   app/build/compose_reports/*-composables.txt  -> same info, human readable
//   app/build/compose_metrics/*-classes.txt      -> stability verdict for YOUR classes
//                                                     (e.g. MediaUIModel, MediaResults)
//
// What to look for: a class you assumed was stable showing up as "unstable" in
// classes.txt, or a composable showing "skippable: false". Both mean Compose
// can't skip recomposition for it even when its inputs haven't changed.
composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature:tabs:tmdb:popular"))
    implementation(project(":feature:tabs:tmdb:toprated"))
    implementation(project(":feature:tabs:tmdb:trending"))
    implementation(project(":feature:tabs:tmdb:presentation"))
    implementation(project(":feature:tabs:more"))
    implementation(project(":feature:tabs:profile"))
    implementation(project(":feature:tabs:favourite"))
    implementation(project(":feature:tabs:search"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.firebase.common.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)

    // Jetpack JankStats: measures real dropped frames against the device's
    // ACTUAL refresh rate (60Hz/90Hz/120Hz), unlike our hand-rolled 16ms
    // heuristic in ComposePerformanceMonitoring.kt.
    implementation(libs.androidx.metrics.performance)

    // Firebase Performance Monitoring -- destination for jank/frame metrics
    // once we forward them here instead of just Log.w. The BoM pins every
    // Firebase artifact's version together so we don't have to track them
    // individually.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.perf)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}