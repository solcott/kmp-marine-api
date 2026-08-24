import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("kmp-jvm")
  alias(libs.plugins.sort.dependencies)
}

val jvmCompat = libs.versions.jvm.compat.get()

kotlin {
  // Targets are declared here rather than through the `kmp-targets` convention plugin, which the
  // library uses. That plugin adds ios and wasmWasi, and neither can produce a demo you can run:
  // an iOS binary needs an Xcode project around it, and the wasmWasi Node runner sandboxes the
  // filesystem. Android is a separate module, :examples-android, because an Android app cannot be
  // a target of a library module.
  //
  // Deliberately never published.
  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  // One compilation serves both environments, so there is one `main` in jsMain that works out
  // which it is in. That is only safe because kotlinx-io binds `node:fs` lazily -- a browser
  // bundle that never touches SystemFileSystem never calls `require`.
  js {
    outputModuleName = "examples"
    nodejs()
    browser()
    binaries.executable()
  }

  macosArm64 {
    // entryPoint is required: Kotlin/Native looks for `main` in the ROOT package by default and
    // fails the LINK step -- not the compile -- with "Could not find '/main' function" if it is
    // anywhere else.
    binaries { executable { entryPoint = "io.github.solcott.marineapi.example.main" } }
  }

  sourceSets {
    commonMain.dependencies {
      // api, not implementation: the demo bodies take a `() -> Source` and Cli.kt hands back a
      // Buffer, so kotlinx-io is in this module's own signatures rather than behind them. Nothing
      // consumes :examples, so this changes no artifact -- it just stops the ABI claim being a lie.
      api(libs.kotlinx.io.core)

      implementation(project(":marine-api"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.datetime)
    }
    jvmMain.dependencies {
      // gnu.io.*, used by SerialPortExample. This was compileOnly on the library (Maven
      // <scope>provided</scope>); here it is a real implementation dependency so that
      // runSerialPortExample actually works.
      implementation(libs.nrjavaserial)
    }
  }
}

// ---------------------------------------------------------------------------
// One run task per demo (jvm)
// ---------------------------------------------------------------------------
// The other platforms get a single executable each and choose their demo from argv. The JVM keeps a
// task per demo because that is the interface this project has always had.
val jvmMain = kotlin.jvm().compilations.getByName("main")
val toolchainVersion = libs.versions.jvm.toolchain.get().toInt()

// JavaExec would otherwise run in examples/, so every --args path would have to be written relative
// to a module the user is not standing in. Captured as a plain File at configuration time so the
// configuration cache can serialize it.
val repositoryRoot = rootProject.layout.projectDirectory.asFile

// Kept in step with DEMOS in commonMain/Cli.kt. A name here with no branch there prints the usage
// text rather than failing, which is the right way round for a demo.
val demos = listOf("file", "positions", "ais", "ublox", "output")

demos.forEach { demo ->
  tasks.register<JavaExec>("run${demo.replaceFirstChar(Char::titlecase)}Example") {
    group = "examples"
    description = "Runs the $demo demo"
    mainClass = "io.github.solcott.marineapi.example.MainKt"
    workingDir = repositoryRoot
    // NOT `args(demo)`: Gradle's `--args` calls setArgsString(), which REPLACES the argument list,
    // so a demo name set here would vanish the moment someone passed a file path. A system property
    // leaves --args free for the path.
    systemProperty("marineapi.demo", demo)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    // There is no `java` plugin (KGP rejects it alongside KMP), so JavaExec has no toolchain
    // launcher by default -- it would silently use the daemon JVM. Resolve the service directly.
    javaLauncher =
      serviceOf<JavaToolchainService>().launcherFor {
        languageVersion = JavaLanguageVersion.of(toolchainVersion)
      }
  }
}

// ---------------------------------------------------------------------------
// Arguments for the js and native run tasks
// ---------------------------------------------------------------------------
// Neither accepts `--args`: the Kotlin/Native run task is a plain Exec, and jsNodeRun is a
// NodeJsExec. Both take them from a project property instead.
//
//   ./gradlew :examples:runDebugExecutableMacosArm64 -PdemoArgs="file nmea.log"
//   ./gradlew :examples:jsNodeRun -PdemoArgs="file nmea.log"
//
// With none given, each entry point falls back to the one demo that carries its own feed.
// Read at configuration time, not through a CommandLineArgumentProvider: a SAM-converted lambda in
// a .gradle.kts captures the enclosing script object, which the configuration cache refuses to
// serialize. A gradleProperty read is tracked as a configuration input, so this stays correct.
val demoArgList: List<String> =
  providers.gradleProperty("demoArgs").orNull?.split(" ")?.filter(String::isNotBlank).orEmpty()

tasks.withType<Exec>().configureEach {
  if (name.startsWith("run") && name.endsWith("ExecutableMacosArm64")) {
    args(demoArgList)
    workingDir = repositoryRoot
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec>().configureEach {
  args(demoArgList)
  workingDir = repositoryRoot
}

tasks.register<JavaExec>("runSerialPortExample") {
  group = "examples"
  // --args is free here, unlike the per-demo tasks: this task passes no system property, so
  // setArgsString() replacing the argument list costs nothing.
  //   --args="COM3"          a named port, opened through the driver
  //   --args="/dev/rfcomm0"  a device node, read directly -- see the KDoc on the example
  description = "Reads NMEA from the named port or device, or scans every port when given none"
  mainClass = "io.github.solcott.marineapi.example.SerialPortExampleKt"
  workingDir = repositoryRoot
  classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
  javaLauncher =
    serviceOf<JavaToolchainService>().launcherFor {
      languageVersion = JavaLanguageVersion.of(toolchainVersion)
    }
}
