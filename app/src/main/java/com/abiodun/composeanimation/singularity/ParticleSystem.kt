package com.abiodun.composeanimation.singularity

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The simulation. Deliberately contains no Compose: no State, no Offset, no Color objects.
 *
 * Decision 1 — flat FloatArrays instead of a `class Particle`.
 *   6,000 objects would be scattered over the heap and each access would chase a pointer.
 *   Arrays are contiguous memory, and stepping them allocates nothing, so the garbage
 *   collector never has a reason to pause a frame.
 *
 * Decision 2 — every particle is its own damped spring towards its target (the same physics
 *   as Compose's `spring()` spec from lesson 2, written out by hand).
 *   A morph is then nothing more than swapping the targets. There is no timeline, so a morph
 *   can be interrupted at any moment by another morph, a touch or a shockwave, and it always
 *   continues smoothly from the current position and velocity (lesson 3's key property).
 *
 * Decision 3 — the simulation runs in view space (after the camera rotation).
 *   The rotating target drags the particles behind it, so the shape visibly flexes as it turns,
 *   and a finger position maps directly onto particle x/y without inverting a 3D rotation.
 *
 * Decision 4 — the output is one triangle mesh: a small quad per particle, in flat arrays.
 *   The first version drew thick round-capped lines with drawLines. It measured ~22 fps: the
 *   renderer builds every such line as its own shape, so "36 calls" was really 12,000 shapes.
 *   A mesh is the GPU's native input. All 6,000 quads go out in a single drawVertices call,
 *   and each vertex carries its own colour, so every particle can have its own tint and glow.
 */
internal class ParticleSystem(val count: Int = 6000) {

    val formations = buildFormations(count)
    var formation = formations[0]
        private set

    // --- per-particle state -------------------------------------------------------------------
    private val pos = FloatArray(count * 3)
    private val vel = FloatArray(count * 3)
    private val stiffness = FloatArray(count)
    private val damping = FloatArray(count)
    private val phase = FloatArray(count)

    // --- camera -------------------------------------------------------------------------------
    private var yaw = 0f
    private var pitch = 0f
    var time = 0f
        private set
    var width = 0f
        private set
    var height = 0f
        private set
    /** Pixels per unit. */
    var scale = 1f
        private set

    // --- touch: the gravity well --------------------------------------------------------------
    private var wellHeld = false
    var wellX = 0f
        private set
    var wellY = 0f
        private set
    /** 0..1, eased, so the collapse builds up while the finger stays down. */
    var wellStrength = 0f
        private set

    // --- shockwave ----------------------------------------------------------------------------
    var shockX = 0f
        private set
    var shockY = 0f
        private set
    private var shockAge = -1f
    val shockRadiusPx: Float get() = shockAge * SHOCK_SPEED * scale
    val shockFade: Float get() = if (shockAge < 0f) 0f else 1f - shockAge / SHOCK_LIFE

    // --- render output ------------------------------------------------------------------------
    /** 4 corners per particle, [x, y] each. Rewritten every frame. */
    val vertices = FloatArray(count * 8)
    /** Which corner of the glow sprite each vertex shows. Never changes, so it is filled once. */
    val texCoords = FloatArray(count * 8)
    /** One ARGB colour per vertex. */
    val vertexColors = IntArray(count * 4)
    /** Two triangles per quad, as indices into [vertices]. Also filled once. */
    val indices = ShortArray(count * 6)
    /** Just the centres, for the simple fallback renderer on old Android versions. */
    val points = FloatArray(count * 2)
    /** The palette's three stops as 0..1 RGB, eased towards the current formation's palette. */
    val stops = FloatArray(9)
    private val targetStops = FloatArray(9)
    private val size = FloatArray(count)

    init {
        val random = Random(42)
        for (i in 0 until count) {
            // Varied stiffness is what makes a morph look organic: particles arrive at different
            // times instead of as one rigid block.
            val k = 16f + random.nextFloat() * 26f
            stiffness[i] = k
            // 2·√k is critical damping. 0.42 of that leaves each particle a little bouncy.
            damping[i] = 2f * sqrt(k) * 0.42f
            phase[i] = random.nextFloat() * 2f * PI.toFloat()
            // Everything starts packed at the centre, so the first frame is a "big bang".
            pos[i * 3] = (random.nextFloat() - 0.5f) * 0.02f
            pos[i * 3 + 1] = (random.nextFloat() - 0.5f) * 0.02f
            pos[i * 3 + 2] = (random.nextFloat() - 0.5f) * 0.02f
        }
        for (i in 0 until count) {
            // Mostly similar sizes, with a few large "bright stars" for sparkle.
            size[i] = if (random.nextFloat() < 0.03f) 2.2f else 0.7f + random.nextFloat() * 0.6f
            val t = i * 8
            texCoords[t] = 0f; texCoords[t + 1] = 0f
            texCoords[t + 2] = SPRITE; texCoords[t + 3] = 0f
            texCoords[t + 4] = SPRITE; texCoords[t + 5] = SPRITE
            texCoords[t + 6] = 0f; texCoords[t + 7] = SPRITE
            val v = i * 4
            val n = i * 6
            indices[n] = v.toShort(); indices[n + 1] = (v + 1).toShort(); indices[n + 2] = (v + 2).toShort()
            indices[n + 3] = v.toShort(); indices[n + 4] = (v + 2).toShort(); indices[n + 5] = (v + 3).toShort()
        }
        computeTargetColors()
        targetStops.copyInto(stops)
        pitch = formation.pitch
    }

    fun resize(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
        scale = min(newWidth, newHeight) * 0.42f
    }

    fun morphTo(index: Int) {
        formation = formations[index % formations.size]
        computeTargetColors()
    }

    fun holdWell(x: Float, y: Float) {
        wellHeld = true
        wellX = x
        wellY = y
    }

    fun moveWell(x: Float, y: Float) {
        wellX = x
        wellY = y
    }

    /** Letting go of a well that had time to build up sets off a shockwave from that point. */
    fun releaseWell() {
        if (wellHeld && wellStrength > 0.45f) shock(wellX, wellY)
        wellHeld = false
    }

    fun shock(x: Float, y: Float) {
        shockX = x
        shockY = y
        shockAge = 0f
    }

    /**
     * Advances the world by [frameSeconds] and rebuilds the mesh. Called once per frame.
     * [spriteRadius] is the on-screen radius of one particle's glow, in pixels.
     */
    fun step(frameSeconds: Float, spriteRadius: Float) {
        if (width == 0f) return
        // Clamp: after the app was in the background the first frame reports a huge gap, and an
        // unclamped step would catapult every spring.
        val dt = min(frameSeconds, 1f / 30f)
        time += dt

        // Camera. `1 - exp(-rate·dt)` is frame-rate independent easing: it closes the same
        // fraction of the gap per second whether the display runs at 60 or 120 Hz.
        if (formation.spins) {
            yaw += dt * 0.45f
        } else {
            val turn = 2f * PI.toFloat()
            yaw -= turn * Math.round(yaw / turn) // wrap to the nearest face-on angle
            val sway = 0.4f * sin(time * 0.8f)
            yaw += (sway - yaw) * (1f - exp(-dt * 3f))
        }
        val pitchTarget = formation.pitch + 0.08f * sin(time * 0.5f)
        pitch += (pitchTarget - pitch) * (1f - exp(-dt * 2.5f))
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        // Touch.
        val wellTarget = if (wellHeld) 1f else 0f
        val wellRate = if (wellHeld) 1.8f else 6f
        wellStrength += (wellTarget - wellStrength) * (1f - exp(-dt * wellRate))
        val well = wellStrength
        // While the well is active the springs go slack, so gravity wins and the shape collapses.
        val springScale = 1f - 0.88f * well

        // Shockwave: an expanding shell. Only particles inside the shell's thickness are pushed.
        if (shockAge >= 0f) {
            shockAge += dt
            if (shockAge > SHOCK_LIFE) shockAge = -1f
        }
        val shocking = shockAge >= 0f
        val shockRadius = shockAge * SHOCK_SPEED
        val shockPower = SHOCK_FORCE * shockFade
        val centerX = width / 2f
        val centerY = height / 2f
        val shockUx = (shockX - centerX) / scale
        val shockUy = (shockY - centerY) / scale

        // Colours ease towards the current formation's palette.
        val colorEase = 1f - exp(-dt * 2.5f)
        for (c in stops.indices) stops[c] += (targetStops[c] - stops[c]) * colorEase

        val target = formation.points
        val invCount = 1f / count

        for (i in 0 until count) {
            val o = i * 3

            // 1. Where this particle wants to be: its target, rotated by the camera.
            val mx = target[o]
            val my = target[o + 1]
            val mz = target[o + 2]
            val rx = mx * cosYaw + mz * sinYaw
            val rz = -mx * sinYaw + mz * cosYaw
            var tx = rx
            var ty = my * cosPitch - rz * sinPitch
            val tz = my * sinPitch + rz * cosPitch
            // A tiny drift so the shape shimmers instead of freezing when it has settled.
            val ph = phase[i]
            tx += 0.012f * sin(time * 1.7f + ph)
            ty += 0.012f * cos(time * 1.3f + ph * 1.7f)

            var px = pos[o]
            var py = pos[o + 1]
            var pz = pos[o + 2]
            var vx = vel[o]
            var vy = vel[o + 1]
            var vz = vel[o + 2]

            // 2. Forces. Spring: pull towards the target, friction against the velocity.
            val k = stiffness[i] * springScale
            val c = damping[i]
            var ax = k * (tx - px) - c * vx
            var ay = k * (ty - py) - c * vy
            var az = k * (tz - pz) - c * vz

            val depth = max(CAMERA + pz, 0.6f)
            val perspective = CAMERA / depth

            if (well > 0.001f) {
                // The finger, expressed in units at this particle's depth.
                val dx = (wellX - centerX) / (scale * perspective) - px
                val dy = (wellY - centerY) / (scale * perspective) - py
                val dz = -pz
                // Softened inverse-distance pull; the +0.03 stops the force exploding at d = 0.
                val pull = well * WELL_GRAVITY / (dx * dx + dy * dy + dz * dz + 0.03f)
                // Adding a sideways component (-dy, dx) turns "fall in" into "orbit in".
                ax += (dx - dy * WELL_SWIRL) * pull
                ay += (dy + dx * WELL_SWIRL) * pull
                az += dz * pull
            }

            if (shocking) {
                val dx = px - shockUx
                val dy = py - shockUy
                val distance = sqrt(dx * dx + dy * dy + pz * pz)
                val fromShell = abs(distance - shockRadius)
                if (fromShell < SHOCK_BAND) {
                    val push = shockPower * (1f - fromShell / SHOCK_BAND) / (distance + 0.05f)
                    ax += dx * push
                    ay += dy * push
                    az += pz * push
                }
            }

            // 3. Integrate (semi-implicit Euler: velocity first, then position with the new
            //    velocity. This ordering stays stable for springs where plain Euler blows up).
            vx += ax * dt
            vy += ay * dt
            vz += az * dt
            px += vx * dt
            py += vy * dt
            pz += vz * dt
            pos[o] = px
            pos[o + 1] = py
            pos[o + 2] = pz
            vel[o] = vx
            vel[o + 1] = vy
            vel[o + 2] = vz

            // 4. Project to the screen: divide by depth, so far things are smaller and closer in.
            val p = CAMERA / max(CAMERA + pz, 0.6f)
            val sx = centerX + px * scale * p
            val sy = centerY + py * scale * p
            points[i * 2] = sx
            points[i * 2 + 1] = sy

            // 5. Build this particle's quad. The quad reaches from where the particle was a
            //    moment ago (the tail) to where it is now (the head), padded by the sprite radius.
            //    Standing still it is a square showing a round glow; moving, the same glow is
            //    stretched along the direction of travel into a streak. Motion blur for free.
            val tailX = vx * scale * p * STREAK
            val tailY = vy * scale * p * STREAK
            val length = sqrt(tailX * tailX + tailY * tailY)
            var dirX = 1f
            var dirY = 0f
            if (length > 0.01f) {
                dirX = tailX / length
                dirY = tailY / length
            }
            val radius = spriteRadius * size[i] * p
            val alongX = dirX * radius
            val alongY = dirY * radius
            val acrossX = -dirY * radius
            val acrossY = dirX * radius
            val backX = sx - tailX - alongX
            val backY = sy - tailY - alongY
            val frontX = sx + alongX
            val frontY = sy + alongY
            val v = i * 8
            vertices[v] = backX + acrossX
            vertices[v + 1] = backY + acrossY
            vertices[v + 2] = frontX + acrossX
            vertices[v + 3] = frontY + acrossY
            vertices[v + 4] = frontX - acrossX
            vertices[v + 5] = frontY - acrossY
            vertices[v + 6] = backX - acrossX
            vertices[v + 7] = backY - acrossY

            // 6. Colour: position along the palette by index, whitened by speed ("hotter" when
            //    fast), dimmer when far away.
            val t = i * invCount
            val s0 = if (t < 0.5f) 0 else 3
            val f = if (t < 0.5f) t * 2f else (t - 0.5f) * 2f
            val speed = sqrt(vx * vx + vy * vy + vz * vz)
            val heat = min(speed * 0.1f, 0.45f)
            var r = stops[s0] + (stops[s0 + 3] - stops[s0]) * f
            var g = stops[s0 + 1] + (stops[s0 + 4] - stops[s0 + 1]) * f
            var b = stops[s0 + 2] + (stops[s0 + 5] - stops[s0 + 2]) * f
            r += (1f - r) * heat
            g += (1f - g) * heat
            b += (1f - b) * heat
            val alpha = min(0.46f * p * p + heat * 0.25f, 1f)
            val argb = ((alpha * 255f).toInt() shl 24) or
                ((r * 255f).toInt() shl 16) or
                ((g * 255f).toInt() shl 8) or
                (b * 255f).toInt()
            val ci = i * 4
            vertexColors[ci] = argb
            vertexColors[ci + 1] = argb
            vertexColors[ci + 2] = argb
            vertexColors[ci + 3] = argb
        }
    }

    private fun computeTargetColors() {
        for (stop in 0 until 3) {
            val color = formation.palette[stop]
            targetStops[stop * 3] = (color shr 16 and 0xFF) / 255f
            targetStops[stop * 3 + 1] = (color shr 8 and 0xFF) / 255f
            targetStops[stop * 3 + 2] = (color and 0xFF) / 255f
        }
    }

    companion object {
        /** Width and height of the glow sprite bitmap, in pixels. */
        const val SPRITE = 64f
        /** Distance from the camera to the origin, in units. Smaller = stronger perspective. */
        const val CAMERA = 3.2f
        const val STREAK = 0.035f
        const val WELL_GRAVITY = 10f
        const val WELL_SWIRL = 1.3f
        const val SHOCK_SPEED = 3.2f
        const val SHOCK_LIFE = 0.95f
        const val SHOCK_BAND = 0.3f
        const val SHOCK_FORCE = 46f
    }
}
