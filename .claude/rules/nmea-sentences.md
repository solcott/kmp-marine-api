---
paths:
  - "marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/nmea/sentence/**"
  - "marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/nmea/SentenceRegistry.kt"
  - "marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/nmea/SentenceFields.kt"
  - "marine-api/src/commonTest/kotlin/io/github/solcott/marineapi/nmea/sentence/**"
---

# NMEA sentence types

To add one, use the `/add-sentence` skill. These conventions hold for editing an existing one too.

## Shape

- One `data class` per sentence type in `nmea/sentence/`, grouped several to a file by theme
  (`WindSentences.kt`, `RadarSentences.kt`, ...). A few core fix types have their own file.
- Each carries `const val ID` and a `from(fields: SentenceFields)` in its companion, plus **private
  zero-based field-index constants**. The class name is not derivable from the ID for proprietary
  sentences (`Stalk` declares `"ALK"`, `Pashr` declares `"ASHR"`).
- KDoc on the type carries an `Example:` NMEA string -- a real one, not an invented one.
- Read fields through the `SentenceFields` accessors (`stringAt`, `doubleAt`, `intAt`, `charAt`,
  `timeAt`, `dateAt`, `codedAt`/`intCodedAt`, their `advisory*` counterparts, and `positionAt` in
  `PositionFields.kt`). Never split the raw sentence yourself.

## Load-bearing vs advisory -- choose deliberately

- A **load-bearing** field changes the meaning or magnitude of another field (a units character, a
  hemisphere, a reference direction). It stays strict: reading it wrong corrupts a value.
- An **advisory** field reports status, provenance or identity and nothing else depends on it. Use
  the `advisory*` accessors so one unrecognised character does not discard a whole sentence. Real
  receivers emit characters no standard lists.

The rule is stated on `SentenceFields.advisoryCodedAt`. When a real device's line fails and gpsd
reads it fine, a field held strictly that should be advisory is the usual cause.

## Two steps that get missed

A new type is not usable until it is registered in `SentenceRegistry.Default` (entries and imports
both alphabetical), and `FieldExposureTest.theExamplesCoverEveryRegisteredType` fails until a
**fully populated** example -- every field non-empty -- is in `fullyPopulated`. A hook warns on
write while either is outstanding.

## Tests

- `kotlin.test` in `commonTest`, named after the sentence (`GgaTest`) or added to the themed test
  file. Use the `parse<Xyz>(line)` helper from `ParseHelper.kt`, and `Checksum.append(...)` for a
  hand-written fixture.
- Test names are sentences: `readsEveryField`, `rejectsATimestampItCannotRead`.
- **Assert a value round-trip, not an exact re-encoded string.** `Double?.field()` trims trailing
  zeros, so `29.9870` comes back as `29.987`.
- Cover the empty-field case, not only the populated one.

## Sources for a field layout

In order of trust: the corpus `.log.chk` files, the gpsd [NMEA reference](https://gpsd.gitlab.io/gpsd/NMEA.html),
then <https://aprs.gids.nl/nmea/>. The official NMEA 0183 standard is a paid document and is not
available here -- say so in the KDoc rather than implying a conformance that was not checked.
