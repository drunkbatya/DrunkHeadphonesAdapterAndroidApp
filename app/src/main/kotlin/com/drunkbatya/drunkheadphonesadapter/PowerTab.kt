package com.drunkbatya.drunkheadphonesadapter

import adapter.UsbcCcPin
import adapter.UsbcDataRole
import adapter.UsbcPortMode
import adapter.UsbcPortStatus
import adapter.UsbcPowerRole
import adapter.UsbcRpCurrent
import adapter.UsbcSpecRevision
import adapter.UsbcStatusResponse
import adapter.UsbcVbusLevel
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

@get:StringRes
private val UsbcPortMode.label: Int
  get() = when (this) {
    UsbcPortMode.USBC_PORT_MODE_DISABLED -> R.string.port_mode_disabled
    UsbcPortMode.USBC_PORT_MODE_SINK -> R.string.power_role_sink
    UsbcPortMode.USBC_PORT_MODE_SOURCE -> R.string.power_role_source
    UsbcPortMode.USBC_PORT_MODE_DUAL_ROLE -> R.string.port_mode_dual_role
    UsbcPortMode.USBC_PORT_MODE_DUAL_ROLE_TRY_SINK -> R.string.port_mode_dual_role_try_sink
  }

@get:StringRes
private val UsbcPowerRole.label: Int
  get() = when (this) {
    UsbcPowerRole.USBC_POWER_ROLE_SINK -> R.string.power_role_sink
    UsbcPowerRole.USBC_POWER_ROLE_SOURCE -> R.string.power_role_source
  }

@get:StringRes
private val UsbcDataRole.label: Int
  get() = when (this) {
    UsbcDataRole.USBC_DATA_ROLE_UFP -> R.string.data_role_device
    UsbcDataRole.USBC_DATA_ROLE_DFP -> R.string.data_role_host
  }

@get:StringRes
private val UsbcCcPin.label: Int
  get() = when (this) {
    UsbcCcPin.USBC_CC_PIN_NONE -> R.string.value_unknown
    UsbcCcPin.USBC_CC_PIN_CC1 -> R.string.cc_pin_cc1
    UsbcCcPin.USBC_CC_PIN_CC2 -> R.string.cc_pin_cc2
  }

@get:StringRes
private val UsbcRpCurrent.label: Int
  get() = when (this) {
    UsbcRpCurrent.USBC_RP_CURRENT_DEFAULT -> R.string.current_default_usb
    UsbcRpCurrent.USBC_RP_CURRENT_1A5 -> R.string.current_1a5
    UsbcRpCurrent.USBC_RP_CURRENT_3A0 -> R.string.current_3a0
  }

@get:StringRes
private val UsbcVbusLevel.label: Int
  get() = when (this) {
    UsbcVbusLevel.USBC_VBUS_LEVEL_SAFE_0V -> R.string.vbus_safe_0v
    UsbcVbusLevel.USBC_VBUS_LEVEL_BELOW_SINK_DISCONNECT -> R.string.vbus_below_sink_disconnect
    UsbcVbusLevel.USBC_VBUS_LEVEL_PRESENT -> R.string.vbus_present
    UsbcVbusLevel.USBC_VBUS_LEVEL_ABOVE_SAFE_5V -> R.string.vbus_above_safe_5v
  }

@get:StringRes
private val FastRoleSwapCurrent.label: Int
  get() = when (this) {
    FastRoleSwapCurrent.NotSupported -> R.string.not_supported
    FastRoleSwapCurrent.DefaultUsb -> R.string.current_default_usb
    FastRoleSwapCurrent.Current1A5 -> R.string.current_1a5
    FastRoleSwapCurrent.Current3A0 -> R.string.current_3a0
  }

@get:StringRes
private val PartnerFeature.label: Int
  get() = when (this) {
    PartnerFeature.DualRolePower -> R.string.feature_dual_role_power
    PartnerFeature.DualRoleData -> R.string.feature_dual_role_data
    PartnerFeature.UsbCommunication -> R.string.feature_usb_communication
    PartnerFeature.UnconstrainedPower -> R.string.feature_unconstrained_power
    PartnerFeature.Epr -> R.string.feature_epr
  }

enum class PowerPort(@param:StringRes val title: Int) {
  Phone(R.string.section_phone),
  Charger(R.string.section_charger),
}

private fun UsbcStatusResponse.port(port: PowerPort): UsbcPortStatus? = when (port) {
  PowerPort.Phone -> phone
  PowerPort.Charger -> charger
}

private val UsbcPortStatus.attachedPort: UsbcPortStatus?
  get() = takeIf { it.attached }

