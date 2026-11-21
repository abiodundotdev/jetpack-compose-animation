package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun Lesson06Gestures() {
    Body(
        "Touch-driven motion feels right when nothing ever jumps: the object follows the finger " +
            "exactly, and when the finger lets go the animation continues at the finger's speed.",
    )

    Heading("1 · Swipe to dismiss")
    Body("Drag the card. A slow drag springs back; a quick flick throws it away, even if it is short.")
    SwipeDeck()
    Code(
        """
        onDrag = { change, drag ->
            scope.launch { offset.snapTo(offset.value + drag) }
        }
        onDragEnd = {
            val v = tracker.calculateVelocity()
            val landing = decay.calculateTargetValue(offset.value.x, v.x)
            if (abs(landing) > threshold) flyOut(v) else
                offset.animateTo(Offset.Zero, spring(), initialVelocity = Offset(v.x, v.y))
        }
        """,
    )
    UnderTheHood(
        "While dragging we call snapTo for every pointer event. snapTo goes through the same " +
            "mutex as animateTo, so touching a card that is still springing back cancels the " +
            "spring on the spot. Catching things in mid-air costs no extra code.",
        "VelocityTracker stores the last pointer samples and fits a curve through them to " +
            "estimate px/second at release. Passing that as initialVelocity is what joins the " +
            "finger's motion to the animation without a visible seam.",
        "A decay spec has friction but no target. calculateTargetValue asks where a fling would " +
            "come to rest without running it. Pagers, sheets and swipe-to-dismiss all decide " +
            "this way, which is why a short fast flick still counts.",
        "Modifier order: pointerInput is placed before graphicsLayer here. The gesture is then " +
            "measured in the card's resting coordinates, not in coordinates that move with it.",
    )

    Heading("2 · Fling with friction")
    Body("Throw the puck. It slows by friction and bounces off the ends.")
    PuckDemo()
    Code(
        """
        var v = releaseVelocity
        while (abs(v) > 60f) {
            val result = x.animateDecay(v, exponentialDecay(frictionMultiplier = 0.5f))
            if (result.endReason == AnimationEndReason.BoundReached)
                v = -result.endState.velocity * 0.7f   // bounce
            else break
        }
        """,
    )
    UnderTheHood(
        "animateDecay takes only a starting velocity. Each frame the decay spec computes " +
            "position from v0 and friction; the animation ends when the speed drops below a " +
            "threshold.",
        "updateBounds makes the Animatable clamp its value. Hitting a bound ends the animation " +
            "early with AnimationEndReason.BoundReached and tells you the velocity it still had, " +
            "so you can reverse it and launch the next decay. The bounce is an ordinary while loop.",
        "Order matters when two things write to one Animatable. The drag's snapTo calls and the " +
            "fling are launched on the same coroutine scope, so they run in the order they were " +
            "requested. If the fling started first, the last snapTo would cancel it through the " +
            "mutex and the puck would simply stop.",
    )
}

private val deckColors = listOf(Ink.Accent, Ink.Pink, Ink.Mint, Ink.Amber)

@Composable
private fun SwipeDeck() {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val decay = rememberSplineBasedDecay<Float>()
    var top by remember { mutableIntStateOf(0) }

    Stage(height = 300.dp) {
        // The next card waits underneath and grows as the top one is dragged away.
        Card(
            index = top + 1,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val progress = (abs(offset.value.x) / (size.width * 0.8f)).coerceIn(0f, 1f)
                    val s = 0.9f + 0.1f * progress
                    scaleX = s
                    scaleY = s
                    alpha = 0.5f + 0.5f * progress
                },
        )
        Card(
            index = top,
            modifier = Modifier
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    var finger = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            finger = Offset.Zero
                        },
                        onDrag = { change, drag ->
                            finger += drag
                            tracker.addPosition(change.uptimeMillis, finger)
                            scope.launch { offset.snapTo(offset.value + drag) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            val landingX = decay.calculateTargetValue(offset.value.x, velocity.x)
                            scope.launch {
                                if (abs(landingX) > size.width * 0.75f) {
                                    val exit = Offset(
                                        x = sign(landingX) * size.width * 1.6f,
                                        y = offset.value.y + velocity.y * 0.15f,
                                    )
                                    offset.animateTo(exit, tween(220))
                                    top++
                                    offset.snapTo(Offset.Zero)
                                } else {
                                    offset.animateTo(
                                        targetValue = Offset.Zero,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                        initialVelocity = Offset(velocity.x, velocity.y),
                                    )
                                }
                            }
                        },
                    )
                }
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    rotationZ = offset.value.x / size.width * 16f
                },
        )
    }
}

@Composable
private fun Card(index: Int, modifier: Modifier) {
    Box(
        modifier
            .size(width = 190.dp, height = 240.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(deckColors[index % deckColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text("${index + 1}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PuckDemo() {
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val friction = remember { exponentialDecay<Float>(frictionMultiplier = 0.5f) }
    val puckPx = with(LocalDensity.current) { 48.dp.toPx() }

    Stage(height = 96.dp) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .onSizeChanged { x.updateBounds(0f, (it.width - puckPx).coerceAtLeast(0f)) },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x.value.roundToInt(), 0) }
                    .size(48.dp)
                    .background(Ink.Amber, CircleShape)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            scope.launch { x.snapTo(x.value + delta) }
                        },
                        orientation = Orientation.Horizontal,
                        onDragStopped = { releaseVelocity ->
                            // Launched on the same scope as the snapTo calls above, so it is
                            // queued behind the last of them. Started directly here, the decay
                            // would begin first and that final snapTo would then cancel it.
                            scope.launch {
                                var v = releaseVelocity
                                while (abs(v) > 60f) {
                                    val result = x.animateDecay(v, friction)
                                    if (result.endReason == AnimationEndReason.BoundReached) {
                                        v = -result.endState.velocity * 0.7f
                                    } else {
                                        break
                                    }
                                }
                            }
                        },
                    ),
            )
        }
    }
}
