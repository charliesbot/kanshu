package com.charliesbot.kanshu.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

private val LocalKanshuColors =
  staticCompositionLocalOf<KanshuColors> { error("KanshuColors not provided") }

private val LocalKanshuTypography =
  staticCompositionLocalOf<KanshuTypography> { error("KanshuTypography not provided") }

private val LocalKanshuShapes =
  staticCompositionLocalOf<KanshuShapes> { error("KanshuShapes not provided") }

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
  CompositionLocalProvider(
    LocalKanshuColors provides LightKanshuColors,
    LocalKanshuTypography provides DefaultKanshuTypography,
    LocalKanshuShapes provides DefaultKanshuShapes,
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
