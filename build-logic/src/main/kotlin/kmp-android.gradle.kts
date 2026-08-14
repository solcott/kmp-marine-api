import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("project-config")
}

val libs: VersionCatalog = the<VersionCatalogsExtension>().named("libs")

kotlin {
  // The `android { }` block inside `kotlin { }` is the AGP 8.12+ DSL, replacing the older
  // `androidLibrary { }`. `androidTarget()` is deprecated and must not be used.
  android {
    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()
    minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(libs.findVersion("jvm-compat").get().requiredVersion)
    }
  }
}
