---
name: add-ais-message
description: Add a decoder for an AIS message type to this library — a data class, its registration, the gpsd cross-check wiring and a test. Use when asked to add, implement, decode or support an AIS message type (e.g. "add AIS type 7", "decode binary broadcast messages", "support message 20").
---

# Adding an AIS message type

Takes a message type number. The types this library does not yet decode are pinned in
`GpsdAisCheckTest`, with the number of corpus messages of each:

```
6 → 68    7 → 2    8 → 73    17 → 5    20 → 14    23 → 2
```

**Check that map first** (`marine-api/src/jvmTest/kotlin/io/github/solcott/marineapi/ais/GpsdAisCheckTest.kt`,
in `everyDecodedMessageMatchesGpsd`) rather than trusting the list above — it is the live record, and
adding a type removes it from there.

**Types 6 and 8 are not one job each.** They are binary containers: the payload after the header is
interpreted according to a DAC (designated area code) and FID (functional ID) pair, and gpsd decodes
many combinations. In the corpus alone, `ais-nmea-type6-fid55.log.chk` carries DAC 1/FID 31 (a
meteorological report) and DAC 200/FID 10 (inland vessel data) as completely different field sets.
Decoding "type 8" means picking which DAC/FID subtypes to support and saying so in the KDoc. Do not
start with 6 or 8. Types 7, 17, 20 and 23 are single fixed layouts and are the right first ones.

Everything is Kotlin in `marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/ais`,
formatted by ktfmt. `explicitApi()` is on: every public declaration needs an explicit visibility and
return type.

## Steps

1. **Get the bit layout from the corpus, not from the web.**

   ```
   grep -l '"type":N,' marine-api/src/jvmTest/resources/data/gpsd/*.log.chk
   grep -h '"type":N,' <that file> | head
   ```

   Each record is gpsd's own decode of a payload in the matching `.log`, emitted with
   `"scaled":false`, so every number is the raw bit field with no unit conversion applied. Its keys
   name the fields and its values tell you what the bits must come out as — which is enough to
   derive and then confirm each range.

   **`WebFetch` is not an acceptable source for bit ranges.** It runs a small model over the page,
   and its rendering of gpsd's AIVDM tables for types 21, 24 and 27 was internally inconsistent and
   contradicted payloads that decode correctly. Bit ranges come from the `.chk` files or from raw
   source. If a type has no corpus coverage at all, say so in the KDoc rather than implying a
   conformance that was not checked.

2. **Read the closest analogue before writing anything.**

   | Analogue | File | What it shows |
   | --- | --- | --- |
   | `AisBaseStationReport` | `AisStationReports.kt:20` | the `TYPES: List<Int>` pattern for one class serving several types, and a composite field (`utcDateTimeAt`) that reads `null` rather than assembling from partly-valid parts |
   | `AisPositionReport` | `AisPositionReports.kt:24` | the position-and-velocity shape, and sentinel handling for unavailable values |
   | `AisStaticAndVoyageData` | `AisStaticReports.kt:31` | text fields, and the type 24 A/B split — the one case needing a factory function (`staticDataReportFrom`) instead of a `::from` reference, because the class is chosen by a field inside the payload |
   | `AisAidToNavigationReport` | `AisStationReports.kt:154` | a variable-length trailing field (`aidNameAt`) |

