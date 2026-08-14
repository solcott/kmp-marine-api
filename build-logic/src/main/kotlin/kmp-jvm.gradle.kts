import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("project-config")
}

val libs: VersionCatalog = the<VersionCatalogsExtension>().named("libs")
val toolchainVersion = libs.findVersion("jvm-toolchain").get().requiredVersion.toInt()

// Pins the JDK that runs kotlinc/javac regardless of which JDK started the daemon. This
// must be >= 17 because AGP refuses anything lower for the Android compilations; the
// Java 17 *bytecode* level is set separately via jvmTarget / options.release.
extensions.configure<KotlinMultiplatformExtension> { jvmToolchain(toolchainVersion) }
