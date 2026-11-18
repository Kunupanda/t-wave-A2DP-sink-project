package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bluetooth.ShellDiagnosticEntry
import com.example.bluetooth.ShellDiagnosticLogHandler
import com.example.ui.theme.LocalAcousticColors
import com.example.ui.theme.MonoLabelStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RootDiagnosticsDialog(
  entries: List<ShellDiagnosticEntry>,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
) {
  val acousticColors = LocalAcousticColors.current
  val clipboardManager = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }
  val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("root_diagnostics_dialog"),
    title = {
      Text(
        text = stringResource(R.string.diagnostics_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (entries.isEmpty()) {
          Text(
            text = stringResource(R.string.diagnostics_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          entries.forEach { entry ->
            val accent =
              if (entry.isSuccess) acousticColors.connectedBeacon
              else MaterialTheme.colorScheme.error
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              color = accent.copy(alpha = 0.08f),
              border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${entry.tag} • ${entry.exitExplanation}",
                    style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = "${timeFormat.format(Date(entry.timestampMs))} (${entry.durationMs}ms)",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (entry.lastStage.isNotBlank()) {
                  Text(
                    text = "Stage: ${entry.lastStage}",
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Text(
                  text =
                    "stderr:\n${entry.stderr.ifBlank { "<no stderr output>" }}",
                  style = MonoLabelStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entries.isNotEmpty()) {
          TextButton(
            onClick = {
              onClear()
              copied = false
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_diagnostics_button"),
          ) {
            Text(text = stringResource(R.string.diagnostics_clear))
          }
          TextButton(
            onClick = {
              clipboardManager.setText(
                AnnotatedString(ShellDiagnosticLogHandler.formatReport(entries))
              )
              copied = true
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_diagnostics_button"),
          ) {
            Text(
              text =
                if (copied) stringResource(R.string.diagnostics_copied)
                else stringResource(R.string.diagnostics_copy)
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
        Text(text = stringResource(R.string.guide_close))
      }
    },
  )
}
