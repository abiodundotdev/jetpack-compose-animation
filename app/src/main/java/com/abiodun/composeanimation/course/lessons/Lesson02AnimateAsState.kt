package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Chips
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood

private val specs: List<Pair<String, AnimationSpec<Float>>> = listOf(
    "spring (default)" to spring<Float>(),
    "spring · bouncy" to spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessLow,
    ),
    "spring · heavy" to spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
    ),
    "tween · ease" to tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing),
    "tween · linear" to tween<Float>(durationMillis = 600, easing = LinearEasing),
    "keyframes" to keyframes<Float> {
        durationMillis = 900
        0f at 0
        1.25f at 300 // overshoot on purpose
        0.9f at 600
        1f at 900
    },
)

@Composable
fun Lesson02AnimateAsState() {
    Body(
        "In lesson 1 you moved a value by hand. animate*AsState does the loop for you: you " +
            "declare the target, it produces the in-between values. Tap the stage, then tap " +
            "again before it finishes.",
    )

    var specIndex by remember { mutableIntStateOf(0) }
    val spec = specs[specIndex].second
    var on by remember { mutableStateOf(false) }

    // One Float drives position, rotation and scale ...
    val p by animateFloatAsState(if (on) 1f else 0f, animationSpec = spec, label = "p")

    // ... while Color and Dp are animated by the very same engine through a converter.
    val color by animateColorAsState(if (on) Ink.Pink else Ink.Mint, label = "color")

    val corner by animateDpAsState(if (on) 28.dp else 10.dp, label = "corner")

    Heading("1 · Declare the target, get the motion")
    Stage(Modifier.clickable { on = !on }, height = 170.dp) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
                .graphicsLayer {
                    translationX = p * 190.dp.toPx()
                    rotationZ = p * 180f
                    val s = 1f + p * 0.35f
                    scaleX = s
                    scaleY = s
                }
                .size(56.dp)
                .clip(RoundedCornerShape(corner))
                .background(color),
        )
    }
    Chips(specs.mapIndexed { i, (label, _) -> label to i }, specIndex) { specIndex = it }
    Code(
        """
        val p by animateFloatAsState(
            targetValue = if (on) 1f else 0f,
            animationSpec = spring(),
        )
        """,
    )
    UnderTheHood(
        "animateFloatAsState is small: it remembers an Animatable (lesson 3), and each time " +
            "targetValue changes it sends the new target through a Channel to a LaunchedEffect " +
            "that calls animatable.animateTo(target, spec). It returns that Animatable as a State.",
        "A new target mid-flight does not restart from zero. The new run begins from the current " +
            "value and the current velocity. That is why the default spec is a spring: a spring " +
            "can absorb any starting velocity, a tween cannot (try 'tween · linear' and tap fast).",
        "The engine only animates AnimationVectors: 1 to 4 floats. A TwoWayConverter turns Dp, " +
            "Color, Offset, Rect and so on into a vector and back. Color is converted through the " +
            "Oklab colour space so that the in-between colours look even.",
    )

    Heading("2 · A spec is just a function of time")
    Body(
        "This plot is not a recording. It asks the selected spec directly: 'going from 0 to 1, " +
            "what is the value at time t?' No animation runs to draw it.",
    )
    SpecPlot(spec)
    Code(
        """
        val v = spec.vectorize(Float.VectorConverter)
        v.getDurationNanos(start, end, startVelocity)
        v.getValueFromNanos(playTime, start, end, startVelocity)
        """,
    )
    UnderTheHood(
        "AnimationSpec → VectorizedAnimationSpec, whose core is two pure functions: " +
            "getValueFromNanos and getVelocityFromNanos. The frame loop from lesson 1 simply " +
            "calls them with (now - startTime) on every frame.",
        "tween and keyframes have a duration you choose. A spring's duration is computed: it " +
            "solves the damped-oscillator equation and reports the time at which the motion " +
            "falls below a visibility threshold. Look at how the duration changes between springs.",
        "Being pure functions is what makes animations seekable, testable and previewable: " +
            "give a time, get a value.",
    )
}

@Composable
private fun SpecPlot(spec: AnimationSpec<Float>) {
    val vectorized = remember(spec) { spec.vectorize(Float.VectorConverter) }
    val zero = remember { AnimationVector1D(0f) }
    val one = remember { AnimationVector1D(1f) }
    val durationNanos = remember(vectorized) { vectorized.getDurationNanos(zero, one, zero) }
    val path = remember { Path() }

    Stage(height = 190.dp) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Value range shown: -0.2 .. 1.5 so overshoot is visible.
            fun y(value: Float) = size.height * (1f - (value + 0.2f) / 1.7f)
            drawLine(Ink.SurfaceHi, Offset(0f, y(0f)), Offset(size.width, y(0f)), 2f)
            drawLine(Ink.Accent.copy(alpha = 0.6f), Offset(0f, y(1f)), Offset(size.width, y(1f)), 2f)

            val samples = 160
            path.rewind()
            for (i in 0..samples) {
                val t = durationNanos * i / samples
                val value = vectorized.getValueFromNanos(t, zero, one, zero).value
                val x = size.width * i / samples
                if (i == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
            }
            drawPath(path, Ink.Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(
            "duration: ${durationNanos / 1_000_000} ms",
            color = Ink.Dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}
