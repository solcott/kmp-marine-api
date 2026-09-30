---
paths:
  - "**/io/github/solcott/marineapi/ais/**"
---

# AIS decoding

To add a message type, use the `/add-ais-message` skill. These conventions hold for editing an
existing decoder too.

## Bit ranges

- **Bit ranges come from the corpus `.log.chk` files or from raw source -- never from a `WebFetch`
  summary.** WebFetch runs a small model over the page, and its rendering of gpsd's AIVDM tables for
  types 21, 24 and 27 was internally inconsistent and contradicted payloads that decode correctly.
  The `.chk` records are emitted with `"scaled":false`, so every number is the raw bit field.
- **Ranges are half-open**: `uintAt(0, 6)` is six bits. Off-by-one here is the most common mistake,
  and it usually still decodes to a plausible-looking number.
- The header is always bits 0-6, 6-8 and 8-38 (type, repeat, MMSI). `AisRegistry` does not dispatch
  a payload shorter than 38 bits.
- Reuse the internal extensions in `AisMessage.kt` (`positionAt`, `speedOverGroundAt`,
  `courseOverGroundAt`, `headingAt`, `utcSecondAt`, `rateOfTurnAt`, `codedAt`, ...) rather than
  re-deriving a scale factor.

## Values

- **A sentinel meaning "not available" becomes `null`, with the sentinel value in a comment.** Where
  the gpsd cross-check needs the raw value, keep a raw counterpart (as `rateOfTurnCode` does).
- A composite field (e.g. `utcDateTimeAt`) reads `null` rather than assembling from partly-valid
  parts.
- Implement the narrowest interface: `AisMessage`, `AisPositionMessage` or
  `AisVesselPositionMessage`. Do not add position properties to a message that carries no position.
- Reject impossible values with `require`; `AisRegistry.decode` returns `AisResult.Malformed`.

## Deliberate fixes -- do not undo

Types 4, 18 and 27 read the position-accuracy bit one place early in the Java original; type 27's
navigational-status range was reversed; type 9's flags were off by one and its speed scaled by ten;
`isDteReady` returned the bit uninverted. The reason is on each declaration.

## The cross-check

`jvmTest/.../ais/GpsdAisCheckTest.kt` compares every corpus AIS message field by field against gpsd.
A decodable class needs: registration in `AisRegistry.Default`, a branch in `compare()` keyed by
**gpsd's own JSON field names**, a `FIELDS_PER_TYPE` entry, and removal from the pinned
`unsupported` map. A hook warns on write while any is outstanding. `AisStaticDataReport` (type 24
part A) is exempt: gpsd emits no record for it.

Types 6 and 8 are binary containers keyed by DAC/FID, not single layouts -- supporting "type 8"
means choosing which subtypes, and saying so in the KDoc.
