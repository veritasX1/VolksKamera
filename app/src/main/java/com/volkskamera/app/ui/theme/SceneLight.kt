package com.volkskamera.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/** Lichtfarben (Farbtemperatur der „Lampe im Raum“). */
enum class LightColor(private val labelDe: String, val r: Float, val g: Float, val b: Float) {
    TAGESLICHT("Tageslicht", 1f, 1f, 1f),
    GLUEHLAMPE("Glühlampe", 1f, 0.84f, 0.62f),
    KERZE("Kerzenlicht", 1f, 0.72f, 0.42f),
    ABENDSONNE("Abendsonne", 1f, 0.8f, 0.55f),
    NEON("Neonröhre", 0.88f, 0.96f, 1f),
    MONDLICHT("Mondlicht", 0.72f, 0.8f, 1f);

    val color get() = Color(r, g, b)
    val label get() = com.volkskamera.app.t(labelDe)
}

/**
 * Das Licht der Szene in Bildschirm-Koordinaten (x rechts, y unten, z zum Betrachter),
 * dazu Versatz der Spiegelung (aus Neigung und Schwenk) und Helligkeit (Licht hinter dem Gerät = dunkler).
 */
data class SceneLight(
    val x: Float, val y: Float, val z: Float,
    val envX: Float = 0f, val envY: Float = 0f,
    val level: Float = 1f,
) {
    companion object {
        /** fest von oben links */
        val FIXED = SceneLight(-0.45f, -0.5f, 0.74f)
    }
}

/** Hält das aktuelle Licht; wird nur beim Zeichnen gelesen (kein Neuaufbau der Oberfläche). */
@Stable
class LightHolder { var light by mutableStateOf(SceneLight.FIXED) }

val LocalLight = staticCompositionLocalOf { LightHolder() }

/**
 * Licht, das fest im Raum steht: aus dem Rotationssensor (Neigen UND Schwenken) wird die Richtung
 * einer Lampe schräg oben in Bildschirm-Koordinaten umgerechnet. Aus [Housing.light] = fest.
 */
@Composable
fun rememberSceneLight(follow: Boolean): LightHolder {
    val context = LocalContext.current
    val holder = remember { LightHolder() }
    if (!follow) { holder.light = SceneLight.FIXED; return holder }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Lampe im Raum: schräg oben (Welt: x Ost, y Nord, z oben)
        val lw = floatArrayOf(-0.35f, 0.45f, 0.82f).let { v -> val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); FloatArray(3) { v[it] / n } }
        val rot = FloatArray(9); val rem = FloatArray(9); val ang = FloatArray(3)
        var sx = SceneLight.FIXED.x; var sy = SceneLight.FIXED.y; var sz = SceneLight.FIXED.z
        var ex = 0f; var ey = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                @Suppress("DEPRECATION")
                val dr = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                when (dr) {
                    Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rem)
                    Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rem)
                    Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rem)
                    else -> System.arraycopy(rot, 0, rem, 0, 9)
                }
                // Gerät = Rᵀ · Welt; Bildschirm-y zeigt nach unten
                val dx = rem[0] * lw[0] + rem[3] * lw[1] + rem[6] * lw[2]
                val dy = rem[1] * lw[0] + rem[4] * lw[1] + rem[7] * lw[2]
                val dz = rem[2] * lw[0] + rem[5] * lw[1] + rem[8] * lw[2]
                SensorManager.getOrientation(rem, ang)
                val k = 0.18f
                sx += (dx - sx) * k; sy += (-dy - sy) * k; sz += (dz - sz) * k
                ex += (ang[0] / (2 * Math.PI.toFloat()) - ex) * k; ey += (ang[1] / Math.PI.toFloat() - ey) * k
                val cur = holder.light
                if (kotlin.math.abs(sx - cur.x) + kotlin.math.abs(sy - cur.y) + kotlin.math.abs(sz - cur.z) +
                    kotlin.math.abs(ex - cur.envX) + kotlin.math.abs(ey - cur.envY) > 0.006f) {
                    // Licht hinter dem Gerät: dunkler, Richtung knapp vor die Fläche legen
                    val level = ((sz + 0.35f) / 0.5f).coerceIn(0.25f, 1f)
                    val z = max(sz, 0.08f)
                    val n = sqrt(sx * sx + sy * sy + z * z)
                    holder.light = SceneLight(sx / n, sy / n, z / n, ex, ey, level)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    return holder
}

