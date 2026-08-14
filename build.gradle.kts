plugins {
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.multiplatform.library) apply false
  alias(libs.plugins.publish) apply false
  alias(libs.plugins.sort.dependencies)
  // Applies and configures ktfmt for every project; see build-logic/.../project-config.gradle.kts
  id("project-config")
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
  languageVersion = JavaLanguageVersion.of(libs.versions.jvm.toolchain.get())
  vendor = JvmVendorSpec.ADOPTIUM
}
