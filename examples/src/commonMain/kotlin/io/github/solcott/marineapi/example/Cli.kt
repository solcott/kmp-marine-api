package io.github.solcott.marineapi.example

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString

/**
 * Picks a demo by name and runs it.
 *
 * Every platform's entry point ends up here. What each one has to supply is the two things that
 * genuinely differ between platforms: how to obtain a [Source], and how to start a coroutine.
 *
 * @param demo which demo to run, or `null` to print the usage text
 * @param open opens the feed, or `null` if the caller had no feed to give. Demos that need one say
 *   so; [demoOutput] needs none and [demoUblox] falls back to [UBLOX_SAMPLE].
 */
suspend fun runDemo(demo: String?, open: (() -> Source)?) {
  when (demo) {
    "file" -> withFeed(demo, open, ::demoFile)
    "positions" -> withFeed(demo, open, ::demoPositions)
    "ais" -> withFeed(demo, open, ::demoAis)
    "traffic" -> withFeed(demo, open, ::demoTraffic)
    // No sample log in this project carries $PUBX, so this one brings its own feed.
    "ublox" -> demoUblox(open ?: { sourceOf(UBLOX_SAMPLE) })
    "output" -> demoOutput()
    else -> println(USAGE)
  }
}

private suspend fun withFeed(
  demo: String,
  open: (() -> Source)?,
  run: suspend (() -> Source) -> Unit,
) {
  if (open == null) println("The $demo demo needs a feed to read.\n\n$USAGE") else run(open)
}

/** The demos [runDemo] knows, in the order the usage text lists them. */
val DEMOS: List<String> = listOf("file", "positions", "ais", "traffic", "ublox", "output")

private val USAGE =
  """
  Usage: <demo> [nmea.log]

    file       position from every GGA, and a count of the lines that did not parse
    positions  fixes, headings and satellite views, correlated across sentences
    ais        AIS traffic, with multi-sentence messages reassembled
    traffic    the same feed as vessels, fused by MMSI, with closest approach
    ublox      ${'$'}PUBX messages; uses a built-in sample if given no file
    output     builds and encodes sentences; needs no file
  """
    .trimIndent()

/** A [Source] over some text, which is all a browser or an embedded sample ever needs. */
fun sourceOf(text: String): Source = Buffer().also { it.writeString(text) }

/**
 * Two `$PUBX` sentences, because none of the sample logs in this project carry any.
 *
 * These are the fixtures the library's own u-blox tests are built on.
 */
val UBLOX_SAMPLE: String =
  listOf(
      "\$PUBX,00,202920.00,1932.33821,N,15555.72641,W,451.876,G3,3.3,4.0,0.177,0.00," +
        "-0.035,,1.11,1.39,1.15,17,0,0*62",
      "\$PUBX,03,4,5,U,063,15,18,000,12,U,100,36,40,064,14,-,257,05,,000,18,-,219,67,20,000*14",
    )
    .joinToString("\r\n", postfix = "\r\n")
