package io.github.solcott.marineapi.ublox

import io.github.solcott.marineapi.nmea.Sentence
import io.github.solcott.marineapi.nmea.sentence.Ubx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/** Builds a [UbloxMessage] from a UBX sentence. */
public fun interface UbloxMessageFactory {
  public fun create(sentence: Ubx): UbloxMessage
}

/**
 * Maps u-blox message ids to the code that decodes them.
 *
 * u-blox defines more `$PUBX` messages than this library implements; an id with no factory decodes
 * to `null` rather than failing, and the [Ubx] sentence still has its fields. Registration is by
 * lambda, replacing the reflective factory of the implementation this succeeds.
 */
public class UbloxRegistry
private constructor(private val factories: Map<Int, UbloxMessageFactory>) {

  /** Message ids this registry can decode. */
  public val ids: Set<Int>
    get() = factories.keys

  /** A copy of this registry that also decodes [id], replacing any existing entry. */
  public fun with(id: Int, factory: UbloxMessageFactory): UbloxRegistry =
    UbloxRegistry(factories + (id to factory))

  /** A copy of this registry with [id] removed. */
  public fun without(id: Int): UbloxRegistry = UbloxRegistry(factories - id)

  /** Decodes [sentence], or `null` if its message id has no factory here. */
  public fun decode(sentence: Ubx): UbloxMessage? = factories[sentence.messageId]?.create(sentence)

  public companion object {
    /** Every u-blox message this library decodes. */
    public val Default: UbloxRegistry =
      UbloxRegistry(
        mapOf(
          UbloxPositionVelocityTime.ID to
            UbloxMessageFactory(UbloxPositionVelocityTime.Companion::from),
          UbloxSatelliteStatusReport.ID to
            UbloxMessageFactory(UbloxSatelliteStatusReport.Companion::from),
        )
      )

    /** A registry that decodes nothing, for building one up from scratch. */
    public val Empty: UbloxRegistry = UbloxRegistry(emptyMap())
  }
}

/**
 * Decodes the u-blox messages in this flow.
 *
 * ```
 * source.nmeaSentences()
 *   .ubloxMessages()
 *   .filterIsInstance<UbloxPositionVelocityTime>()
 *   .collect { fix -> ... }
 * ```
 *
 * Sentences that are not UBX, and UBX sentences whose message id this registry does not decode, are
 * dropped. This is what replaces `AbstractUBXMessageListener` and the reflection it used to recover
 * its own type parameter.
 */
public fun Flow<Sentence>.ubloxMessages(
  registry: UbloxRegistry = UbloxRegistry.Default
): Flow<UbloxMessage> = mapNotNull { (it as? Ubx)?.let(registry::decode) }

/** Decodes this sentence, or `null` if [registry] has no factory for its message id. */
public fun Ubx.ubloxMessage(registry: UbloxRegistry = UbloxRegistry.Default): UbloxMessage? =
  registry.decode(this)
