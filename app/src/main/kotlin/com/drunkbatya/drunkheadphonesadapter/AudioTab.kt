package com.drunkbatya.drunkheadphonesadapter

import adapter.AudioStatusResponse
import adapter.AudioStreamStatus
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun AudioTab(audio: AudioStatusResponse?, modifier: Modifier = Modifier) {
  StatusColumn(modifier) {
    Section(stringResource(R.string.section_headphones)) {
      InfoRow(
        stringResource(R.string.headphones_plugged),
        audio?.let { stringResource(if (it.headphone_plugged) R.string.yes else R.string.no) },
      )
      InfoRow(
        stringResource(R.string.headphones_output),
        audio?.let { stringResource(if (it.headphone_output_powered) R.string.on else R.string.off) },
      )
    }
    StreamSection(stringResource(R.string.section_playback), audio?.speaker)
    StreamSection(stringResource(R.string.section_microphone), audio?.mic)
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
