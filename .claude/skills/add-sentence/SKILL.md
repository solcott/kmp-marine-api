---
name: add-sentence
description: Add support for a new NMEA 0183 sentence type to this library — a data class, its registration, a test, and a field-exposure example. Use when asked to add, implement, or support a new NMEA sentence (e.g. "add MWV support", "implement the XDR sentence").
---

# Adding a new NMEA sentence

Takes a three-letter sentence id (e.g. `XYZ`) and an example sentence string. **If the user did not supply an example NMEA string, ask for one** — the KDoc, the test and the field-exposure fixture all need a real one, and inventing a plausible-looking sentence is how wrong field layouts get baked in.

Everything is Kotlin in `marine-api/src/commonMain/kotlin/io/github/solcott/marineapi`, formatted by ktfmt. **The conventions a sentence type must follow — nullable fields, the `SentenceFields` accessors, load-bearing vs advisory, `require`, test style, trusted sources for a layout — are in `.claude/rules/nmea-sentences.md` and `.claude/rules/library-source.md`.** Read them before step 2 if they are not already loaded; this skill is the procedure, not the conventions.

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

   For every field, decide load-bearing vs advisory (`codedAt` vs `advisoryCodedAt`) per the rule — that is the judgement most worth getting right here. Reject impossible values in an `init` block with `require`.

3. **Register it** — add `Xyz.ID to SentenceFactory(Xyz::from)` to `SentenceRegistry.Default` in `nmea/SentenceRegistry.kt`, and the import, both **in alphabetical order**. Without this the sentence parses as `UnknownSentence`.

4. **Field-exposure example** — add a **fully populated** example line to `fullyPopulated` in `commonTest/.../sentence/FieldExposureTest.kt`, with a comment naming each field in order. Every field must be non-empty, including ones no real device sends. This test re-encodes the sentence and reports any field that was read but not exposed by a property — the failure mode no value assertion can catch.

   **Steps 3 and 4 are the ones that get missed.** `theExamplesCoverEveryRegisteredType` fails until both are done.

5. **Test** — `commonTest/.../sentence/XyzTest.kt`, or a class added to the themed test file. `kotlin.test` only. Use the `parse<Xyz>(line)` helper from `ParseHelper.kt`, and `Checksum.append("$GPXYZ,...")` when writing a fixture by hand rather than copying a real one.

   Assert a **value** round-trip (`parse(x.toNmeaString()) == x`), not an exact string, and cover the empty-field case as well as the populated one.

6. **Verify**:
   ```
   ./gradlew ktfmtFormat
   ./gradlew :marine-api:jvmTest --tests '*XyzTest'
   ./gradlew :marine-api:allTests     # the common suite on every target with a test run
   ```

   The common test count goes up by the number of tests added; the baseline is in `CLAUDE.md`'s "The regression signal", which is updated in the same commit.

## Notes

- If `jvmTest/resources/data/gpsd/` contains the new type, `GpsdCorpusTest` and `SampleDataTest` will start counting it; their expected sets need updating in the same commit.
- The `check-registration.sh` hook warns after each write while step 3 or 4 is still outstanding. It is advisory; `theExamplesCoverEveryRegisteredType` is the gate.
