# Java Marine API
[![License](https://img.shields.io/badge/License-LGPL%20v3-brightgreen.svg)](./LICENSE)
[![Build & Test](https://github.com/solcott/kmp-marine-api/actions/workflows/build.yml/badge.svg)](https://github.com/solcott/kmp-marine-api/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.solcott/kmp-marine-api)](https://central.sonatype.com/artifact/io.github.solcott/kmp-marine-api)
[![Javadocs](https://javadoc.io/badge2/io.github.solcott/kmp-marine-api/javadoc.svg)](https://javadoc.io/doc/io.github.solcott/kmp-marine-api)

- [Java Marine API](#java-marine-api)
  - [About](#about)
    - [Features](#features)
    - [Licensing](#licensing)
    - [Disclaimer](#disclaimer)
    - [Requirements](#requirements)
    - [Usage](#usage)
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

Java Marine API is an [NMEA 0183](http://en.wikipedia.org/wiki/NMEA_0183) parser
library for decoding and encoding the data provided by various electronic marine
devices such as GPS, echo sounder and weather instruments.

Originally a Java library, it is now **Kotlin Multiplatform**: the same code runs on the
JVM, Android, iOS, macOS, JS and WebAssembly.

### Features

- Reads NMEA 0183 from anything that can produce a `kotlinx-io` `Source`
    - A file, a serial port, a TCP/IP or UDP socket, an in-memory buffer
    - The library never opens one itself, so it needs no platform IO of its own
- Turns the stream into a `Flow` of immutable sentence values for [selected sentences](#nmea-0183)
    - `filterIsInstance<Gga>()` picks a type; no listener interfaces, no reflection
- Optional NMEA fields are **nullable**, not exceptions: an empty field reads as `null`
- Line-level failures are **values**, not exceptions: a bad checksum arrives as a `ParseResult`
  you can count, log or ignore
- Additional sentence types may be registered at runtime, without compiling the library
- Sentence encoding with a checksum that always matches the body
- Several sentences aggregate into one value with the flow operators `positions()`,
  `headings()` and `satellites()` — for example, to record current position and speed
- Decoding of selected [AIS messages](#ais), reassembled across multiple sentences
- The [u-blox](#u-blox) vendor extension, and the NMEA 0183 layer of
  [Raymarine SeaTalk<sup>1</sup>](http://www.raymarine.com/view/?id=5535)
- Utilities and enumerations for handling the extracted data

### Licensing

Java Marine API is free software: you can redistribute it and/or modify it
under the terms of the [GNU Lesser General Public License](./LICENSE) published
by the Free Software Foundation, either version 3 of the License, or (at your
option) any later version.

Java Marine API is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with Java Marine API. If not, see http://www.gnu.org/licenses/.

- See also: [LGPL and Java](https://www.gnu.org/licenses/lgpl-java.en.html)

### Disclaimer

Java Marine API is not official NMEA 0183 software. Further, it is not related
to [National Marine Electronics Association](http://www.nmea.org/).

The interpretation of NMEA 0183 and related protocols is based entirely on
varying set of publicly available [documents](#references) in the Internet.
Thus, it is not guaranteed that the library follows and implements these
standards correctly.

Electronic devices and software do not replace safe navigation practices and
should never be your only reference.

### Requirements

* Kotlin 2.1 or newer
* On the JVM: Java SE JRE/JDK 17 or newer
* For serial port communication on the JVM (choose one):
  * [Neuron Robotics Java Serial Library](https://github.com/NeuronRobotics/nrjavaserial)
  * [PureJavaComm](http://www.sparetimelabs.com/purejavacomm)
  * [RXTX library](http://rxtx.qbang.org)
  * [Java Communications API](http://www.oracle.com/technetwork/java/index-jsp-141752.html)

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
- [Graphical User Interface](https://github.com/aitov/gps-info) using marine-api by @aitov


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
|ALK    |The NMEA 0183 layer of Raymarine SeaTalk<sup>1</sup> (`$STALK`)
|APB    |Autopilot cross-track error, destination bearings and heading
|BOD    |Bearing from origin to destination
|CUR    |Water currents information
|DBT    |Water depth below transducer in meters, feet and fathoms
|DPT    |Water depth in meters with offset to transducer
|DTA    |Boreal GasFinder2 and GasFinderMC
|DTB    |Boreal GasFinder2 and GasFinderMC
|DTM    |Datum reference
|GBS    |Glonass satellite fault detection (RAIM support)
|GGA    |GPS fix data
|GLL    |Current geographic position and time
|GNS    |Glonass fix data
|GSA    |Precision of GPS fix
|GST    |GPS pseudorange noise statistics
|GSV    |Detailed GPS satellite data
|HDG    |Heading with magnetic deviation and variation
|HDM    |Magnetic heading in degrees
|HDT    |True heading in degrees
|HTC    |Heading/Track control systems input data and commands.
|HTD    |Heading/Track control systems output data and commands.
|MDA    |Meteorological composite
|MHU    |Relative and absolute humidity with dew point
|MMB    |Barometric pressure
|MTA    |Air temperature in degrees Celcius
|MTW    |Water temperature in degrees Celcius
|MWD    |Wind speed and direction.
|MWV    |Wind speed and angle
|OSD    |Own ship data
|RMB    |Recommended minimum navigation information "type B"
|RMC    |Recommended minimum navigation information "type C"
|ROT    |Vessel's rate of turn
|RPM    |Engine or shaft revolutions
|RSA    |Rudder angle in degrees
|RSD    |Radar system data
|RTE    |GPS route data with list of waypoints
|TLB    |Target label
|TTM    |Tracked target message
|TXT    |Text message
|VBW    |Dual ground/water speed.
|VDM    |The NMEA 0183 layer of AIS: other vessels' data
|VDO    |The NMEA 0183 layer of AIS: vessel's own data
|VDR    |Set and drift
|VHW    |Water speed and heading
|VLW    |Distance traveled through water
|VTG    |Course and speed over ground
|VWR    |Relative wind speed and angle
|VWT    |True wind speed and angle
|WPL    |Destination waypoint location and ID
|XDR    |Transducer measurements
|XTE    |Measured cross-track error
|ZDA    |UTC time and date with local time offset

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

Releases and snapshots are published every now and then, but there is no clear
plan or schedule for this as most of the development happens per user requests
or contribution.

### Gradle

Releases are published to Maven Central as `io.github.solcott:kmp-marine-api`.

```kotlin
dependencies {
    implementation("io.github.solcott:kmp-marine-api:0.12.0")
}
```

### Maven

This is a Kotlin Multiplatform library, so the root module carries only Gradle module
metadata. Maven consumers must depend on the JVM artifact directly:

```xml
<dependency>
  <groupId>io.github.solcott</groupId>
  <artifactId>kmp-marine-api-jvm</artifactId>
  <version>0.12.0</version>
</dependency>
```

### Building from source

```
./gradlew build                 # all targets (requires macOS for the Apple targets)
./gradlew :marine-api:jvmTest   # JVM tests only
./gradlew :marine-api:allTests  # every target's test suite
./gradlew tasks --group examples
```

Requires JDK 17+ and an Android SDK (`ANDROID_HOME`) — the Android Gradle plugin is needed
at configuration time even for JVM-only tasks.


## Contributing

Any feedback or contribution is welcome. You have several options:

- [Contact me](https://github.com/ktuukkan)
- [Report a bug](https://github.com/ktuukkan/marine-api/issues)
- [Fork](https://help.github.com/articles/fork-a-repo/) and open a [pull request](https://help.github.com/articles/about-pull-requests/)
to share improvements.


## References

All information and specifications for this library has been gathered from the
following documents, availability last checked on 2020-03-15.

*Notice: any warnings regarding the accuracy of the information in below
documents apply equally to Java Marine API.*

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
