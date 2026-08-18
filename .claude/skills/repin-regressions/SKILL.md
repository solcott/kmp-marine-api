---
name: repin-regressions
description: Deliberately update a pinned regression number after an intended behaviour change — the gpsd corpus failure counts, the correlated fix/satellite/heading totals, the AIS comparison counts, or the field-exposure and sample-data coverage sets. Use when a pinned count has moved and the change behind it was intentional, or when asked to update/re-pin/fix a corpus number or expected count.
---

# Re-pinning a regression number

This library's real regression signal is a set of numbers pinned inside its test suites. They are not
arbitrary and they are not fixtures: they record how many fixes, satellite views and headings the
correlating operators extract from 103 real device logs, and how many lines of those logs
legitimately fail to parse. **A number moving in either direction fails the build**, because both
directions mean behaviour changed.

The `regression-checker` agent detects that a number moved and says which one. It cannot decide
whether it *should* have. That is what this skill is for.

## Step 1 — establish intent, and be willing to stop here

**A moved number is a behaviour change. Ask what change caused it before touching any assertion.**

- The change was intentional (a fix to `positions()` or `satellites()`, a new sentence or AIS type, a
  deliberate shift in what delimits an update cycle, a field moved between load-bearing and
  advisory) → re-pin, and say in the commit message which change moved it and why the new number is
  right.
- The change was not intentional, or nobody can name the change → **the number is the bug report.**
  Go fix the code. Do not re-pin.
- Unclear which → use the `gpsd-crosscheck` skill. The `.chk` files decide whether the new behaviour
  or the old one matches real hardware.

**Never adjust a number to make the build green.** That converts the project's strongest regression
signal into a rubber stamp, and it is silent when it happens.

## Step 2 — read the actual values from the failure output

Run the suite and take the numbers from the assertion message. Do not compute them by hand and do not
guess a direction:

```
./gradlew :marine-api:jvmTest --tests '*GpsdCorpusTest' --tests '*CorpusFixTest' \
                              --tests '*GpsdAisCheckTest' --tests '*GpsdFixCheckTest'
./gradlew :marine-api:jvmTest --tests '*SampleDataTest'
./gradlew :marine-api:allTests    # FieldExposureTest and the rest of the common suite
```

`GpsdCorpusTest` prints its diff as expected-vs-actual maps with the unexpected keys named, which is
the format to read rather than reconstructing.

## Step 3 — where the pins live

All paths under `marine-api/src/`. Line numbers drift; the names do not.

| Pin | File | What it is |
| --- | --- | --- |
| `expectedFailures` | `jvmTest/.../nmea/GpsdCorpusTest.kt` (~line 30) | per-file map of legitimately failing lines. Fails in **either** direction |
| corpus file count (`103`) | same file, `corpusFiles()` test | changes only when logs are added or removed |
| exercised-type set | same file (~line 129) | which ported types the corpus actually covers; the comment above it explains each addition and must be kept true |
| `2089` fixes, `1697` with altitude, `1848` with a date | `jvmTest/.../nmea/io/CorpusFixTest.kt` (~lines 47–49) | output of `positions()` across the corpus |
| the four-device readiness map (`ait250` 34, `april6_2019` 2, `magellan-ec10` 21, `meinberg-gps164` 70) | same file (~line 62) | the captures that drove the readiness rule; a change here means the rule itself moved |
| `14` silent logs | same file (~line 85) | captures where every sentence reports no fix |
| `2201` views, `20_429` satellites | same file (~lines 114–115) | output of `satellites()` |
| `36` headings | same file (~line 156) | output of the heading operator |
| `1308` compared, `unsupported` map | `jvmTest/.../ais/GpsdAisCheckTest.kt` (~lines 72–74) | AIS messages checked against gpsd, and the types gpsd decodes that this library does not |
| `FIELDS_PER_TYPE` | same file (~line 317) | per-type field count feeding the `fields > 9_000` coverage tripwire |
| `expectedCoverage` | `jvmTest/.../nmea/SampleDataTest.kt` (~line 91) | which ported types have real device data behind them |
| `fullyPopulated` | `commonTest/.../nmea/sentence/FieldExposureTest.kt` (~line 27) | one fully populated example per registered type |
| common test count | — | 403 per target; JVM runs 428 (the same 403 plus 25 corpus tests) |

**The assertions are the record — read them rather than any summary of them, including this table and
including `CLAUDE.md`.** Several carry comments explaining exactly which device or which rule the
number came from; a re-pin that invalidates the comment has to update the comment too.

## Step 4 — update CLAUDE.md in the same commit

`CLAUDE.md`'s "The regression signal" section duplicates several of these figures in prose. **It has
drifted before** — commit `f77263b` existed to correct a stale fix count and stale test counts that
lagged the suites for several commits, and the `regression-checker` agent is explicitly instructed to
distrust it and read the test sources instead.

If a number you changed appears there, change it in the same commit. If a number there is *already*
wrong for an unrelated reason, fix that too and say so.

## Step 5 — verify

```
./gradlew :marine-api:jvmTest      # the corpus suites
./gradlew :marine-api:allTests     # the common suite on all seven targets
```

**Check the reported test count, not the exit code.** `jvmTest` must use `useJUnit()`: `kotlin-test`
resolves to `kotlin-test-junit` under it, and that is what runs the common tests on the JVM target.
Get half of a JUnit 5 migration right and zero tests run while the build reports success. Expect 428
on `jvmTest` and 403 common tests per target unless you deliberately added some.
