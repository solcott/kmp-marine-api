# KMP Marine API
[![License](https://img.shields.io/badge/License-LGPL%20v3-brightgreen.svg)](./LICENSE)
[![Build & Test](https://github.com/solcott/kmp-marine-api/actions/workflows/build.yml/badge.svg)](https://github.com/solcott/kmp-marine-api/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.solcott/kmp-marine-api)](https://central.sonatype.com/artifact/io.github.solcott/kmp-marine-api)
[![API docs](https://javadoc.io/badge2/io.github.solcott/kmp-marine-api/API%20docs.svg)](https://javadoc.io/doc/io.github.solcott/kmp-marine-api)

- [KMP Marine API](#kmp-marine-api)
  - [About](#about)
    - [Features](#features)
    - [Supported platforms](#supported-platforms)
    - [Licensing](#licensing)
    - [Disclaimer](#disclaimer)
    - [Requirements](#requirements)
    - [Usage](#usage)
  - [Differences from Java Marine API](#differences-from-java-marine-api)
  - [Supported Protocols](#supported-protocols)
    - [NMEA 0183](#nmea-0183)
    - [AIS](#ais)
    - [Raymarine SeaTalk<sup>1</sup>](#raymarine-seatalksup1sup)
    - [u-blox](#u-blox)
  - [Distribution](#distribution)
    - [Gradle](#gradle)
    - [Maven](#maven)
    - [Building from source](#building-from-source)
  - [Contributing](#contributing)
  - [References](#references)
    - [National Marine Electronics Association](#national-marine-electronics-association)
    - [Navigation Center of U.S. Department of Homeland Security](#navigation-center-of-us-department-of-homeland-security)
    - [Product Manuals and User Guides](#product-manuals-and-user-guides)
    - [Wikipedia](#wikipedia)
    - [Miscellaneus](#miscellaneus)
    - [No longer available](#no-longer-available)

## About

KMP Marine API is a **Kotlin Multiplatform** parser library for
[NMEA 0183](http://en.wikipedia.org/wiki/NMEA_0183), AIS, u-blox and Raymarine
SeaTalk<sup>1</sup> — the data produced by electronic marine devices such as GPS receivers,
echo sounders, AIS transponders and weather instruments.

It is a fork of [ktuukkan/marine-api](https://github.com/ktuukkan/marine-api) ("Java Marine
API"), rewritten in Kotlin. The same code now runs on the JVM, Android, iOS, macOS, Linux,
Windows, Android NDK, JS and WebAssembly. The rewrite was a redesign rather than a
transliteration — see [Differences from Java Marine API](#differences-from-java-marine-api)
before upgrading from the Java library.

### Features

- Reads NMEA 0183 from anything that can produce a `kotlinx-io` `Source`
    - A file, a serial port, a TCP/IP or UDP socket, an in-memory buffer
    - The library never opens one itself, so it needs no platform IO of its own
- Turns the stream into a `Flow` of immutable sentence values for [selected sentences](#nmea-0183)
    - `filterIsInstance<Gga>()` picks a type; no listener interfaces, no reflection
- Optional NMEA fields are **nullable**, not exceptions: an empty field reads as `null`
- Line-level failures are **values**, not exceptions: a bad checksum arrives as a `ParseResult`
  you can count, log or ignore
- **No reflection anywhere** — which is what lets the same code run on Native and JS, and what
  makes the Proguard configuration two lines instead of a list of kept attributes
- Additional sentence types may be registered at runtime, without compiling the library
- Sentence encoding with a checksum that always matches the body
- Several sentences aggregate into one value with the flow operators `positions()`,
  `headings()` and `satellites()` — for example, to record current position and speed
- Decoding of selected [AIS messages](#ais), reassembled across multiple sentences
- The [u-blox](#u-blox) vendor extension, and the NMEA 0183 layer of
  [Raymarine SeaTalk<sup>1</sup>](http://www.raymarine.com/view/?id=5535)
- Utilities and enumerations for handling the extracted data

### Supported platforms

Fourteen targets, all built from the same `commonMain` source — there is no per-platform
source set in the library at all.

|Platform |Targets
|---      |---
|JVM      |`jvm` — bytecode 17
|Android  |`android` — minSdk 24, compileSdk 37
|Apple    |`iosArm64`, `iosSimulatorArm64`, `macosArm64`
|Native   |`linuxX64`, `linuxArm64`, `mingwX64`
|Android NDK |`androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64`
|Web      |`js` (browser and Node), `wasmJs` (browser and Node), `wasmWasi` (Node)

The x64 Apple variants are deliberately absent: `macosX64` and `iosX64` are deprecated, and
arm64 covers current hardware and simulators.

### Licensing

KMP Marine API is free software: you can redistribute it and/or modify it
under the terms of the [GNU Lesser General Public License](./LICENSE) published
by the Free Software Foundation, either version 3 of the License, or (at your
option) any later version.

KMP Marine API is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with KMP Marine API. If not, see http://www.gnu.org/licenses/.

- See also: [LGPL and Java](https://www.gnu.org/licenses/lgpl-java.en.html), which covers what
  dynamic linking means for the JVM and Android targets

### Disclaimer

KMP Marine API is not official NMEA 0183 software. Further, it is not related
to [National Marine Electronics Association](http://www.nmea.org/).

The interpretation of NMEA 0183 and related protocols is based entirely on
varying set of publicly available [documents](#references) in the Internet.
Thus, it is not guaranteed that the library follows and implements these
standards correctly.

Electronic devices and software do not replace safe navigation practices and
should never be your only reference.

### Requirements

* Kotlin 2.4 or newer, and a build that reads Gradle module metadata — see
  [Maven](#maven) if yours does not
* On the JVM: Java 17 or newer
* On Android: minSdk 24

These arrive transitively, and are part of the public API rather than hidden behind it:
[kotlinx-coroutines-core](https://github.com/Kotlin/kotlinx.coroutines),
[kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) and
[kotlinx-io-core](https://github.com/Kotlin/kotlinx-io).

**Serial port drivers are not a dependency of this library.** It reads a `Source` and never opens
a port, so the driver is yours to choose. On the JVM,
[nrjavaserial](https://github.com/NeuronRobotics/nrjavaserial) is what `:examples` uses;
[PureJavaComm](http://www.sparetimelabs.com/purejavacomm),
[RXTX](http://rxtx.qbang.org) and the original Java Communications API work the same way. On
Android, a USB-serial library or `UsbDeviceConnection` gives you an `InputStream`, which is all
`asSource()` needs.

A Bluetooth receiver needs no driver at all. Those speak the Serial Port Profile, and SPP is
already a byte stream by the time it reaches you: `BluetoothSocket.inputStream` on Android — see
`:examples-android` — an RFCOMM device node (`rfcomm bind /dev/rfcomm0 <MAC> 1`) on Linux, or a
virtual COM port on Windows, each of which `:examples`' serial demo will read if you name it.

### Usage

Read a log, and act on one sentence type:

```kotlin
SystemFileSystem.source(Path("/var/log/nmea.log")).buffered()
    .nmeaSentences()
    .filterIsInstance<Gga>()
    .flowOn(Dispatchers.IO)          // the library will not choose a dispatcher for you
    .collect { gga -> println(gga.position) }
```

`nmeaSentences()` drops the lines that did not parse. To see them instead — a feed from real
hardware always carries some corruption — use `nmeaResults()`, which reports every line:

```kotlin
source.nmeaResults()
    .onEach { if (it !is ParseResult.Ok) log(it) }
    .sentences()
```

A whole fix is spread across several sentences, so gather one update cycle into one value:

```kotlin
source.nmeaSentences().positions().collect { fix ->
    println("${fix.dateTime}  ${fix.position}  ${fix.speedKnots} kn")
}
```

Parsing a single line, with no IO and no coroutines:

```kotlin
val nmea = "$GPGSA,A,3,03,05,07,08,10,15,18,19,21,28,,,1.4,0.9,1.1*3A"
val gsa = SentenceRegistry.Default.parse(nmea).sentenceOrNull() as? Gsa
```

**The `Source` is the only part that differs between platforms.** Everything above is common
code; this is how you get one:

```kotlin
// JVM, Android, Native, Node — anywhere with a filesystem
SystemFileSystem.source(Path("nmea.log")).buffered()

// JVM and Android — a serial port, a socket, a process, anything with an InputStream
port.inputStream.asSource().buffered()

// Android — a file the user picked, with no permission declared
contentResolver.openInputStream(uri)!!.asSource().buffered()

// Browser — no filesystem, so wrap the bytes you fetched. A WebSocket is the same shape.
Buffer().also { it.write(bytes) }
```

Pick the dispatcher to read on at that same edge. There is no one right answer, which is why
the library will not choose: `Dispatchers.IO` is public API on JVM and Android, is `internal`
on Kotlin/Native, and does not exist on JS or Wasm.

Sentences are immutable values, so writing one is construction rather than a series of
setters, and `toNmeaString()` computes the checksum:

```kotlin
Mwv(TalkerId.II, windAngle = 43.7, reference = AngleReference.TRUE,
    windSpeed = 4.5, speedUnits = Units.METER, status = DataStatus.ACTIVE)
    .toNmeaString()          // $IIMWV,43.7,T,4.5,M,A*09
```

Recommended Android Proguard settings when `minifyEnabled` is set `true`:

```
-keep class io.github.solcott.marineapi.** { *; }
-keep interface io.github.solcott.marineapi.** { *; }
```

The `-keepattributes MethodParameters` and the `gnu.io` rules the Java version needed are no
longer required: nothing here uses reflection, and the serial port driver was only ever a
dependency of the examples.

See also:
- [Examples](examples/src/commonMain/kotlin/io/github/solcott/marineapi/example) — the demo
  bodies, shared by every platform, with entry points for
  [the JVM](examples/src/jvmMain/kotlin/io/github/solcott/marineapi/example),
  [Node and the browser](examples/src/jsMain/kotlin/io/github/solcott/marineapi/example),
  [macOS](examples/src/macosArm64Main/kotlin/io/github/solcott/marineapi/example) and
  [Android](examples-android/src/main/kotlin/io/github/solcott/marineapi/example/android)
- [API documentation](https://javadoc.io/doc/io.github.solcott/kmp-marine-api)
- [Graphical User Interface](https://github.com/aitov/gps-info) by @aitov, built against the
  original Java library


## Differences from Java Marine API

The Kotlin rewrite is source-incompatible with `net.sf.marineapi` throughout — this is a port
to move to deliberately, not a drop-in upgrade.

- **Coordinates and packages changed.** `net.sf.marineapi:marineapi` becomes
  `io.github.solcott:kmp-marine-api`, and the package root `net.sf.marineapi` becomes
  `io.github.solcott.marineapi`.
- **Sentences are immutable `data class` values.** You build one by construction rather than by
  creating a parser and calling setters on it, and reading one is property access.
- **Optional fields are nullable.** An empty field reads as `null` instead of throwing
  `DataNotAvailableException`.
- **Parse failures are values.** A bad checksum or an unreadable field arrives as a
  `ParseResult` you can count, log or ignore, rather than as a thrown exception.
- **`Flow` replaces the listener and provider model.** `SentenceReader`, the listener
  interfaces and `PositionProvider` and friends are gone; `nmeaSentences()` gives you a flow,
  and `positions()`, `headings()` and `satellites()` do the aggregating those providers did.
- **No reflection.** Sentence and AIS types are registered as lambdas rather than looked up by
  class name, which is what allows the Native and JS targets.
- **An unregistered sentence is not an error.** It arrives as an `UnknownSentence` that keeps
  its fields and re-encodes intact, where the Java library threw.

### Bugs deliberately not carried over

Where behaviour differs from the Java library because the Java library was wrong, the reason is
recorded in KDoc on the declaration. Do not expect these to match a `net.sf.marineapi` release:

- AIS types 4, 18 and 27 read the position-accuracy bit one place early
- AIS type 27's navigational status bit range was reversed
- AIS type 9's flags were off by one, and its speed was scaled by ten
- `isDteReady` returned the bit uninverted — the bit means the opposite of its name
- `NavStatus` read `V` as valid
- `PositionProvider` required a GGA or GLL in every update cycle

## Supported Protocols

### NMEA 0183

The following sentences are decoded and encoded. Additional types may be registered at
runtime, _without compiling_ the library:

```kotlin
val registry = SentenceRegistry.Default.with("XYZ") { fields -> MyXyz.from(fields) }
```

A sentence type that is not registered is not an error: it arrives as an `UnknownSentence`
that keeps its fields and re-encodes intact.

|ID     | Description
|---    |---
|AAM    |Waypoint arrival alarm: circle entered and perpendicular passed
|ALK    |The NMEA 0183 layer of Raymarine SeaTalk<sup>1</sup> (`$STALK`)
|ALM    |GPS almanac data, with the orbital fields kept as raw hex
|APA    |Autopilot cross-track error and bearings, the older sibling of APB
|APB    |Autopilot cross-track error, destination bearings and heading
|ASHR    |Ashtech roll, pitch and heave (`$PASHR`)
|BOD    |Bearing from origin to destination
|BWC    |Bearing and distance to waypoint, great circle
|BWR    |Bearing and distance to waypoint, rhumb line
|BWW    |Bearing from one waypoint to another
|CUR    |Water currents information
|DBK    |Water depth below the keel in meters, feet and fathoms
|DBS    |Water depth below the surface in meters, feet and fathoms
|DBT    |Water depth below transducer in meters, feet and fathoms
|DPT    |Water depth in meters with offset to transducer
|DTA    |Boreal GasFinder2 and GasFinderMC
|DTB    |Boreal GasFinder2 and GasFinderMC
|DTM    |Datum reference
|FSI    |Frequency set information for a radio transceiver
|GBS    |Glonass satellite fault detection (RAIM support)
|GGA    |GPS fix data
|GLL    |Current geographic position and time
|GNS    |Glonass fix data
|GRME   |Garmin estimated position error, in meters (`$PGRME`)
|GRMM   |Garmin map datum in use (`$PGRMM`)
|GRMZ   |Garmin altitude, in feet (`$PGRMZ`)
|GRS    |GPS range residuals, for checking a fix satellite by satellite
|GSA    |Precision of GPS fix
|GST    |GPS pseudorange noise statistics
|GSV    |Detailed GPS satellite data
|HDG    |Heading with magnetic deviation and variation
|HDM    |Magnetic heading in degrees
|HDT    |True heading in degrees
|HFB    |Trawl headrope to footrope and to bottom
|HSC    |Heading steering command, true and magnetic
|HTC    |Heading/Track control systems input data and commands.
|HTD    |Heading/Track control systems output data and commands.
|ITS    |Second trawl door spread distance
|MDA    |Meteorological composite
|MHU    |Relative and absolute humidity with dew point
|MMB    |Barometric pressure
|MSK    |Beacon receiver tuning command
|MSS    |Beacon receiver signal strength and status
|MTA    |Air temperature in degrees Celcius
|MTW    |Water temperature in degrees Celcius
|MWD    |Wind speed and direction.
|MWV    |Wind speed and angle
|OSD    |Own ship data
|R00    |Waypoint ids of the active route
|RLM    |Return link message, an AIS-SART acknowledgement
|RMA    |Recommended minimum navigation information "type A" (Loran-C)
|RMB    |Recommended minimum navigation information "type B"
|RMC    |Recommended minimum navigation information "type C"
|ROT    |Vessel's rate of turn
|RPM    |Engine or shaft revolutions
|RSA    |Rudder angle in degrees
|RSD    |Radar system data
|RTE    |GPS route data with list of waypoints
|SFI    |Scanning frequency information, a list of frequency and mode pairs
|STN    |Multiple data id, naming the talker of the sentences that follow
|TDS    |Trawl door spread distance
|TFI    |Trawl filling indicator, from up to three catch sensors
|THS    |True heading and status
|TLB    |Target label
|TLL    |Target latitude and longitude
|TPC    |Trawl position as offsets from the vessel
|TPR    |Trawl position, range and bearing relative to the vessel
|TPT    |Trawl position, range and true bearing
|TTM    |Tracked target message
|TXT    |Text message
|UBX    |The NMEA 0183 layer of u-blox proprietary messages (`$PUBX`)
|VBW    |Dual ground/water speed.
|VDM    |The NMEA 0183 layer of AIS: other vessels' data
|VDO    |The NMEA 0183 layer of AIS: vessel's own data
|VDR    |Set and drift
|VHW    |Water speed and heading
|VLW    |Distance traveled through water
|VPW    |Speed measured parallel to the wind
|VTG    |Course and speed over ground
|VWR    |Relative wind speed and angle
|VWT    |True wind speed and angle
|WCV    |Waypoint closure velocity
|WNC    |Distance between two waypoints
|WPL    |Destination waypoint location and ID
|XDR    |Transducer measurements
|XTE    |Measured cross-track error
|XTR    |Measured cross-track error, without the status fields of XTE
|ZDA    |UTC time and date with local time offset
|ZFO    |Elapsed time from the origin waypoint
|ZTG    |Time remaining to the destination waypoint

Field layouts follow [gpsd's NMEA reference](https://gpsd.gitlab.io/gpsd/NMEA.html), and
`GpsdCorpusTest` re-encodes 103 logs from ~90 real receivers on every build. `THS` is the
exception: gpsd does not document it, and its layout is taken from a capture where a Skytraq
PX1172RH sends it immediately before an `HDT` carrying the same heading.

The obsolete positioning systems are deliberately absent — Decca (`DCN`), Loran-C (`GLC`,
`LCD`), Transit (`GTD`, `GXA`, `TRF`) and Omega (`OLN`). Nothing transmits them. They are not
errors either: an unregistered type arrives as an `UnknownSentence` that keeps its fields.

### AIS

The following [AIS](https://en.wikipedia.org/wiki/Automatic_Identification_System)
messages are decoded.

| ID    | Description
|---    |---
|01     |Position Report Class A
|02     |Position Report Class A (Assigned schedule)
|03     |Position Report Class A (Response to interrogation)
|04     |Base Station Report
|05     |Static and Voyage Related Data
|07     |Binary Acknowledge
|09     |Standard SAR Aircraft Position Report
|18     |Standard Class B CS Position Report
|19     |Extended Class B Equipment Position Report
|21     |Aid-to-Navigation Report
|24     |Static Data Report
|27     |Position Report for long range applications

### Raymarine SeaTalk<sup>1</sup>

*Not to be confused with SeaTalk<sup>ng</sup> derived from NMEA 2000.*

Only the NMEA layer is currently supported, see
[Stalk](marine-api/src/commonMain/kotlin/io/github/solcott/marineapi/nmea/sentence/ProprietarySentences.kt)
and [Issue #67](https://github.com/ktuukkan/marine-api/issues/67). The datagram is delivered
intact; interpreting it needs a SeaTalk reference this library does not implement.

### u-blox

The following [u-blox](https://www.u-blox.com/)
vendor extension messages are supported:

| ID      | Description
|---      |---
| PUBX,00 |Lat/Long position, velocity and time, with accuracy estimates in metres
| PUBX,03 |Satellite status, including per-satellite carrier lock times

## Distribution

Releases are published to [Maven Central](https://central.sonatype.com/artifact/io.github.solcott/kmp-marine-api)
as `io.github.solcott:kmp-marine-api`. This fork has not released yet, so the coordinates below
describe the version in development; there is no schedule, as development happens per user
request or contribution.

### Gradle

Gradle reads the module metadata and resolves the variant for whichever target you are
building — one coordinate for every platform:

```kotlin
dependencies {
    implementation("io.github.solcott:kmp-marine-api:0.5.0")
}
```

### Maven

This is a Kotlin Multiplatform library, so the root module carries only Gradle module
metadata. Maven consumers must depend on the JVM artifact directly:

```xml
<dependency>
  <groupId>io.github.solcott</groupId>
  <artifactId>kmp-marine-api-jvm</artifactId>
  <version>0.5.0</version>
</dependency>
```

### Building from source

```
./gradlew build                 # all targets (requires macOS for the Apple targets)
./gradlew :marine-api:jvmTest   # JVM tests only
./gradlew :marine-api:allTests  # every target's test suite
./gradlew :marine-api:apiDump   # re-pin the public ABI in marine-api/api/
./gradlew tasks --group examples
```

Requires JDK 17+ and an Android SDK (`ANDROID_HOME`) — the Android Gradle plugin is needed
at configuration time even for JVM-only tasks.


## Contributing

Any feedback or contribution is welcome. You have several options:

- [Report a bug](https://github.com/solcott/kmp-marine-api/issues) in this fork
- [Fork](https://help.github.com/articles/fork-a-repo/) and open a [pull request](https://help.github.com/articles/about-pull-requests/)
to share improvements.

This fork does not send pull requests upstream, and it has diverged deliberately — a bug in
`net.sf.marineapi` belongs in [ktuukkan/marine-api's tracker](https://github.com/ktuukkan/marine-api/issues)
rather than here, and the reverse is equally true.


## References

All information and specifications for this library has been gathered from the
following documents, availability last checked on 2020-03-15.

*Notice: any warnings regarding the accuracy of the information in below
documents apply equally to KMP Marine API.*

### National Marine Electronics Association

* [Amendment to NMEA 0183 v4.10 # 20130814](https://www.nmea.org/Assets/21030814%20nmea%200183_man%20overboard%20notification_mob_sentence%20amendment.pdf)
* [Amendment to NMEA0183 v4.10 # 20130815](https://www.nmea.org/Assets/20131028%200183%20safetynet%20%20v.2%20amendment%20version%204.10%20.pdf)
* [Amendment to NMEA0183 v4.10 # 20131216](https://www.nmea.org/Assets/20131216%200183%20epv_spw_trl%20amendment%20version%204.10.pdf)
* [Approved 0183 Manufacturer's Mnemonic Codes](https://www.nmea.org/Assets/20160523%200183%20manufacturer%20codes.pdf)
* [Manufacturer Mnemonic Codes and Sentence Formatters List](https://www.nmea.org/Assets/20130801%200183%20identifier%20list.pdf)
* [NMEA 0183 Sentences Not Recommended for New Designs](http://www.nmea.org/Assets/100108_nmea_0183_sentences_not_recommended_for_new_designs.pdf)
* [Standards Update October 2014 by Steve Spitzer](http://www.nmea.org/Assets/20141004%20nmea%20standards%20update%20for%202014%20conference.pdf)

### Navigation Center of U.S. Department of Homeland Security

* [Automatic Identification System Overview](http://www.navcen.uscg.gov/?pageName=AISMessages)

### Product Manuals and User Guides

* [BD9xx GNSS Receivers Help](http://www.trimble.com/OEM_ReceiverHelp/V4.44/en/) by Trimble Navigation Limited
* [Guide for AgGPS Receivers](http://trl.trimble.com/docushare/dsweb/Get/Document-159714/NMEA_Messages_RevA_Guide_ENG.pdf) by Trimble Navigation Limited
* [Hydromagic NMEA 0183 documentation](https://www.eye4software.com/hydromagic/documentation/nmea0183/) by Eye4Software
* [NM-2C User's Guide](http://www.nuovamarea.com/files/product%20manuals/nm%20manuals/NM-2C_v1.00.pdf) by Nuova Marea Ltd
* [PB100 WeatherStation Manual](http://www.airmartechnology.com/uploads/installguide/PB100TechnicalManual_rev1.007.pdf) by Airmar
* [RT Intertial+ NMEA Description (rev. 100720)](https://www.datrontechnology.co.uk/wp-content/uploads/2016/10/nmeaman.pdf) by Oxford Technical Solutions Ltd
* [SeaTalk/NMEA/RS232 Converter Manual](https://community.atmel.com/sites/default/files/project_files/ManualV3-5.pdf) by gadgetPool
* [SiRF NMEA Reference Manual](https://www.sparkfun.com/datasheets/GPS/NMEA%20Reference%20Manual-Rev2.1-Dec07.pdf) by SiRF Technology, Inc.
* [The NMEA Information Sheet](https://www.actisense.com/wp-content/uploads/2020/01/NMEA-0183-Information-sheet-issue-4-1-1.pdf) by Actisense
* [ZED-F9P F9 high precision GNSS receiver Interface Description](https://www.u-blox.com/en/docs/UBX-18010854) by u-blox

### Wikipedia

  * [NMEA 0183](http://en.wikipedia.org/wiki/NMEA_0183)
  * [Automatic Identification System](https://en.wikipedia.org/wiki/Automatic_identification_system)

### Miscellaneus

* [AIVDM/AIVDO protocol decoding](https://gpsd.gitlab.io/gpsd/AIVDM.html) by Eric S. Raymond
* [NMEA Revealed](https://gpsd.gitlab.io/gpsd/NMEA.html) by Eric S. Raymond
* [SeaTalk Technical Reference](http://www.thomasknauf.de/seatalk.htm) by Thomas Knauf

### No longer available

* [NMEA Data](http://www.gpsinformation.org/dale/nmea.htm) by Dale DePriest
* [NMEA Sentence Information](http://home.mira.net/~gnb/gps/nmea.html) by Glenn Baddeley (not found)
* [RS232/SeaTalk/NMEA Converter manual](http://www.gadgetpool.de/nuke/downloads/ManualRS232.pdf) by gadgetPool (not found)
* [The NMEA FAQ](http://vancouver-webpages.com/peter/nmeafaq.txt) by Peter Bennett (see [older copy](http://www.eoss.org/pubs/nmeafaq.htm))


---
