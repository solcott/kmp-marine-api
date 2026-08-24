package io.github.solcott.marineapi.example.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.solcott.marineapi.example.android.ui.ExampleApp
import io.github.solcott.marineapi.example.android.ui.theme.ExampleTheme

/**
 * The single activity, and deliberately almost empty.
 *
 * Everything that used to be here has moved: the reading into `data`, the state into the two
 * ViewModels, the screens into `ui`. What that bought is the thing this class no longer has to do
 * -- there is no `onDestroy`, no `CoroutineScope` field and no re-reading after a rotation, because
 * a read and a Bluetooth socket now belong to a `ViewModel` that the rotation does not touch.
 *
 * `@AndroidEntryPoint` is what lets `hiltViewModel()` work in the composition below.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // targetSdk 35 and up draws edge to edge whether or not this is called; calling it explicitly
    // keeps the behaviour the same on older releases. Scaffold is what then pads content away
    // from the system bars.
    enableEdgeToEdge()
    setContent { ExampleTheme { ExampleApp() } }
  }
}
