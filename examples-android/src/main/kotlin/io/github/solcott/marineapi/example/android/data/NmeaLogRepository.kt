package io.github.solcott.marineapi.example.android.data

import android.content.ContentResolver
import android.net.Uri
import io.github.solcott.marineapi.example.android.di.IoDispatcher
import io.github.solcott.marineapi.nmea.io.PositionFix
import io.github.solcott.marineapi.nmea.io.nmeaSentences
import io.github.solcott.marineapi.nmea.io.positions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * A log file, as a flow of position fixes.
 *
 * **The identifier is a `String`, not a `Uri`, and that is deliberate.** `android.net.Uri` is one
 * of the framework classes that is a stub in a JVM unit test -- `Uri.parse` throws "not mocked" --
 * so a ViewModel holding a `Uri` could only be tested under Robolectric. Keeping the Android type
 * below this line means everything above it is ordinary Kotlin, and [NmeaLogRepository] is the seam
 * where a fake goes in.
 */
interface NmeaLogRepository {

  /** Cold: nothing is opened until collection starts, and the stream closes when it ends. */
  fun fixes(uri: String): Flow<PositionFix>
}

/**
 * Reads a log the user picked through the Storage Access Framework.
 *
 * The whole Android-specific part of this app is the one line in the middle:
 * ```
 * contentResolver.openInputStream(uri)!!.asSource().buffered()
 * ```
 *
 * That is the same `InputStream.asSource()` the JVM serial-port example uses, which is the point
 * worth taking away: Android is not a third IO story, it is the JVM one with a different way of
 * naming a file. Everything after that call -- `nmeaSentences().positions()` -- is the common code
 * every other platform in this repository runs, unchanged.
 *
 * The Storage Access Framework is used rather than a directory scan because it hands back a
 * readable `Uri` with **no permission declared at all**; see the manifest, whose only permission is
 * the one the Bluetooth screen needs.
 *
 * [BluetoothGpsRepository] is the same argument made twice: it opens a socket instead of a file,
 * and everything after `asSource()` is identical.
 *
 * `flowOn` is what moves the reading off the main thread. The library will not do it for you -- see
 * [IoDispatcher] -- so an app that reads a blocking source has to say where.
 */
internal class ContentResolverNmeaLogRepository
@Inject
constructor(
  private val contentResolver: ContentResolver,
  @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : NmeaLogRepository {

  override fun fixes(uri: String): Flow<PositionFix> = flow {
    // A picked file may be anything at all -- a JPEG, a directory, a file whose permission was
    // revoked between picking and reading -- so this throws rather than pretending. The
    // ViewModel turns whatever comes out into a message on screen.
    val stream = contentResolver.openInputStream(Uri.parse(uri)) ?: error("Could not open $uri")
    stream.use { emitAll(it.asSource().buffered().nmeaSentences().positions()) }
  }
    .flowOn(dispatcher)
}
