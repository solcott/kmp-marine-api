import java.io.File
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.dokka)
  id("kmp-jvm")
  id("kmp-android")
  id("kmp-targets")
  alias(libs.plugins.publish)
  alias(libs.plugins.sort.dependencies)
}

val jvmCompat = libs.versions.jvm.compat.get()

kotlin {
  // Every public declaration needs an explicit visibility and return type. This is a
  // published library and the Kotlin API is new, so the discipline costs nothing now and
  // prevents accidental API surface later.
  explicitApi()

  android { namespace = "io.github.solcott.marineapi" }

  jvm {
    // Do not call withJava(), and do not apply the java/java-library plugins: KGP rejects
    // them as incompatible with KMP. Nothing here needs them -- the Java tree is gone and
    // src/jvmMain holds only resources.
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      // Kotlin's equivalent of javac --release: validates against the Java 17 API
      // signatures, not just the bytecode version.
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  sourceSets {
    // commonTest uses kotlin.test. On the jvm target that resolves to kotlin-test-junit,
    // because jvmTest is configured with useJUnit().
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      api(libs.kotlinx.io.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8" // was project.build.sourceEncoding

  // AGP names its own Java compile tasks "compile<Variant>JavaWithJavac"; those keep
  // AGP's own release level. Only the KMP jvm target's tasks get pinned here.
  if (!name.endsWith("JavaWithJavac")) {
    // sourceCompatibility/targetCompatibility exist to satisfy KGP's jvm-target
    // consistency check, which reads them rather than options.release.
    sourceCompatibility = jvmCompat
    targetCompatibility = jvmCompat
    options.release = jvmCompat.toInt()
  }
}

tasks.named<Test>("jvmTest") {
  // kotlin-test resolves to kotlin-test-junit under this, which is what runs the common
  // tests on the jvm target. Switching to useJUnitPlatform() means switching kotlin-test to
  // its JUnit 5 variant as well; get only half of that right and ZERO tests run while the
  // build still reports success. Check the count, not the exit code, if you change it.
  useJUnit()

  testLogging {
    events("failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
}

// ---------------------------------------------------------------------------
// jvmJar manifest + OSGi bundle metadata (replaces maven-bundle-plugin)
// ---------------------------------------------------------------------------
// The `biz.aQute.bnd.builder` plugin cannot be used here: it applies the `java` plugin,
// which KGP rejects as incompatible with Kotlin Multiplatform. So the headers Felix used
// to generate are written by hand instead. What this cannot reproduce is bnd's bytecode
// analysis -- per-package `version=` attributes and `uses:=` directives are absent, so
// strict OSGi resolvers get less information than before. Everything the old POM actually
// configured (Export-Package as a wildcard, Import-Package suppressed) is preserved.
//
// The source of the package list is commonMain: since the Java tree was deleted, everything
// in the jvm jar is compiled from there.
val exportRoot = layout.projectDirectory.dir("src/commonMain/kotlin")
val exportSources = fileTree(exportRoot) { include("**/*.kt") }

tasks.named<Jar>("jvmJar") {
  val rootPath = exportRoot.asFile
  val bundleVersion = project.version.toString()

  // Felix expanded <Export-Package>net.sf.marineapi.*</Export-Package> to every package
  // holding a class. Derived from the source tree rather than hardcoded so it cannot rot,
  // and evaluated lazily so the jar task's own up-to-date checks drive re-evaluation.
  //
  // Deriving is also what makes this survive a move: pointed at a directory that does not
  // exist, a fileTree is simply empty, and the bundle would ship an EMPTY Export-Package
  // without failing anything. If this header ever comes out blank, it is looking in the
  // wrong place.
  val exportedPackages = provider {
    exportSources.files
      .map { it.parentFile.toRelativeString(rootPath).replace(File.separatorChar, '.') }
      .toSortedSet()
      .joinToString(",")
  }

  manifest {
    attributes(
      // maven-jar-plugin declared this, but <packaging>bundle</packaging> meant the Felix
      // plugin built the jar and never read that config, so it almost certainly never
      // reached the artifact. Setting it here is a deliberate fix, not a port.
      //
      // Both names were net.sf.marineapi until the Kotlin port replaced that tree. They
      // follow the package root, so that the name a consumer imports the bundle by and the
      // packages it exports still share a prefix.
      "Automatic-Module-Name" to "io.github.solcott.marineapi",
      "Bundle-ManifestVersion" to "2",
      "Bundle-SymbolicName" to "io.github.solcott.marineapi",
      "Bundle-Name" to "Java Marine API",
      "Bundle-Version" to bundleVersion,
      "Bundle-License" to "http://www.opensource.org/licenses/lgpl-3.0.html",
      "Bundle-DocURL" to "https://github.com/solcott/kmp-marine-api",
      "Export-Package" to exportedPackages,
      // Import-Package is deliberately ABSENT, which is the effect of the POM's empty
      // <Import-Package/> element. It used to stop gnu.io becoming a mandatory import;
      // that dependency is gone with the Java tree, but the kotlinx-* packages this now
      // depends on would take its place.
    )
  }
}

// ---------------------------------------------------------------------------
// Publishing
// ---------------------------------------------------------------------------
mavenPublishing {
  publishToMavenCentral()

  signAllPublications()

  // The published javadoc jars were empty stubs: this plugin defaults KMP publications to
  // JavadocJar.Empty(). Applying the Dokka plugin is enough to change that -- the publish
  // plugin detects it and switches to JavadocJar.Dokka on its own, so there is no configure()
  // call here to drift out of step with the Dokka task names.

  coordinates(group.toString(), "kmp-marine-api", version.toString())
  pom {
    name = "Java Marine API"
    description = "NMEA 0183, AIS and u-blox parser library for Kotlin Multiplatform."
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
