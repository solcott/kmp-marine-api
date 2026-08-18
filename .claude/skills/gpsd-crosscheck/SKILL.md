---
name: gpsd-crosscheck
description: Adjudicate a disagreement between this library and gpsd using the vendored .log.chk reference decodes — deciding whether a decoded value, a failing corpus line or an unexpected count is this library's bug or gpsd's. Use when a corpus test fails and it is unclear which side is right, when a decoded field looks wrong, or when asked to check behaviour against gpsd or against real device data.
---

# Cross-checking against gpsd

This is a **debugging** skill, not an authoring one. It answers one question: when this library and
gpsd disagree, who is wrong?

## The premise, and it is strong

`marine-api/src/jvmTest/resources/data/gpsd/` holds 103 NMEA logs from around 90 receivers, vendored
from gpsd. Beside each `X.log` is an `X.log.chk`: **gpsd's own decoder output for that exact input**,
emitted with `"scaled":false` so every number is the raw bit field or raw field value, with no unit
conversion in between and therefore no room for two errors to cancel.

That makes gpsd the reference implementation for anything the corpus covers. It is not prose that has
to be read correctly — it is the same project's working code run over real transponder and receiver
traffic. **A disagreement is a bug in this library until shown otherwise.** Four defects the port had
carried were found exactly this way (listed below), and in none of them was gpsd wrong.

Corollary: **do not use `WebFetch` to settle a bit-level question.** It runs a small model over the
page, and its rendering of gpsd's AIVDM bit tables for types 21, 24 and 27 was internally
inconsistent and contradicted payloads that decode correctly. The `.chk` files or raw source, or
nothing.

## Before concluding anything: check whether the disagreement is deliberate

**This library intentionally differs from the pre-cutover Java implementation in a number of
places**, each with the reason on the declaration, and almost always because the old behaviour was
wrong. Restoring one of these to match an old release is a regression, not a fix. The known set:

- the position-accuracy bit in AIS types 4, 18 and 27 — the old code read it one place early
- type 27's navigational-status range, which was reversed
- type 9's flags, off by one, and its speed, scaled by ten
- `isDteReady`, which returned the bit uninverted (0 means ready)
- `NavStatus`, which read `V` as valid when it means the opposite
- `PositionProvider`, which required a GGA or GLL in every cycle

That list drifts. Get the live one:

```
grep -rn "implementation this replaces\|Java implementation" marine-api/src/commonMain/kotlin/
```

If a cross-check appears to contradict one of these, **read the KDoc on the declaration first**. It
will usually explain the disagreement, and gpsd will usually be on this library's side.

## Locating the record for a given line

The `.log` and the `.chk` are **not** line-for-line. gpsd emits nothing for a sentence it cannot
decode, nothing for an AIS type 24 part A, and one record per completed multi-sentence message rather
than one per sentence. **Match on content, never on line number**:

```
grep -n 'GPGGA' marine-api/src/jvmTest/resources/data/gpsd/<file>.log | head
grep -n '"mmsi":244620320' marine-api/src/jvmTest/resources/data/gpsd/<file>.log.chk
grep -n '"type":21,' marine-api/src/jvmTest/resources/data/gpsd/<file>.log.chk
```

Useful anchors, in rough order of how well they pin a record: the MMSI, the payload string itself,
the timestamp, then the type. For AIS, `GpsdAisCheckTest.aisRecordsOf` already does this pairing —
read it rather than reinventing the correlation.

An AIS record looks like this, and the key names are the ones the cross-check test uses:

```
{"class":"AIS","type":7,"repeat":0,"mmsi":244620320,"scaled":false,"mmsi1":2268402,"mmsi2":0,...}
```

## What the harnesses already cover, so a manual check is not duplicated

| Test | Location | Covers |
| --- | --- | --- |
| `GpsdAisCheckTest` | `jvmTest/.../ais/` | 1,308 AIS messages, field by field, against the `.chk` records |
| `GpsdFixCheckTest` | `jvmTest/.../nmea/io/` | every capture gpsd gets a fix from, `positions()` must too |
| `GpsdCorpusTest` | `jvmTest/.../nmea/` | parses and re-encodes every line of all 103 logs; per-file counts of the lines that legitimately fail |
| `CorpusFixTest` | `jvmTest/.../nmea/io/` | the correlating operators over the same logs, with the totals pinned |
| `SampleDataTest` | `jvmTest/.../nmea/` | which ported types rest only on reference tables rather than real device data |

If the question is "does this hold across the corpus", the answer is to extend or read one of these,
not to write a one-off script.

## The four defects this found, as worked examples

Each shows what a genuine disagreement looks like, and all four were this library's fault:

1. **Type 21 name extension appended when the name field was not full.** The extension only applies
   when the base name occupies its whole field; gpsd's decode of the same payload had the short name.
2. **AIS text losing its leading whitespace.** A trim that should only have been a trailing trim.
3. **`positions()` requiring a velocity**, so cycles from receivers that report position without
   speed produced no fix at all — visible as gpsd getting a fix from a capture where this library
   got none.
4. **RMC's date treated as load-bearing** when it is not, so one bad date discarded the whole
   sentence.

The corpus round-trip in `GpsdCorpusTest`, separately, surfaced an `IllegalArgumentException`
escaping `parse`, the load-bearing/advisory field split, VTG's format detection and the 8-decimal
coordinate cap.

## The fix is usually the load-bearing/advisory judgement

When a real device's line fails to parse and gpsd reads it fine, the cause is most often a field held
strictly that should not be:

- A **load-bearing** field changes the meaning or magnitude of another field — a units character, a
  hemisphere, a reference direction. It stays strict: reading it wrong corrupts a value.
- An **advisory** field reports status, provenance or identity, and nothing else depends on it. Use
  the `advisory*` accessors on `SentenceFields` so one unrecognised character does not discard an
  otherwise good sentence.

The rule is documented on `SentenceFields.advisoryCodedAt`. Real receivers emit characters no
standard lists, and that is the case this split exists for.

## After a fix

A behaviour change moves the pinned counts, and every one of them fails the build in **either**
direction. Re-pin them deliberately with the `repin-regressions` skill — never adjust a number just
to get to green, because a moved count is the signal these suites exist to produce.
