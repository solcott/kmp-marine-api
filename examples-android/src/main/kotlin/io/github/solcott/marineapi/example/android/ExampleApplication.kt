package io.github.solcott.marineapi.example.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The Hilt entry point, and the only reason this class exists.
 *
 * `@HiltAndroidApp` generates the application component every other injection here hangs off, so it
 * has to be named in the manifest even though it adds no behaviour of its own.
 */
@HiltAndroidApp class ExampleApplication : Application()
