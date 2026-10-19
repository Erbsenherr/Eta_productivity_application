import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * The minute this build was made, `2026-10-19-1412`: its `versionName`, the name
 * of its APK in `dist/`, and the tag of its release on GitHub, against which the
 * app's "App-Update" page compares itself.
 *
 * `tools/build-apk.ps1` hands it in (`-PetaStamp=…`) so the three agree. Without
 * one it is the current minute, read through a ValueSource so the configuration
 * cache asks again every build instead of keeping the first one's.
 */
abstract class CurrentStamp : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
}

val buildStamp: String = providers.gradleProperty("etaStamp")
    .orElse(providers.of(CurrentStamp::class) {})
    .get()

/**
 * The stamp as the minutes since 2026, plus a thousand to stay clear of the
 * `versionCode = 1` every build carried before. Android only needs it to grow.
 */
fun versionCodeOf(stamp: String): Int {
    val built = LocalDateTime.parse(stamp, DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
    val base = LocalDateTime.of(2026, 1, 1, 0, 0)
    return 1000 + Duration.between(base, built).toMinutes().toInt()
}

android {
    namespace = "com.example.eta"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app was called ERIK until the rename to Eta. The id keeps the old
        // name on purpose: a new one would install as a second, empty app.
        applicationId = "com.example.erik_iteration_2"
        minSdk = 24
        targetSdk = 37
        // The stamp of the build — see `buildStamp` below and `tools/build-apk.ps1`.
        versionCode = versionCodeOf(buildStamp)
        versionName = buildStamp

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // kotlinx-datetime resolves to java.time, which minSdk 24 lacks natively.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Exported schemas are the input for Room migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}