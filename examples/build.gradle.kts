import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("kmp-jvm")
  alias(libs.plugins.sort.dependencies)
}

val jvmCompat = libs.versions.jvm.compat.get()

kotlin {
  // jvm() ONLY. These are demo applications with main methods; there is nothing
  // multiplatform about them -- SerialPortExample in particular is JVM-bound by the driver
  // it uses. Hence no kmp-targets, no kmp-android, and -- deliberately -- no publish plugin:
  // this module is never released.
  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  sourceSets.jvmMain.dependencies {
    implementation(project(":marine-api"))
    // The examples collect flows from a main function, which the library itself never does --
    // it exposes flows and leaves running them to the caller.
    implementation(libs.kotlinx.coroutines.core)
    // gnu.io.*, used by SerialPortExample. This was compileOnly on the library (Maven
    // <scope>provided</scope>); here it is a real implementation dependency so that
    // runSerialPortExample actually works.
    implementation(libs.nrjavaserial)
  }
}

// ---------------------------------------------------------------------------
// One run task per example
// ---------------------------------------------------------------------------
// Every .kt file here holds a top-level `main`. Registering from the directory listing means
// a new example gets a run task for free; File.listFiles() is tracked by the configuration
// cache as a directory-listing input, so adding one invalidates the cache as it should.
val exampleDir =
  layout.projectDirectory.dir("src/jvmMain/kotlin/io/github/solcott/marineapi/example")
val jvmMain = kotlin.jvm().compilations.getByName("main")
val toolchainVersion = libs.versions.jvm.toolchain.get().toInt()

// JavaExec would otherwise run in examples/, so every --args path would have to be written
// relative to a module the user is not standing in. Captured as a plain File at configuration
// time so the configuration cache can serialize it.
val repositoryRoot = rootProject.layout.projectDirectory.asFile

exampleDir.asFile
  .listFiles { file -> file.extension == "kt" }
  .orEmpty()
  .sortedBy { it.name }
  .forEach { file ->
    val simpleName = file.nameWithoutExtension
    tasks.register<JavaExec>("run$simpleName") {
      group = "examples"
      description = "Runs the $simpleName example"
      // A file-level `main` compiles into a class named after the FILE with `Kt` appended,
      // not after any class in it. FileExample.kt -> FileExampleKt.
      mainClass = "io.github.solcott.marineapi.example.${simpleName}Kt"
      workingDir = repositoryRoot
      // allOutputs carries the task dependency on the jvm compilation.
      classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
      // There is no `java` plugin (KGP rejects it alongside KMP), so JavaExec has no
      // toolchain launcher by default -- it would silently use the daemon JVM. Resolve the
      // service directly, the same trick :marine-api's javadocJvm uses.
      javaLauncher =
        serviceOf<JavaToolchainService>().launcherFor {
          languageVersion = JavaLanguageVersion.of(toolchainVersion)
        }
    }
  }
