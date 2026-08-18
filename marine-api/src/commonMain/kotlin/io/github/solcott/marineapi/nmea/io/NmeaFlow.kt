package io.github.solcott.marineapi.nmea.io

import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import io.github.solcott.marineapi.nmea.sentenceOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.io.Source

/**
 * Every line of this source, parsed, including the ones that failed.
 *
 * Nothing is dropped but a blank line: a checksum mismatch, a truncated sentence and a line that is
 * not NMEA at all all arrive as [ParseResult.BadChecksum] or [ParseResult.Malformed], so a caller
 * can count them, log them or give up on a feed that is producing nothing but noise. Use
 * [nmeaSentences] instead to see only what parsed, or [sentences] to narrow this flow after the
 * fact.
 *
 * ```
 * source.nmeaResults()
 *   .onEach { if (it !is ParseResult.Ok) log(it) }
 *   .sentences()
 *   .filterIsInstance<Rmc>()
 *   .collect { fix -> ... }
 * ```
 *
 * The flow is cold and reads the source when collected, so collecting twice reads twice -- from a
 * network or serial source, the second collection sees only what arrives after it starts. The
 * source is not closed when the flow ends; whoever opened it still owns it.
 *
 * **Reading blocks the collecting coroutine.** [Source] is a blocking interface, and this library
 * cannot choose a dispatcher for you, because there is no one dispatcher to choose:
 * `Dispatchers.IO` is public API on JVM and Android only. On Kotlin/Native it exists but is
 * `internal`, so use `Dispatchers.Default` or a pool of your own there; on JS and Wasm there are no
 * threads to move the work to at all. Given a source that can stall -- a socket, a serial port --
 * add `.flowOn(...)` with whatever your platform provides.
 *
 * @param registry which sentence types to recognise; unregistered types arrive as
 *   [io.github.solcott.marineapi.nmea.UnknownSentence] rather than as failures.
 */
public fun Source.nmeaResults(
  registry: SentenceRegistry = SentenceRegistry.Default
): Flow<ParseResult> = flow {
  val reader = NmeaLineReader(this@nmeaResults)
  while (true) {
    val line = reader.readLine() ?: break
    if (line.isBlank()) continue
    emit(registry.parse(line))
  }
}

/**
 * Every line of this source that parsed into a sentence.
 *
 * The same as [nmeaResults] with the failures dropped, which is what most callers want: a feed from
 * real hardware always carries some corruption, and a reader that cannot act on it gains nothing by
 * seeing it. When you do want to know, use [nmeaResults].
 *
 * ```
 * source.nmeaSentences()
 *   .filterIsInstance<Gga>()
 *   .collect { fix -> ... }
 * ```
 *
 * `filterIsInstance` is what replaces the reflection-based listener classes of the implementation
 * this succeeds -- it needs no runtime type lookup and works on every target.
 *
 * See [nmeaResults] for the notes on cold flows, source ownership and blocking.
 */
public fun Source.nmeaSentences(
  registry: SentenceRegistry = SentenceRegistry.Default
): Flow<Sentence> = nmeaResults(registry).sentences()

/**
 * Drops the failures from a flow of results.
 *
 * ```
 * results.sentences().filterIsInstance<Rmc>()
 * ```
 */
public fun Flow<ParseResult>.sentences(): Flow<Sentence> = mapNotNull { it.sentenceOrNull() }

/**
 * The raw text of each result, whether or not it parsed.
 *
 * For echoing a feed to a log or a file while also acting on it: the line is exactly as it arrived,
 * without the terminator.
 */
public fun Flow<ParseResult>.lines(): Flow<String> = map { it.line }
