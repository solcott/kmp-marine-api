plugins {
  // Applied by id without a version: AGP is already on the build script classpath, put there by
  // build-logic's dependency on it. Requesting a version here fails with "already on the classpath
  // with an unknown version".
  //
  // There is no kotlin-android plugin: AGP 9 has built-in Kotlin support and REJECTS
  // org.jetbrains.kotlin.android outright.
  id("com.android.application")
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

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.compat.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.compat.get())
  }
}

dependencies {
  implementation(project(":marine-api"))
  // activity for ComponentActivity and registerForActivityResult. NOT activity-ktx: since 1.13.0
  // that artifact is an empty shim that adds nothing but a dependency on `activity` and three
  // legacy -ktx transitives (lifecycle-runtime-ktx, lifecycle-viewmodel-ktx, savedstate-ktx),
  // none of which this example uses. No Compose either: this repository uses none, and adding it
  // would make the example about Compose rather than about reading NMEA.
  implementation(libs.androidx.activity)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.io.core)
}
