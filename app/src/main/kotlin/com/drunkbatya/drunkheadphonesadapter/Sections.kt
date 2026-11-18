package com.drunkbatya.drunkheadphonesadapter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StatusColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
  )
}

@Composable
fun Section(title: String, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  val color = MaterialTheme.colorScheme.surfaceContainer
  val modifier = Modifier.fillMaxWidth()
  val body: @Composable () -> Unit = {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
      }
      content()
    }
  }
  if (onClick == null) {
    Surface(shape = shape, color = color, modifier = modifier, content = body)
  } else {
    Surface(onClick = onClick, shape = shape, color = color, modifier = modifier, content = body)
  }
}

@Composable
fun InfoRow(label: String, value: String?, valueColor: Color = Color.Unspecified) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(
      label,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      value?.takeUnless { it.isBlank() } ?: stringResource(R.string.value_unknown),
      color = valueColor,
      textAlign = TextAlign.End,
      style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
    )
  }
}

@Composable
fun MeterRow(label: String, value: String?, fraction: Float?) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    InfoRow(label, value)
    LinearProgressIndicator(
      progress = { fraction ?: 0f },
      trackColor = MaterialTheme.colorScheme.background,
      gapSize = 0.dp,
      drawStopIndicator = {},
      modifier = Modifier.fillMaxWidth().height(4.dp),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  BackHandler(onBack = onBack)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
          }
        },
      )
    },
  ) { padding ->
    StatusColumn(Modifier.padding(padding), content)
  }
}
