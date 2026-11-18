package com.drunkbatya.drunkheadphonesadapter

import adapter.SystemStatusResponse

data class SystemMetrics(
  val status: SystemStatusResponse,
  val awakeFraction: Float?,
  val interruptFraction: Float?,
) {
  val uptimeSeconds: Long get() = status.tick.toUInt().toLong() / status.tick_frequency_hz
}

private fun counterDelta(current: Int, previous: Int): Long = (current - previous).toUInt().toLong()

fun systemMetrics(status: SystemStatusResponse, previous: SystemStatusResponse?): SystemMetrics {
  if (previous == null) return SystemMetrics(status, awakeFraction = null, interruptFraction = null)
  val ticks = counterDelta(status.tick, previous.tick)
  val awakeCycles = counterDelta(status.cycle_counter, previous.cycle_counter)
  val interruptCycles = counterDelta(status.isr_cycles, previous.isr_cycles)
  val elapsedCycles = ticks.toDouble() * status.cycle_frequency_hz / status.tick_frequency_hz
  return SystemMetrics(
    status = status,
    awakeFraction = (awakeCycles / elapsedCycles).toFloat().coerceIn(0f, 1f),
    interruptFraction = (interruptCycles.toDouble() / awakeCycles).toFloat().coerceIn(0f, 1f),
  )
}
