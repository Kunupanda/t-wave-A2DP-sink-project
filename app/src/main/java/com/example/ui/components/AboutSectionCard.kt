package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalAcousticColors
import com.example.ui.theme.MonoLabelStyle

@Composable
fun AboutDeveloperCard(modifier: Modifier = Modifier) {
  val acousticColors = LocalAcousticColors.current
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  Card(
    modifier = modifier.fillMaxWidth().testTag("about_section_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, acousticColors.surfaceBorder),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_launcher_artwork),
          contentDescription = stringResource(R.string.app_name),
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Code,
              contentDescription = null,
              tint = acousticColors.vuSpectrumCyan,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = stringResource(R.string.about_section_title).uppercase(),
              style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = stringResource(R.string.about_app_tagline),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = acousticColors.surfaceWell,
        border = BorderStroke(1.dp, acousticColors.surfaceBorder),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.about_developer_credit),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp).testTag("about_developer_credit"),
          )
          AssistChip(
            onClick = {
              clipboardManager.setText(AnnotatedString("@xenon3x"))
              try {
                val intent =
                  Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/xenon3x")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                context.startActivity(intent)
              } catch (_: ActivityNotFoundException) {
              }
            },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("about_telegram_chip"),
            leadingIcon = {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
            },
            label = {
              Text(
                text = stringResource(R.string.about_telegram_action),
                style = MonoLabelStyle,
              )
            },
            colors =
              AssistChipDefaults.assistChipColors(
                containerColor = acousticColors.vuSpectrumCyan.copy(alpha = 0.14f),
                labelColor = acousticColors.vuSpectrumCyan,
                leadingIconContentColor = acousticColors.vuSpectrumCyan,
              ),
          )
        }
      }
    }
  }
}
