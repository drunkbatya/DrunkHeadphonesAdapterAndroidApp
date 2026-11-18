package com.drunkbatya.drunkheadphonesadapter

import adapter.VersionResponse
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun DeviceInfoScreen(firmware: VersionResponse?, onBack: () -> Unit) {
  DetailScreen(stringResource(R.string.device_info), onBack) {
    Section(stringResource(R.string.firmware)) {
      InfoRow(stringResource(R.string.firmware_version), firmware?.version)
      InfoRow(stringResource(R.string.build_date), firmware?.build_date)
      InfoRow(stringResource(R.string.git_commit), firmware?.git_commit)
      InfoRow(stringResource(R.string.git_branch), firmware?.git_branch)
      InfoRow(
        stringResource(R.string.dirty_build),
        firmware?.let { stringResource(if (it.dirty) R.string.yes else R.string.no) },
      )
      InfoRow(stringResource(R.string.target), firmware?.target?.toString())
    }
  }
}