3. **Write the data class**, appended to whichever themed file its siblings belong in —
   `AisPositionReports.kt`, `AisStationReports.kt`, `AisStaticReports.kt`, or a new themed file if it
   is genuinely a new theme (types 6, 7, 8 and 13 would be `AisBinaryMessages.kt` /
   `AisAcknowledgements.kt`).

   ```kotlin
   public data class AisXyzReport(
     override val messageType: Int,
     override val repeatIndicator: Int,
     override val mmsi: Int,
     val someField: Int?,
   ) : AisMessage {

     public companion object {
       /** Message type decoded by this class. */
       public const val TYPE: Int = 7

       public fun from(bits: Sixbit): AisXyzReport =
         AisXyzReport(
           messageType = bits.uintAt(0, 6),
           repeatIndicator = bits.uintAt(6, 8),
           mmsi = bits.uintAt(8, 38),
           someField = bits.uintAt(40, 70).takeIf { it != 0 },
         )
     }
   }
   ```

   - The header is always the same three fields at bits 0–6, 6–8 and 8–38. `AisRegistry` will not
     even dispatch a payload shorter than 38 bits.
   - Implement the narrowest interface that fits: `AisMessage`, or `AisPositionMessage` (adds
     `position`, `isAccurate`), or `AisVesselPositionMessage` (adds `speedOverGround`,
     `courseOverGround`, `utcSecond`). All in `AisMessage.kt`. Do not add position properties to a
     message that carries no position.
   - Accessors on `Sixbit`: `uintAt(from, to)`, `intAt(from, to)` (two's complement),
     `booleanAt(index)`, `booleanAtOrNull(index)`, `stringAt(from, to)`. Internal extensions in
     `AisMessage.kt` cover the recurring composites: `positionAt`, `speedOverGroundAt`,
     `courseOverGroundAt`, `headingAt`, `utcSecondAt`, `rateOfTurnAt`, `rateOfTurnCodeAt`, and
     `codedAt(from, to, Enum.entries)` for an enum-coded field. Reuse these rather than
     re-deriving a scale factor.
   - **Ranges are half-open**: `uintAt(0, 6)` is six bits. Off-by-one here is the single most common
     mistake, and it usually still decodes to a plausible-looking number.
   - **A sentinel meaning "not available" becomes `null`, and the sentinel value goes in a
     comment.** That is the library's convention and it is why `rateOfTurnCode` keeps a raw
     counterpart — see step 5 on how the cross-check handles it.
   - Reject impossible values with `require`. `AisRegistry.decode` catches
     `IllegalArgumentException` and returns `AisResult.Malformed`, so it never escapes to a caller.

4. **Register it** in `AisRegistry.Default`, in `AisRegistry.kt` (around line 110):

   ```kotlin
   put(AisXyzReport.TYPE, AisMessageFactory(AisXyzReport::from))
   // or, for a class serving several types:
   for (type in AisXyzReport.TYPES) put(type, AisMessageFactory(AisXyzReport::from))
   ```

   Without this the payload decodes to `AisResult.Unsupported` and nothing else runs.

5. **Wire the gpsd cross-check. These three edits are the ones that get missed**, all in
   `marine-api/src/jvmTest/kotlin/io/github/solcott/marineapi/ais/GpsdAisCheckTest.kt`:

   1. **A branch in the `when (message)` inside `compare()`** — one `check(name, gpsd, ours)` per
      field, where `name` is **gpsd's own JSON key** (`"mmsi1"`, `"wspeed"`, …). `check` skips a
      field gpsd did not emit, so a key gpsd omits costs nothing. Where this library nulls a
      sentinel, compare against the raw value the way the existing branches do:
      `check("turn", json.int("turn"), message.rateOfTurnCode ?: RATE_OF_TURN_UNAVAILABLE)`.
   2. **An entry in `FIELDS_PER_TYPE`** (around line 317) equal to the number of `check` calls in
      that branch, including the three header fields checked for every message. It only feeds a
      `fields > 9_000` assertion, so it is a coverage tripwire, not a correctness one — but a wrong
      number here quietly weakens that tripwire.
   3. **Remove the type from the pinned `unsupported` map** (around line 74). Leave it and the build
      fails with "message types gpsd decodes and this library does not", which reads like a
      regression rather than like a forgotten step.

6. **A test** in `commonTest/kotlin/io/github/solcott/marineapi/ais/AisMessageTest.kt`. `kotlin.test`
   only. Take the payload from a corpus log that has a `.chk` beside it and take the expected values
   from **that `.chk` record**, not from arithmetic done by hand — otherwise the test and the decoder
   share one author's misreading and agree with each other while both being wrong.

   Name tests as sentences describing the behaviour: `readsEveryField`,
   `reportsNoAcknowledgementAsNull`.

7. **Verify**:

   ```
   ./gradlew ktfmtFormat
   ./gradlew :marine-api:jvmTest --tests '*GpsdAisCheckTest'   # the real gate
   ./gradlew :marine-api:jvmTest --tests '*AisMessageTest'
   ./gradlew :marine-api:allTests
   ```

   `GpsdAisCheckTest` is the gate that matters: it now compares the new type field by field against
   gpsd across every corpus message of it.

## Notes

- **gpsd is the reference implementation here, and a disagreement is a bug in this library until
  shown otherwise.** The `.chk` files are the same project's working decoder run over real
  transponder traffic, not prose that has to be read correctly. If a mismatch looks like gpsd's
  fault, use the `gpsd-crosscheck` skill before concluding that.
- Moving a type out of `unsupported` changes the pinned `compared` count (1308) and can change the
  corpus counts in `GpsdCorpusTest` and `CorpusFixTest`. Re-pin those deliberately — see the
  `repin-regressions` skill. Do not nudge a number to make the build green.
- **This library deliberately disagrees with the Java original in several AIS decoders**, with the
  reason on each declaration: the position-accuracy bit in types 4, 18 and 27 was read one place
  early, type 27's navigational-status range was reversed, type 9's flags were off by one and its
  speed scaled by ten, and `isDteReady` returned the bit uninverted. Run
  `grep -rn "implementation this replaces\|Java implementation" marine-api/src/commonMain/kotlin/`
  to see them all. Do not "restore" any of these.
- The sentence layer is a separate concern: `AisSentence` (VDM/VDO) already carries the payload and
  fill bits, and `joinFragments` reassembles multi-sentence messages. Nothing at this layer needs
  touching to add a message type.
