package io.github.solcott.marineapi.nmea.io

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readString

private const val CARRIAGE_RETURN = '\r'.code.toByte()
private const val LINE_FEED = '\n'.code.toByte()

/**
 * Splits a [Source] into lines on `CR`, `LF` or `CRLF`.
 *
 * Not [kotlinx.io.readLine], which breaks on `LF` alone. NMEA terminates a sentence with `CRLF`,
 * but captures from real hardware are not that tidy: of the 103 device logs in this project's
 * conformance corpus, several use a lone `CR` throughout and at least one mixes both within a
 * single file. Reading those with an `LF`-only splitter yields one enormous line, and on a live
 * `CR`-terminated feed it would buffer without bound.
 *
 * Reading is one byte at a time rather than by scanning ahead for a terminator. Scanning would mean
 * looking for two different bytes and taking the earlier, and a search for the one that never
 * arrives buffers the whole stream. At NMEA's baud rates the difference does not matter.
 */
internal class NmeaLineReader(private val source: Source) {

  /**
   * Set after a line ended with `CR`, so that an `LF` arriving next is recognised as the second
   * half of a `CRLF` rather than the end of an empty line.
   *
   * A flag, rather than peeking at the next byte, because the pair can straddle two reads: on a
   * live feed the `CR` may be the last byte available and the `LF` may not have arrived yet.
   */
  private var pendingLineFeed = false

  /** The next line, without its terminator, or `null` at the end of the source. */
  fun readLine(): String? {
    val line = Buffer()
    while (true) {
      if (source.exhausted()) {
        return if (line.size == 0L) null else line.readString()
      }
      val byte = source.readByte()
      if (pendingLineFeed) {
        pendingLineFeed = false
        if (byte == LINE_FEED) continue
      }
      when (byte) {
        LINE_FEED -> return line.readString()
        CARRIAGE_RETURN -> {
          pendingLineFeed = true
          return line.readString()
        }
        else -> line.writeByte(byte)
      }
    }
  }
}
