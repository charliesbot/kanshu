package com.charliesbot.kanshu.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode

private val LocalKanshuColors =
  staticCompositionLocalOf<KanshuColors> { error("KanshuColors not provided") }

private val LocalKanshuTypography =
  staticCompositionLocalOf<KanshuTypography> { error("KanshuTypography not provided") }

private val LocalKanshuShapes =
  staticCompositionLocalOf<KanshuShapes> { error("KanshuShapes not provided") }

// Theme-aware content color local. KanshuTheme provides it with `colors.onBackground` as the
// default, which is what KanshuText and KanshuIcon read when nothing closer in the tree has
// overridden it. Buttons and other surface-flipping components override this via
// CompositionLocalProvider to swap icon/text color on press without piping a tint parameter
// through every call site. When a dark theme arrives, `colors.onBackground` flips and every
// descendant rerenders with the new color — no call-site changes.
//
// Errors when read outside KanshuTheme so misuse surfaces loudly, matching the strictness of
// the other LocalKanshu* locals in this file. compositionLocalOf (not static) because the value
// is overridden per-subtree by buttons on press, and we want only consumers to recompose.
val LocalKanshuContentColor =
  compositionLocalOf<Color> { error("LocalKanshuContentColor not provided") }

object KanshuTheme {
  val colors: KanshuColors
    @Composable @ReadOnlyComposable get() = LocalKanshuColors.current

  val typography: KanshuTypography
    @Composable @ReadOnlyComposable get() = LocalKanshuTypography.current

  val shapes: KanshuShapes
    @Composable @ReadOnlyComposable get() = LocalKanshuShapes.current
}

@Composable
fun KanshuTheme(content: @Composable () -> Unit) {
  val colors = LightKanshuColors
  CompositionLocalProvider(
    LocalKanshuColors provides colors,
    LocalKanshuTypography provides DefaultKanshuTypography,
    LocalKanshuShapes provides DefaultKanshuShapes,
    LocalKanshuContentColor provides colors.onBackground,
    LocalIndication provides NoIndication,
    content = content,
  )
}

private object NoIndication : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = -1
}

private class NoIndicationNode : Modifier.Node()
