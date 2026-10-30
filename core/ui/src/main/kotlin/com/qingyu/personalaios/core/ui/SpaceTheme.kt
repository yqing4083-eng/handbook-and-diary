package com.qingyu.personalaios.core.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Color

data class SpaceColors(val foreground: Color = SpacePalette.Foreground)
val LocalSpaceColors = staticCompositionLocalOf { SpaceColors() }

@Composable
fun SpaceTheme(foreground: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpaceColors provides SpaceColors(foreground), LocalIndication provides ClearIndication, content = content)
}

/** Glass owns press feedback; Foundation's grey pressed overlay must never be added. */
private object ClearIndication : IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode = ClearNode()
    private class ClearNode : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
        override fun ContentDrawScope.draw() { drawContent() }
    }
    override fun equals(other:Any?)=other===this
    override fun hashCode()=1847
}
