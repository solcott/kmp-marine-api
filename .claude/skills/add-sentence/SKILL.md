---
name: add-sentence
description: Add support for a new NMEA 0183 sentence type to this library — a data class, its registration, a test, and a field-exposure example. Use when asked to add, implement, or support a new NMEA sentence (e.g. "add MWV support", "implement the XDR sentence").
---

# Adding a new NMEA sentence

Takes a three-letter sentence id (e.g. `XYZ`) and an example sentence string. **If the user did not supply an example NMEA string, ask for one** — the KDoc, the test and the field-exposure fixture all need a real one, and inventing a plausible-looking sentence is how wrong field layouts get baked in.

Everything is Kotlin in `marine-api/src/commonMain/kotlin/io/github/solcott/marineapi`, formatted by ktfmt. No file headers; no `@author` tags.

## Steps

1. **Read a close analogue first.** Find an existing sentence with a similar field layout — `Gga.kt` for position-and-time, `Dpt.kt` for a short numeric one, `WindSentences.kt` for a group sharing a helper — and read the data class and its test together. Match their structure rather than inventing one.

2. **Data class** — in `nmea/sentence/`, either its own file (`Xyz.kt`) or appended to the themed file its siblings live in (`WindSentences.kt`, `RadarSentences.kt`, …). Follow this shape:

   ```kotlin
   public data class Xyz(
     override val talker: TalkerId,
     val someValue: Double? = null,
     val someCode: SomeEnum? = null,
   ) : Sentence {

     override val id: String get() = ID

     override fun toNmeaString(): String =
       buildNmea(talker, ID, listOf(someValue.field(), someCode.field()))

     public companion object {
       public const val ID: String = "XYZ"

       private const val SOME_VALUE = 0
       private const val SOME_CODE = 1

       public fun from(fields: SentenceFields): Xyz =
         Xyz(
           talker = fields.talker,
           someValue = fields.doubleAt(SOME_VALUE),
           someCode = fields.codedAt(SOME_CODE, SomeEnum.entries),
         )
     }
   }
   ```

   - Optional fields are **nullable with a `null` default**. An empty NMEA field is how the format says "no data"; it is not an error.
   - Use the accessors on `SentenceFields` — `stringAt`, `doubleAt`, `intAt`, `charAt`, `timeAt`, `dateAt`, `codedAt`/`intCodedAt` (and their `advisory*` counterparts), plus the `positionAt` extension in `PositionFields.kt`. Do not split the raw sentence yourself.
   - **Choose `codedAt` vs `advisoryCodedAt` deliberately.** A *load-bearing* field changes the meaning or magnitude of another field (a units character, a hemisphere, a reference direction) and stays strict. An *advisory* field reports status, provenance or identity and nothing else depends on it — use the `advisory*` accessors so one unrecognised character does not discard a whole sentence. The doc on `SentenceFields.advisoryCodedAt` states the rule.
   - Reject impossible values in an `init` block with `require`. `SentenceRegistry.parse` catches `IllegalArgumentException` and reports `ParseResult.Malformed`, so this never escapes to a caller.

3. **Register it** — add `Xyz.ID to SentenceFactory(Xyz::from)` to `SentenceRegistry.Default` in `nmea/SentenceRegistry.kt`, and the import, both **in alphabetical order**. Without this the sentence parses as `UnknownSentence`.

4. **Field-exposure example** — add a **fully populated** example line to `fullyPopulated` in `commonTest/.../sentence/FieldExposureTest.kt`, with a comment naming each field in order. Every field must be non-empty, including ones no real device sends. This test re-encodes the sentence and reports any field that was read but not exposed by a property — the failure mode no value assertion can catch.

   **Steps 3 and 4 are the ones that get missed.** `theExamplesCoverEveryRegisteredType` fails until both are done.

5. **Test** — `commonTest/.../sentence/XyzTest.kt`, or a class added to the themed test file. `kotlin.test` only. Use the `parse<Xyz>(line)` helper from `ParseHelper.kt`, and `Checksum.append("$GPXYZ,...")` when writing a fixture by hand rather than copying a real one.

   - Name tests as sentences describing the behaviour: `readsEveryField`, `rejectsAUnitItCannotRead`, `keepsANegativeElevation`.
   - Prefer asserting a **value** round-trip (`parse(x.toNmeaString()) == x`) over an exact string match. `Double?.field()` trims trailing zeros, so `29.9870` re-encodes as `29.987` and a string assertion fails for no good reason.
   - Cover the empty-field case, not just the populated one.

6. **Verify**:
   ```
   ./gradlew ktfmtFormat
   ./gradlew :marine-api:jvmTest --tests '*XyzTest'
   ./gradlew :marine-api:allTests     # the common suite runs on all seven targets
   ```

## Notes

- Sources for field layouts, in order of trust: the gpsd [NMEA reference](https://gpsd.gitlab.io/gpsd/NMEA.html), then <https://aprs.gids.nl/nmea/>. **The official NMEA 0183 standard is a paid document and is not available here** — say so in the KDoc rather than implying a conformance that was not checked.
- If `jvmTest/resources/data/gpsd/` contains the new type, `GpsdCorpusTest` and `SampleDataTest` will start counting it; their expected sets need updating in the same commit.
- Write KDoc that says **why**, not what. The field order is in the code. What is not in the code: which reading of an ambiguous field the sentence uses and on what evidence, what real receivers actually send, and where this deliberately differs from the Java implementation that preceded it.
