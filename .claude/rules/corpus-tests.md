---
paths:
  - "marine-api/src/jvmTest/**"
---

# `jvmTest`: the corpus and gpsd cross-check suites

These tests live in `jvmTest` rather than `commonTest` for one reason: they load logs from the
classpath.

## Resources

- **Load test resources from the classpath**, e.g.
  `GpsdCorpusTest::class.java.getResource("/data/gpsd")`. Never a filesystem-relative path --
  Gradle splits classes from processed resources, so no directory contains both.
- **`resources/data/gpsd/` is a vendored conformance corpus** -- 103 NMEA logs from ~90 receivers,
  taken from gpsd and **BSD-2-Clause, not LGPL**. The notice in that directory's `README.md` must
  stay with the files, and the logs stay byte for byte as they are. A hook asks before any edit
  there.
- **Each log has a `.log.chk` beside it: gpsd's own decoder output for that input**, with
  `"scaled":false` so numbers are raw bit fields. This makes gpsd the reference implementation: a
  disagreement is a bug here until shown otherwise (`/gpsd-crosscheck`). The `.log` and `.chk` are
  not line-for-line; match on content (MMSI, payload, timestamp), never on line number.

## The pinned numbers

| Suite | Pins |
| --- | --- |
| `nmea/GpsdCorpusTest` | per-file count of legitimately failing lines across all 103 logs; the exercised-type set |
| `nmea/io/CorpusFixTest` | fixes, satellite views, headings, fixes with a measured accuracy, and more |
| `nmea/io/GpsdFixCheckTest` | every capture gpsd gets a fix from, `positions()` must too |
| `ais/GpsdAisCheckTest` | AIS messages compared field by field; the `unsupported` type map; `FIELDS_PER_TYPE` |
| `nmea/SampleDataTest` | which ported types rest on real device data rather than reference tables |

**A count moving in either direction fails the build**, because both directions mean behaviour
changed. The assertions are the record -- several carry comments naming the device or rule behind
the number, and a re-pin that invalidates the comment must update it too. Use
`/repin-regressions`; never adjust a number just to get to green. If a pinned figure also appears in
`CLAUDE.md`'s "The regression signal", update it there in the same commit.
