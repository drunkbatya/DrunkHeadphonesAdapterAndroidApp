package com.drunkbatya.drunkheadphonesadapter

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val VOLUME_STEPS_PER_DB = 256
private const val BYTES_PER_KIBIBYTE = 1024.0
private const val SECONDS_PER_DAY = 86400
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

fun formatThousandths(value: Int): String =
  BigDecimal(value).movePointLeft(3).stripTrailingZeros().toPlainString()

fun formatVolume(volumeDbX256: Int): String {
  val decibels = BigDecimal(volumeDbX256).divide(BigDecimal(VOLUME_STEPS_PER_DB)).setScale(2, RoundingMode.HALF_UP)
  return "${decibels.stripTrailingZeros().toPlainString()} dB"
}

fun formatVolumeOffset(steps: Int): String = if (steps > 0) "+$steps" else "$steps"

fun formatSampleFormat(sampleRateHz: Int, sampleBits: Int): String = "${formatThousandths(sampleRateHz)} kHz, $sampleBits bit"

fun formatFrequency(frequencyHz: Int): String = "${formatThousandths(frequencyHz / 1000)} MHz"

fun formatKibibytes(bytes: Int): String = "%.1f KiB".format(Locale.ROOT, bytes / BYTES_PER_KIBIBYTE)

fun formatPercent(fraction: Float): String = "%.1f %%".format(Locale.ROOT, fraction * 100)

fun formatUptime(seconds: Long): String {
  val days = seconds / SECONDS_PER_DAY
  val clock = "%02d:%02d:%02d".format(
    Locale.ROOT,
    seconds % SECONDS_PER_DAY / SECONDS_PER_HOUR,
    seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
    seconds % SECONDS_PER_MINUTE,
  )
  return if (days > 0) "$days d $clock" else clock
}
