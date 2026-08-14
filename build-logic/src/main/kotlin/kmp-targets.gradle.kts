@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import com.android.build.api.withAndroid
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("project-config")
}

kotlin {
  jvm()

  // js and wasmJs must declare an environment or configuration fails outright.
  js {
    outputModuleName = project.name
    browser()
    nodejs()
  }
  wasmJs {
    outputModuleName = "${project.name}-wasm"
    browser()
    nodejs()
  }
  // wasmWasi supports nodejs() only -- there is no browser() for it.
  wasmWasi { nodejs() }

  // arm64 only; the x64 Apple variants are deprecated.
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  sourceSets {
    applyDefaultHierarchyTemplate {
      common {
        group("browserCommon") {
          withJs()
          withWasmJs()
        }
        group("commonJvm") {
          withJvm()
          @Suppress("UnstableApiUsage") withAndroid()
        }
        group("nonAndroid") {
          withJvm()
          withJs()
          withWasmJs()
          withWasmWasi()
          withNative()
        }
      }
    }
  }
}