/**
 * Weicher Schlagschatten, der vom Licht weg fällt (Richtung und Länge aus der Lichtrichtung).
 * Vor .clip(...) setzen, damit der Schatten außerhalb des Elements sichtbar ist.
 */
fun Modifier.lightShadow(shape: Shape, elevation: Dp, strength: Float = 1f): Modifier = composed {
    val holder = LocalLight.current
    drawBehind {
        val l = holder.light
        val e = elevation.toPx()
        // Schattenversatz: je flacher das Licht, desto länger der Schatten
        val dx = (-l.x / max(l.z, 0.3f) * e).coerceIn(-2.5f * e, 2.5f * e)
        val dy = (-l.y / max(l.z, 0.3f) * e).coerceIn(-2.5f * e, 2.5f * e)
        val path = Path().apply {
            when (val o = shape.createOutline(size, layoutDirection, this@drawBehind)) {
                is Outline.Rectangle -> addRect(o.rect)
                is Outline.Rounded -> addRoundRect(o.roundRect)
                is Outline.Generic -> addPath(o.path)
            }
        }
        drawIntoCanvas { c ->
            val p = android.graphics.Paint().apply {
                isAntiAlias = true
                color = Color.Black.copy(alpha = (0.55f * strength * l.level).coerceIn(0f, 0.8f)).toArgb()
                maskFilter = BlurMaskFilter(max(e * 0.9f, 1f), BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.save()
            c.nativeCanvas.translate(dx, dy)
            c.nativeCanvas.drawPath(path.asAndroidPath(), p)
            c.nativeCanvas.restore()
        }
    }
}

/** Spiegel-Umgebung: eigenes Frontkamera-Foto oder ein neutrales „Studio“ (helle Softbox oben, dunkler Boden). */
object Environment {
    fun file(c: Context) = File(c.filesDir, "umgebung.jpg")

    fun load(c: Context): Bitmap = runCatching { BitmapFactory.decodeFile(file(c).path) }.getOrNull() ?: studio()

    /** Weiches Studio: Decke hell, zwei verschwommene Softboxen, zum Boden dunkel – keine harten Kanten. */
    fun studio(): Bitmap {
        val n = 128
        val px = IntArray(n * n)
        fun soft(d: Float, r: Float) = kotlin.math.exp(-(d * d) / (2 * r * r))
        for (y in 0 until n) for (x in 0 until n) {
            val u = x / (n - 1f); val v = y / (n - 1f)
            var l = 0.62f - 0.5f * v                                     // Decke → Boden
            l += 0.35f * soft(v - 0.2f, 0.07f) * soft(u - 0.3f, 0.14f)   // Softbox links oben
            l += 0.25f * soft(v - 0.26f, 0.08f) * soft(u - 0.75f, 0.12f) // Softbox rechts
            val g = (l.coerceIn(0f, 1f) * 255).toInt()
            px[y * n + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /** Frontkamera-Bild zur Umgebung machen: spiegeln (wie ein Spiegel), quadratisch, weich. */
    fun save(c: Context, src: Bitmap, rotation: Int) {
        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()); postScale(-1f, 1f) }
        val r = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val s = minOf(r.width, r.height)
        val sq = Bitmap.createBitmap(r, (r.width - s) / 2, (r.height - s) / 2, s, s)
        // erst klein, dann in Stufen hoch: weiche, unscharfe Spiegelung ohne Pixeltreppen
        var b = Bitmap.createScaledBitmap(sq, 40, 40, true)
        for (size in intArrayOf(56, 80, 112, 160, 192)) b = Bitmap.createScaledBitmap(b, size, size, true)
        val soft = b
        file(c).outputStream().use { soft.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
