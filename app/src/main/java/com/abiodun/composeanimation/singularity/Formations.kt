package com.abiodun.composeanimation.singularity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A shape the swarm can take: one (x, y, z) target per particle, in "units" where the shape
 * roughly fills -1..1. y points down, like the screen, so no flipping is needed later.
 *
 * Decision: every generator places particle i according to i / count (along the arm, along the
 * curve, left to right in the text). Colour is also chosen by i, so each shape gets a gradient
 * that follows its structure instead of random speckle.
 */
internal class Formation(
    val name: String,
    /** Three ARGB stops; the swarm's colour runs through them from particle 0 to the last. */
    val palette: IntArray,
    /** Solid 3D shapes spin all the way round; flat ones (heart, text) only sway so they stay readable. */
    val spins: Boolean,
    /** Camera tilt in radians that shows this shape best. */
    val pitch: Float,
    val points: FloatArray,
)

private const val TAU = (2.0 * PI).toFloat()

internal fun buildFormations(count: Int): List<Formation> {
    val random = Random(7) // fixed seed: the shapes look the same on every launch
    return listOf(
        Formation("GALAXY", palette(0xFFE9B0, 0xFF4FA3, 0x4F7BFF), true, 1.0f, galaxy(count, random)),
        Formation("ORBIT", palette(0x3CFFD0, 0x4F9BFF, 0xB06BFF), true, 0.38f, planet(count, random)),
        Formation("KNOT", palette(0xFFB23C, 0xFF3C6E, 0x8A3CFF), true, 0.35f, knot(count, random)),
        Formation("HELIX", palette(0x3CFF8A, 0x3CD5FF, 0x5C6BFF), true, 0.12f, helix(count, random)),
        Formation("HEART", palette(0xFF2E63, 0xFF5E8A, 0xFFB199), false, 0f, heart(count, random)),
        Formation("COMPOSE", palette(0x3DDC84, 0x4285F4, 0xD8E6FF), false, 0f, text(count, random, "COMPOSE")),
    )
}

private fun palette(a: Int, b: Int, c: Int) = intArrayOf(a, b, c)

/** Normally distributed random number (Box–Muller). Gives soft, natural-looking scatter. */
private fun Random.gauss(): Float {
    val u = 1f - nextFloat()
    val v = nextFloat()
    return sqrt(-2f * ln(u)) * cos(TAU * v)
}

private inline fun fill(count: Int, point: (i: Int, out: FloatArray) -> Unit): FloatArray {
    val result = FloatArray(count * 3)
    val tmp = FloatArray(3)
    for (i in 0 until count) {
        point(i, tmp)
        result[i * 3] = tmp[0]
        result[i * 3 + 1] = tmp[1]
        result[i * 3 + 2] = tmp[2]
    }
    return result
}

/** A bright core plus three logarithmic spiral arms lying in the x–z plane. */
private fun galaxy(count: Int, random: Random) = fill(count) { i, out ->
    val t = i / count.toFloat()
    if (t < 0.14f) {
        out[0] = random.gauss() * 0.12f
        out[1] = random.gauss() * 0.08f
        out[2] = random.gauss() * 0.12f
    } else {
        val u = (t - 0.14f) / 0.86f
        val radius = 0.12f + 0.95f * u.pow(0.8f)
        val arm = (i % 3) * TAU / 3f
        // The angle grows with the radius: that is what winds the arms into a spiral.
        val angle = arm + radius * 5.2f + random.gauss() * 0.26f * (1.15f - radius)
        out[0] = cos(angle) * radius
        out[1] = random.gauss() * 0.03f
        out[2] = sin(angle) * radius
    }
}

