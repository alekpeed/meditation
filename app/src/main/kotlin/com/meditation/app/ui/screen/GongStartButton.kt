package com.meditation.app.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.meditation.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Big tappable gong that acts as the start control for a quick session. Pressing dips the disc;
 * releasing "strikes" it — fires [onStrike] (the sound) together with a bounce, a swelling glow, and
 * expanding ripple rings that decay over the ring-out, then calls [onStart] a beat later to begin
 * the session. The artwork is a drawable ([R.drawable.gong_placeholder]) so it can be swapped for
 * final art without touching this motion code.
 */
@Composable
fun GongStartButton(
    label: String,
    onStrike: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val strike = remember { Animatable(0f) } // 0 at rest, animates 0->1 on each hit
    val gold = Color(0xFFE6B65C)

    val idle = rememberInfiniteTransition(label = "gongIdle")
    val breath by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )

    fun hit() {
        onStrike()
        scope.launch {
            scale.snapTo(0.88f)
            scale.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessLow))
        }
        scope.launch {
            strike.snapTo(0f)
            strike.animateTo(1f, tween(2400, easing = LinearOutSlowInEasing))
        }
        scope.launch {
            delay(700)
            onStart()
        }
    }

    Box(
        modifier = modifier
            .size(260.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scope.launch { scale.animateTo(0.93f, tween(90)) }
                    val released = tryAwaitRelease()
                    if (released) hit() else scope.launch { scale.animateTo(1f, tween(140)) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = size.minDimension / 2f
            val p = strike.value

            // Always-on halo that gently breathes, inviting a tap.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(gold.copy(alpha = 0.05f + 0.05f * breath), Color.Transparent),
                    center = c,
                    radius = maxR,
                ),
                radius = maxR,
                center = c,
            )

            if (p > 0f) {
                // Glow swells then fades across the strike.
                val swell = sin(p * PI).toFloat().coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(gold.copy(alpha = 0.45f * swell), Color.Transparent),
                        center = c,
                        radius = maxR,
                    ),
                    radius = maxR,
                    center = c,
                )
                // Three staggered ripple rings expanding outward and fading.
                for (i in 0..2) {
                    val rp = (p - i * 0.14f).coerceIn(0f, 1f)
                    if (rp > 0f && rp < 1f) {
                        drawCircle(
                            color = gold.copy(alpha = (1f - rp) * 0.5f),
                            radius = maxR * (0.62f + rp * 0.42f),
                            center = c,
                            style = Stroke(width = (1f - rp) * 12.dp.toPx() + 1.5f),
                        )
                    }
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.gong_placeholder),
            contentDescription = label,
            modifier = Modifier
                .fillMaxSize(0.78f)
                .graphicsLayer {
                    val s = scale.value * (1f + 0.012f * breath)
                    scaleX = s
                    scaleY = s
                },
        )

        Text(
            text = label,
            color = Color(0xFF3A2A16),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}