@Composable
fun PowerTab(usbc: UsbcStatusResponse?, onPortClick: (PowerPort) -> Unit, modifier: Modifier = Modifier) {
  StatusColumn(modifier) {
    PowerPort.entries.forEach { powerPort ->
      val port = usbc?.port(powerPort)
      Section(stringResource(powerPort.title), onClick = { onPortClick(powerPort) }) {
        InfoRow(stringResource(R.string.partner), port?.let { partnerDescription(it) })
        InfoRow(stringResource(R.string.contract), port?.let { contractDescription(it) })
        InfoRow(stringResource(R.string.vbus), port?.let { stringResource(it.vbus_level.label) })
        InfoRow(stringResource(R.string.port_mode), port?.let { stringResource(it.mode.label) })
      }
    }
  }
}

@Composable
fun PowerPortScreen(powerPort: PowerPort, usbc: UsbcStatusResponse?, onBack: () -> Unit) {
  val port = usbc?.port(powerPort)
  val attachedPort = port?.attachedPort
  DetailScreen(stringResource(powerPort.title), onBack) {
    Section(stringResource(R.string.connection)) {
      InfoRow(stringResource(R.string.partner), port?.let { partnerDescription(it) })
      InfoRow(stringResource(R.string.contract), port?.let { contractDescription(it) })
      InfoRow(stringResource(R.string.power_role), attachedPort?.let { stringResource(it.power_role.label) })
      InfoRow(stringResource(R.string.data_role), attachedPort?.let { stringResource(it.data_role.label) })
      InfoRow(stringResource(R.string.cc_pin), attachedPort?.let { stringResource(it.cc_pin.label) })
      InfoRow(stringResource(R.string.vbus), port?.let { stringResource(it.vbus_level.label) })
      InfoRow(stringResource(R.string.port_mode), port?.let { stringResource(it.mode.label) })
      InfoRow(
        stringResource(R.string.protection),
        port?.let { stringResource(if (it.tcpp03_faulted) R.string.protection_fault else R.string.ok) },
        if (port?.tcpp03_faulted == true) MaterialTheme.colorScheme.error else Color.Unspecified,
      )
    }
    Section(stringResource(R.string.partner)) {
      InfoRow(
        stringResource(R.string.fast_role_swap),
        attachedPort?.let { fastRoleSwapCurrent(it.partner_sink_capabilities) }?.let { stringResource(it.label) },
      )
      PartnerFeature.entries.forEach { feature ->
        val isSupported = attachedPort?.let {
          isPartnerFeatureSupported(feature, it.partner_source_capabilities, it.partner_sink_capabilities)
        }
        InfoRow(stringResource(feature.label), isSupported?.let { stringResource(if (it) R.string.yes else R.string.no) })
      }
    }
    CapabilitiesSection(stringResource(R.string.source_capabilities), attachedPort?.partner_source_capabilities)
    CapabilitiesSection(stringResource(R.string.sink_capabilities), attachedPort?.partner_sink_capabilities)
    CapabilitiesSection(stringResource(R.string.offer), attachedPort?.offer)
  }
}

@Composable
private fun CapabilitiesSection(title: String, powerDataObjects: List<Int>?) {
  Section(title) {
    if (powerDataObjects.isNullOrEmpty()) {
      InfoRow(stringResource(R.string.power_data_object, 1), null)
    }
    powerDataObjects?.forEachIndexed { index, pdo ->
      InfoRow(stringResource(R.string.power_data_object, index + 1), describePowerDataObject(pdo))
    }
  }
}

@Composable
private fun partnerDescription(port: UsbcPortStatus): String = when {
  !port.attached -> stringResource(R.string.partner_not_attached)
  port.partner_not_pd -> stringResource(R.string.partner_type_c)
  port.spec_revision == UsbcSpecRevision.USBC_SPEC_REVISION_2 -> stringResource(R.string.partner_pd_2)
  port.spec_revision == UsbcSpecRevision.USBC_SPEC_REVISION_3 -> stringResource(R.string.partner_pd_3)
  else -> stringResource(R.string.partner_pd)
}

@Composable
private fun contractDescription(port: UsbcPortStatus): String? = when {
  !port.attached -> null
  port.contract_pdo != 0 -> describeContract(port.contract_pdo, port.contract_rdo)
  port.partner_not_pd && port.power_role == UsbcPowerRole.USBC_POWER_ROLE_SINK ->
    stringResource(R.string.contract_type_c, stringResource(port.partner_rp_current.label))
  else -> null
}
