package io.github.solcott.marineapi.ais

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.AisSentence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Reassembles the AIS sentences in this flow and decodes them.
 *
 * ```
 * source.nmeaSentences()
 *   .aisMessages()
 *   .messages()
 *   .filterIsInstance<AisPositionReport>()
 *   .collect { target -> plot(target.mmsi, target.position) }
 * ```
 *
 * Non-AIS sentences pass straight through and are ignored, so this composes with the rest of a feed
 * rather than needing one of its own -- a receiver interleaves `!AIVDM` with its own `$GPGGA`.
 *
 * **A multi-sentence message is held until it is complete.** A type 5 static report is 424 bits and
 * always arrives as two sentences; a type 21 with a long name can take three. A sequence that never
 * completes -- because a fragment was lost to interference, which happens constantly on a busy
 * waterway -- is discarded when the next sequence starts rather than being decoded from the pieces
 * that did arrive.
 *
 * Sequences from the two radio channels interleave, and are kept apart by [AisSentence.continues].
 * Anything held when the flow ends is dropped: an incomplete message is not a message.
 *
 * @param registry which message types to decode; the rest arrive as [AisResult.Unsupported]
 */
public fun Flow<Sentence>.aisMessages(
  registry: AisRegistry = AisRegistry.Default
): Flow<AisResult> = flow {
  val pending = mutableListOf<AisSentence>()
  collect { sentence ->
    if (sentence !is AisSentence) return@collect

    if (!sentence.isFragmented) {
      // The common case by a wide margin: one sentence, one message, nothing to hold.
      pending.clear()
      emit(registry.decode(Sixbit(sentence.payload, sentence.fillBits)))
      return@collect
    }

    val continuesPending = pending.isNotEmpty() && sentence.continues(pending.last())
    if (!continuesPending) {
      // Either this starts a sequence, or the one being held has been interrupted and will never
      // finish. Both mean the same thing for what is held.
      pending.clear()
      if (!sentence.isFirstFragment) return@collect
    }
    pending += sentence

    if (sentence.isLastFragment) {
      emit(registry.decode(joinFragments(pending)))
      pending.clear()
    }
  }
}

/** Drops the payloads that did not decode, leaving the messages. */
public fun Flow<AisResult>.messages(): Flow<AisMessage> = mapNotNull { it.messageOrNull() }

/**
 * Decodes this sentence on its own, or `null` if it is one fragment of a longer message.
 *
 * For a caller holding a single sentence rather than a flow. A fragmented message needs the
 * sentences around it, which only [aisMessages] has.
 */
public fun AisSentence.aisMessageOrNull(registry: AisRegistry = AisRegistry.Default): AisResult? =
  if (isFragmented) null else registry.decode(Sixbit(payload, fillBits))
