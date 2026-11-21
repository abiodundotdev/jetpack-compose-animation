package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Chips
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.PillButton
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Lesson03Animatable() {
    Body(
        "Animatable is the engine underneath animate*AsState. You get it directly when you need " +
            "control: start, stop, snap, sequence, or hand over a velocity. Its API is suspend " +
            "functions, so you drive it from a coroutine.",
    )

    Heading("1 · Interrupting an animation")
    Body(
        "Tap anywhere. Then tap somewhere else while the ball is still moving. The thin line is " +
            "the ball's current velocity, which Animatable tracks for you.",
    )
    ChaseDemo()
    Code(
        """
        val ball = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        scope.launch { ball.animateTo(tapPosition, spring()) }
        // ball.value, ball.velocity, ball.isRunning
        """,
    )
    UnderTheHood(
        "Animatable is a value holder (a State), plus a velocity, plus suspend functions. " +
            "animateTo runs the lesson-1 loop: each frame it asks the spec for value and " +
            "velocity at the current play time and writes them into the state.",
        "A MutatorMutex inside allows one animation at a time. A second animateTo cancels the " +
            "first (its coroutine gets a CancellationException) and starts from the same value " +
            "and velocity. With a spring the ball curves smoothly; with a tween it turns sharply, " +
            "because a tween's shape ignores incoming velocity.",
        "animateTo returns only when the animation ends, so code after it runs after the motion. " +
            "That one fact replaces listeners and end-callbacks.",
    )

    Heading("2 · Coroutines are the timeline")
    Body("Five Animatables. The only difference between the three buttons is how they are launched.")
    ChoreographyDemo()
    Code(
        """
        // one after another
        dots.forEach { it.animateTo(1f) }
        // all together
        dots.forEach { launch { it.animateTo(1f) } }
        // staggered
        dots.forEachIndexed { i, d -> launch { delay(i * 70L); d.animateTo(1f) } }
        """,
    )
    UnderTheHood(
        "Sequential = suspend calls written in order. Parallel = one launch per animation. " +
            "Stagger = a delay before each. There is no AnimatorSet in Compose because structured " +
            "concurrency already expresses all of it.",
        "Cancel the parent coroutine and every child animation stops, each one frozen at its " +
            "current value, ready to be continued from there.",
        "Other members worth knowing: snapTo(value) jumps without animating (used while dragging, " +
            "lesson 6), stop() freezes, updateBounds() clamps, animateDecay() runs a fling.",
    )
}

@Composable
private fun ChaseDemo() {
    val specs: List<Pair<String, AnimationSpec<Offset>>> = remember {
        listOf(
            "spring" to spring<Offset>(dampingRatio = 0.55f, stiffness = 60f),
            "tween" to tween<Offset>(durationMillis = 900, easing = LinearEasing),
        )
    }
    var specIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ball = remember { Animatable(Offset(160f, 160f), Offset.VectorConverter) }
    var target by remember { mutableStateOf(Offset(160f, 160f)) }

    Stage(
        Modifier.pointerInput(specIndex) {
            detectTapGestures { position ->
                target = position
                scope.launch { ball.animateTo(position, specs[specIndex].second) }
            }
        },
        height = 280.dp,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // ball.value / ball.velocity are read here, in the draw phase only.
            val position = ball.value
            drawCircle(Ink.Dim, 10.dp.toPx(), target, style = Stroke(2.dp.toPx()))
            drawLine(
                Ink.Mint,
                position,
                position + ball.velocity * 0.12f,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(Ink.Pink, 14.dp.toPx(), position)
        }
    }
    Chips(specs.mapIndexed { i, (label, _) -> label to i }, specIndex) { specIndex = it }
}

@Composable
private fun ChoreographyDemo() {
    val scope = rememberCoroutineScope()
    val dots = remember { List(5) { Animatable(0f) } }
    val bounce = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    }

    fun play(mode: Int) = scope.launch {
        dots.forEach { it.snapTo(0f) }
        when (mode) {
            0 -> dots.forEach { it.animateTo(1f, tween(220)) }
            1 -> dots.forEach { launch { it.animateTo(1f, bounce) } }
            else -> dots.forEachIndexed { i, dot ->
                launch {
                    delay(i * 70L)
                    dot.animateTo(1f, bounce)
                }
            }
        }
    }

    Stage(height = 150.dp) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            dots.forEachIndexed { i, dot ->
                Box(
                    Modifier
                        .graphicsLayer { translationY = -dot.value * 64.dp.toPx() }
                        .size(24.dp)
                        .background(if (i % 2 == 0) Ink.Mint else Ink.Accent, CircleShape),
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton("Sequential") { play(0) }
        PillButton("Parallel") { play(1) }
        PillButton("Staggered") { play(2) }
    }
}
