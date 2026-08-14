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
  // multiplatform about them. Hence no kmp-targets, no kmp-android, and -- deliberately --
  // no publish plugin: this module is never released.
  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  sourceSets.jvmMain.dependencies {
    implementation(project(":marine-api"))
    // gnu.io.*, used by SerialPortExample. This was compileOnly on the library (Maven
    // <scope>provided</scope>); here it is a real implementation dependency so that
    // runSerialPortExample actually works.
    implementation(libs.nrjavaserial)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"

  // sourceCompatibility/targetCompatibility exist to satisfy KGP's jvm-target consistency
  // check, which reads them rather than options.release. Unlike :marine-api there is no
  // AGP here, so every JavaCompile task in this project belongs to the jvm target.
  sourceCompatibility = jvmCompat
  targetCompatibility = jvmCompat
  options.release = jvmCompat.toInt()
}

// ---------------------------------------------------------------------------
// One run task per example
// ---------------------------------------------------------------------------
// Every .java file here except package-info.java is a top-level public class with a main
// method, so the file name is the class name. Registering from the directory listing means
// a new example gets a run task for free; File.listFiles() is tracked by the configuration
// cache as a directory-listing input, so adding one invalidates the cache as it should.
val exampleDir = layout.projectDirectory.dir("src/jvmMain/java/net/sf/marineapi/example")
val jvmMain = kotlin.jvm().compilations.getByName("main")
val toolchainVersion = libs.versions.jvm.toolchain.get().toInt()

exampleDir.asFile
  .listFiles { file -> file.extension == "java" && file.name != "package-info.java" }
  .orEmpty()
  .sortedBy { it.name }
  .forEach { file ->
    val simpleName = file.nameWithoutExtension
    tasks.register<JavaExec>("run$simpleName") {
      group = "examples"
      description = "Runs net.sf.marineapi.example.$simpleName"
      mainClass = "net.sf.marineapi.example.$simpleName"
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
