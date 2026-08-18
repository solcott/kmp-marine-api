package io.github.solcott.marineapi.nmea

import kotlin.jvm.JvmInline

/**
 * Two-character talker identifier: the `GP` in `$GPGGA`, naming the device that sent the sentence.
 *
 * This is a value class over the raw code rather than an enum, deliberately. The registry of talker
 * IDs grows with every NMEA revision and vendor, and the Java implementation this replaces threw
 * `IllegalArgumentException` from `TalkerId.parse` for any code it did not know -- so a single
 * sentence from an unrecognised talker aborted parsing of that line. An unknown talker is not a
 * parse failure; it is just a talker this library has no constant for.
 *
 * The well-known codes are available as constants on the companion, so `when (talker)` against
 * [GP], [GL] and friends reads exactly as it would against an enum.
 */
@JvmInline
public value class TalkerId private constructor(public val code: String) {

  override fun toString(): String = code

  public companion object {

    /**
     * The talker for proprietary sentences, whose tag is `$P` followed by a manufacturer mnemonic
     * rather than a two-character talker and a three-character type.
     */
    public val P: TalkerId = TalkerId("P")

    /** AIS Mobile Class A or B AIS Station */
    public val AI: TalkerId = TalkerId("AI")

    /** AIS Independent AIS Base Station (NMEA 4.0) */
    public val AB: TalkerId = TalkerId("AB")

    /** AIS Dependent AIS Base Station (NMEA 4.0) */
    public val AD: TalkerId = TalkerId("AD")

    /** AIS Aids to Navigation Station (NMEA 4.0) */
    public val AN: TalkerId = TalkerId("AN")

    /** AIS Receiving Station (NMEA 4.0) */
    public val AR: TalkerId = TalkerId("AR")

    /** AIS Limited Base Station (NMEA 4.0) */
    public val AS: TalkerId = TalkerId("AS")

    /** AIS Transmitting Station (NMEA 4.0) */
    public val AT: TalkerId = TalkerId("AT")

    /** AIS Simplex Repeater Station (NMEA 4.0) */
    public val AX: TalkerId = TalkerId("AX")

    /** AIS Base station (obsolete since NMEA 4.0) */
    public val BS: TalkerId = TalkerId("BS")

    /** Heading Track Controller/Autopilot: General */
    public val AG: TalkerId = TalkerId("AG")

    /** Heading Track Controller/Autopilot: Magnetic */
    public val AP: TalkerId = TalkerId("AP")

    /** BeiDou satellite navigation system (Chinese) */
    public val BD: TalkerId = TalkerId("BD")

    /** Bilge Systems */
    public val BI: TalkerId = TalkerId("BI")

    /** Bridge Navigational Watch Alarm System */
    public val BN: TalkerId = TalkerId("BN")

    /** Central Alarm Management */
    public val CA: TalkerId = TalkerId("CA")

    /** Computer - Programmed Calculator (obsolete) */
    public val CC: TalkerId = TalkerId("CC")

    /** Communications - Digital Selective Calling (DSC) */
    public val CD: TalkerId = TalkerId("CD")

    /** Computer - Memory Data (obsolete) */
    public val CM: TalkerId = TalkerId("CM")

    /** Channel Pilot (Navicom Dynamics proprietary) */
    public val CP: TalkerId = TalkerId("CP")

    /** Data Receiver */
    public val CR: TalkerId = TalkerId("CR")

    /** Communications - Satellite */
    public val CS: TalkerId = TalkerId("CS")

    /** Communications - Radio-Telephone (MF/HF) */
    public val CT: TalkerId = TalkerId("CT")

    /** Communications - Radio-Telephone (VHF) */
    public val CV: TalkerId = TalkerId("CV")

    /** Communications - Scanning Receiver */
    public val CX: TalkerId = TalkerId("CX")

    /** DECCA Navigation (obsolete) */
    public val DE: TalkerId = TalkerId("DE")

    /** Direction Finder */
    public val DF: TalkerId = TalkerId("DF")

    /** Velocity Sensor, Speed Log, Water, Magnetic */
    public val DM: TalkerId = TalkerId("DM")

    /** Dynamic Position */
    public val DP: TalkerId = TalkerId("DP")

    /** Duplex Repeater Station */
    public val DU: TalkerId = TalkerId("DU")

    /** Electronic Chart System (ECS) */
    public val EC: TalkerId = TalkerId("EC")

    /** Electronic Chart Display &amp; Information System (ECDIS) */
    public val EI: TalkerId = TalkerId("EI")

    /** Emergency Position Indicating Beacon (EPIRB) */
    public val EP: TalkerId = TalkerId("EP")

    /** Engine Room Monitoring Systems */
    public val ER: TalkerId = TalkerId("ER")

    /** Fire Door Controller/Monitoring Point */
    public val FD: TalkerId = TalkerId("FD")

    /** Fire Extinguisher System */
    public val FE: TalkerId = TalkerId("FE")

    /** Fire Detection Point */
    public val FR: TalkerId = TalkerId("FR")

    /** Fire Sprinkler System */
    public val FS: TalkerId = TalkerId("FS")

    /** Galileo satellite navigation system (European) */
    public val GA: TalkerId = TalkerId("GA")

    /** BeiDou satellite navigation system (Chinese) */
    public val GB: TalkerId = TalkerId("GB")

    /** Gas Finder (Boreal) */
    public val GF: TalkerId = TalkerId("GF")

    /** Indian Regional Navigation Satellite System (IRNSS) */
    public val GI: TalkerId = TalkerId("GI")

    /** GLONASS Receiver */
    public val GL: TalkerId = TalkerId("GL")

    /** Global Navigation Satellite System (GNSS) */
    public val GN: TalkerId = TalkerId("GN")

    /** Global Positioning System (GPS) */
    public val GP: TalkerId = TalkerId("GP")

    /** Quasi Zenith Satellite System (QXSS, Japanese) */
    public val GQ: TalkerId = TalkerId("GQ")

    /** Heading Sensors: Compass, Magnetic */
    public val HC: TalkerId = TalkerId("HC")

    /** Hull Door Controller/Monitoring Panel */
    public val HD: TalkerId = TalkerId("HD")

    /** Heading Sensors: Gyro, North Seeking */
    public val HE: TalkerId = TalkerId("HE")

    /** Heading Sensors: Fluxgate */
    public val HF: TalkerId = TalkerId("HF")

    /** Heading Sensors: Gyro, Non-North Seeking */
    public val HN: TalkerId = TalkerId("HN")

    /** Hull Stress Monitoring */
    public val HS: TalkerId = TalkerId("HS")

    /** Integrated Instrumentation */
    public val II: TalkerId = TalkerId("II")

    /** Integrated Navigation */
    public val IN: TalkerId = TalkerId("IN")

    /** Automation: Alarm and monitoring system (reserved for future use) */
    public val JA: TalkerId = TalkerId("JA")

    /** Automation: Reefer Monitoring System (reserved for future use) */
    public val JB: TalkerId = TalkerId("JB")

    /** Automation: Power Management System (reserved for future use) */
    public val JC: TalkerId = TalkerId("JC")

    /** Automation: Propulsion Control System (reserved for future use) */
    public val JD: TalkerId = TalkerId("JD")

    /** Automation: Engine Control Console (reserved for future use) */
    public val JE: TalkerId = TalkerId("JE")

    /** Automation: Propulsion Boiler (reserved for future use) */
    public val JF: TalkerId = TalkerId("JF")

    /** Automation: Auxiliary Boiler (reserved for future use) */
    public val JG: TalkerId = TalkerId("JG")

    /** Automation: Electronic Governor System (reserved for future use) */
    public val JH: TalkerId = TalkerId("JH")

    /** Loran A (obsolete) */
    public val LA: TalkerId = TalkerId("LA")

    /** Loran C (obsolete) */
    public val LC: TalkerId = TalkerId("LC")

    /** Microwave Positioning System (obsolete) */
    public val MP: TalkerId = TalkerId("MP")

    /** Multiplexer */
    public val MX: TalkerId = TalkerId("MX")

    /** Navigation Light Controller */
    public val NL: TalkerId = TalkerId("NL")

    /** OpenPlotter calculated */
    public val OC: TalkerId = TalkerId("OC")

    /** OMEGA Navigation System (obsolete) */
    public val OM: TalkerId = TalkerId("OM")

    /** Distress Alarm System (obsolete) */
    public val OS: TalkerId = TalkerId("OS")

    /** QZSS regional GPS augmentation system (Japan) */
    public val QZ: TalkerId = TalkerId("QZ")

    /** Radar and/or Radar Plotting */
    public val RA: TalkerId = TalkerId("RA")

    /** Record Book (reserved for future use) */
    public val RB: TalkerId = TalkerId("RB")

    /** Propulsion Machinery Including Remote Control */
    public val RC: TalkerId = TalkerId("RC")

    /** Rudder Angle Indicator(reserved for future use) */
    public val RI: TalkerId = TalkerId("RI")

    /** Indian Regional Navigation Satellite System (IRNSS) */
    public val IR: TalkerId = TalkerId("IR")

    /** AIS - NMEA 4.0 Physical Shore AIS Station */
    public val SA: TalkerId = TalkerId("SA")

    /** Steering Control System/Device (reserved for future use) */
    public val SC: TalkerId = TalkerId("SC")

    /** Sounder, depth */
    public val SD: TalkerId = TalkerId("SD")

    /** Steering Gear / Steering Engine */
    public val SG: TalkerId = TalkerId("SG")

    /** Electronic Positioning System, other/general */
    public val SN: TalkerId = TalkerId("SN")

    /** Sounder, Scanning */
    public val SS: TalkerId = TalkerId("SS")

    /** Raymarine SeaTalk ($STALK) */
    public val ST: TalkerId = TalkerId("ST")

    /** Track Control System (reserved for future use) */
    public val TC: TalkerId = TalkerId("TC")

    /** Turn Rate Indicator */
    public val TI: TalkerId = TalkerId("TI")

    /** TRANSIT Navigation System */
    public val TR: TalkerId = TalkerId("TR")

    /** Microprocessor Controller */
    public val UP: TalkerId = TalkerId("UP")

    /** VHF Data Exchange System: ASM */
    public val VA: TalkerId = TalkerId("VA")

    /** Velocity Sensor: Doppler, other/general */
    public val VD: TalkerId = TalkerId("VD")

    /** Velocity Sensor: Speed Log, Water, Magnetic */
    public val VM: TalkerId = TalkerId("VM")

    /** Voyage Data Recorder */
    public val VR: TalkerId = TalkerId("VR")

    /** VHF Data Exchange System: Satellite */
    public val VS: TalkerId = TalkerId("VS")

    /** VHF Data Exchange System: Terrestrial */
    public val VT: TalkerId = TalkerId("VT")

    /** Velocity Sensor: Speed Log, Water, Mechanical */
    public val VW: TalkerId = TalkerId("VW")

    /** Weather Instruments */
    public val WI: TalkerId = TalkerId("WI")

    /** Water Level Detection Systems */
    public val WL: TalkerId = TalkerId("WL")

    /** Wärtsilä proprietary */
    public val WV: TalkerId = TalkerId("WV")

    /** Transducer - Temperature (obsolete) */
    public val YC: TalkerId = TalkerId("YC")

    /** Transducer - Displacement, Angular or Linear (obsolete) */
    public val YD: TalkerId = TalkerId("YD")

    /** Transducer - Frequency (obsolete) */
    public val YF: TalkerId = TalkerId("YF")

    /** Transducer - Level (obsolete) */
    public val YL: TalkerId = TalkerId("YL")

    /** Transducer - Pressure (obsolete) */
    public val YP: TalkerId = TalkerId("YP")

    /** Transducer - Flow Rate (obsolete) */
    public val YR: TalkerId = TalkerId("YR")

    /** Transducer - Tachometer (obsolete) */
    public val YT: TalkerId = TalkerId("YT")

    /** Transducer - Volume (obsolete) */
    public val YV: TalkerId = TalkerId("YV")

    /** Transducer */
    public val YX: TalkerId = TalkerId("YX")

    /** Timekeeper - Atomic Clock */
    public val ZA: TalkerId = TalkerId("ZA")

    /** Timekeeper - Chronometer */
    public val ZC: TalkerId = TalkerId("ZC")

    /** Timekeeper - Quartz */
    public val ZQ: TalkerId = TalkerId("ZQ")

    /** Timekeeper - Radio Update, WWV or WWVH */
    public val ZV: TalkerId = TalkerId("ZV")

    /**
     * Wraps [code] as a talker id, without checking it against the known list.
     *
     * Codes are used as written; NMEA tags are upper-case, and a lower-case code will not compare
     * equal to the constants above.
     */
    public fun of(code: String): TalkerId = TalkerId(code)
  }
}
