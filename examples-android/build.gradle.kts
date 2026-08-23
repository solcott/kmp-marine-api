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
  // Navigation 3 saves its back stack by serializing the route keys, so the keys are
  // @Serializable and the serialization compiler plugin has to be here to generate for them.
  // Like the Compose plugin, this is a Kotlin compiler plugin and attaches to AGP's built-in
  // Kotlin without org.jetbrains.kotlin.android.
  alias(libs.plugins.kotlin.serialization)
  // Hilt's processor runs under KSP rather than kapt: with built-in Kotlin there is no
  // kotlin-kapt to apply, and Hilt's Gradle plugin does not work with com.android.legacy-kapt.
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
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

  // The ViewModel tests are plain JVM tests, and android.util.Log is one of the framework classes
  // that throws "not mocked" in one. Returning defaults turns the two Log calls in GpsViewModel
  // into no-ops instead of pulling Robolectric in for them.
  testOptions { unitTests { isReturnDefaultValues = true } }

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
  // Compose is split across many small artifacts, and the ones a file imports from are rarely the
  // ones it declares: `ui` alone does not carry Modifier's `dp`, Color or FontFamily. Each artifact
  // this module actually imports is listed, so a transitive re-shuffle upstream cannot silently
  // take one away. `activity` is here for ComponentActivity and enableEdgeToEdge, which arrive via
  // activity-compose but are not its API.
  implementation(libs.androidx.activity)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.runtime.saveable)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.ui.unit)
  // hilt-lifecycle-viewmodel-compose, not hilt-navigation-compose: hiltViewModel() moved out of
  // the navigation artifact, and the one it left behind drags Navigation 2 in behind it, which is
  // not what an app built on Navigation 3 wants on its classpath.
  implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.common)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  // dagger, hilt-core and javax.inject are what the annotations in di/ and on the ViewModels
  // actually come from; hilt-android is the runtime that reads them. All four are named for the
  // same reason every Compose artifact is: an upstream re-shuffle should not be able to take one
  // away silently.
  implementation(libs.dagger)
  implementation(libs.hilt.android)
  implementation(libs.hilt.core)
  implementation(libs.javax.inject)
  implementation(libs.kotlinx.coroutines.core)
  // Fix.dateTime and Fix.time are kotlinx-datetime types, reached through :marine-api's `api`
  // dependency. Declared directly because this module names them, not because it adds anything.
  implementation(libs.kotlinx.datetime)
  implementation(libs.kotlinx.io.core)
  implementation(libs.kotlinx.serialization.core)

  // Powers the @Preview rendering and the layout inspector; debug-only so it stays out of release.
  debugImplementation(libs.androidx.compose.ui.tooling)

  // The ViewModels are constructed directly from fakes, so these tests need neither Hilt nor
  // Robolectric -- which is the whole reason the repository interfaces speak in Strings rather
  // than in Uri and BluetoothDevice.
  // JUnit 4 is the runner AGP's unit tests use, and kotlin-test-junit is named rather than left to
  // variant resolution because there is no `useJUnit()` here to make that choice -- resolution
  // lands on the frameworkless variant and `kotlin.test.Test` does not exist. The failure is at
  // least loud, unlike :marine-api's, where getting the same choice half right runs zero tests and
  // still reports success.
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  ksp(libs.hilt.compiler)
}
