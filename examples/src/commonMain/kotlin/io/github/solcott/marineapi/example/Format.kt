package io.github.solcott.marineapi.example

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration

/*
 * The formatting the demos share.
 *
 * `println` is the whole output layer here -- there is no logging or formatting dependency in
 * :examples on purpose -- so the handful of things that need rendering the same way in more than
 * one demo live together rather than being written twice.
 */

/** Pads or truncates to exactly [width], so a column of these lines up. */
internal fun String.fit(width: Int): String = padEnd(width).take(width)

/**
 * Hours and minutes, because two vessels barely moving relative to each other produce a closest
 * approach days away and "in 2278m" is not a number anyone reads.
 */
internal fun Duration.toHoursAndMinutes(): String = toComponents { hours, minutes, _, _ ->
  if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Two decimal places, without pulling a formatting library into a demo.
 *
 * Rounds rather than truncates, and takes the sign off before splitting the number up. Both matter
 * more than they look: the vector arithmetic behind `trueWind` lands on 24.499999999 where the
 * instrument said 24.5, which truncation renders as "24.49" and makes an operator that changed
 * nothing look as though it changed something; and a closing velocity of -0.3 kn -- RMB reports one
 * whenever the waypoint is receding -- has an integer part of 0 and a remainder of -30, which the
 * naive version prints as "0.-30".
 */
internal fun Double.toHundredths(): String {
  val hundredths = (abs(this) * 100).roundToLong()
  val sign = if (this < 0) "-" else ""
  return "$sign${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

/**
 * Whole degrees, which is all the precision a bearing on a display ever needs.
 *
 * Rounded for the same reason: an angle that has been through `atan2` comes back as 266.9999997,
 * and truncating that to 266 misreports a bearing the instrument gave as 267.
 */
internal fun Double.toWholeDegrees(): String = "${roundToInt()}°"
