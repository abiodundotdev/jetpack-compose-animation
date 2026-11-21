package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** An Easing is only a function from fraction to fraction. This one overshoots and rings. */
private val ElasticOut = Easing { t ->
    if (t == 0f || t == 1f) t else 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (2f * PI.toFloat() / 3f)) + 1f
}

/** Four control-point numbers describe a curve; these pull back before going and overshoot after. */
private val BackInOut = CubicBezierEasing(0.68f, -0.55f, 0.27f, 1.55f)

@Composable
fun Lesson05Infinite() {
    Body(
        "Loaders, pulses and ambient motion never reach a target. They repeat. " +
            "rememberInfiniteTransition is a Transition with no end state.",
    )

    Heading("1 · Repeat, reverse, offset")
    Stage(height = 150.dp) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncingDots()
            Spinner()
            Heartbeat()
        }
    }
    Code(
        """
        val t = rememberInfiniteTransition(label = "dots")
        val y by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 140),
            ),
        )
        """,
    )
    UnderTheHood(
        "One InfiniteTransition runs one frame loop and updates all of its children in it. " +
            "The loop exists only while the composable is in the composition; leave the screen " +
            "and it stops by itself.",
        "infiniteRepeatable wraps a spec that has a duration and maps play time onto it with a " +
            "modulo (mirrored for RepeatMode.Reverse). That is why it accepts tween and keyframes " +
            "but not spring: a spring has no fixed duration to repeat.",
        "StartOffset shifts each child's phase, which is how three dots become a wave with no " +
            "delay() calls. keyframes sets exact values at exact times: the heartbeat is two " +
            "quick beats and then a rest.",
        "An infinite animation read in composition recomposes forever. Every value on this page " +
            "is read inside graphicsLayer { } or a Canvas draw lambda, so only drawing repeats.",
    )

    Heading("2 · Easing is a function you can write")
    EasingDemo()
    Code(
        """
        val ElasticOut = Easing { t ->
            2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (2f * PI / 3f)) + 1f
        }
        tween(durationMillis = 1200, easing = ElasticOut)
        """,
    )
    UnderTheHood(
        "A tween computes fraction = playTime / duration, passes it through easing.transform(), " +
            "and interpolates start → end with the result. Easing never sees your values, only " +
            "0..1.",
        "Returning less than 0 or more than 1 is allowed, and is how anticipation and overshoot " +
            "are made. CubicBezierEasing is the same thing with the function defined by a curve.",
    )
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { index ->
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 140),
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .graphicsLayer {
                        translationY = -lift * 22.dp.toPx()
                        alpha = 0.45f + lift * 0.55f
                    }
                    .size(14.dp)
                    .background(Ink.Mint, CircleShape),
            )
        }
    }
}

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                30f at 0
                280f at 700
                30f at 1400
            },
        ),
        label = "sweep",
    )
    Canvas(Modifier.size(52.dp)) {
        drawArc(
            color = Ink.Accent,
            startAngle = rotation,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun Heartbeat() {
    val transition = rememberInfiniteTransition(label = "heart")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 0
                1.3f at 110
                1f at 230
                1.18f at 340
                1f at 500
            },
        ),
        label = "scale",
    )
    Text(
        "♥",
        color = Ink.Pink,
        fontSize = 46.sp,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun EasingDemo() {
    val transition = rememberInfiniteTransition(label = "easing")
    val rows = listOf(
        Triple("LinearEasing", LinearEasing, Ink.Dim),
        Triple("CubicBezier (back)", BackInOut, Ink.Amber),
        Triple("custom ElasticOut", ElasticOut, Ink.Mint),
    )
    Stage(height = 190.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEach { (name, easing, color) ->
                val p by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 1300, delayMillis = 400, easing = easing),
                        RepeatMode.Reverse,
                    ),
                    label = name,
                )
                Column {
                    Text(name, color = Ink.Dim, fontSize = 11.sp)
                    Box(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 30.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .graphicsLayer { translationX = p * 190.dp.toPx() }
                                .size(22.dp)
                                .background(color, RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
        }
    }
}
