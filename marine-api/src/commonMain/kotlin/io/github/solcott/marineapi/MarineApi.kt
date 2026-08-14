package io.github.solcott.marineapi

/**
 * Placeholder so that every Kotlin target has something to compile.
 *
 * The Kotlin Multiplatform port of this library has not started yet: all real code still lives in
 * `src/jvmMain/java` under the `net.sf.marineapi` package. With a completely empty `commonMain` the
 * Kotlin/Native compilations are `NO-SOURCE` and produce no `.klib`, which makes the `iosArm64`,
 * `iosSimulatorArm64` and `macosArm64` publications fail with a missing-artifact error.
 *
 * Delete this file once real common code exists.
 */
public object MarineApi {
  /** Version of this library, matching the `version` property in `gradle.properties`. */
  public const val VERSION: String = "0.12.0"
}
