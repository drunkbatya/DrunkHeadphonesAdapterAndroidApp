package com.drunkbatya.drunkheadphonesadapter

import adapter.VersionResponse
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
  onFirmwareFile: (Uri) -> Unit,
  onConfirmUpdate: (FirmwareImage) -> Unit,
  onDismissUpdate: () -> Unit,
  onDismissConnectionError: () -> Unit,
) {
  UpdateDialogs(update, state.firmware?.version, onConfirmUpdate, onDismissUpdate)
  connectionError?.let { MessageDialog(stringResource(it.title), it.reason, onDismissConnectionError) }
  var isDeviceInfoOpen by rememberSaveable { mutableStateOf(false) }
  var openedPowerPort by rememberSaveable { mutableStateOf<PowerPort?>(null) }
  var selectedTab by rememberSaveable { mutableStateOf(AppTab.Adapter) }
  val statusSource = when {
    isDeviceInfoOpen -> null
    openedPowerPort != null -> StatusSource.Usbc
    else -> selectedTab.statusSource
  }
  if (statusSource != null && state.connection == ConnectionState.Connected && update == null) {
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
        modifier = tabModifier,
      )
      AppTab.Audio -> AudioTab(state.audio, tabModifier)
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
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).padding(top = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      FlippableBoard(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
    }
    ConnectionButton(state.connection, onConnect, onDisconnect)
    DeviceInfoCard(
      connection = state.connection,
      firmware = state.firmware,
      isUpdateAvailable = state.connection == ConnectionState.Connected ||
        state.connection == ConnectionState.Bootloader,
      onClick = onDeviceInfo,
      onFirmwareFile = onFirmwareFile,
    )
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
