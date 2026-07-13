package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuStepperSlider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import java.io.File

@Composable
fun FontTab(
  prefs: ReaderPreferences,
  onFontChange: (ReaderFont) -> Unit,
  onFontScaleChange: (Float) -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ReaderFont.entries.forEach { font ->
        FontChip(font = font, selected = font == prefs.font, onClick = { onFontChange(font) })
      }
    }
    KanshuDivider()
    KanshuStepperSlider(
      value = prefs.fontScale,
      onValueChange = onFontScaleChange,
      valueRange = ReaderPreferences.SCALE_MIN..ReaderPreferences.SCALE_MAX,
      step = ReaderPreferences.SCALE_STEP,
      decreaseContentDescription = stringResource(R.string.reader_prefs_font_size_smaller),
      increaseContentDescription = stringResource(R.string.reader_prefs_font_size_larger),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
  }
}

@Composable
private fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val sampleFontFamily =
    remember(font, isPreview) {
      if (isPreview) {
        FontFamily.Default
      } else {
        val cached = File(context.cacheDir, "font-preview-${font.name}")
        if (!cached.exists()) {
          context.assets.open(font.regularAssetPath).use { input ->
            cached.outputStream().use { output -> input.copyTo(output) }
          }
        }
        FontFamily(Font(cached))
      }
    }
  Column(
    Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.height(40.dp).widthIn(min = 50.dp, max = 100.dp),
      contentAlignment = Alignment.Center,
    ) {
      KanshuText(
        text = stringResource(R.string.reader_prefs_font_sample),
        style =
          KanshuTheme.typography.headlineMedium.copy(
            fontFamily = sampleFontFamily,
            fontSize = if (font == ReaderFont.OpenDyslexic) 18.sp else 22.sp,
            lineHeight = 28.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
          ),
      )
    }
    KanshuText(
      text = font.displayName,
      style = KanshuTheme.typography.labelMedium.copy(fontSize = 14.sp),
    )
    Box(
      Modifier.fillMaxWidth()
        .padding(top = 6.dp)
        .height(3.dp)
        .background(if (selected) KanshuTheme.colors.onBackground else Color.Transparent)
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun FontTabPreview() {
  KanshuTheme {
    FontTab(
      prefs = ReaderPreferences(font = ReaderFont.Literata, fontScale = 1.2f),
      onFontChange = {},
      onFontScaleChange = {},
    )
  }
}
