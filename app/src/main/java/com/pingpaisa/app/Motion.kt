package com.pingpaisa.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val SoftEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
internal fun rememberEntrance(delay: Int = 0, duration: Int = 1000): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delay > 0) delay(delay.toLong())
        anim.animateTo(1f, tween(duration, easing = SoftEase))
    }
    return anim.asState()
}

internal fun Modifier.riseIn(
    delay: Int = 0,
    distance: Float = 18f,
    duration: Int = 500
): Modifier = composed {
    val progress = rememberEntrance(delay, duration)
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance * density
    }
}

internal object ClayPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return ClayPressNode(interactionSource)
    }

    override fun hashCode(): Int = 100
    override fun equals(other: Any?): Boolean = other === this
}

private class ClayPressNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    private val animatable = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        animatable.animateTo(
                            0.96f,
                            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        animatable.animateTo(
                            1f,
                            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val scale = animatable.value
        scale(scale, scale) {
            this@draw.drawContent()
        }
    }
}
