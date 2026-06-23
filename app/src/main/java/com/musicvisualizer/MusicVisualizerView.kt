package com.musicvisualizer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class MusicVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var theme: VisualizationTheme = VisualizationTheme.SPECTRUM_BARS
        set(value) {
            field = value
            resetThemeState()
            invalidate()
        }

    var waveform: ByteArray = ByteArray(1024)
    var magnitude: FloatArray = FloatArray(512)
    var beatEnergy: Float = 0f
    var bassEnergy: Float = 0f
    var midEnergy: Float = 0f
    var trebleEnergy: Float = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Particle system
    private val particles = mutableListOf<Particle>()
    private val maxParticles = 200

    // Kaleidoscope
    private val kaleidoPath = Path()

    // Auto-mode theme cycling
    private var autoThemeIndex = 0
    private val autoThemes = listOf(
        VisualizationTheme.SPECTRUM_BARS,
        VisualizationTheme.CIRCULAR,
        VisualizationTheme.PARTICLES,
        VisualizationTheme.NEON_PULSE,
        VisualizationTheme.WAVEFORM,
        VisualizationTheme.KALEIDOSCOPE
    )
    private var autoThemeTick = 0

    // Colors
    private val neonColors = intArrayOf(
        Color.parseColor("#FF00FF"),
        Color.parseColor("#00FFFF"),
        Color.parseColor("#FF6600"),
        Color.parseColor("#00FF88"),
        Color.parseColor("#FF0055")
    )

    private var hue = 0f
    private var colorShift = 0f

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var color: Int,
        var life: Float,
        var maxLife: Float
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        hue = (hue + 0.5f) % 360f
        colorShift += 0.01f

        val activeTheme = if (theme == VisualizationTheme.AUTO) {
            autoThemeTick++
            if (autoThemeTick > 600) {
                autoThemeTick = 0
                autoThemeIndex = (autoThemeIndex + 1) % autoThemes.size
            }
            autoThemes[autoThemeIndex]
        } else theme

        when (activeTheme) {
            VisualizationTheme.WAVEFORM -> drawWaveform(canvas)
            VisualizationTheme.SPECTRUM_BARS -> drawSpectrumBars(canvas)
            VisualizationTheme.PARTICLES -> drawParticles(canvas)
            VisualizationTheme.CIRCULAR -> drawCircular(canvas)
            VisualizationTheme.NEON_PULSE -> drawNeonPulse(canvas)
            VisualizationTheme.KALEIDOSCOPE -> drawKaleidoscope(canvas)
            VisualizationTheme.AUTO -> drawSpectrumBars(canvas)
        }

        postInvalidateOnAnimation()
    }

    private fun drawWaveform(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f

        val gradient = LinearGradient(0f, 0f, w, 0f,
            Color.HSVToColor(floatArrayOf(hue, 1f, 1f)),
            Color.HSVToColor(floatArrayOf((hue + 120f) % 360f, 1f, 1f)),
            Shader.TileMode.CLAMP)
        paint.shader = gradient

        path.reset()
        val step = w / waveform.size
        path.moveTo(0f, mid)
        waveform.forEachIndexed { i, byte ->
            val x = i * step
            val y = mid + (byte.toFloat() / 128f) * (h * 0.4f) * (1f + beatEnergy)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        // Mirror below
        paint.alpha = 120
        path.reset()
        waveform.forEachIndexed { i, byte ->
            val x = i * step
            val y = mid - (byte.toFloat() / 128f) * (h * 0.3f) * (1f + bassEnergy)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        paint.alpha = 255
        paint.shader = null
    }

    private fun drawSpectrumBars(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = 64
        val barWidth = w / barCount
        val gap = barWidth * 0.15f

        for (i in 0 until barCount) {
            val magIndex = (i.toFloat() / barCount * magnitude.size).toInt()
                .coerceIn(0, magnitude.size - 1)
            val mag = magnitude[magIndex]
            val barH = mag * h * 0.85f * (1f + beatEnergy * 0.3f)

            val barHue = (hue + i * 360f / barCount) % 360f
            val color = Color.HSVToColor(floatArrayOf(barHue, 0.9f, 1f))

            val gradient = LinearGradient(
                0f, h - barH, 0f, h,
                Color.HSVToColor(floatArrayOf(barHue, 0.7f, 1f)),
                Color.HSVToColor(floatArrayOf(barHue, 1f, 0.4f)),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            paint.style = Paint.Style.FILL

            val left = i * barWidth + gap
            val right = (i + 1) * barWidth - gap
            canvas.drawRoundRect(left, h - barH, right, h, 4f, 4f, paint)

            // Reflection
            paint.alpha = 60
            canvas.drawRoundRect(left, h, right, h + barH * 0.4f, 4f, 4f, paint)
            paint.alpha = 255
        }
        paint.shader = null
    }

    private fun drawParticles(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        // Spawn new particles on beats
        val spawnCount = (beatEnergy * 15).toInt()
        repeat(spawnCount) {
            if (particles.size < maxParticles) {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val speed = 2f + Random.nextFloat() * 6f * bassEnergy
                val colorHue = (hue + Random.nextFloat() * 60f) % 360f
                particles.add(Particle(
                    x = cx + (Random.nextFloat() - 0.5f) * 40f,
                    y = cy + (Random.nextFloat() - 0.5f) * 40f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    radius = 3f + Random.nextFloat() * 8f,
                    color = Color.HSVToColor(floatArrayOf(colorHue, 1f, 1f)),
                    life = 1f,
                    maxLife = 1f
                ))
            }
        }

        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.05f // gravity
            p.vx *= 0.99f
            p.life -= 0.015f + trebleEnergy * 0.01f

            if (p.life <= 0f) {
                iter.remove()
                continue
            }

            val alpha = (p.life / p.maxLife * 255).toInt()
            paint.color = p.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL

            // Glow effect
            paint.maskFilter = BlurMaskFilter(p.radius * 2, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(p.x, p.y, p.radius * p.life, paint)
        }
        paint.maskFilter = null
        paint.alpha = 255

        // Central pulsing orb
        val orbRadius = 40f + bassEnergy * 80f
        val orbGradient = RadialGradient(cx, cy, orbRadius,
            Color.HSVToColor(floatArrayOf(hue, 0.5f, 1f)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP)
        paint.shader = orbGradient
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, orbRadius, paint)
        paint.shader = null
    }

    private fun drawCircular(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(width, height) * 0.25f
        val n = waveform.size

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f

        // Draw multiple rings
        for (ring in 0..2) {
            val ringRadius = baseRadius * (1f + ring * 0.4f + bassEnergy * 0.3f)
            val ringHue = (hue + ring * 60f) % 360f
            paint.color = Color.HSVToColor(200 - ring * 40, floatArrayOf(ringHue, 1f, 1f))

            path.reset()
            for (i in 0..n) {
                val angle = (i.toFloat() / n) * 2f * PI.toFloat()
                val sample = waveform[i % n].toFloat() / 128f
                val r = ringRadius + sample * (50f + beatEnergy * 40f)
                val x = cx + cos(angle) * r
                val y = cy + sin(angle) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        // Frequency spokes
        paint.strokeWidth = 1.5f
        val spokeCount = 32
        for (i in 0 until spokeCount) {
            val angle = (i.toFloat() / spokeCount) * 2f * PI.toFloat()
            val magIndex = (i.toFloat() / spokeCount * magnitude.size).toInt()
                .coerceIn(0, magnitude.size - 1)
            val mag = magnitude[magIndex]
            val inner = baseRadius * 0.6f
            val outer = baseRadius * (1f + mag * 1.5f)
            val spokeHue = (hue + i * 360f / spokeCount) % 360f
            paint.color = Color.HSVToColor(floatArrayOf(spokeHue, 1f, 1f))
            canvas.drawLine(
                cx + cos(angle) * inner, cy + sin(angle) * inner,
                cx + cos(angle) * outer, cy + sin(angle) * outer,
                paint
            )
        }
    }

    private fun drawNeonPulse(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // Pulsing concentric circles
        val pulseCount = 8
        for (i in 0 until pulseCount) {
            val phase = (colorShift + i * 0.3f) % 1f
            val radius = phase * maxOf(w, h) * 0.8f
            val alpha = ((1f - phase) * 220).toInt()
            val colorIndex = (i + autoThemeIndex) % neonColors.size
            paint.color = neonColors[colorIndex]
            paint.alpha = (alpha * (0.5f + beatEnergy * 0.5f)).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f + bassEnergy * 5f
            paint.maskFilter = BlurMaskFilter(8f + beatEnergy * 12f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx, cy, radius * (0.7f + bassEnergy * 0.3f), paint)
        }

        // Neon waveform line
        paint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        paint.strokeWidth = 4f
        paint.color = neonColors[(autoThemeIndex + 2) % neonColors.size]
        paint.alpha = 230

        path.reset()
        waveform.forEachIndexed { i, byte ->
            val x = i * w / waveform.size
            val y = cy + (byte.toFloat() / 128f) * h * 0.3f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)

        paint.maskFilter = null
        paint.alpha = 255

        // Central star burst on beat
        if (beatEnergy > 0.6f) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = ((beatEnergy - 0.6f) / 0.4f * 180).toInt()
                maskFilter = BlurMaskFilter(60f * beatEnergy, BlurMaskFilter.Blur.NORMAL)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 20f * beatEnergy, starPaint)
        }
    }

    private fun drawKaleidoscope(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val segments = 8
        val angleStep = (2f * PI / segments).toFloat()
        val radius = minOf(width, height) * 0.6f

        canvas.save()
        canvas.translate(cx, cy)

        repeat(segments) { seg ->
            canvas.save()
            canvas.rotate(Math.toDegrees(seg * angleStep.toDouble()).toFloat())

            kaleidoPath.reset()
            val n = minOf(waveform.size, 64)
            for (i in 0..n) {
                val t = i.toFloat() / n
                val angle = t * angleStep
                val sample = waveform[i % waveform.size].toFloat() / 128f
                val r = (0.3f + t * 0.7f) * radius + sample * radius * 0.15f * (1f + beatEnergy)
                val x = cos(angle) * r
                val y = sin(angle) * r
                if (i == 0) kaleidoPath.moveTo(x, y) else kaleidoPath.lineTo(x, y)
            }

            val segHue = (hue + seg * 360f / segments) % 360f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f + bassEnergy * 3f
            paint.color = Color.HSVToColor(floatArrayOf(segHue, 1f, 1f))
            paint.shader = null
            canvas.drawPath(kaleidoPath, paint)

            // Mirror
            canvas.scale(1f, -1f)
            canvas.drawPath(kaleidoPath, paint)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun resetThemeState() {
        particles.clear()
        autoThemeTick = 0
        path.reset()
    }
}