/** A Fibonacci sphere (the most even way to spread points on a ball) with a tilted ring. */
private fun planet(count: Int, random: Random): FloatArray {
    val onSphere = (count * 0.6f).toInt()
    val tilt = 0.35f
    return fill(count) { i, out ->
        if (i < onSphere) {
            val y = 1f - 2f * (i + 0.5f) / onSphere
            val ringRadius = sqrt(1f - y * y)
            val angle = i * 2.39996f // the golden angle
            out[0] = cos(angle) * ringRadius * 0.5f
            out[1] = y * 0.5f
            out[2] = sin(angle) * ringRadius * 0.5f
        } else {
            val u = (i - onSphere) / (count - onSphere).toFloat()
            var radius = 0.72f + 0.36f * u
            if (u > 0.5f) radius += 0.05f // a gap in the ring
            val angle = random.nextFloat() * TAU
            val x = cos(angle) * radius
            val y = random.gauss() * 0.006f
            out[0] = x * cos(tilt) - y * sin(tilt)
            out[1] = x * sin(tilt) + y * cos(tilt)
            out[2] = sin(angle) * radius
        }
    }
}

/** A (2,3) torus knot: one closed curve that winds around a doughnut without ever crossing itself. */
private fun knot(count: Int, random: Random) = fill(count) { i, out ->
    val t = TAU * i / count
    val ring = 2f + cos(3f * t)
    out[0] = ring * cos(2f * t) / 3.1f + random.gauss() * 0.03f
    out[1] = ring * sin(2f * t) / 3.1f + random.gauss() * 0.03f
    out[2] = sin(3f * t) / 2.4f + random.gauss() * 0.03f
}

/** Two strands twisting up the y axis, joined by rungs. */
private fun helix(count: Int, random: Random) = fill(count) { i, out ->
    val radius = 0.36f
    val y = -1.25f + 2.5f * i / count
    if (random.nextFloat() < 0.2f) {
        val level = (y * 8f).roundToInt() / 8f
        val angle = level * TAU * 0.9f
        val along = random.nextFloat() * 2f - 1f
        out[0] = cos(angle) * radius * along
        out[1] = level
        out[2] = sin(angle) * radius * along
    } else {
        val angle = y * TAU * 0.9f + if (random.nextBoolean()) 0f else PI.toFloat()
        out[0] = cos(angle) * radius + random.gauss() * 0.012f
        out[1] = y
        out[2] = sin(angle) * radius + random.gauss() * 0.012f
    }
}

/** The classic heart curve, filled inwards and puffed out in z so it is a real 3D cushion. */
private fun heart(count: Int, random: Random) = fill(count) { i, out ->
    val t = TAU * i / count
    val u = random.nextFloat()
    val inset = 1f - 0.55f * u * u // most particles near the outline, fewer towards the middle
    val x = 16f * sin(t).pow(3) / 17f
    val y = -(13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)) / 17f
    val side = if (random.nextBoolean()) 1f else -1f
    out[0] = x * inset * 0.92f
    out[1] = y * inset * 0.92f - 0.12f
    out[2] = sqrt(1f - inset * inset) * 0.4f * side
}

/**
 * Text as particles. Decision: instead of describing letter shapes by hand, let Android draw the
 * word into a small off-screen bitmap once, and use every lit pixel as a possible particle
 * position. This works for any string, font or language.
 */
private fun text(count: Int, random: Random, word: String): FloatArray {
    val width = 640
    val height = 180
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 130f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
    }
    val measured = paint.measureText(word)
    if (measured > width - 24) paint.textSize *= (width - 24) / measured
    val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
    Canvas(bitmap).drawText(word, width / 2f, baseline, paint)

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()

    // Scan column by column so that the list is ordered left to right (for the colour gradient).
    val lit = IntArray(width * height)
    var litCount = 0
    for (x in 0 until width) {
        for (y in 0 until height) {
            if (pixels[y * width + x] ushr 24 > 128) lit[litCount++] = (x shl 16) or y
        }
    }
    if (litCount == 0) return planet(count, random)

    val halfSpan = paint.measureText(word) / 2f
    return fill(count) { i, out ->
        val pick = (((i + random.nextFloat()) / count) * litCount).toInt().coerceAtMost(litCount - 1)
        val px = lit[pick] shr 16
        val py = lit[pick] and 0xFFFF
        out[0] = (px + random.nextFloat() - width / 2f) / halfSpan * 0.98f
        out[1] = (py + random.nextFloat() - height / 2f) / halfSpan * 0.98f
        out[2] = random.gauss() * 0.03f
    }
}
