package com.abiodun.composeanimation.course.lessons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.Stage
import com.abiodun.composeanimation.course.UnderTheHood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Lesson08GraphicsLayer() {
    Body(
        "Now we leave composables as the unit of animation. graphicsLayer transforms a whole " +
            "subtree on the GPU; Canvas lets you draw each frame yourself. Both live entirely in " +
            "the draw phase from lesson 1.",
    )

    Heading("1 · A 3D flip with graphicsLayer")
    FlipCard()
    Code(
        """
        val rotation by animateFloatAsState(if (flipped) 180f else 0f, spring(0.55f, 80f))
        val showBack by remember { derivedStateOf { rotation > 90f } }
        Box(Modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 14f * density
        }) { if (showBack) Back() else Front() }
        """,
    )
    UnderTheHood(
        "graphicsLayer gives the subtree its own layer (a RenderNode on Android). The drawing " +
            "commands of the content are recorded once. Changing rotation, scale, translation or " +
            "alpha only updates properties on that node, and the GPU re-composites the same " +
            "recording. Nothing is recomposed, measured or redrawn. It is the cheapest animation " +
            "there is.",
        "rotation is read in two places. Inside graphicsLayer { } it changes every frame and " +
            "costs almost nothing. For choosing front or back we need composition, but only at " +
            "the instant it crosses 90°. derivedStateOf turns a value that changes 60 times a " +
            "second into one that changes once, so composition runs once per flip.",
        "A layer has no real depth; cameraDistance sets how strong the perspective looks. The " +
            "back face is counter-rotated 180° so its text is not mirrored.",
    )

    Heading("2 · Drawing a path over time")
    Body("Tap to replay.")
    DrawOnDemo()
    Code(
        """
        measure.setPath(fullPath, forceClosed = false)
        measure.getSegment(0f, measure.length * progress, partial, true)
        drawPath(partial, color, style = Stroke(...))
        """,
    )
    UnderTheHood(
        "Canvas is a Spacer with a drawBehind modifier. Its lambda is the draw phase: any state " +
            "read inside re-runs just this lambda. progress is an Animatable read only here.",
        "PathMeasure treats a path as a line with a length. getSegment copies out the part from " +
            "0 to length × progress, which draws the stroke as if by a pen. Any 'draw-on' logo " +
            "animation is this.",
        "The Path objects are created once with remember and rewound each frame. Allocating in a " +
            "draw lambda means garbage 60 times a second, and the collector's pauses show up as " +
            "dropped frames.",
    )

    Heading("3 · Shapes from maths")
    BlobDemo()
    UnderTheHood(
        "There is no target and no spec here: the shape is a function of time, r(θ, t) = R · (1 + " +
            "waves). We are back to the lesson-1 loop, with one state (time) read in draw.",
        "This is the model for everything procedural: keep the simulation in plain variables, " +
            "advance it in withFrameNanos, and draw the result. The capstone does exactly this " +
            "with 6,000 particles.",
    )
}

@Composable
private fun FlipCard() {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 80f),
        label = "flip",
    )
    val showBack by remember { derivedStateOf { rotation > 90f } }

    Stage(Modifier.clickable { flipped = !flipped }, height = 240.dp) {
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }
                .size(width = 150.dp, height = 190.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (showBack) Brush.linearGradient(listOf(Ink.Pink, Ink.Amber))
                    else Brush.linearGradient(listOf(Ink.Accent, Ink.Mint)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (showBack) "BACK" else "FRONT",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { rotationY = if (showBack) 180f else 0f },
            )
        }
    }
}

@Composable
private fun DrawOnDemo() {
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(replay) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
    }
    val check = remember { Path() }
    val partial = remember { Path() }
    val measure = remember { PathMeasure() }

    Stage(Modifier.clickable { replay++ }, height = 190.dp) {
        Canvas(Modifier.align(Alignment.Center).size(120.dp)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val p = progress.value
            // First 60% of the time: the ring. Last 40%: the tick.
            val ring = (p / 0.6f).coerceIn(0f, 1f)
            val tick = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)

            drawArc(Ink.Mint, startAngle = -90f, sweepAngle = 360f * ring, useCenter = false, style = stroke)

            check.rewind()
            check.moveTo(size.width * 0.28f, size.height * 0.52f)
            check.lineTo(size.width * 0.45f, size.height * 0.68f)
            check.lineTo(size.width * 0.74f, size.height * 0.36f)
            measure.setPath(check, forceClosed = false)
            partial.rewind()
            measure.getSegment(0f, measure.length * tick, partial, startWithMoveTo = true)
            if (tick > 0f) drawPath(partial, Color.White, style = stroke)
        }
    }
}

@Composable
private fun BlobDemo() {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = (now - start) / 1e9f }
        }
    }
    val path = remember { Path() }
    val fill = remember { Brush.linearGradient(listOf(Ink.Accent, Ink.Pink, Ink.Amber)) }

    Stage(height = 230.dp) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.3f
            val points = 90
            path.rewind()
            for (i in 0..points) {
                val angle = i * 2f * PI.toFloat() / points
                val radius = base * (
                    1f +
                        0.13f * sin(3f * angle + time * 1.6f) +
                        0.08f * sin(5f * angle - time * 2.3f) +
                        0.05f * sin(2f * angle + time * 0.9f)
                    )
                val point = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, fill)
        }
    }
}
