package io.github.solcott.marineapi.nmea.sentence

import io.github.solcott.marineapi.nmea.ParseResult
import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.SentenceRegistry
import kotlin.test.assertIs

/**
 * Parses [line] and asserts it produced a [T].
 *
 * Failing here rather than at the first property access keeps a bad fixture -- a wrong checksum,
 * say -- from being reported as a null-pointer somewhere further down the test.
 */
internal inline fun <reified T : Sentence> parse(line: String): T {
  val result = SentenceRegistry.Default.parse(line)
  assertIs<ParseResult.Ok>(result, "failed to parse: $result")
  return assertIs<T>(result.sentence)
}
