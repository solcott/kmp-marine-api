---
paths:
  - "marine-api/src/commonMain/**"
---

# Library source

All of it lives in `commonMain` under `io.github.solcott.marineapi` (`nmea/`, `nmea/sentence/`,
`nmea/io/`, `ais/`, `ublox/`). There is no `jvmMain` and no `net.sf.marineapi` tree.

## Design

The port from Java was a redesign, not a transliteration:

- Values are immutable `data class`es. Optional NMEA fields are **nullable with a `null` default**
  -- an empty field is how the format says "no data", not an error.
- Line-level failures are returned as `ParseResult`, never thrown. Reject impossible values with
  `require`; `SentenceRegistry.parse` and `AisRegistry.decode` catch `IllegalArgumentException` and
  report it as malformed, so it never reaches a caller.
- **No reflection.** It does not work on Native or JS.
- **No platform APIs.** This compiles to 16 targets including JS and Wasm; `java.*` and
  `Dispatchers.IO` are not available to it.

## Declarations

- `explicitApi()` is on: every public declaration needs an explicit visibility and return type.
- KDoc on every public declaration. **Say why, not what** -- the field layout is visible in the
  code; what is not visible is which reading the standard supports, what real receivers actually
  send, and where this disagrees with the Java original.
- No file headers, no `@author` tags. ktfmt (Google style) formats everything.
- Changing a public signature moves the pinned ABI in `marine-api/api/`; run
  `./gradlew :marine-api:apiDump` and read the diff.

## Deliberate disagreements with the Java original

Where behaviour differs from the pre-cutover implementation, the reason is on the declaration --
usually because the old one was wrong. **Do not "restore" any of these to match an old release.**
The known set:

- AIS types 4, 18 and 27 read the position-accuracy bit one place early
- type 27's navigational-status range was reversed
- type 9's flags were off by one and its speed scaled by ten
- `isDteReady` returned the bit uninverted (0 means ready)
- `NavStatus` read `V` as valid
- `PositionProvider` required a GGA or GLL in every cycle

The list drifts; the live one is

```
grep -rn "implementation this replaces\|Java implementation" marine-api/src/commonMain/kotlin/
```

## gpsd is the reference implementation

For anything the vendored corpus covers, gpsd's own decode (the `.log.chk` files under
`jvmTest/resources/data/gpsd/`) is the reference, and **a disagreement is a bug here until shown
otherwise**. Use the `/gpsd-crosscheck` skill to adjudicate one.
