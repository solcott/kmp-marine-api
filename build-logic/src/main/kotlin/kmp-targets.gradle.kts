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
  wasmWasi {
    wasmtime()
    nodejs()
  }

  // arm64 only; the x64 Apple variants are deprecated.
  iosArm64()
  iosSimulatorArm64()
  macosArm64()

  // Desktop and server Kotlin/Native. Of everything below, linuxX64 and mingwX64 are the only two
  // that can run the common tests at all, and each only on its own OS -- KGP gives them a
  // KotlinNativeTargetWithHostTests, while the rest return a plain KotlinNativeTarget, which has no
  // test run on any host. That is the type's doing, not an omission here: do not go looking for a
  // missing linuxArm64Test. All of them are still compiled and linked everywhere, so a break shows
  // up as a build failure rather than as a silent gap.
  linuxX64()
  linuxArm64()
  mingwX64()

  // Kotlin/Native on Android without a JVM -- NDK binaries, not the `android` target, which is the
  // ordinary JVM-on-Android one and comes from the kmp-android convention plugin. All four,
  // including the 32-bit pair: they cost four lines and no source, and publishing part of a family
  // is what leaves a consumer with nowhere to resolve from.
  androidNativeArm32()
  androidNativeArm64()
  androidNativeX86()
  androidNativeX64()

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
