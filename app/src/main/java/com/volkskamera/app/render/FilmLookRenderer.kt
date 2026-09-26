package com.volkskamera.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.volkskamera.app.data.SequenceAsset
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Die komplette Film-Look-Kette in einem Shader-Durchgang. Wird vom Media3-Export
 * (FilmLookEffect) und von der animierten Vorschau (LookPreview) gleichermaßen benutzt,
 * damit beide garantiert gleich aussehen. Muss auf einem GL-Thread mit aktivem Kontext
 * erzeugt, benutzt und freigegeben werden.
 */
@UnstableApi
class FilmLookRenderer(private val context: Context, private val maxCachedSequences: Int = Int.MAX_VALUE) {

    private class Layer(val tex: IntArray, val fps: Int, val stats: Stats)

    private val program = GlProgram(VERTEX, FRAGMENT).apply {
        setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    // Textur-Cache: Material wird einmal geladen; beim Umstellen von Reglern wird nichts neu geladen.
    // In der Vorschau begrenzt (maxCachedSequences), damit der Grafikspeicher beim Durchprobieren
    // vieler Leaks/Staub-Folgen nicht voll läuft.
    private val lutCache = HashMap<String, Int>()
    private val seqCache = LinkedHashMap<String, Layer>(16, 0.75f, true)

    private fun lutTexFor(file: String?): Int = file?.let { lutCache.getOrPut(it) { loadTexture(context, it).first } } ?: 0

    private fun layerFor(a: SequenceAsset?, amount: Float): Layer? {
        if (a == null || amount <= 0f) return null
        seqCache[a.id]?.let { return it }
        val l = layer(context, a)
        seqCache[a.id] = l
        while (seqCache.size > maxCachedSequences) {
            val oldest = seqCache.entries.first()
            GLES20.glDeleteTextures(oldest.value.tex.size, oldest.value.tex, 0)
            seqCache.remove(oldest.key)
        }
        return l
    }

    private lateinit var look: FilmLook
    private var lutTex = 0
    private var lutMono = false
    private var grain: Layer? = null
    private var dust: Layer? = null
    private var scratch: Layer? = null
    private var leak: Layer? = null
    private var frame: Layer? = null
    private var burn: Layer? = null

    /** Look setzen und benötigtes Material laden (nur beim ersten Gebrauch, danach aus dem Cache). */
    fun setLook(l0: FilmLook) {
        val l = l0.effective()
        look = l
        lutTex = lutTexFor(l.lutFile)
        // Schwarzweiß-LUT: Mischung gegen Grau statt gegen Farbe, Ergebnis komplett grau
        lutMono = l.lutFile?.let { LutMatrix.info(context, it).mono } ?: false
        grain = layerFor(l.grain, l.grainAmount)
        dust = layerFor(l.dust, l.dustAmount)
        scratch = layerFor(l.scratch, l.scratchAmount)
        leak = layerFor(l.leak, l.leakAmount)
        frame = layerFor(l.frame?.takeIf { it.gate != null }, 1f)
        burn = layerFor(l.burnEnd, 1f)
    }

    /** Ausgabegröße: auf das gewählte Seitenverhältnis beschnitten (bei Hochkant-Video gedreht). */
    fun outputSize(inW: Int, inH: Int): Pair<Int, Int> {
        check(::look.isInitialized) { "setLook() fehlt" }
        val c = crop(inW, inH)
        return even(inW * c[2]) to even(inH * c[3])
    }

    /**
     * Ein Bild zeichnen. [inputFlipY]: true, wenn die Eingangstextur aus einem Bitmap
     * stammt (Zeile 0 oben) – bei Media3-Texturen ist Zeile 0 unten.
     * Die Eingabe ist immer aufrecht (Blickrichtung): Media3 dreht Hochformat-Videos vor den
     * Effekten selbst aufrecht und erst zum Kodieren zurück (Drehung dann als Metadaten).
     *
     * [filmFps]: Bildrate des fertigen Films. ALLE Effekte wechseln genau einmal pro Filmbild –
     * bei 18 fps flackert/zittert/staubt es 18-mal pro Sekunde, bei 60 fps 60-mal.
     * [clipDurationUs]: Länge des Clips (für den Filmriss am Ende); 0 = unbekannt/kein Filmriss.
     */
    fun draw(inputTexId: Int, timeUs: Long, inW: Int, inH: Int, inputFlipY: Boolean, filmFps: Float, clipDurationUs: Long = 0) {
        val fps = filmFps.coerceIn(1f, 120f).toDouble()
        val filmFrame = floor(timeUs / 1_000_000.0 * fps + 0.5).toLong()
        val t = filmFrame / fps   // Zeit auf das Filmbild gerastert
        val (outW, outH) = outputSize(inW, inH)
        val portrait = outH > outW
        val p = program
        p.use()
        p.setSamplerTexIdUniform("uTex", inputTexId, 0)
        p.setFloatsUniform("uCrop", crop(inW, inH))
        p.setFloatUniform("uInputFlip", if (inputFlipY) 1f else 0f)
        p.setFloatUniform("uPortrait", if (portrait) 1f else 0f)
        p.setFloatsUniform("uTexel", floatArrayOf(1f / inW, 1f / inH))

        p.setFloatUniform("uUseLut", if (lutTex != 0) 1f else 0f)
        p.setSamplerTexIdUniform("uLut", if (lutTex != 0) lutTex else inputTexId, 1)
        p.setFloatUniform("uLutMix", look.lutMix)
        p.setFloatUniform("uLutMono", if (lutMono && lutTex != 0) 1f else 0f)
        p.setFloatUniform("uSoften", look.soften * 1.5f)
        p.setFloatUniform("uBright", look.brightness)
        p.setFloatUniform("uContrast", look.contrast)
        p.setFloatUniform("uSharp", look.sharpness)
        p.setFloatUniform("uTemp", look.temperature)
        p.setFloatUniform("uShadows", look.shadows)
        p.setFloatUniform("uHighlights", look.highlights)
        p.setFloatUniform("uSat", look.saturation)
        p.setFloatUniform("uVignette", look.vignette)
        p.setFloatUniform("uAspect", outW / outH.toFloat())
        p.setFloatUniform("uHalation", look.halation)

        p.setFloatUniform("uFlicker", look.flicker * 0.10f * (hash(filmFrame * 7 + 1) - 0.5f))
        val w = look.weave * 0.004f
        p.setFloatsUniform("uWeave", floatArrayOf(
            w * (0.6f * sin(filmFrame * 0.7f) + (hash(filmFrame * 7 + 2) - 0.5f)),
            w * 0.8f * (hash(filmFrame * 7 + 3) - 0.5f),
        ))
        p.setFloatUniform("uZoom", 1f + look.weave * 0.012f)

        // Korn: jedes Filmbild ein anderes Kornbild, zufällig verschoben
        bindLayer("uGrain", 2, grain, grain?.let { (filmFrame % it.tex.size).toInt() } ?: 0, inputTexId)
        p.setFloatUniform("uGrainAmt", if (grain != null) look.grainAmount else 0f)
        p.setFloatUniform("uGrainMean", grain?.stats?.luma ?: 0.5f)
        p.setFloatUniform("uGrainGain", grain?.let { (GRAIN_TARGET_STD / it.stats.std.coerceAtLeast(0.005f)).coerceAtMost(12f) } ?: 1f)
        p.setFloatsUniform("uGrainOffset", floatArrayOf(hash(filmFrame * 7 + 4), hash(filmFrame * 7 + 5)))

        // Staub: wie bei echtem Film trägt jedes Bild seinen eigenen Staub – zufälliges Staubbild,
        // zufällig verschoben und gespiegelt, damit die kurze Schleife nie als Wiederholung auffällt
        overlayLayer("uDust", 3, dust, look.dust, look.dustAmount, dust?.let { (hash(filmFrame * 7 + 11) * it.tex.size).toInt().coerceAtMost(it.tex.size - 1) } ?: 0, inputTexId)
        p.setFloatsUniform("uDustXform", floatArrayOf(
            (hash(filmFrame * 7 + 12) - 0.5f) * 0.08f, (hash(filmFrame * 7 + 13) - 0.5f) * 0.08f,
            if (hash(filmFrame * 7 + 14) > 0.5f) 1f else 0f,
        ))
        // Kratzer: laufen durchgehend weiter (Laufkratzer), zittern pro Filmbild leicht seitlich
        overlayLayer("uScratch", 4, scratch, look.scratch, look.scratchAmount, scratch?.let { (floor(t * it.fps).toLong() % it.tex.size).toInt() } ?: 0, inputTexId)
        p.setFloatUniform("uScratchShift", (hash(filmFrame * 7 + 15) - 0.5f) * 0.004f)

        // Light Leak: nur zu bestimmten Zeiten, weich ein- und ausgeblendet
        // Licht hat keine eigene Bildrate: zwischen den Leak-Einzelbildern weich überblenden,
        // so ist der Leak bei 18 und bei 60 fps gleich flüssig
        val lk = leak
        val lp = leakAt(t)
        val bp = burnAt(t, clipDurationUs / 1_000_000.0)
        val bn = burn
        if (bn != null && bp != null) {
            // Filmriss am Ende: nutzt dieselben Textureinheiten wie der Leak (GLES2 garantiert nur 8)
            p.setSamplerTexIdUniform("uLeak", bn.tex[bp.idx], 5)
            p.setSamplerTexIdUniform("uLeak2", bn.tex[min(bp.idx + 1, bn.tex.size - 1)], 7)
            p.setFloatUniform("uLeakMix", bp.frac)
            p.setFloatUniform("uLeakAmt", 0f)
            p.setFloatUniform("uBurnAmt", bp.env)
        } else if (lk != null && lp != null) {
            p.setFloatUniform("uBurnAmt", 0f)
            p.setSamplerTexIdUniform("uLeak", lk.tex[lp.idx], 5)
            p.setSamplerTexIdUniform("uLeak2", lk.tex[min(lp.idx + 1, lk.tex.size - 1)], 7)
            p.setFloatUniform("uLeakMix", lp.frac)
            p.setFloatUniform("uLeakAmt", look.leakAmount * lp.env)
        } else {
            p.setFloatUniform("uBurnAmt", 0f)
            p.setSamplerTexIdUniform("uLeak", inputTexId, 5)
            p.setSamplerTexIdUniform("uLeak2", inputTexId, 7)
            p.setFloatUniform("uLeakMix", 0f)
            p.setFloatUniform("uLeakAmt", 0f)
        }

        // Rahmen mit Bildfenster (Scan: außen der Rahmen selbst, Projektion: außen schwarz)
        val gate = look.frame?.gate
        val fr = frame
        if (fr != null && gate != null) {
            bindLayer("uFrame", 6, fr, (filmFrame % fr.tex.size).toInt(), inputTexId)
            p.setFloatUniform("uFrameOn", 1f)
            p.setFloatUniform("uFrameScan", if (look.frameMode == FrameMode.SCAN) 1f else 0f)
            p.setFloatsUniform("uFrameGain", frameGain(fr.stats))
            p.setFloatUniform("uFrameBlack", look.frameBlack)
            val ovAspect = maxOf(outW, outH) / minOf(outW, outH).toFloat()
            p.setFloatsUniform("uFrameCrop", frameFit(gate, ovAspect))
            p.setFloatsUniform("uGate", floatArrayOf(gate[0].toFloat(), gate[1].toFloat(), gate[2].toFloat(), gate[3].toFloat()))
            p.setFloatUniform("uGateR", gate[4].toFloat())
        } else {
            p.setSamplerTexIdUniform("uFrame", inputTexId, 6)
            p.setFloatUniform("uFrameOn", 0f)
            p.setFloatUniform("uFrameScan", 0f)
            p.setFloatsUniform("uFrameGain", floatArrayOf(1f, 1f, 1f))
            p.setFloatUniform("uFrameBlack", 0f)
            p.setFloatsUniform("uFrameCrop", floatArrayOf(0f, 1f))
            p.setFloatsUniform("uGate", floatArrayOf(0f, 0f, 1920f, 1080f))
            p.setFloatUniform("uGateR", 0f)
        }
        p.setFloatUniform("uMono", if (look.mono || (lutMono && lutTex != 0)) 1f else 0f)

        p.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        program.delete()
        val all = (lutCache.values + seqCache.values.flatMap { it.tex.toList() }).toIntArray()
        if (all.isNotEmpty()) GLES20.glDeleteTextures(all.size, all, 0)
        lutCache.clear()
        seqCache.clear()
    }

    // ---------------- Hilfsrechnungen ----------------

    private fun bindLayer(name: String, unit: Int, l: Layer?, idx: Int, fallbackTex: Int) {
        program.setSamplerTexIdUniform(name, l?.tex?.get(idx.coerceIn(0, l.tex.size - 1)) ?: fallbackTex, unit)
    }

    private fun overlayLayer(prefix: String, unit: Int, l: Layer?, asset: SequenceAsset?, amount: Float, idx: Int, fallbackTex: Int) {
        bindLayer(prefix, unit, l, idx, fallbackTex)
        val mode = when (asset?.blend) {
            "multiply" -> 1f
            "screen" -> 2f
            "key" -> 3f
            else -> 0f
        }
        program.setFloatUniform("${prefix}Amt", if (l != null) amount else 0f)
        program.setFloatUniform("${prefix}Mode", mode)
        // Multiply: Mitte des Overlays auf Weiß ziehen, sonst dunkelt es alles ab
        val gain = l?.stats?.center?.map { (1f / it.coerceAtLeast(0.02f)).coerceAtMost(8f) }?.toFloatArray()
        program.setFloatsUniform("${prefix}Gain", if (mode == 1f && gain != null) gain else floatArrayOf(1f, 1f, 1f))
        val k = asset?.keyColor ?: 0x00FF00
        program.setFloatsUniform("${prefix}Key", floatArrayOf((k shr 16 and 0xFF) / 255f, (k shr 8 and 0xFF) / 255f, (k and 0xFF) / 255f))
    }

    /** Farbstich-Regler: 0 = jeder Kanal einzeln auf Weiß, 1 = nur Helligkeit angleichen. */
    private fun frameGain(s: Stats): FloatArray {
        val lum = 1f / ((s.center[0] + s.center[1] + s.center[2]) / 3f).coerceAtLeast(0.02f)
        return FloatArray(3) { i ->
            val neutral = 1f / s.center[i].coerceAtLeast(0.02f)
            (neutral * (1 - look.frameTint) + lum * look.frameTint).coerceAtMost(8f)
        }
    }

    /**
     * Ausschnitt des 1920x1080-Rahmens fürs Ausgabeformat (wie frame_fit in filmlook.py):
     * passt das Bildfenster unverzerrt hinein, wird nur beschnitten, sonst leicht gestaucht.
     * Ergebnis: [x0, Breite] als Anteil von 1920.
     */
    private fun frameFit(gate: IntArray, aspect: Float, margin: Int = 40): FloatArray {
        val cw = 1080f * aspect
        val gw = gate[2] - gate[0] + 2 * margin
        return if (gw <= cw) {
            val cx = ((gate[0] + gate[2]) / 2f - cw / 2f).coerceIn(0f, 1920f - min(cw, 1920f))
            floatArrayOf(cx / 1920f, min(cw, 1920f) / 1920f)
        } else {
            val a = max(gate[0] - margin, 0)
            val b = min(gate[2] + margin, 1920)
            floatArrayOf(a / 1920f, (b - a) / 1920f)
        }
    }

    /** Mittiger Ausschnitt der Eingabe fürs Seitenverhältnis: [x0, y0, Breite, Höhe] als Anteile. */
    private fun crop(inW: Int, inH: Int): FloatArray {
        val a = look.aspect ?: return floatArrayOf(0f, 0f, 1f, 1f)
        val target = if (inH > inW) 1f / a else a
        val inA = inW / inH.toFloat()
        return if (inA > target) {
            val w = target / inA
            floatArrayOf((1 - w) / 2, 0f, w, 1f)
        } else {
            val h = inA / target
            floatArrayOf(0f, (1 - h) / 2, 1f, h)
        }
    }

    private fun even(v: Float) = (v.roundToInt() / 2) * 2

    private class LeakPos(val idx: Int, val frac: Float, val env: Float)

    /**
     * Filmriss: läuft über die letzten Sekunden (Länge des Burn-Clips, höchstens 60 % des Clips).
     * env steigt über 60 % der Dauer von 0 auf 1 – das Bild brennt sichtbar durch und ist am Ende ganz weg.
     */
    private fun burnAt(t: Double, dur: Double): LeakPos? {
        val b = burn ?: return null
        if (dur <= 0) return null
        val len = min(b.tex.size / b.fps.toDouble(), dur * 0.6)
        val start = dur - len
        if (t < start) return null
        val local = t - start
        val pos = local * b.fps
        val idx = floor(pos).toInt().coerceIn(0, b.tex.size - 1)
        val x = (local / (len * 0.6)).coerceIn(0.0, 1.0)
        return LeakPos(idx, (pos - floor(pos)).toFloat(), (x * x * (3 - 2 * x)).toFloat())
    }

    /** Wann läuft ein Leak? Deterministisch aus der Zeit, damit Vorschau und Export übereinstimmen. */
    private fun leakAt(t: Double): LeakPos? {
        val l = leak ?: return null
        val len = l.tex.size / l.fps.toDouble()
        val gapBase = 15.0 + (2.0 - 15.0) * look.leakFrequency
        var start = 1.0
        var k = 0L
        while (start + len < t) {
            start += len + gapBase * (0.7 + 0.6 * hash(k * 13 + 9))
            k++
            if (k > 10_000) return null
        }
        if (t < start) return null
        val local = t - start
        val pos = local * l.fps
        val idx = floor(pos).toInt().coerceIn(0, l.tex.size - 1)
        val env = min(1.0, min(local / 0.4, (len - local) / 0.6)).coerceIn(0.0, 1.0).toFloat()
        return LeakPos(idx, (pos - floor(pos)).toFloat(), env)
    }

    private fun hash(n: Long): Float {
        var x = n * -0x61c8864680b583ebL
        x = x xor (x ushr 31)
        x *= -0x40a7b892e31b1a47L
        x = x xor (x ushr 29)
        return ((x ushr 40) and 0xFFFF).toFloat() / 65535f
    }

    // ---------------- Texturen laden ----------------

    class Stats(val luma: Float, val std: Float, val center: FloatArray)

    private fun layer(context: Context, a: SequenceAsset) =
        a.frames.map { loadTexture(context, it) }.let { list ->
            Layer(list.map { it.first }.toIntArray(), a.fps, list.first().second)
        }

    companion object {
        /** Korn-Streuung bei Regler 100 % (Anteil von Weiß). Echtes 8mm-Korn ist grob und kräftig. */
        const val GRAIN_TARGET_STD = 0.09f

        fun loadTexture(context: Context, assetPath: String): Pair<Int, Stats> {
            val bitmap = LutFile.open(context, assetPath).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPremultiplied = false
                })
            } ?: error("Kann $assetPath nicht laden")
            val stats = stats(bitmap)
            val id = uploadTexture(bitmap)
            bitmap.recycle()
            return id to stats
        }

        fun uploadTexture(bitmap: Bitmap): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            // Nicht-Zweierpotenz-Texturen: in GLES2 nur CLAMP_TO_EDGE, Wiederholung macht der Shader
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GlUtil.checkGlError()
            return ids[0]
        }

        private fun stats(b: Bitmap): Stats {
            // Stichprobe: jedes 7. Pixel; Mitte = mittlere 40 % in beide Richtungen
            var sum = 0.0
            var sq = 0.0
            var n = 0
            val c = DoubleArray(3)
            var cn = 0
            for (y in 0 until b.height step 7) for (x in 0 until b.width step 7) {
                val p = b.getPixel(x, y)
                val r = (p shr 16 and 0xFF) / 255.0
                val g = (p shr 8 and 0xFF) / 255.0
                val bl = (p and 0xFF) / 255.0
                val l = 0.299 * r + 0.587 * g + 0.114 * bl
                sum += l; sq += l * l; n++
                if (x > b.width * .3 && x < b.width * .7 && y > b.height * .3 && y < b.height * .7) {
                    c[0] += r; c[1] += g; c[2] += bl; cn++
                }
            }
            if (n == 0) return Stats(0.5f, 0.1f, floatArrayOf(1f, 1f, 1f))
            val mean = sum / n
            val center = if (cn == 0) floatArrayOf(1f, 1f, 1f) else FloatArray(3) { (c[it] / cn).toFloat() }
            return Stats(mean.toFloat(), sqrt((sq / n - mean * mean).coerceAtLeast(0.0)).toFloat(), center)
        }

        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() {
                gl_Position = aFramePosition;
                vUv = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT = """
            precision highp float;
            varying vec2 vUv;

            uniform sampler2D uTex;
            uniform vec4 uCrop;
            uniform float uInputFlip;
            uniform float uPortrait;
            uniform vec2 uTexel;

            uniform sampler2D uLut;
            uniform float uUseLut;
            uniform float uLutMix;
            uniform float uLutMono;
            uniform float uSoften;
            uniform float uBright;
            uniform float uContrast;
            uniform float uSharp;
            uniform float uTemp;
            uniform float uShadows;
            uniform float uHighlights;
            uniform float uSat;
            uniform float uVignette;
            uniform float uAspect;
            uniform float uHalation;
            uniform float uFlicker;
            uniform vec2 uWeave;
            uniform float uZoom;

            uniform sampler2D uGrain;
            uniform float uGrainAmt;
            uniform float uGrainMean;
            uniform float uGrainGain;
            uniform vec2 uGrainOffset;

            uniform sampler2D uDust;
            uniform float uDustAmt;
            uniform float uDustMode;
            uniform vec3 uDustGain;
            uniform vec3 uDustKey;
            uniform vec3 uDustXform;

            uniform sampler2D uScratch;
            uniform float uScratchAmt;
            uniform float uScratchMode;
            uniform vec3 uScratchGain;
            uniform vec3 uScratchKey;
            uniform float uScratchShift;

            uniform sampler2D uLeak;
            uniform sampler2D uLeak2;
            uniform float uLeakMix;
            uniform float uLeakAmt;
            uniform float uBurnAmt;

            uniform sampler2D uFrame;
            uniform float uFrameOn;
            uniform float uFrameScan;
            uniform vec3 uFrameGain;
            uniform float uFrameBlack;
            uniform vec2 uFrameCrop;
            uniform vec4 uGate;
            uniform float uGateR;

            uniform float uMono;

            const float N = 33.0;

            vec3 src(vec2 uv) {
                vec2 s = uCrop.xy + clamp(uv, 0.0, 1.0) * uCrop.zw;
                if (uInputFlip > 0.5) s.y = 1.0 - s.y;
                return texture2D(uTex, s).rgb;
            }

            // 33er-LUT als Streifen N*N x N: Kachel = Blau, x in Kachel = Rot, y = Grün
            vec3 applyLut(vec3 c) {
                float b = c.b * (N - 1.0);
                float b0 = floor(b);
                float b1 = min(b0 + 1.0, N - 1.0);
                vec2 rg = vec2((c.r * (N - 1.0) + 0.5) / (N * N), (c.g * (N - 1.0) + 0.5) / N);
                vec3 l0 = texture2D(uLut, vec2(rg.x + b0 / N, rg.y)).rgb;
                vec3 l1 = texture2D(uLut, vec2(rg.x + b1 / N, rg.y)).rgb;
                return mix(l0, l1, b - b0);
            }

            float ov1(float a, float b) { return a < 0.5 ? 2.0 * a * b : 1.0 - 2.0 * (1.0 - a) * (1.0 - b); }
            vec3 ov3(vec3 a, vec3 b) { return vec3(ov1(a.r, b.r), ov1(a.g, b.g), ov1(a.b, b.b)); }
            vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - b); }

            vec3 blendLayer(vec3 c, vec3 o, float mode, float amt, vec3 gain, vec3 key) {
                vec3 r;
                if (mode < 0.5) r = ov3(c, o);
                else if (mode < 1.5) r = c * clamp(o * gain, 0.0, 1.0);
                else if (mode < 2.5) r = screen(c, o);
                else r = mix(c, o, smoothstep(0.25, 0.45, distance(o, key)));
                return mix(c, r, amt);
            }

            float roundedBox(vec2 p, vec4 box, float r) {
                vec2 c = (box.xy + box.zw) * 0.5;
                vec2 h = (box.zw - box.xy) * 0.5 - vec2(r);
                return length(max(abs(p - c) - h, 0.0)) - r;
            }

            void main() {
                // Overlays (Korn, Staub, Kratzer, Leaks) in Blickrichtung, Zeile 0 oben wie die Bitmaps:
                // Kratzer laufen also auch im Hochformat senkrecht
                vec2 ov = vec2(vUv.x, 1.0 - vUv.y);
                // Rahmen: im Hochformat um 90° gedreht, damit das Bildfenster zum Format passt
                vec2 fov = uPortrait > 0.5 ? vec2(ov.y, 1.0 - ov.x) : ov;

                // Bildstand-Wackeln: der Film bewegt sich, der Rahmen (Bildfenster) nicht
                vec2 uv = (vUv - 0.5) / uZoom + 0.5 + uWeave;

                vec3 c = src(uv);
                if (uSoften > 0.0) {
                    vec2 d = uTexel * uSoften;
                    c = c * 0.4 + 0.15 * (src(uv + vec2(d.x, 0.0)) + src(uv - vec2(d.x, 0.0))
                                        + src(uv + vec2(0.0, d.y)) + src(uv - vec2(0.0, d.y)));
                }
                // Schärfe (Unscharf-Maske) bzw. Unschärfe (zwei Ringe à 6 Abtastpunkte)
                if (uSharp > 0.0) {
                    vec2 d = uTexel * 1.5;
                    vec3 b = (src(uv + vec2(d.x, 0.0)) + src(uv - vec2(d.x, 0.0)) + src(uv + vec2(0.0, d.y)) + src(uv - vec2(0.0, d.y))) * 0.25;
                    c = c + (c - b) * uSharp * 1.8;
                } else if (uSharp < 0.0) {
                    float r = 2.0 + 10.0 * (-uSharp);
                    vec3 acc = c;
                    for (int i = 0; i < 12; i++) {
                        float a = float(i) * 0.5236 + (mod(float(i), 2.0) < 0.5 ? 0.0 : 0.26);
                        float rr = mod(float(i), 2.0) < 0.5 ? r : r * 0.5;
                        acc += src(uv + vec2(cos(a), sin(a)) * uTexel * rr);
                    }
                    c = mix(c, acc / 13.0, min(1.0, -uSharp * 1.5));
                }
                // Bildkorrektur: Helligkeit, Farbtemperatur, Schatten/Lichter, Kontrast, Sättigung
                c *= exp2(uBright * 0.8);
                c *= vec3(1.0 + uTemp * 0.10, 1.0 + uTemp * 0.02, 1.0 - uTemp * 0.12);
                float lum = dot(c, vec3(0.299, 0.587, 0.114));
                c += vec3(uShadows * 0.22 * (1.0 - smoothstep(0.0, 0.55, lum)) + uHighlights * 0.22 * smoothstep(0.45, 1.0, lum));
                c = (c - 0.5) * (1.0 + uContrast * 0.6) + 0.5;
                c = mix(vec3(dot(c, vec3(0.299, 0.587, 0.114))), c, 1.0 + uSat);
                c = clamp(c, 0.0, 1.0);

                if (uUseLut > 0.5) {
                    vec3 base = uLutMono > 0.5 ? vec3(dot(c, vec3(0.299, 0.587, 0.114))) : c;
                    c = mix(base, applyLut(clamp(c, 0.0, 1.0)), uLutMix);
                }

                // Halation: helle Stellen strahlen rötlich über
                if (uHalation > 0.0) {
                    vec3 glow = vec3(0.0);
                    float rr = 0.012;
                    for (int i = 0; i < 8; i++) {
                        float a = float(i) * 0.785398;
                        vec3 s = src(uv + vec2(cos(a), sin(a)) * rr);
                        float l = dot(s, vec3(0.299, 0.587, 0.114));
                        glow += vec3(max(l - 0.72, 0.0) / 0.28);
                    }
                    glow = glow / 8.0 * vec3(1.0, 0.35, 0.12);
                    c = mix(c, screen(c, glow), uHalation);
                }

                // Vignette (Objektiv): Ränder abdunkeln, bezogen auf das Ausgabeformat
                if (uVignette > 0.0) {
                    vec2 vq = (vUv - 0.5) * vec2(uAspect, 1.0);
                    float vd = length(vq) / length(vec2(uAspect, 1.0) * 0.5);
                    c *= 1.0 - uVignette * 0.85 * smoothstep(0.35, 1.0, vd);
                }

                c = clamp(c + uFlicker, 0.0, 1.0);

                if (uGrainAmt > 0.0) {
                    float raw = texture2D(uGrain, fract(ov + uGrainOffset)).r;
                    float g = clamp(0.5 + (raw - uGrainMean) * uGrainGain, 0.0, 1.0);
                    c = mix(c, ov3(c, vec3(g)), uGrainAmt);
                }
                if (uDustAmt > 0.0) {
                    vec2 dv = uDustXform.z > 0.5 ? vec2(1.0 - ov.x, ov.y) : ov;
                    dv = (dv - 0.5) / 1.1 + 0.5 + uDustXform.xy;   // leicht vergrößert, damit Verschieben keine Kante zeigt
                    c = blendLayer(c, texture2D(uDust, dv).rgb, uDustMode, uDustAmt, uDustGain, uDustKey);
                }
                if (uScratchAmt > 0.0) c = blendLayer(c, texture2D(uScratch, ov + vec2(uScratchShift, 0.0)).rgb, uScratchMode, uScratchAmt, uScratchGain, uScratchKey);
                if (uLeakAmt > 0.0) {
                    vec3 lk = mix(texture2D(uLeak, ov).rgb, texture2D(uLeak2, ov).rgb, uLeakMix);
                    c = mix(c, screen(c, lk), uLeakAmt);
                }
                if (uBurnAmt > 0.0) {
                    // Durchbrennen: erst überstrahlt der Brand das Bild, dann ersetzt er es ganz
                    vec3 bu = mix(texture2D(uLeak, ov).rgb, texture2D(uLeak2, ov).rgb, uLeakMix);
                    c = mix(screen(c, bu * uBurnAmt * 1.5), bu, uBurnAmt * uBurnAmt);
                }

                if (uFrameOn > 0.5) {
                    vec2 fuv = vec2(uFrameCrop.x + fov.x * uFrameCrop.y, fov.y);
                    vec3 fr = texture2D(uFrame, fuv).rgb;
                    vec3 fn = clamp((clamp(fr * uFrameGain, 0.0, 1.0) - uFrameBlack) / (1.0 - uFrameBlack), 0.0, 1.0);
                    float d = roundedBox(fuv * vec2(1920.0, 1080.0), uGate, uGateR);
                    float mask = 1.0 - smoothstep(-10.0, 10.0, d);
                    vec3 inner = c * fn;
                    c = uFrameScan > 0.5 ? mix(fr, inner, mask) : inner * mask;
                }

                if (uMono > 0.5) c = vec3(dot(c, vec3(0.299, 0.587, 0.114)));
                gl_FragColor = vec4(c, 1.0);
            }
        """
    }
}
