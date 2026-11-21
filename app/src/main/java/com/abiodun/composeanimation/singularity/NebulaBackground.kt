package com.abiodun.composeanimation.singularity

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The background: drifting gas clouds, twinkling stars, the shockwave ring and the black hole,
 * all computed per pixel on the GPU (lesson 9), and all driven by the *same* numbers the
 * particle simulation uses. Sharing that state is what makes the two layers feel like one world.
 *
 * Decision: the clouds use "fbm" (several layers of smooth noise added together, each finer and
 * fainter), and the noise is looked up at a position that was itself pushed around by noise
 * ("domain warping"). That is the standard recipe for smoke, clouds and nebulae.
 */
private const val NEBULA = """
uniform float2 iResolution;
uniform float iTime;
uniform float2 iWell;
uniform float iWellStrength;
uniform float2 iShock;
uniform float iShockRadius;
uniform float iShockFade;
uniform float3 iColorA;
uniform float3 iColorB;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + float2(17.0, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

// The warp field only needs to be smooth, so it uses two layers instead of four.
float fbmCoarse(float2 p) {
    return 0.62 * noise(p) + 0.38 * noise(p * 2.03 + float2(17.0, 9.2));
}

half4 main(float2 fragCoord) {
    float m = min(iResolution.x, iResolution.y);
    float2 uv = (fragCoord - 0.5 * iResolution) / m;
    float2 well = (iWell - 0.5 * iResolution) / m;

    // Gravity bends space: twist and pinch the coordinates around the finger.
    float2 d = uv - well;
    float r = length(d);
    float twist = iWellStrength * 1.6 / (1.0 + r * 7.0);
    float cs = cos(twist);
    float sn = sin(twist);
    d = float2(d.x * cs - d.y * sn, d.x * sn + d.y * cs);
    d *= 1.0 + iWellStrength * 0.35 / (0.25 + r * 5.0);
    float2 p = well + d;

    float t = iTime * 0.05;
    float2 q = float2(fbmCoarse(p * 1.6 + float2(t, -t)), fbmCoarse(p * 1.6 + float2(5.2 - t, 1.3 + t)));
    float n = fbm(p * 2.2 + q * 1.8);

    float3 col = float3(0.008, 0.008, 0.02);
    col += iColorA * pow(n, 3.0) * 0.55;
    col += iColorB * pow(q.x, 3.0) * 0.35;

    // Stars: one possible star per grid cell, lit only where the cell's random number is high.
    float2 g = fragCoord / m * 90.0;
    float2 cell = floor(g);
    float h = hash(cell);
    float2 jitter = (float2(hash(cell + 3.1), hash(cell + 7.7)) - 0.5) * 0.7;
    float star = step(0.93, h) * smoothstep(0.09, 0.0, length(fract(g) - 0.5 - jitter));
    star *= 0.5 + 0.5 * sin(iTime * (1.0 + h * 3.0) + h * 40.0);
    col += star * 0.55;

    // The shockwave ring, at the radius the simulation reports.
    float fromRing = abs(length(fragCoord - iShock) - iShockRadius);
    float ring = smoothstep(0.09 * m, 0.0, fromRing) * iShockFade;
    col += mix(iColorA, float3(1.0), 0.5) * ring * 0.35;

    // The black hole: a glow that climbs towards the centre, and a dark core cut out of it.
    float glow = iWellStrength * 0.012 / (r * r + 0.01);
    col += mix(iColorB, float3(1.0), 0.4) * glow;
    col *= mix(1.0, smoothstep(0.0, 0.07, r), iWellStrength);

    col *= smoothstep(1.6, 0.2, length(uv));
    return half4(col, 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class NebulaBackground {
    private val shader = RuntimeShader(NEBULA)
    private val paint = Paint()

    fun draw(canvas: Canvas, system: ParticleSystem) {
        shader.setFloatUniform("iResolution", system.width, system.height)
        shader.setFloatUniform("iTime", system.time)
        shader.setFloatUniform("iWell", system.wellX, system.wellY)
        shader.setFloatUniform("iWellStrength", system.wellStrength)
        shader.setFloatUniform("iShock", system.shockX, system.shockY)
        shader.setFloatUniform("iShockRadius", system.shockRadiusPx)
        shader.setFloatUniform("iShockFade", system.shockFade)
        // Tint the gas with two of the swarm's current colours, so a morph recolours the sky too.
        val stops = system.stops
        shader.setFloatUniform("iColorA", stops[3], stops[4], stops[5])
        shader.setFloatUniform("iColorB", stops[6], stops[7], stops[8])
        paint.shader = shader
        canvas.drawPaint(paint)
    }
}
