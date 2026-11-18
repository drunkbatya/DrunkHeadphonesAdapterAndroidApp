package com.drunkbatya.drunkheadphonesadapter

import adapter.ResetReason
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@get:StringRes
private val ResetReason.label: Int
  get() = when (this) {
    ResetReason.RESET_REASON_UNKNOWN -> R.string.value_unknown
    ResetReason.RESET_REASON_POWER_ON -> R.string.reset_reason_power_on
    ResetReason.RESET_REASON_PIN -> R.string.reset_reason_pin
    ResetReason.RESET_REASON_SOFTWARE -> R.string.reset_reason_software
    ResetReason.RESET_REASON_INDEPENDENT_WATCHDOG -> R.string.reset_reason_independent_watchdog
    ResetReason.RESET_REASON_WINDOW_WATCHDOG -> R.string.reset_reason_window_watchdog
    ResetReason.RESET_REASON_LOW_POWER -> R.string.reset_reason_low_power
    ResetReason.RESET_REASON_OPTION_BYTES_LOAD -> R.string.reset_reason_option_bytes_load
  }

@Composable
fun SystemTab(system: SystemMetrics?, modifier: Modifier = Modifier) {
  val status = system?.status
  StatusColumn(modifier) {
    Section(stringResource(R.string.section_runtime)) {
      InfoRow(stringResource(R.string.uptime), system?.let { formatUptime(it.uptimeSeconds) })
      InfoRow(stringResource(R.string.reset_reason), status?.let { stringResource(it.reset_reason.label) })
    }
    Section(stringResource(R.string.section_cpu)) {
      InfoRow(stringResource(R.string.cpu_clock), status?.let { formatFrequency(it.cycle_frequency_hz) })
      MeterRow(stringResource(R.string.cpu_awake), system?.awakeFraction?.let(::formatPercent), system?.awakeFraction)
      MeterRow(
        stringResource(R.string.cpu_interrupts),
        system?.interruptFraction?.let(::formatPercent),
        system?.interruptFraction,
      )
    }
    Section(stringResource(R.string.section_heap)) {
      MeterRow(
        stringResource(R.string.heap_used),
        status?.let { "${formatKibibytes(it.heap_total - it.heap_free)} / ${formatKibibytes(it.heap_total)}" },
        status?.let { (it.heap_total - it.heap_free).toFloat() / it.heap_total },
      )
      InfoRow(stringResource(R.string.heap_peak), status?.let { formatKibibytes(it.heap_total - it.heap_min_free) })
    }
  }
}
