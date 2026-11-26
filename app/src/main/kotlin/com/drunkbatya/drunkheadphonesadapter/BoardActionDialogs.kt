package com.drunkbatya.drunkheadphonesadapter

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

@get:StringRes
private val BoardAction.confirmTitle: Int
  get() = when (this) {
    BoardAction.UsbBypass -> R.string.usb_bypass_confirm_title
    BoardAction.Reboot -> R.string.reboot_confirm_title
    BoardAction.RebootToBootloader -> R.string.reboot_dfu_confirm_title
  }

@get:StringRes
private val BoardAction.confirmText: Int
  get() = when (this) {
    BoardAction.UsbBypass -> R.string.usb_bypass_confirm_text
    BoardAction.Reboot -> R.string.reboot_confirm_text
    BoardAction.RebootToBootloader -> R.string.reboot_dfu_confirm_text
  }

@get:StringRes
private val BoardAction.waitingText: Int
  get() = when (this) {
    BoardAction.UsbBypass -> R.string.usb_bypass_waiting
    BoardAction.Reboot -> R.string.update_rebooting
    BoardAction.RebootToBootloader -> R.string.reboot_dfu_waiting
  }

@get:StringRes
private val BoardAction.failedTitle: Int
  get() = when (this) {
    BoardAction.UsbBypass -> R.string.usb_bypass_failed
    BoardAction.Reboot, BoardAction.RebootToBootloader -> R.string.reboot_failed
  }

@Composable
fun BoardActionDialogs(
  boardAction: BoardActionState?,
  onConfirm: (BoardAction) -> Unit,
  onDismiss: () -> Unit,
) {
  when (boardAction) {
    null -> Unit
    is BoardActionState.Confirm -> AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(boardAction.action.confirmTitle)) },
      text = { Text(stringResource(boardAction.action.confirmText)) },
      confirmButton = {
        TextButton(onClick = { onConfirm(boardAction.action) }) { Text(stringResource(R.string.yes)) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } },
    )
    is BoardActionState.Running -> ProgressDialog(stringResource(R.string.board_action_hint)) {
      CircularProgressIndicator()
      Text(stringResource(boardAction.action.waitingText), textAlign = TextAlign.Center)
    }
    is BoardActionState.Failed -> MessageDialog(
      stringResource(boardAction.action.failedTitle),
      boardAction.reason,
      onDismiss,
    )
  }
}
