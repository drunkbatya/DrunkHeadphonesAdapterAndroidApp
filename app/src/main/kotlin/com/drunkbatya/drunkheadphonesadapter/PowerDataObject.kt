package com.drunkbatya.drunkheadphonesadapter

private const val PDO_TYPE_FIXED = 0
private const val PDO_TYPE_BATTERY = 1
private const val PDO_TYPE_VARIABLE = 2
private const val APDO_TYPE_PPS = 0
private const val APDO_TYPE_EPR_AVS = 1

private const val FIXED_PDO_DUAL_ROLE_POWER_BIT = 29
private const val FIXED_PDO_UNCONSTRAINED_POWER_BIT = 27
private const val FIXED_PDO_USB_COMMUNICATION_BIT = 26
private const val FIXED_PDO_DUAL_ROLE_DATA_BIT = 25
private const val FIXED_SOURCE_PDO_EPR_BIT = 23
private const val FIXED_SINK_PDO_FAST_ROLE_SWAP_SHIFT = 23

enum class FastRoleSwapCurrent { NotSupported, DefaultUsb, Current1A5, Current3A0 }

enum class PartnerFeature { DualRolePower, DualRoleData, UsbCommunication, UnconstrainedPower, Epr }

private fun Int.bits(shift: Int, width: Int): Int = (this ushr shift) and ((1 shl width) - 1)

private fun Int?.hasBit(bit: Int): Boolean = this != null && bits(bit, 1) == 1

private fun formatMillivolts(millivolts: Int) = "${formatThousandths(millivolts)} V"

private fun formatMilliamps(milliamps: Int) = "${formatThousandths(milliamps)} A"

private fun formatMilliwatts(milliwatts: Int) = "${formatThousandths(milliwatts)} W"

private fun formatVoltageRange(minMillivolts: Int, maxMillivolts: Int) =
  "${formatThousandths(minMillivolts)}-${formatThousandths(maxMillivolts)} V"

private fun Int.fixedVoltage() = formatMillivolts(bits(10, 10) * 50)

private fun Int.voltageRange() = formatVoltageRange(bits(10, 10) * 50, bits(20, 10) * 50)

fun describePowerDataObject(pdo: Int): String = when (pdo.bits(30, 2)) {
  PDO_TYPE_FIXED -> "${pdo.fixedVoltage()} ${formatMilliamps(pdo.bits(0, 10) * 10)}"
  PDO_TYPE_BATTERY -> "${pdo.voltageRange()} ${formatMilliwatts(pdo.bits(0, 10) * 250)}"
  PDO_TYPE_VARIABLE -> "${pdo.voltageRange()} ${formatMilliamps(pdo.bits(0, 10) * 10)}"
  else -> when (pdo.bits(28, 2)) {
    APDO_TYPE_PPS ->
      "PPS ${formatVoltageRange(pdo.bits(8, 8) * 100, pdo.bits(17, 8) * 100)} ${formatMilliamps(pdo.bits(0, 7) * 50)}"
    APDO_TYPE_EPR_AVS ->
      "AVS ${formatVoltageRange(pdo.bits(8, 8) * 100, pdo.bits(17, 9) * 100)} ${formatMilliwatts(pdo.bits(0, 8) * 1000)}"
    else -> "APDO 0x%08X".format(pdo)
  }
}

fun describeContract(pdo: Int, rdo: Int): String = when (pdo.bits(30, 2)) {
  PDO_TYPE_FIXED -> "${pdo.fixedVoltage()} ${formatMilliamps(rdo.bits(10, 10) * 10)}"
  PDO_TYPE_BATTERY -> "${pdo.voltageRange()} ${formatMilliwatts(rdo.bits(10, 10) * 250)}"
  PDO_TYPE_VARIABLE -> "${pdo.voltageRange()} ${formatMilliamps(rdo.bits(10, 10) * 10)}"
  else -> when (pdo.bits(28, 2)) {
    APDO_TYPE_PPS -> "PPS ${formatMillivolts(rdo.bits(9, 12) * 20)} ${formatMilliamps(rdo.bits(0, 7) * 50)}"
    APDO_TYPE_EPR_AVS -> "AVS ${formatMillivolts(rdo.bits(9, 12) * 25)} ${formatMilliamps(rdo.bits(0, 7) * 50)}"
    else -> "RDO 0x%08X".format(rdo)
  }
}

fun fastRoleSwapCurrent(sinkCapabilities: List<Int>): FastRoleSwapCurrent? =
  sinkCapabilities.firstOrNull()?.let { FastRoleSwapCurrent.entries[it.bits(FIXED_SINK_PDO_FAST_ROLE_SWAP_SHIFT, 2)] }

fun isPartnerFeatureSupported(
  feature: PartnerFeature,
  sourceCapabilities: List<Int>,
  sinkCapabilities: List<Int>,
): Boolean? {
  val source = sourceCapabilities.firstOrNull()
  val sink = sinkCapabilities.firstOrNull()
  val bit = when (feature) {
    PartnerFeature.DualRolePower -> FIXED_PDO_DUAL_ROLE_POWER_BIT
    PartnerFeature.DualRoleData -> FIXED_PDO_DUAL_ROLE_DATA_BIT
    PartnerFeature.UsbCommunication -> FIXED_PDO_USB_COMMUNICATION_BIT
    PartnerFeature.UnconstrainedPower -> FIXED_PDO_UNCONSTRAINED_POWER_BIT
    PartnerFeature.Epr -> return source?.let { it.hasBit(FIXED_SOURCE_PDO_EPR_BIT) }
  }
  if (source == null && sink == null) return null
  return source.hasBit(bit) || sink.hasBit(bit)
}
