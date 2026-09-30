---
paths:
  - "examples-android/**"
---

# `:examples-android`

An Android app, the only non-KMP module in the build. Never published. Consumes `:marine-api`'s
`android` target through module metadata, as an external consumer would, and shares no code with
`:examples`.

Laid out as an app: `data/` holds the two repositories (a picked file and a paired Bluetooth
receiver, which are the same code after `asSource()`), `di/` the Hilt modules, `ui/log` and `ui/gps`
a ViewModel and a screen each, reached through Navigation 3 from `ui/ExampleApp.kt`. `MainActivity`
does nothing but `setContent`.

## Plugins

- **`com.android.application` with no `kotlin-android` plugin.** AGP 9 has built-in Kotlin and
  rejects it. `org.jetbrains.kotlin.plugin.compose` and `kotlin.plugin.serialization` (for the
  Navigation 3 route keys) attach to the built-in Kotlin without it. The Compose compiler is
  versioned with Kotlin, not with the Compose BOM, so the two move independently.
- **Hilt runs under KSP, and that works despite what the internet says.** KSP's own error text
  ("KSP is not compatible with Android Gradle Plugin's built-in Kotlin") belongs to versions before
  2.3.1. Hilt's Gradle plugin needs 2.59 or newer for AGP 9. **Do not reach for `kotlin-kapt`** --
  built-in Kotlin has no such plugin, and Hilt does not work with `com.android.legacy-kapt`.

## Keeping `src/test` free of Robolectric

- **The repository interfaces take a `String` uri and a `String` MAC address**, never a `Uri` or a
  `BluetoothDevice`. Those framework types throw "not mocked" in a JVM unit test, so a ViewModel
  holding one could not be tested without Robolectric.
- **`testOptions { unitTests { isReturnDefaultValues = true } }`** is what lets `GpsViewModel` log to
  `android.util.Log`, which otherwise throws "not mocked".
- **`kotlin-test-junit` is named explicitly, and must be.** There is no `useJUnit()` hook in an AGP
  unit test, so plain `kotlin-test` resolves to the frameworkless variant and `kotlin.test.Test`
  does not exist. (`:marine-api`'s android target differs: that one is a KGP compilation.)
- **A ViewModel under `MainDispatcherRule` must be built inside the test, not in a field
  initializer.** A JUnit rule runs *after* the test instance is constructed, so a field-built
  ViewModel starts collecting before `Dispatchers.setMain` and its `stateIn` never leaves the
  initial value -- every assertion then reads the initial state. Both suites use `by lazy`.

## Dependency analysis

`androidx.annotation`, `androidx.fragment` and `lifecycle-viewmodel-savedstate` are referenced only
by the Java that Hilt and KSP generate, so they are excluded in the root `dependencyAnalysis` block
rather than declared. Everything the hand-written source names is declared.
