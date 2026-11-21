package com.drunkbatya.drunkheadphonesadapter

import adapter.AudioStatusRequest
import adapter.AudioStatusResponse
import adapter.ErrType
import adapter.RebootToBootloaderRequest
import adapter.RpcRequest
import adapter.Settings
import adapter.SettingsGetRequest
import adapter.SettingsSaveRequest
import adapter.SettingsSaveResult
import adapter.SettingsSetRequest
import adapter.SystemStatusRequest
import adapter.SystemStatusResponse
import adapter.UsbcStatusRequest
import adapter.UsbcStatusResponse
import adapter.VersionRequest
import adapter.VersionResponse
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "Adapter"
private const val CONNECT_TIMEOUT_MS = 1000L
private const val REQUEST_TIMEOUT_MS = 1000L
private const val REBOOT_TIMEOUT_MS = 10000L
private const val SETTINGS_SAVE_TIMEOUT_MS = 3000L
private const val DEVICE_LIST_POLL_MS = 100L
private const val STATUS_POLL_MS = 1000L

enum class ConnectionState(@param:StringRes val label: Int) {
  Disconnected(R.string.connection_disconnected),
  Connecting(R.string.connection_connecting),
  Connected(R.string.connection_connected),
  Bootloader(R.string.connection_bootloader),
}

data class AdapterState(
  val connection: ConnectionState = ConnectionState.Disconnected,
  val firmware: VersionResponse? = null,
  val system: SystemMetrics? = null,
  val audio: AudioStatusResponse? = null,
  val usbc: UsbcStatusResponse? = null,
  val settings: Settings? = null,
  val settingsSaving: Boolean = false,
)

enum class StatusSource(val request: RpcRequest) {
  System(RpcRequest(system_status = SystemStatusRequest())),
  Audio(RpcRequest(audio_status = AudioStatusRequest())),
  Usbc(RpcRequest(usbc_status = UsbcStatusRequest())),
}

data class ConnectionError(@param:StringRes val title: Int, val reason: String)

sealed interface UpdateState {
  data class Confirm(val image: FirmwareImage) : UpdateState
  data class Writing(val progress: Float) : UpdateState
  data object Rebooting : UpdateState
  data class Done(val version: String) : UpdateState
  data class Failed(@param:StringRes val title: Int, val reason: String) : UpdateState
}

class AdapterViewModel(application: Application) : AndroidViewModel(application) {
  private val usbManager = application.getSystemService(UsbManager::class.java)
  private val mutableState = MutableStateFlow(AdapterState())
  val state: StateFlow<AdapterState> = mutableState
  private val mutableUpdateState = MutableStateFlow<UpdateState?>(null)
  val updateState: StateFlow<UpdateState?> = mutableUpdateState
  private val mutableConnectionError = MutableStateFlow<ConnectionError?>(null)
  val connectionError: StateFlow<ConnectionError?> = mutableConnectionError
  private val mutableSettingsSaveError = MutableStateFlow<String?>(null)
  val settingsSaveError: StateFlow<String?> = mutableSettingsSaveError
  private var adapterLink: AdapterLink? = null
  private var bootloaderDevice: UsbDevice? = null
  private var isAppVisible = false
  private val speakerVolumeOffsetRequest = MutableStateFlow<Int?>(null)

