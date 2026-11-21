package com.abiodun.composeanimation.course.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun Lesson01FrameClock() {
    Body(
        "Forget every animation API for a moment. An animation is a value that changes a " +
            "little before each frame is drawn. Here is one written entirely by hand.",
    )

    Heading("1 · An animation with no animation API")
    HandWrittenLoop()
    Code(
        """
        var seconds by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> seconds = (now - start) / 1e9f }
            }
        }
        Box(Modifier.graphicsLayer { translationX = cos(seconds * 2f) * 100.dp.toPx() })
        """,
    )
    UnderTheHood(
        "withFrameNanos suspends the coroutine until the next display frame and hands you that " +
            "frame's timestamp. On Android the wake-up comes from Choreographer (vsync).",
        "The position is computed from time, never from a frame count. That is why a 60 Hz and a " +
            "120 Hz screen show the same speed, and why a dropped frame causes a jump, not a slowdown.",
        "The clock is a MonotonicFrameClock stored in the coroutine context. animate*AsState, " +
            "Animatable, Transition and InfiniteTransition all end up in this same call. " +
            "Everything else in this course is convenience on top of this loop.",
    )

    Heading("2 · Where you read the state decides the cost")
    Body(
        "Compose produces a frame in three phases: Composition (run your functions), Layout " +
            "(measure and place), Draw. A state read is recorded in the phase where it happens, " +
            "and a change re-runs only that phase and the ones after it. The three dots below are " +
            "driven by the same value, read in three different places.",
    )
    PhaseDemo()
    UnderTheHood(
        "Each read registers the current scope as an observer of that state (the snapshot system). " +
            "Read it in a composable body and that composable is re-executed every frame.",
        "offset { } and graphicsLayer { } take lambdas that Compose calls later, during layout or " +
            "draw. The read happens there, so composition is skipped entirely: the counter stays at 1.",
        "The rule for the rest of the course: pass the State (or a lambda) down, and read the " +
            "value as late as possible. Draw-only is the cheapest, and it is what makes 6,000 " +
            "particles possible in the last lesson.",
    )
}

@Composable
private fun HandWrittenLoop() {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds = (now - start) / 1e9f }
        }
    }
    Stage(height = 150.dp) {
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = cos(seconds * 2f) * 100.dp.toPx()
                    translationY = sin(seconds * 4f) * 34.dp.toPx()
                }
                .size(26.dp)
                .background(Ink.Mint, CircleShape),
        )
    }
}

private val Travel = 220.dp

@Composable
private fun PhaseDemo() {
    // Note we keep the State object itself, not `by`: we want to hand the *holder* to children.
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                progress.floatValue = (sin((now - start) / 1e9f * 2f) + 1f) / 2f
            }
        }
    }

    // Plain array, deliberately not state: counting must not itself cause recomposition.
    val counters = remember { IntArray(3) }

    Stage(height = 262.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PhaseRow("Composition", "Modifier.offset(x = value.dp)", counters, 0) {
                CompositionReader(progress, counters)
            }
            PhaseRow("Layout", "Modifier.offset { IntOffset(value, 0) }", counters, 1) {
                LayoutReader(progress, counters)
            }
            PhaseRow("Draw", "Modifier.graphicsLayer { translationX = value }", counters, 2) {
                DrawReader(progress, counters)
            }
        }
    }
}

@Composable
private fun PhaseRow(
    title: String,
    code: String,
    counters: IntArray,
    index: Int,
    dot: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f))
        CounterLabel(counters, index)
    }
    Text(code, color = Ink.Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) { dot() }
}

/** Polls the plain counter four times a second so that showing it doesn't disturb the experiment. */
@Composable
private fun CounterLabel(counters: IntArray, index: Int) {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            shown = counters[index]
            delay(250)
        }
    }
    Text(
        "recompositions: $shown",
        color = if (shown > 5) Ink.Pink else Ink.Mint,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CompositionReader(progress: FloatState, counters: IntArray) {
    SideEffect { counters[0]++ } // runs after every (re)composition of this function
    val value = progress.floatValue // <- read in the composable body = composition phase
    Dot(Modifier.offset(x = Travel * value), Ink.Pink)
}

@Composable
private fun LayoutReader(progress: FloatState, counters: IntArray) {
    SideEffect { counters[1]++ }
    Dot(
        Modifier.offset { // <- this lambda runs during layout (placement)
            IntOffset((Travel.toPx() * progress.floatValue).roundToInt(), 0)
        },
        Ink.Amber,
    )
}

@Composable
private fun DrawReader(progress: FloatState, counters: IntArray) {
    SideEffect { counters[2]++ }
    Dot(
        Modifier.graphicsLayer { // <- this lambda runs when the layer is updated, at draw time
            translationX = Travel.toPx() * progress.floatValue
        },
        Ink.Mint,
    )
}

@Composable
private fun Dot(modifier: Modifier, color: Color) {
    Box(modifier.size(22.dp).background(color, CircleShape))
}
