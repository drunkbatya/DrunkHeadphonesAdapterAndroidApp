package com.drunkbatya.drunkheadphonesadapter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
  private val adapter: AdapterViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by adapter.state.collectAsState()
      val update by adapter.updateState.collectAsState()
      val connectionError by adapter.connectionError.collectAsState()
      AppTheme {
        AppScreen(
          state = state,
          update = update,
          connectionError = connectionError,
          onConnect = adapter::connect,
          onDisconnect = adapter::disconnect,
          onPollStatus = adapter::pollStatus,
          onFirmwareFile = adapter::selectFirmwareFile,
          onConfirmUpdate = adapter::startUpdate,
          onDismissUpdate = adapter::dismissUpdate,
          onDismissConnectionError = adapter::dismissConnectionError,
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    adapter.onAppVisible()
  }

  override fun onStop() {
    adapter.onAppHidden()
    super.onStop()
  }
}
