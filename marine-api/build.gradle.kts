import java.io.File
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
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
    // Since Kotlin 2.1.20 the jvm target compiles src/jvmMain/java and src/jvmTest/java
    // by DEFAULT. Do not call withJava(), and do not apply the java/java-library plugins.
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(jvmCompat)
      // Kotlin's equivalent of javac --release: validates against the Java 17 API
      // signatures, not just the bytecode version.
      freeCompilerArgs.add("-Xjdk-release=$jvmCompat")
    }
  }

  sourceSets {
    // commonTest uses kotlin.test. On the jvm target that resolves to kotlin-test-junit
    // because jvmTest is configured with useJUnit(), so the common tests and the legacy
    // Java JUnit 4 suite run in the same task.
    commonMain.dependencies { api(libs.kotlinx.datetime) }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    jvmTest.dependencies { implementation(libs.junit) }
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
  // MUST be useJUnit(), not useJUnitPlatform(). The suite is JUnit 4 and BODTest still
  // extends junit.framework.TestCase (JUnit 3). Under Gradle's default JUnit Platform
  // zero tests run and the build still passes.
  useJUnit()

  // SentenceReaderTest.testSetDatagramSocket binds a FIXED UDP port 3810 that
  // UDPServerMock also sends to, and several tests assert on Thread.sleep timing.
  maxParallelForks = 1

  testLogging {
    events("failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
}

// ---------------------------------------------------------------------------
// Javadoc for the jvm target (replaces maven-javadoc-plugin)
// ---------------------------------------------------------------------------
// There is no `java` plugin, so java { withJavadocJar() } is unavailable -- but the
// Javadoc task TYPE is core Gradle and just has to be wired by hand.
val javadocJvm =
  tasks.register<Javadoc>("javadocJvm") {
    group = "documentation"
    description = "Generates Javadoc for the jvm target's Java sources."

    source(fileTree("src/jvmMain/java") { include("**/*.java") })
    // The old <excludePackageNames>net.sf.marineapi.example</excludePackageNames> is gone:
    // the examples live in the :examples module now, so there is nothing to filter out.

    classpath = files(configurations.named("jvmCompileClasspath"))
    destinationDir = layout.buildDirectory.dir("docs/javadoc").get().asFile

    // The `javaToolchains` extension comes from the `jvm-toolchains` plugin, which cannot
    // be applied here -- it pulls in `java`, which KGP rejects as incompatible with KMP.
    // The underlying build service is still available, so resolve it directly.
    javadocTool =
      serviceOf<JavaToolchainService>().javadocToolFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.jvm.toolchain.get())
      }

    (options as StandardJavadocDocletOptions).apply {
      encoding = "UTF-8"
      charSet = "UTF-8"
      docEncoding = "UTF-8"
      source = jvmCompat
      windowTitle = "Java Marine API ${project.version}"
      docTitle = "Java Marine API ${project.version}"
      links("https://docs.oracle.com/en/java/javase/$jvmCompat/docs/api/")
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
val jvmJavaRoot = layout.projectDirectory.dir("src/jvmMain/java")
val jvmJavaSources = fileTree(jvmJavaRoot) { include("**/*.java") }

tasks.named<Jar>("jvmJar") {
  val rootPath = jvmJavaRoot.asFile
  val bundleVersion = project.version.toString()

  // Felix expanded <Export-Package>net.sf.marineapi.*</Export-Package> to every package
  // holding a class, which under Maven included net.sf.marineapi.example; that package now
  // lives in the :examples module and is no longer exported. Derived from the source tree
  // rather than hardcoded so it cannot rot, and evaluated lazily so the jar task's own
  // up-to-date checks drive re-evaluation.
  val exportedPackages = provider {
    jvmJavaSources.files
      .map { it.parentFile.toRelativeString(rootPath).replace(File.separatorChar, '.') }
      .toSortedSet()
      .joinToString(",")
  }

  manifest {
    attributes(
      // maven-jar-plugin declared this, but <packaging>bundle</packaging> meant the Felix
      // plugin built the jar and never read that config, so it almost certainly never
      // reached the artifact. Setting it here is a deliberate fix, not a port.
      "Automatic-Module-Name" to "net.sf.marineapi",
      "Bundle-ManifestVersion" to "2",
      "Bundle-SymbolicName" to "net.sf.marineapi",
      "Bundle-Name" to "Java Marine API",
      "Bundle-Version" to bundleVersion,
      "Bundle-License" to "http://www.opensource.org/licenses/lgpl-3.0.html",
      "Bundle-DocURL" to "https://github.com/solcott/kmp-marine-api",
      "Export-Package" to exportedPackages,
      // Import-Package is deliberately ABSENT, which is the effect of the POM's empty
      // <Import-Package/> element: it stops gnu.io becoming a mandatory OSGi import.
    )
  }
}

// ---------------------------------------------------------------------------
// Publishing
// ---------------------------------------------------------------------------
mavenPublishing {
  publishToMavenCentral()

  signAllPublications()

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
