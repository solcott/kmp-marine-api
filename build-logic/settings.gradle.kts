@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
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

dependencyResolutionManagement {
  repositories {
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

  versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"
