plugins {
  // Applied by id without a version: AGP is already on the build script classpath, put there by
  // build-logic's dependency on it. Requesting a version here fails with "already on the classpath
  // with an unknown version".
  //
  // There is no kotlin-android plugin: AGP 9 has built-in Kotlin support and REJECTS
  // org.jetbrains.kotlin.android outright.
  id("com.android.application")
  // The Compose compiler is a Kotlin compiler plugin, so it is versioned with Kotlin rather than
  // with the Compose libraries. It attaches to whatever compiles Kotlin here, which under AGP 9 is
  // AGP's built-in support rather than org.jetbrains.kotlin.android.
  alias(libs.plugins.kotlin.compose)
  id("project-config")
  alias(libs.plugins.sort.dependencies)
}

// The one module in this build that is not Kotlin Multiplatform. An Android application cannot be a
// target of a KMP library module -- it needs an Activity, a manifest and an APK -- so it is a plain
// AGP project that consumes :marine-api's `android` target through Gradle module metadata, exactly
// as an external consumer would.
//
// Deliberately never published.
android {
  namespace = "io.github.solcott.marineapi.example.android"
  compileSdk = libs.versions.androidCompileSdk.get().toInt()

  defaultConfig {
    applicationId = "io.github.solcott.marineapi.example.android"
    minSdk = libs.versions.androidMinSdk.get().toInt()
    targetSdk = libs.versions.androidCompileSdk.get().toInt()
    versionCode = 1
    versionName = project.version.toString()
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.compat.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.compat.get())
  }
}

dependencies {
  // The BOM sets every androidx.compose.* version, which is why those entries in the catalog
  // carry no version of their own.
  implementation(platform(libs.androidx.compose.bom))
  implementation(project(":marine-api"))
  // Brings plain `activity` with it, so ComponentActivity, enableEdgeToEdge and
  // rememberLauncherForActivityResult all come from this one line.
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.io.core)

  // Powers the @Preview rendering and the layout inspector; debug-only so it stays out of release.
  debugImplementation(libs.androidx.compose.ui.tooling)
}
