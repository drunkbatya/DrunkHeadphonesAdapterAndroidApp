package com.drunkbatya.drunkheadphonesadapter

import adapter.VersionResponse
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private enum class AppTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val statusSource: StatusSource?) {
  Adapter(R.string.tab_adapter, R.drawable.ic_board, null),
  Audio(R.string.tab_audio, R.drawable.ic_headphones, StatusSource.Audio),
  Power(R.string.tab_power, R.drawable.ic_power, StatusSource.Usbc),
  System(R.string.tab_system, R.drawable.ic_chip, StatusSource.System),
}

@Composable
fun AppScreen(
  state: AdapterState,
  update: UpdateState?,
  connectionError: ConnectionError?,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onPollStatus: suspend (StatusSource) -> Unit,
  settingsSaveError: String?,
  onSpeakerVolumeOffset: (Int) -> Unit,
  onMicGain: (Int) -> Unit,
  onSaveSettings: () -> Unit,
  onDismissSettingsSaveError: () -> Unit,
  onFirmwareFile: (Uri) -> Unit,
  onConfirmUpdate: (FirmwareImage) -> Unit,
  onDismissUpdate: () -> Unit,
  onDismissConnectionError: () -> Unit,
  boardAction: BoardActionState?,
  onBoardAction: (BoardAction) -> Unit,
  onConfirmBoardAction: (BoardAction) -> Unit,
  onDismissBoardAction: () -> Unit,
) {
  UpdateDialogs(update, state.firmware?.version, onConfirmUpdate, onDismissUpdate)
  BoardActionDialogs(boardAction, onConfirmBoardAction, onDismissBoardAction)
  connectionError?.let { MessageDialog(stringResource(it.title), it.reason, onDismissConnectionError) }
  settingsSaveError?.let {
    MessageDialog(stringResource(R.string.settings_save_failed), it, onDismissSettingsSaveError)
  }
  var isDeviceInfoOpen by rememberSaveable { mutableStateOf(false) }
  var openedPowerPort by rememberSaveable { mutableStateOf<PowerPort?>(null) }
  var selectedTab by rememberSaveable { mutableStateOf(AppTab.Adapter) }
  val statusSource = when {
    isDeviceInfoOpen -> null
    openedPowerPort != null -> StatusSource.Usbc
    else -> selectedTab.statusSource
  }
  if (statusSource != null && state.connection == ConnectionState.Connected && update == null && boardAction == null) {
    LaunchedEffect(statusSource) { onPollStatus(statusSource) }
  }
  if (isDeviceInfoOpen) {
    DeviceInfoScreen(state.firmware, onBack = { isDeviceInfoOpen = false })
    return
  }
  openedPowerPort?.let { powerPort ->
    PowerPortScreen(powerPort, state.usbc, onBack = { openedPowerPort = null })
    return
  }
  Scaffold(
    bottomBar = {
      NavigationBar {
        AppTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = tab == selectedTab,
            onClick = { selectedTab = tab },
            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
            label = { Text(stringResource(tab.label)) },
          )
        }
      }
    },
  ) { padding ->
    val tabModifier = Modifier.padding(padding)
    when (selectedTab) {
      AppTab.Adapter -> BoardTab(
        state = state,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        onDeviceInfo = { isDeviceInfoOpen = true },
        onFirmwareFile = onFirmwareFile,
        onBoardAction = onBoardAction,
        modifier = tabModifier,
      )
      AppTab.Audio -> AudioTab(
        state.audio,
        state.settings,
        state.settingsSaving,
        onSpeakerVolumeOffset,
        onMicGain,
        onSaveSettings,
        tabModifier,
      )
      AppTab.Power -> PowerTab(state.usbc, onPortClick = { openedPowerPort = it }, modifier = tabModifier)
      AppTab.System -> SystemTab(state.system, tabModifier)
    }
  }
}

@Composable
private fun BoardTab(
  state: AdapterState,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onDeviceInfo: () -> Unit,
  onFirmwareFile: (Uri) -> Unit,
  onBoardAction: (BoardAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isConnected = state.connection == ConnectionState.Connected
  val isBootloader = state.connection == ConnectionState.Bootloader
  BoxWithConstraints(modifier.fillMaxSize()) {
    val boardHeight = maxHeight / 2
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().height(boardHeight).padding(top = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        FlippableBoard(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
      }
      ConnectionButton(state.connection, onConnect, onDisconnect)
      DeviceInfoCard(
        connection = state.connection,
        firmware = state.firmware,
        isUpdateAvailable = isConnected || isBootloader,
        onClick = onDeviceInfo,
        onFirmwareFile = onFirmwareFile,
      )
      UsbBypassCard(isAvailable = isConnected, onBoardAction = onBoardAction)
      RebootCard(isConnected = isConnected, isBootloader = isBootloader, onBoardAction = onBoardAction)
    }
  }
}

@Composable
private fun UsbBypassCard(isAvailable: Boolean, onBoardAction: (BoardAction) -> Unit) {
  Section(stringResource(R.string.section_usb_bypass)) {
    FilledTonalButton(
      onClick = { onBoardAction(BoardAction.UsbBypass) },
      enabled = isAvailable,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(R.string.usb_bypass_enable))
    }
  }
}

@Composable
private fun RebootCard(isConnected: Boolean, isBootloader: Boolean, onBoardAction: (BoardAction) -> Unit) {
  Section(stringResource(R.string.section_reboot)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(
        onClick = { onBoardAction(BoardAction.Reboot) },
        enabled = isConnected || isBootloader,
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.reboot_normal))
      }
      FilledTonalButton(
        onClick = { onBoardAction(BoardAction.RebootToBootloader) },
        enabled = isConnected,
        modifier = Modifier.weight(1f),
      ) {
        Text(stringResource(R.string.reboot_dfu))
      }
    }
  }
}

@Composable
private fun ConnectionButton(connection: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
  val modifier = Modifier.fillMaxWidth()
  when (connection) {
    ConnectionState.Connected, ConnectionState.Bootloader -> OutlinedButton(onClick = onDisconnect, modifier = modifier) {
      Text(stringResource(R.string.disconnect))
    }
    ConnectionState.Connecting -> Button(onClick = {}, enabled = false, modifier = modifier) {
      Text(stringResource(R.string.connection_connecting))
    }
    ConnectionState.Disconnected -> Button(onClick = onConnect, modifier = modifier) {
      Text(stringResource(R.string.connect))
    }
  }
}

@Composable
private fun DeviceInfoCard(
  connection: ConnectionState,
  firmware: VersionResponse?,
  isUpdateAvailable: Boolean,
  onClick: () -> Unit,
  onFirmwareFile: (Uri) -> Unit,
) {
  val firmwareFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onFirmwareFile(uri)
  }
  Section(stringResource(R.string.device_info), onClick = onClick) {
    InfoRow(stringResource(R.string.connection), stringResource(connection.label))
    InfoRow(stringResource(R.string.firmware_version), firmware?.version)
    InfoRow(stringResource(R.string.build_date), firmware?.build_date)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.update))
      }
      FilledTonalIconButton(onClick = { firmwareFilePicker.launch(arrayOf("*/*")) }, enabled = isUpdateAvailable) {
        Icon(painterResource(R.drawable.ic_file), contentDescription = stringResource(R.string.update_select_file))
      }
    }
  }
}
