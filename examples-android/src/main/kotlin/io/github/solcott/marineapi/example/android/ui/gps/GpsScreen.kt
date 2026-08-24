package io.github.solcott.marineapi.example.android.ui.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.solcott.marineapi.example.android.R
import io.github.solcott.marineapi.example.android.data.BluetoothAvailability
import io.github.solcott.marineapi.example.android.data.PairedDevice
import io.github.solcott.marineapi.example.android.ui.common.Message
import io.github.solcott.marineapi.example.android.ui.common.accuracyEstimate
import io.github.solcott.marineapi.example.android.ui.common.describe
import io.github.solcott.marineapi.example.android.ui.common.metresToOneDecimal
import io.github.solcott.marineapi.example.android.ui.theme.ExampleTheme
import io.github.solcott.marineapi.nmea.Position
import io.github.solcott.marineapi.nmea.io.AccuracySource
import io.github.solcott.marineapi.nmea.io.FixAccuracy
import io.github.solcott.marineapi.nmea.io.PositionFix
import kotlinx.datetime.LocalTime

/**
 * Reads a paired Bluetooth GPS and shows its fixes as they arrive.
 *
 * The permission *request* stays here rather than moving into the ViewModel: an Activity Result
 * launcher needs a composition to be remembered in, and asking is a UI act. Its answer is the only
 * thing that travels down.
 */
@Composable
fun GpsScreen(modifier: Modifier = Modifier, viewModel: GpsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsStateWithLifecycle()

  val requestPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.onPermissionResult(granted)
    }

  // Re-runs after a rotation, which is what it is for: the radio may have been switched on, or a
  // receiver paired, while the app was in the background.
  LaunchedEffect(Unit) { viewModel.refresh(context.hasBluetoothPermission()) }

  GpsScreen(
    state = state,
    onGrant = { requestPermission.launch(BLUETOOTH_CONNECT) },
    onConnect = viewModel::connect,
    onDisconnect = viewModel::disconnect,
    modifier = modifier,
  )
}

/** The stateless half, so a `@Preview` can render a live feed with no radio anywhere near it. */
@Composable
private fun GpsScreen(
  state: GpsUiState,
  onGrant: () -> Unit,
  onConnect: (PairedDevice) -> Unit,
  onDisconnect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    val device = state.connectedTo
    if (device == null) {
      DevicePicker(state, onGrant, onConnect)
    } else {
      OutlinedButton(onClick = onDisconnect, modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.disconnect, device.label))
      }
      FixFeed(state.connection)
    }
  }
}

/**
 * Offers the paired devices, or explains why there are none to offer.
 *
 * Four different nothings, each with a different thing to do about it, which is why they are not
 * collapsed into one "no GPS found".
 */
@Composable
private fun DevicePicker(
  state: GpsUiState,
  onGrant: () -> Unit,
  onConnect: (PairedDevice) -> Unit,
) {
  when {
    state.availability == BluetoothAvailability.NO_RADIO ->
      Message(stringResource(R.string.no_bluetooth))
    !state.hasPermission ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Message(stringResource(R.string.needs_permission))
        Button(onClick = onGrant, modifier = Modifier.padding(16.dp)) {
          Text(stringResource(R.string.grant_permission))
        }
      }
    state.availability == BluetoothAvailability.DISABLED ->
      Message(stringResource(R.string.bluetooth_off))
    state.devices.isEmpty() -> Message(stringResource(R.string.no_paired_devices))
    else -> {
      Message(stringResource(R.string.pick_device))
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // The MAC address is the one thing about a paired device that cannot change, so it is the
        // key; two receivers of the same model share a name.
        items(state.devices, key = { it.address }) { device ->
          Button(onClick = { onConnect(device) }, modifier = Modifier.fillMaxWidth()) {
            Text(device.label)
          }
        }
      }
    }
  }
}

/** The latest fix, large, over the tail of the ones before it. */
@Composable
private fun FixFeed(connection: Connection?) {
  when (connection) {
    // Null only in the instant between picking a device and the feed's first emission.
    null,
    is Connection.Connecting ->
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator()
        Message(stringResource(R.string.connecting))
      }
    is Connection.Ended -> Message(stringResource(R.string.feed_ended))
    is Connection.Failed ->
      Message(
        stringResource(R.string.gps_failed, connection.message),
        color = MaterialTheme.colorScheme.error,
      )
    is Connection.Streaming -> {
      Text(
        text = connection.latest.position.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Accuracy(connection.latest)
      Text(
        text = pluralStringResource(R.plurals.fix_count, connection.count, connection.count),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
      )
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Same reasoning as the log reader's list: consecutive fixes are genuinely distinct
        // entries even when they read identically, so the index is the only honest key.
        items(connection.recent) { fix ->
          Text(
            text = fix.describe(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
          )
        }
      }
    }
  }
}

/**
 * How good the fix is, when the receiver has given any basis for saying so.
 *
 * Nothing at all is shown otherwise, rather than a dash or a zero: those read as a measurement of
 * something, and a receiver sending neither GST nor an HDOP has measured nothing. The measured and
 * estimated cases get different wording because they are different claims -- one is the receiver's
 * own figure, the other is geometry times an assumption this app supplies. Only about one receiver
 * in five reports the former, so the estimate is what most feeds will show.
 */
@Composable
private fun Accuracy(fix: PositionFix) {
  val estimate = fix.accuracyEstimate() ?: return
  val metres = estimate.metresToOneDecimal()
  Text(
    text =
      if (estimate.measured) {
        stringResource(R.string.accuracy_measured, metres, fix.accuracy?.source?.name.orEmpty())
      } else {
        stringResource(R.string.accuracy_estimated, metres)
      },
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
  )
}

/**
 * Whether `bondedDevices` may be read.
 *
 * `BLUETOOTH_CONNECT` is a runtime permission from API 31 only. Below that the install-time
 * `BLUETOOTH` in the manifest is the whole story, and asking for the newer one at runtime there
 * returns a denial for a permission the system does not have.
 */
private fun Context.hasBluetoothPermission(): Boolean =
  Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    checkSelfPermission(BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/**
 * The one runtime permission this app asks for.
 *
 * Held in a constant to say once why lint's InlinedApi warning is wrong here: a permission name is
 * a plain string compiled into the app rather than a field looked up on the platform, so naming an
 * API 31 constant costs nothing on API 24 -- and nothing below 31 reaches it anyway, since
 * [hasBluetoothPermission] answers true there and the button that requests it is never shown.
 */
@SuppressLint("InlinedApi") private val BLUETOOTH_CONNECT = Manifest.permission.BLUETOOTH_CONNECT

@Preview(showBackground = true)
@Composable
private fun FixFeedPreview() {
  // A receiver that reports its own error, so the preview shows the measured wording. Drop the
  // accuracy and keep horizontalDilution to see the estimated one instead.
  val fix =
    PositionFix(
      Position(60.0, 25.0, 12.5),
      time = LocalTime(10, 39, 0),
      speedKnots = 4.2,
      accuracy = FixAccuracy(horizontal = 0.9, vertical = 1.1, source = AccuracySource.GST),
      horizontalDilution = 0.95,
    )
  ExampleTheme { Column { FixFeed(Connection.Streaming(fix, 2, listOf(fix, fix))) } }
}
