package com.mab.f2mbox.desktop.ui.common

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/** Clickable hover/press overlay disabled; focus styling is custom elsewhere. */
object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DelegatableNode {}

    override fun hashCode(): Int = -893_274_821
    override fun equals(other: Any?): Boolean = other === this
}