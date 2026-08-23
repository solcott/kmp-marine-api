package io.github.solcott.marineapi.example.android.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.solcott.marineapi.example.android.R
import io.github.solcott.marineapi.example.android.ui.gps.GpsScreen
import io.github.solcott.marineapi.example.android.ui.log.LogScreen
import kotlinx.serialization.Serializable

/**
 * The two ways into the same library.
 *
 * `@Serializable` because Navigation 3 saves the back stack by serializing its keys, which is how a
 * destination survives the process being killed. The keys carry no arguments -- there is nothing to
 * pass, since what each screen is reading lives in its ViewModel.
 */
@Serializable
sealed interface Route : NavKey {

  /** A log file the user picks. */
  @Serializable data object Log : Route

  /** A paired Bluetooth receiver. */
  @Serializable data object Gps : Route
}

/**
 * Holds the two screens and the switch between them.
 *
 * Navigation 3 keeps the back stack as an ordinary list this code owns, so "switch tab" is written
 * here as what it means: **replace** the single entry rather than push onto it, which leaves system
 * back exiting the app instead of walking backwards through tabs.
 *
 * The decorators are not boilerplate to copy past. [rememberViewModelStoreNavEntryDecorator] is
 * what scopes a `ViewModel` to its entry; without it `hiltViewModel()` inside an entry resolves
 * against the activity's store instead and both screens would share a lifetime. It lives in a
 * separate artifact from Navigation 3 itself, which is the hint that it is not there unless you
 * ask.
 *
 * Tabs rather than a `NavigationBar`: a bottom bar item wants an icon, and two glyphs are not worth
 * pulling in the frozen `material-icons` artifact for.
 */
@Composable
fun ExampleApp(modifier: Modifier = Modifier) {
  val backStack = rememberNavBackStack(Route.Log)
  val current = backStack.lastOrNull()

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      // Scaffold pads its *content* away from the system bars and leaves the bars to the slot that
      // occupies that edge. A TopAppBar insets itself; a TabRow does not, so without this the tab
      // labels are drawn underneath the clock.
      PrimaryTabRow(
        selectedTabIndex = TABS.indexOfFirst { it.route == current }.coerceAtLeast(0),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
      ) {
        TABS.forEach { tab ->
          Tab(
            selected = tab.route == current,
            onClick = {
              if (tab.route != current) {
                backStack.clear()
                backStack.add(tab.route)
              }
            },
            text = { Text(stringResource(tab.label)) },
          )
        }
      }
    },
  ) { insets ->
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryDecorators =
        listOf(
          rememberSaveableStateHolderNavEntryDecorator(),
          rememberViewModelStoreNavEntryDecorator(),
        ),
      entryProvider =
        entryProvider {
          entry(Route.Log) { LogScreen() }
          entry(Route.Gps) { GpsScreen() }
        },
      modifier = Modifier.padding(insets),
    )
  }
}

/** A destination and the string that names it, in the order they appear. */
private data class Destination(val route: Route, val label: Int)

private val TABS =
  listOf(Destination(Route.Log, R.string.mode_log), Destination(Route.Gps, R.string.mode_gps))
