package com.drunkbatya.drunkheadphonesadapter

import adapter.AudioStatusResponse
import adapter.AudioStreamStatus
import adapter.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

private const val SPEAKER_VOLUME_OFFSET_MAX_STEPS = 20

@Composable
fun AudioTab(
  audio: AudioStatusResponse?,
  settings: Settings?,
  settingsSaving: Boolean,
  onSpeakerVolumeOffset: (Int) -> Unit,
  onSaveSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  StatusColumn(modifier) {
    Section(stringResource(R.string.section_headphones)) {
      InfoRow(
        stringResource(R.string.headphones_plugged),
        audio?.let { stringResource(if (it.headphone_plugged) R.string.yes else R.string.no) },
      )
      InfoRow(
        stringResource(R.string.headset_mic),
        audio?.takeIf { it.headphone_plugged }?.let {
          stringResource(if (it.headset_mic_present) R.string.yes else R.string.no)
        },
      )
      InfoRow(
        stringResource(R.string.headphones_output),
        audio?.let { stringResource(if (it.headphone_output_powered) R.string.on else R.string.off) },
      )
    }
    SettingsSection(settings, settingsSaving, onSpeakerVolumeOffset, onSaveSettings)
    StreamSection(stringResource(R.string.section_playback), audio?.speaker)
    StreamSection(stringResource(R.string.section_microphone), audio?.mic)
  }
}

@Composable
private fun SettingsSection(
  settings: Settings?,
  saving: Boolean,
  onSpeakerVolumeOffset: (Int) -> Unit,
  onSave: () -> Unit,
) {
  val offset = settings?.speaker_volume_offset ?: 0
  Section(stringResource(R.string.section_settings)) {
    InfoRow(stringResource(R.string.volume_offset), settings?.let { formatVolumeOffset(offset) })
    Slider(
      enabled = settings != null,
      value = offset.toFloat(),
      onValueChange = { value ->
        val steps = value.roundToInt()
        if (steps != offset) onSpeakerVolumeOffset(steps)
      },
      valueRange = -SPEAKER_VOLUME_OFFSET_MAX_STEPS.toFloat()..SPEAKER_VOLUME_OFFSET_MAX_STEPS.toFloat(),
      steps = 2 * SPEAKER_VOLUME_OFFSET_MAX_STEPS - 1,
    )
    Button(onClick = onSave, enabled = settings != null && !saving, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(if (saving) R.string.settings_saving else R.string.settings_save))
    }
  }
}

@Composable
private fun StreamSection(title: String, stream: AudioStreamStatus?) {
  Section(title) {
    InfoRow(
      stringResource(R.string.stream),
      stream?.let {
        if (it.active) formatSampleFormat(it.sample_rate, it.sample_bits) else stringResource(R.string.stream_idle)
      },
    )
    InfoRow(
      stringResource(R.string.volume),
      stream?.let { if (it.muted) stringResource(R.string.volume_muted) else formatVolume(it.volume_db_x256) },
    )
    InfoRow(stringResource(R.string.i2s_errors), stream?.i2s_error_count?.toString())
  }
}
