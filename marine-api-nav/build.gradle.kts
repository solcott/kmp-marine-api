import java.io.File
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.dokka)
  id("kmp-jvm")
  id("kmp-android")
  id("kmp-targets")
  alias(libs.plugins.publish)
  alias(libs.plugins.binary.compatibility.validator)
  alias(libs.plugins.sort.dependencies)
}

val jvmCompat = libs.versions.jvm.compat.get()

// The second published module, so the same ABI pinning applies. See marine-api/build.gradle.kts
// for why the klib dump is enabled and why strictValidation is not.
apiValidation {
  @OptIn(ExperimentalBCVApi::class)
  klib {
    enabled = true
    strictValidation = false
  }
}

kotlin {
  explicitApi()

  android { namespace = "io.github.solcott.marineapi.nav" }

  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  sourceSets {
    commonMain.dependencies {
      // api, not implementation: every operator here takes a Flow<Sentence> or a PositionFix and
      // hands back a type built out of Position, so the parser's types are in this module's own
      // signatures. A consumer cannot use this module without the parser on its compile classpath.
      api(project(":marine-api"))
      // api as well: every operator here takes and returns a Flow, so coroutines is in this
      // module's signatures rather than behind them.
      api(libs.kotlinx.coroutines.core)

      // Internal only -- the LocalTime arithmetic in Teleports.kt. The datetime types on
      // PositionFix reach a consumer through :marine-api's own api dependency, not this one.
      implementation(libs.kotlinx.datetime)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"

  if (!name.endsWith("JavaWithJavac")) {
    sourceCompatibility = jvmCompat
    targetCompatibility = jvmCompat
    options.release = jvmCompat.toInt()
  }
}

tasks.named<Test>("jvmTest") {
  // Same as :marine-api, and for the same reason: kotlin-test resolves to kotlin-test-junit under
  // useJUnit(), and switching only half of that pair over runs ZERO tests while reporting success.
  useJUnit()

  testLogging {
    events("failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
}

// ---------------------------------------------------------------------------
// jvmJar manifest + OSGi bundle metadata
// ---------------------------------------------------------------------------
// Mirrors the block in marine-api/build.gradle.kts. The package root differs deliberately:
// io.github.solcott.marineapi.nav rather than io.github.solcott.marineapi, so the two jars do not
// share a package. A split package is a hard error for an OSGi resolver and for JPMS, and two
// separately versioned artifacts exporting the same package is exactly that.
val exportRoot = layout.projectDirectory.dir("src/commonMain/kotlin")
val exportSources = fileTree(exportRoot) { include("**/*.kt") }

tasks.named<Jar>("jvmJar") {
  val rootPath = exportRoot.asFile
  val bundleVersion = project.version.toString()

  // Derived rather than hardcoded, and lazily so the jar's up-to-date checks drive it. Pointed at
  // a directory that does not exist a fileTree is simply EMPTY, and the bundle would ship an empty
  // Export-Package without failing anything -- so if this header comes out blank, it is looking in
  // the wrong place.
  val exportedPackages = provider {
    exportSources.files
      .map { it.parentFile.toRelativeString(rootPath).replace(File.separatorChar, '.') }
      .toSortedSet()
      .joinToString(",")
  }

  manifest {
    attributes(
      "Automatic-Module-Name" to "io.github.solcott.marineapi.nav",
      "Bundle-ManifestVersion" to "2",
      "Bundle-SymbolicName" to "io.github.solcott.marineapi.nav",
      "Bundle-Name" to "Java Marine API -- navigation",
      "Bundle-Version" to bundleVersion,
      "Bundle-License" to "http://www.opensource.org/licenses/lgpl-3.0.html",
      "Bundle-DocURL" to "https://github.com/solcott/kmp-marine-api",
      "Export-Package" to exportedPackages,
      // Import-Package deliberately absent, as in :marine-api. Here it would otherwise make
      // io.github.solcott.marineapi.* a mandatory import, on top of the kotlinx-* packages.
    )
  }
}

// ---------------------------------------------------------------------------
// Publishing
// ---------------------------------------------------------------------------
mavenPublishing {
  publishToMavenCentral()

  signAllPublications()

  // Note the KMP coordinate split applies here too: a Gradle consumer resolves
  // io.github.solcott:kmp-marine-api-nav through module metadata, but a plain Maven consumer has
  // to name kmp-marine-api-nav-jvm.
  coordinates(group.toString(), "kmp-marine-api-nav", version.toString())
  pom {
    name = "Java Marine API -- navigation"
    description =
      "Higher-level navigation, AIS traffic and instrument operators over the NMEA 0183 parser."
    url = "https://github.com/solcott/kmp-marine-api"
    licenses {
      license {
        name = "GNU Lesser General Public License, Version 3.0"
        url = "http://www.opensource.org/licenses/lgpl-3.0.html"
        distribution = "repo"
      }
    }
    developers {
      developer {
        id = "ktuukkan"
        name = "Kimmo Tuukkanen"
      }
      developer {
        id = "solcott"
        name = "Scott Olcott"
      }
    }
    scm {
      connection = "scm:git:git://github.com/solcott/kmp-marine-api.git"
      developerConnection = "scm:git:ssh://github.com/solcott/kmp-marine-api.git"
      url = "https://github.com/solcott/kmp-marine-api"
    }
  }
}
