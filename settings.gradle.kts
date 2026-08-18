@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("build-logic")
  repositories {
    // Google first: its content filter claims all Android dependencies before the
    // other repositories are asked for them.
    google {
      content {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.google")
        includeGroupAndSubgroups("com.android")
      }
    }
    gradlePluginPortal()
    mavenCentral()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  // repositoriesMode is deliberately left at the default. The Kotlin JS/Wasm toolchain
  // registers its own project-level Ivy repositories for the Node.js and Yarn
  // distributions, which FAIL_ON_PROJECT_REPOS would reject.
  repositories {
    google {
      content {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.google")
        includeGroupAndSubgroups("com.android")
      }
    }
    mavenCentral()
  }
}

rootProject.name = "kmp-marine-api"

include(":marine-api")

include(":examples")

include(":examples-android")
