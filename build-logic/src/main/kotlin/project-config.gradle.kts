// Shared configuration applied to every project via the convention plugins (and the root build).
// Centralizes ktfmt and detekt so we no longer apply plugins in an allprojects {} block.
plugins {
  id("com.ncorti.ktfmt.gradle")
  id("dev.detekt")
  // DAGP must be applied to every project, root included -- the project plugin does not cascade,
  // and buildHealth aggregates the per-project advice. It lives here rather than in a settings
  // plugins block because it must share a classloader with KGP and AGP, which build-logic owns.
  id("com.autonomousapps.dependency-analysis")
}

ktfmt {
  googleStyle()
  removeUnusedImports = true
}

detekt {
  // The checked-in config lists only what this project overrides; everything else comes from
  // detekt's own defaults, so an upgrade brings new rules with it rather than pinning a snapshot.
  buildUponDefaultConfig = true
  config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
  parallel = true

  // The plain `detekt` task, pointed at the whole source tree. Detekt's own default is the
  // src/main/kotlin layout, which does not exist in a multiplatform project; the alternative --
  // its per-compilation tasks -- would report every finding in commonMain once per target, so a
  // single pass over each file is both the cheaper and the more readable signal.
  source.setFrom(layout.projectDirectory.dir("src"))
}
