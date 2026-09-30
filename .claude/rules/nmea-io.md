---
paths:
  - "marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/nmea/io/**"
  - "marine-api/src/commonTest/kotlin/io/github/solcott/marineapi/nmea/io/**"
  - "marine-api/src/jvmTest/kotlin/io/github/solcott/marineapi/nmea/io/**"
---

# The IO layer (`nmea/io`)

`Source.nmeaResults()` / `nmeaSentences()` read lines, and the correlating operators
(`positions()`, `satellites()`, headings) group sentences into update cycles.

## It must not choose a dispatcher

Blocking sources are read on the collecting coroutine. There is no one dispatcher to pick:
`Dispatchers.IO` is public API on **JVM and Android only** -- on Native it exists but is `internal`,
and JS/Wasm have no threads. Callers add `.flowOn(...)` with whatever their platform has. **Never
import `Dispatchers.IO` or call `flowOn` in `commonMain`.** A hook warns if either appears.

## Line splitting

**Do not replace `NmeaLineReader` with `kotlinx.io.readLine`.** That splits on `LF` alone; NMEA uses
`CRLF`, and corpus captures use a lone `CR` throughout or mix both in one file. An `LF`-only split
turns a `CR`-terminated feed into one unbounded line.

## Update cycles move pinned numbers

`CorpusFixTest` runs these operators over the 103-log gpsd corpus and pins their totals (fixes,
satellite views, headings, fixes carrying a measured accuracy, and more). **Any change to what
delimits an update cycle, or to which sentences a cycle records, moves them** -- deliberately or
not. `GpsdFixCheckTest` separately requires that every capture gpsd gets a fix from,
`positions()` does too.

Past defects found this way: `positions()` requiring a velocity, and `PositionProvider` requiring a
GGA or GLL in every cycle. When a number moves, read the assertion (it records which device or rule
the number came from) and use `/repin-regressions`. Never nudge a number to get green.
