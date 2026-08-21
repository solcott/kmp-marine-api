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

// ---------------------------------------------------------------------------
// Dependency analysis exclusions
// ---------------------------------------------------------------------------
// Two pieces of buildHealth advice are artefacts of how KGP assembles a compile classpath rather
// than anything this build declares. They are excluded here, with the reason, so that the report
// stays a list of things worth acting on.
dependencyAnalysis {
  issues {
    all {
      // KGP puts kotlin-dom-api-compat on every js compilation itself. It is declared in no build
      // script here, so "unused, should be removed" points at nothing that can be removed.
      onUnusedDependencies { exclude("org.jetbrains.kotlin:kotlin-dom-api-compat") }
    }
    project(":marine-api") {
      // jvmTest imports kotlin.test only -- never org.junit. kotlin-test-junit is on the classpath
      // because `useJUnit()` makes kotlin-test resolve to it, and kotlin.test.Test is then a
      // typealias for org.junit.Test. Declaring it directly would pin the framework choice that
      // resolution currently makes, which is the failure mode where zero tests run and the build
      // still passes. See the jvmTest block in marine-api/build.gradle.kts.
      onUsedTransitiveDependencies { exclude("org.jetbrains.kotlin:kotlin-test-junit") }
    }
  }
}
