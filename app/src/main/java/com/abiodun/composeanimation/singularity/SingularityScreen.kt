package com.abiodun.composeanimation.singularity

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The one texture every particle shares: a white dot with a bright core and a faint wide halo.
 * Each quad shows this picture, multiplied by that particle's own vertex colour.
 */
private fun glowSprite(): Bitmap {
    val size = ParticleSystem.SPRITE.toInt()
    val pixels = IntArray(size * size)
    val half = size / 2f
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = (x + 0.5f - half) / half
            val dy = (y + 0.5f - half) / half
            val d2 = dx * dx + dy * dy
            val core = exp(-d2 * 14f)
            val halo = exp(-d2 * 3.2f) * 0.22f
            // Fade to exactly zero at the rim so the square edge of the quad never shows.
            val rim = (1f - sqrt(d2)).coerceIn(0f, 1f)
            val alpha = ((core + halo) * rim).coerceIn(0f, 1f)
            pixels[y * size + x] = AndroidColor.argb((alpha * 255f).toInt(), 255, 255, 255)
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun SingularityScreen(modifier: Modifier = Modifier) {
    // The whole world lives in one plain object that survives recomposition.
    val system = remember { ParticleSystem() }

    // The only Compose state the simulation touches. It is written once per frame and read only
    // inside the Canvas draw lambda, so a frame costs: 0 recompositions, 0 layouts, 1 draw.
    var frame by remember { mutableLongStateOf(0L) }

    var index by rememberSaveable { mutableIntStateOf(0) }

    val spriteRadius = with(LocalDensity.current) { 4.6.dp.toPx() }

    // Lesson 1's loop, unchanged: wait for a frame, advance the world by the elapsed time.
    LaunchedEffect(system, spriteRadius) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                system.step(dt, spriteRadius)
                frame = now
            }
        }
    }

    // Morph when `index` changes, then auto-advance after a pause. Because the effect is keyed on
    // `index`, a tap (which changes it) cancels this coroutine and restarts the countdown.
    LaunchedEffect(index) {
        system.morphTo(index)
        delay(9000)
        index = (index + 1) % system.formations.size
    }

    val nebula = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) NebulaBackground() else null
    }
    val paint = remember {
        Paint().apply {
            // The sprite is the paint's shader; drawVertices multiplies it by the vertex colours.
            shader = BitmapShader(glowSprite(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            isFilterBitmap = true
            // Additive blending: overlapping particles add their light instead of covering each
            // other, so dense regions glow towards white the way real light sources do. It also
            // makes drawing order irrelevant, so the particles never need sorting by depth.
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }
    // Hardware-accelerated drawVertices exists from Android 10. Older versions get plain dots.
    val fallbackPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { system.resize(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(system) {
                    // Raw pointer handling (not detectTapGestures) because one finger does two
                    // jobs here and we need to react from the very first touch-down.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        system.holdWell(down.position.x, down.position.y)
                        var last = down.position
                        var travelled = 0f
                        var lastTime = down.uptimeMillis
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastTime = change.uptimeMillis
                                if (!change.pressed) break
                                travelled += (change.position - last).getDistance()
                                last = change.position
                                system.moveWell(last.x, last.y)
                                change.consume()
                            }
                        } finally {
                            system.releaseWell()
                        }
                        val isTap = lastTime - down.uptimeMillis < 250 &&
                            travelled < viewConfiguration.touchSlop
                        if (isTap) {
                            system.shock(last.x, last.y)
                            index = (index + 1) % system.formations.size
                        }
                    }
                },
        ) {
            frame // reading the tick here is what schedules this lambda again next frame

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                drawRect(Brush.radialGradient(listOf(Color(0xFF14102E), Color.Black)))
            }
            drawIntoCanvas { canvas ->
                // Decision: drop to the platform Canvas. Compose's own drawPoints/drawVertices
                // take List<Offset> / Vertices objects, i.e. thousands of allocations per frame.
                // The platform call takes the primitive arrays the simulation already filled.
                val native = canvas.nativeCanvas
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && nebula != null) {
                    nebula.draw(native, system)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 6,000 particles, one draw call.
                    native.drawVertices(
                        AndroidCanvas.VertexMode.TRIANGLES,
                        system.vertices.size, system.vertices, 0,
                        system.texCoords, 0,
                        system.vertexColors, 0,
                        system.indices, 0, system.indices.size,
                        paint,
                    )
                } else {
                    fallbackPaint.strokeWidth = 2.dp.toPx()
                    fallbackPaint.setARGB(
                        200,
                        (system.stops[3] * 255f).toInt(),
                        (system.stops[4] * 255f).toInt(),
                        (system.stops[5] * 255f).toInt(),
                    )
                    native.drawPoints(system.points, fallbackPaint)
                }
            }
        }

        // The label is ordinary UI, so it uses the ordinary high-level API (lesson 4).
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(
                targetState = system.formations[index].name,
                transitionSpec = {
                    (slideInVertically(tween(500)) { it } + fadeIn(tween(500))) togetherWith
                        (slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)))
                },
                label = "formation",
            ) { name ->
                Text(
                    name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 12.sp,
                )
            }
            Text(
                "TAP  morph   ·   HOLD  collapse   ·   RELEASE  supernova",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
        }

        HowItWorks(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp))
    }
}

@Composable
private fun HowItWorks(modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            if (open) "close" else "how it works",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        AnimatedVisibility(open) {
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "L1  One withFrameNanos loop steps the world. One state is read, in draw only.",
                    "L2  Each of 6,000 particles is a hand-written damped spring.",
                    "L3  A morph only swaps targets, so it can be interrupted at any time.",
                    "L6  Raw pointer input: hold is a gravity well, a quick tap is a shockwave.",
                    "L8  FloatArrays in, one drawVertices call out. Nothing is allocated per frame.",
                    "L9  An AGSL shader paints the nebula from the same touch and shock values.",
                    "L4  This panel and the label are plain AnimatedVisibility / AnimatedContent.",
                ).forEach {
                    Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}