  private val detachReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
      if (device != null && (device == adapterLink?.device || device == bootloaderDevice)) {
        disconnectWithError("${device.deviceName} detached")
      }
    }
  }

  init {
    application.registerReceiver(
      detachReceiver,
      IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
      Context.RECEIVER_NOT_EXPORTED,
    )
    viewModelScope.launch {
      speakerVolumeOffsetRequest.filterNotNull().collect { sendSpeakerVolumeOffset(it) }
    }
  }

  fun connect() = connect(isNotFoundReported = true)

  fun onAppVisible() {
    isAppVisible = true
    connect(isNotFoundReported = false)
  }

  fun onAppHidden() {
    isAppVisible = false
    if (!isUpdateRunning()) disconnect()
  }

  private fun isUpdateRunning(): Boolean {
    val update = mutableUpdateState.value
    return update is UpdateState.Writing || update is UpdateState.Rebooting
  }

  fun dismissConnectionError() {
    mutableConnectionError.value = null
  }

  fun dismissSettingsSaveError() {
    mutableSettingsSaveError.value = null
  }

  private fun connect(isNotFoundReported: Boolean) {
    if (mutableState.value.connection != ConnectionState.Disconnected || isUpdateRunning()) return
    viewModelScope.launch {
      val adapterDevice = findAdapterDevice(usbManager)
      val bootloader = findBootloaderDevice(usbManager)
      when {
        adapterDevice != null -> connectAdapter(adapterDevice)
        bootloader != null -> connectBootloader(bootloader)
        isNotFoundReported -> reportConnectionError(
          R.string.connect_failed,
          "Adapter %04X:%04X and bootloader %04X:%04X not found".format(
            ADAPTER_VENDOR_ID,
            ADAPTER_PRODUCT_ID,
            BOOTLOADER_VENDOR_ID,
            BOOTLOADER_PRODUCT_ID,
          ),
        )
      }
    }
  }

  fun disconnect() {
    adapterLink?.close()
    adapterLink = null
    bootloaderDevice = null
    speakerVolumeOffsetRequest.value = null
    mutableState.value = AdapterState()
  }

  fun setSpeakerVolumeOffset(offset: Int) {
    val state = mutableState.value
    val settings = state.settings ?: return
    mutableState.value = state.copy(settings = settings.copy(speaker_volume_offset = offset))
    speakerVolumeOffsetRequest.value = offset
  }

  private suspend fun sendSpeakerVolumeOffset(offset: Int) {
    val link = adapterLink ?: return
    val request = RpcRequest(settings_set = SettingsSetRequest(Settings(speaker_volume_offset = offset)))
    try {
      val response = withTimeout(REQUEST_TIMEOUT_MS) { link.request(request) }
      if (adapterLink !== link) return
      val settings = response.settings_set?.settings
      if (settings == null) {
        Log.e(TAG, "Settings set request to ${link.device.deviceName}: ${response.generic?.err_type}")
        return
      }
      if (speakerVolumeOffsetRequest.value == offset) mutableState.value = mutableState.value.copy(settings = settings)
    } catch (e: TimeoutCancellationException) {
      if (adapterLink !== link) return
      disconnectWithError("Settings set request to ${link.device.deviceName}: no response in $REQUEST_TIMEOUT_MS ms")
    } catch (e: IOException) {
      if (adapterLink !== link) return
      disconnectWithError("Settings set request to ${link.device.deviceName}: ${e.message}")
    }
  }

  fun saveSettings() {
    val link = adapterLink ?: return
    val settings = mutableState.value.settings ?: return
    if (mutableState.value.settingsSaving) return
    mutableState.value = mutableState.value.copy(settingsSaving = true)
    viewModelScope.launch {
      val error = try {
        withTimeout(SETTINGS_SAVE_TIMEOUT_MS) {
          val setResponse = link.request(RpcRequest(settings_set = SettingsSetRequest(settings)))
          if (setResponse.settings_set?.settings == null) {
            "Settings set request to ${link.device.deviceName}: ${setResponse.generic?.err_type}"
          } else {
            val result = link.request(RpcRequest(settings_save = SettingsSaveRequest())).settings_save?.result
            if (result == SettingsSaveResult.SETTINGS_SAVE_RESULT_OK) null else "$result"
          }
        }
      } catch (e: TimeoutCancellationException) {
        "Settings save request to ${link.device.deviceName}: no response in $SETTINGS_SAVE_TIMEOUT_MS ms"
      } catch (e: IOException) {
        "Settings save request to ${link.device.deviceName}: ${e.message}"
      }
      if (adapterLink !== link) return@launch
      mutableState.value = mutableState.value.copy(settingsSaving = false)
      if (error == null) {
        Toast.makeText(getApplication(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
      } else {
        Log.e(TAG, error)
        mutableSettingsSaveError.value = error
      }
    }
  }

  private suspend fun requestSettings(link: AdapterLink): Settings? {
    val response = try {
      withTimeout(REQUEST_TIMEOUT_MS) { link.request(RpcRequest(settings_get = SettingsGetRequest())) }
    } catch (e: TimeoutCancellationException) {
      throw IOException("Settings request to ${link.device.deviceName}: no response in $REQUEST_TIMEOUT_MS ms")
    }
    val settings = response.settings_get?.settings
    if (settings == null) {
      Log.e(TAG, "Settings request to ${link.device.deviceName}: ${response.generic?.err_type}")
    }
    return settings
  }

  fun selectFirmwareFile(uri: Uri) {
    viewModelScope.launch {
      mutableUpdateState.value = try {
        val bytes = withContext(Dispatchers.IO) {
          getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Open $uri failed")
        }
        val image = parseFirmwareImage(bytes)
        val adapterTarget = mutableState.value.firmware?.target
        if (adapterTarget != null) {
          require(image.target == adapterTarget) { "Firmware target ${image.target}, adapter target $adapterTarget" }
        }
        UpdateState.Confirm(image)
      } catch (e: Exception) {
        UpdateState.Failed(R.string.update_file_invalid, e.message.orEmpty())
      }
    }
  }

  fun dismissUpdate() {
    mutableUpdateState.value = null
  }

  fun startUpdate(image: FirmwareImage) {
    val connectedAdapterLink = adapterLink
    val connectedBootloader = bootloaderDevice
    mutableUpdateState.value = UpdateState.Rebooting
    viewModelScope.launch {
      try {
        val dfuDevice = connectedBootloader
          ?: rebootToBootloader(checkNotNull(connectedAdapterLink) { "Adapter not connected" })
        mutableUpdateState.value = UpdateState.Writing(0f)
        writeFirmwareOverDfu(dfuDevice, image)
        mutableUpdateState.value = UpdateState.Rebooting
        val version = awaitRestartedAdapter(dfuDevice)
        check(version == image.version) { "Adapter reports version $version, expected ${image.version}" }
        mutableUpdateState.value = UpdateState.Done(version)
        if (!isAppVisible) disconnect()
      } catch (e: Exception) {
        Log.e(TAG, "Update failed: ${e.message}")
        disconnect()
        mutableUpdateState.value = UpdateState.Failed(R.string.update_failed, e.message.orEmpty())
      }
    }
  }

  suspend fun pollStatus(source: StatusSource) {
    val link = adapterLink ?: return
    var previousSystemStatus: SystemStatusResponse? = null
    try {
      while (adapterLink === link) {
        val response = withTimeout(REQUEST_TIMEOUT_MS) { link.request(source.request) }
        if (adapterLink !== link) return
        val state = mutableState.value
        val system = response.system_status
        mutableState.value = when {
          system != null -> state.copy(system = systemMetrics(system, previousSystemStatus))
          response.audio_status != null -> state.copy(audio = response.audio_status)
          response.usbc_status != null -> state.copy(usbc = response.usbc_status)
          else -> {
            Log.e(TAG, "${source.name} status request to ${link.device.deviceName}: ${response.generic?.err_type}")
            return
          }
        }
        previousSystemStatus = system
        delay(STATUS_POLL_MS)
      }
    } catch (e: TimeoutCancellationException) {
      if (adapterLink !== link) return
      disconnectWithError("${source.name} status request to ${link.device.deviceName}: no response in $REQUEST_TIMEOUT_MS ms")
    } catch (e: IOException) {
      if (adapterLink !== link) return
      disconnectWithError("${source.name} status request to ${link.device.deviceName}: ${e.message}")
    }
  }

  private fun disconnectWithError(reason: String) {
    disconnect()
    reportConnectionError(R.string.adapter_disconnected, reason)
  }

  private fun reportConnectionError(@StringRes title: Int, reason: String) {
    Log.e(TAG, reason)
    if (mutableUpdateState.value == null) mutableConnectionError.value = ConnectionError(title, reason)
  }

  private suspend fun connectAdapter(device: UsbDevice): VersionResponse? {
    mutableState.value = AdapterState(ConnectionState.Connecting)
    if (!requestUsbPermission(getApplication(), usbManager, device)) {
      mutableState.value = AdapterState()
      reportConnectionError(R.string.connect_failed, "USB permission for ${device.deviceName} denied")
      return null
    }
    return try {
      val link = AdapterLink.open(usbManager, device)
      adapterLink = link
      val response = try {
        withTimeout(CONNECT_TIMEOUT_MS) { link.request(RpcRequest(version = VersionRequest())) }
      } catch (e: TimeoutCancellationException) {
        throw IOException("Version request to ${device.deviceName}: no response in $CONNECT_TIMEOUT_MS ms")
      }
      val firmware = response.version
        ?: throw IOException("Version request to ${device.deviceName}: ${response.generic?.err_type}")
      val settings = requestSettings(link)
      mutableState.value = AdapterState(ConnectionState.Connected, firmware, settings = settings)
      firmware
    } catch (e: IOException) {
      disconnect()
      reportConnectionError(R.string.connect_failed, e.message.orEmpty())
      null
    }
  }

  private suspend fun connectBootloader(device: UsbDevice) {
    mutableState.value = AdapterState(ConnectionState.Connecting)
    if (!requestUsbPermission(getApplication(), usbManager, device)) {
      mutableState.value = AdapterState()
      reportConnectionError(R.string.connect_failed, "USB permission for ${device.deviceName} denied")
      return
    }
    bootloaderDevice = device
    mutableState.value = AdapterState(ConnectionState.Bootloader)
  }

  private suspend fun rebootToBootloader(link: AdapterLink): UsbDevice {
    link.requestAndCheckOk("Reboot to bootloader", RpcRequest(reboot_to_bootloader = RebootToBootloaderRequest()))
    awaitDetached(link.device)
    disconnect()
    val device = awaitAttached("Bootloader") { findBootloaderDevice(usbManager) }
    if (!requestUsbPermission(getApplication(), usbManager, device)) {
      throw IOException("USB permission for ${device.deviceName} denied")
    }
    return device
  }

  private suspend fun writeFirmwareOverDfu(device: UsbDevice, image: FirmwareImage) = withContext(Dispatchers.IO) {
    DfuDevice.open(usbManager, device).use { dfu ->
      dfu.writeFirmware(image.bytes) { mutableUpdateState.value = UpdateState.Writing(it) }
      val writtenOptr = try {
        dfu.writeRequiredOptionBytes()
      } catch (e: IOException) {
        Log.e(TAG, "Option bytes on ${device.deviceName} failed: ${e.message}")
        null
      }
      if (writtenOptr == null) {
        dfu.startFirmware()
      } else {
        Log.i(TAG, "Option bytes OPTR 0x%08X written".format(writtenOptr))
      }
    }
  }

  private suspend fun awaitRestartedAdapter(dfuDevice: UsbDevice): String {
    awaitDetached(dfuDevice)
    disconnect()
    val device = awaitAttached("Adapter") { findAdapterDevice(usbManager) }
    val firmware = connectAdapter(device) ?: throw IOException("Connect ${device.deviceName} after reboot failed")
    return firmware.version
  }

  private suspend fun awaitDetached(device: UsbDevice) {
    try {
      withTimeout(REBOOT_TIMEOUT_MS) {
        while (usbManager.deviceList.values.contains(device)) delay(DEVICE_LIST_POLL_MS)
      }
    } catch (e: TimeoutCancellationException) {
      throw IOException("${device.deviceName} still attached after $REBOOT_TIMEOUT_MS ms")
    }
  }

  private suspend fun awaitAttached(name: String, find: () -> UsbDevice?): UsbDevice {
    try {
      return withTimeout(REBOOT_TIMEOUT_MS) {
        var device = find()
        while (device == null) {
          delay(DEVICE_LIST_POLL_MS)
          device = find()
        }
        device
      }
    } catch (e: TimeoutCancellationException) {
      throw IOException("$name not attached in $REBOOT_TIMEOUT_MS ms")
    }
  }

  private suspend fun AdapterLink.requestAndCheckOk(step: String, request: RpcRequest) {
    val response = try {
      withTimeout(REQUEST_TIMEOUT_MS) { request(request) }
    } catch (e: TimeoutCancellationException) {
      throw IOException("$step: no response in $REQUEST_TIMEOUT_MS ms")
    }
    val errType = response.generic?.err_type
    if (errType != ErrType.ERROR_TYPE_OK) throw IOException("$step: $errType")
  }

  override fun onCleared() {
    getApplication<Application>().unregisterReceiver(detachReceiver)
    adapterLink?.close()
  }
}
