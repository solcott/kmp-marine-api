# gpsd device logs

Recorded NMEA 0183 output from ~90 GNSS receivers and marine instruments, taken from the
[gpsd](https://gitlab.com/gpsd/gpsd) project's `test/daemon/` directory. They are used here as a
conformance corpus: `SampleDataTest` parses every line of every file and re-encodes each parsed
sentence, so a sentence type that mishandles what a real device emits fails the build.

## The `.chk` files

Each `<name>.log` has a `<name>.log.chk` beside it holding **gpsd's own decoder output** for that
input, interleaved line by line. Fetched from the same directory on 2026-08-16; they track gpsd
master and are a snapshot rather than a live reference.

`"scaled":false` means the numbers are raw bit-field values, so they compare directly against what
this library decodes with no unit conversion in between — and therefore no way for two errors to
cancel. `GpsdAisCheckTest` compares 1,308 AIS messages against them field by field.

This makes gpsd's decoder the reference implementation for anything these files cover. **A
disagreement with them is a bug in this library until shown otherwise.** It has already found two:
the type 21 name extension was being appended when the name field was not full, and text fields
were having their leading whitespace stripped where gpsd keeps it.

A sentence gpsd ignores produces no output line, and a type 24 part A produces none either — gpsd
caches it and emits one merged record when part B arrives, where this library reports the two
halves separately.

Only the pure-NMEA logs were taken. gpsd's directory also holds ~90 captures of binary protocols
(SiRF, TSIP, Zodiac, Garmin binary and others) which this library does not parse, and
`st-teseo-liv4f.log`, whose every line carries a doubled `$$` from a capture artifact.

Each file keeps gpsd's structured `#` header naming the receiver, chipset, firmware and submitter.

## License

These files are part of the GPSD project and are licensed BSD-2-Clause, which is what this
directory redistributes them under. The rest of this repository is LGPL v3; the two are compatible,
but the notice below must stay with these files.

> Compilation copyright is held by the GPSD project. All rights reserved.
>
> GPSD project copyrights are assigned to the project lead, currently Eric S. Raymond. Other
> portions of the GPSD code are Copyright 1997, 1998, 1999, 2000, 2001, 2002 by Remco Treffkorn,
> and others Copyright 2005 by Eric S. Raymond. For other copyrights, see individual files.
>
> SPDX short identifier: BSD-2-Clause
>
> Redistribution and use in source and binary forms, with or without modification, are permitted
> provided that the following conditions are met:
>
> 1. Redistributions of source code must retain the above copyright notice, this list of conditions
>    and the following disclaimer.
>
> 2. Redistributions in binary form must reproduce the above copyright notice, this list of
>    conditions and the following disclaimer in the documentation and/or other materials provided
>    with the distribution.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR
> IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
> FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
> CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
> DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
> DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
> IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
> OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

These are test resources only. They are not compiled into the published artifact, and the
`:marine-api` jar contains none of them.
