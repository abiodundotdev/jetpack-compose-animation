package com.abiodun.composeanimation.course.lessons

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abiodun.composeanimation.course.Body
import com.abiodun.composeanimation.course.Code
import com.abiodun.composeanimation.course.Heading
import com.abiodun.composeanimation.course.Ink
import com.abiodun.composeanimation.course.UnderTheHood
import kotlinx.coroutines.launch

/**
 * AGSL. `main` runs once for every pixel of the layer, every frame, on the GPU.
 * `content` is the already-rendered composable; we choose where to sample it from.
 */
private const val RIPPLE = """
uniform shader content;
uniform float2 iOrigin;   // tap position in pixels
uniform float iTime;      // seconds since the tap
uniform float iDensity;

half4 main(float2 fragCoord) {
    float2 toPixel = fragCoord - iOrigin;
    float dist = length(toPixel);
    float2 dir = toPixel / max(dist, 0.001);

    // A wave front travelling outwards, with a gaussian envelope around it that dies over time.
    float front = iTime * 520.0 * iDensity;
    float x = (dist - front) / iDensity;
    float envelope = exp(-x * x / 3200.0) * exp(-iTime * 1.6);
    float wave = sin(x * 0.11) * envelope;

    // Distortion = reading the content from a displaced coordinate.
    float2 shift = dir * wave * 14.0 * iDensity;
    half4 color = content.eval(fragCoord + shift);
    // Chromatic aberration: red and blue are displaced by slightly different amounts.
    color.r = content.eval(fragCoord + shift * 1.6).r;
    color.b = content.eval(fragCoord + shift * 0.4).b;
    // A little light on the crest.
    color.rgb += half3(max(wave, 0.0) * 0.35) * color.a;
    return color;
}
"""

@Composable
fun Lesson09Shaders() {
    Body(
        "Everything so far animated a few numbers per frame. A shader animates every pixel. " +
            "Tap the card: an ordinary composable, bent by a small program running on the GPU.",
    )

    Heading("1 · A ripple through real UI")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RippleDemo()
    } else {
        Body("This demo needs Android 13 (API 33). On older versions provide a plain fallback.")
    }
    Code(
        """
        val shader = remember { RuntimeShader(RIPPLE) }
        Modifier.graphicsLayer {
            shader.setFloatUniform("iTime", time.value)
            shader.setFloatUniform("iOrigin", origin.x, origin.y)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
        """,
    )
    UnderTheHood(
        "AGSL is Android's shader language (Skia's SkSL). The string is compiled once into a GPU " +
            "program. main(fragCoord) is then executed for each pixel in parallel; there is no " +
            "loop over pixels anywhere in your code.",
        "As a RenderEffect on a graphicsLayer: the layer's content is first rendered to a " +
            "texture, and the shader receives it as 'uniform shader content'. content.eval(p) " +
            "reads that texture at p. Reading from somewhere other than your own position is all " +
            "that distortion, blur, glass and ripple effects are.",
        "Uniforms are the bridge from Kotlin: plain numbers you set per frame. The animation is " +
            "still lesson 3, an Animatable counting seconds; the shader turns that one float into " +
            "a million different pixel results.",
        "The time is read inside graphicsLayer { }, so again only the draw phase runs. The card " +
            "is never recomposed while it ripples.",
        "RuntimeShader needs API 33. Gate it with a version check and keep a fallback, as this " +
            "file does.",
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RippleDemo() {
    val scope = rememberCoroutineScope()
    val shader = remember { RuntimeShader(RIPPLE) }
    // Starts "long after" a tap, where the envelope has decayed to nothing.
    val time = remember { Animatable(10f) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    origin = position
                    scope.launch {
                        time.snapTo(0f)
                        time.animateTo(4f, tween(durationMillis = 4000, easing = LinearEasing))
                    }
                }
            }
            .graphicsLayer {
                shader.setFloatUniform("iTime", time.value)
                shader.setFloatUniform("iOrigin", origin.x, origin.y)
                shader.setFloatUniform("iDensity", density)
                // A new RenderEffect each frame is how the layer learns the uniforms changed.
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
                clip = true
                shape = RoundedCornerShape(24.dp)
            }
            .background(Brush.linearGradient(listOf(Color(0xFF2B1B6B), Color(0xFF0E3B4F)))),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Now playing", color = Ink.Mint, fontSize = 12.sp, letterSpacing = 2.sp)
                Text("Tap anywhere", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("this is normal Compose UI", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Ink.Pink, Ink.Amber, Ink.Mint, Ink.Accent).forEach {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(it))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
            )
        }
    }
}
