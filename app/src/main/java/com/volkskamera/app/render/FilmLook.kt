package com.volkskamera.app.render

import com.volkskamera.app.data.AssetCatalog
import com.volkskamera.app.data.SequenceAsset

enum class FrameMode { SCAN, PROJEKTION }

/** Einzeln schaltbare Stufen. Abgeschaltet behält jede Stufe ihre Einstellung (Asset, Stärke). */
enum class Stage { BILD, VIGNETTE, LUT, KORN, STAUB, KRATZER, LEAK, RAHMEN, HALATION, WEICH, FLACKERN, WACKELN }

/**
 * Alle Einstellungen eines Film-Looks. Jede Stufe ist über ihren Wert 0 bzw. null abschaltbar.
 * Regler laufen 0..1; die Umrechnung in echte Größen steckt in FilmLookRenderer.
 * Reihenfolge wie filmlook.py: Weichzeichnen -> LUT -> Halation -> Flackern -> Wackeln ->
 * Korn -> Staub -> Kratzer -> Light Leak -> Rahmen -> Schwarzweiß.
 */
data class FilmLook(
    // --- Bildkorrektur (−1 … +1, 0 = unverändert); wirkt vor dem Farblook ---
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    /** > 0 schärfer, < 0 unscharf */
    val sharpness: Float = 0f,
    /** > 0 wärmer, < 0 kühler */
    val temperature: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val saturation: Float = 0f,
    /** Vignette 0 … 1 */
    val vignette: Float = 0f,
    val lutFile: String? = null,
    val lutMix: Float = 1f,
    val grain: SequenceAsset? = null,
    val grainAmount: Float = 0.5f,
    val dust: SequenceAsset? = null,
    val dustAmount: Float = 0.7f,
    val scratch: SequenceAsset? = null,
    val scratchAmount: Float = 0.5f,
    val leak: SequenceAsset? = null,
    val leakAmount: Float = 0.8f,
    /** 0 = selten (alle ~15 s), 1 = oft (alle ~2 s) */
    val leakFrequency: Float = 0.4f,
    val frame: SequenceAsset? = null,
    val frameMode: FrameMode = FrameMode.SCAN,
    /** 0 = Rahmen färbt das Bild nicht, 1 = voller Farbstich des Rahmens */
    val frameTint: Float = 0.3f,
    val frameBlack: Float = 0.4f,
    val halation: Float = 0f,
    val soften: Float = 0f,
    /** Diffusion: weicher Schimmer um helle Bildteile (wie ein Diffusionsfilter / Nebel vor dem Objektiv) */
    val diffusion: Float = 0f,
    /** Größe des Schimmers (1 = normal, wächst mit der Analog-Verstärkung) */
    val diffusionSize: Float = 1f,
    /** Scheiben-Unschärfe in Bildpunkten (Regler „Analog“) */
    val blurPx: Float = 0f,
    /** Moiré-Filter 0…1: optischer Tiefpass vor allen anderen Stufen (glättet feinste Linien-/Rastermuster) */
    val antiMoire: Float = 0f,
    val flicker: Float = 0f,
    val weave: Float = 0f,
    val mono: Boolean = false,
    /** Farblook im Sucher andeuten (Farbmatrix-Näherung der LUT, kostet keine Kamera-fps). */
    val finderLook: Boolean = true,
    /** Seitenverhältnis des fertigen Films (4/3 oder 16/9), null = wie das Original */
    val aspect: Float? = 16f / 9f,
    /** Ausgabe-Bildrate, null = Original */
    val targetFps: Float? = 18f,
    // --- Anfang & Ende (standardmäßig aus) ---
    /** Filmriss/Durchbrennen über den letzten Sekunden des Clips; null = aus */
    val burnEnd: SequenceAsset? = null,
    /** Countdown-Vorspann 5 … 1 vor dem Clip */
    val countdown: Boolean = false,
    // --- Ton ---
    val audioMode: AudioMode = AudioMode.ORIGINAL,
    /** Alte Aufnahme: 0 = kaum gealtert, 1 = sehr alt (dumpf, schwankend, knisternd) */
    val aging: Float = 0.5f,
    /** Projektor-Rattern im Filmtakt dazumischen */
    val projector: Boolean = false,
    val projectorVolume: Float = 0.4f,
    val projectorModel: ProjectorModel = ProjectorModel.HEIMKINO,
    /** 0 = Metall, 1 = Plastik */
    val projectorMaterial: Float = 0.3f,
    /** −1 = dumpf, +1 = klar */
    val projectorTone: Float = 0f,
    /** true = Anschläge im Takt der Film-fps, false = [projectorSpeed] */
    val projectorSyncFps: Boolean = true,
    /** Anschläge pro Sekunde, wenn nicht an die fps gebunden */
    val projectorSpeed: Float = 18f,
    // --- Hintergrundgeräusche ---
    val bgNoiseOn: Boolean = false,
    val bgNoiseType: NoiseType = NoiseType.ROSA,
    val bgNoiseLevel: Float = 0.2f,
    val bgHumOn: Boolean = false,
    val bgHumType: HumType = HumType.NETZ,
    /** 20 Hz (Wummern) … 8000 Hz (Piepen) */
    val bgHumFreq: Float = 50f,
    val bgHumLevel: Float = 0.2f,
    // --- Knacken (alte Filmkopie) ---
    val crackleOn: Boolean = false,
    val crackleAmount: Float = 0.5f,
    val crackleDensity: Float = 0.4f,
    // --- Mikrofon / Aufnahmekette der Zeit; null = keine ---
    val mic: MicProfile? = null,
    /** Verzerrung 0..1 (Vorgabe aus dem Profil) */
    val micDrive: Float = 0.3f,
    /** −1 = enger, 0 = wie das Profil, +1 = weiter */
    val micBandwidth: Float = 0f,
    /** Eigenrauschen 0..1 */
    val micNoise: Float = 0.2f,
    /** Aussteuerungsautomatik (Pumpen) 0..1 */
    val micAgc: Float = 0f,
    /** Rauschsperre gegen das Eigenrauschen des Handymikrofons 0..1 */
    val micGate: Float = 0.3f,
    // --- Projektor-Aufnahme statt Klangerzeuger (ID aus SoundLibrary); null = Modell ---
    val projectorRecording: String? = null,
    // --- Effekt-Taste und Ambient-Regler in der Kamera (IDs aus SoundLibrary) ---
    val fxSound: String? = null,
    val fxVolume: Float = 0.8f,
    val fxChain: Boolean = false,
    val ambA: String? = null,
    val ambALevel: Float = 0.5f,
    val ambAChain: Boolean = false,
    val ambB: String? = null,
    val ambBLevel: Float = 0.5f,
    val ambBChain: Boolean = false,
    /** abgeschaltete Stufen */
    val off: Set<Stage> = emptySet(),
) {
    fun isOn(s: Stage) = s !in off
    val hasBackground get() = bgNoiseOn || bgHumOn || crackleOn
    /** Mikrofonprofil wählen: Regler auf die Vorgaben des Profils */
    fun withMic(p: MicProfile?) = if (p == null) copy(mic = null)
        else copy(mic = p, micDrive = p.drive, micNoise = p.noise, micAgc = p.agc, micBandwidth = 0f)
    /** Ton- und Aufnahmeeinstellungen (Mikrofon, Geräusche, Bildrate, Format) aus [o] übernehmen. */
    fun withAudioFrom(o: FilmLook) = copy(
        aspect = o.aspect, targetFps = o.targetFps, audioMode = o.audioMode, aging = o.aging,
        projector = o.projector, projectorVolume = o.projectorVolume, projectorModel = o.projectorModel,
        projectorMaterial = o.projectorMaterial, projectorTone = o.projectorTone, projectorSyncFps = o.projectorSyncFps,
        projectorSpeed = o.projectorSpeed, projectorRecording = o.projectorRecording,
        bgNoiseOn = o.bgNoiseOn, bgNoiseType = o.bgNoiseType, bgNoiseLevel = o.bgNoiseLevel,
        bgHumOn = o.bgHumOn, bgHumType = o.bgHumType, bgHumFreq = o.bgHumFreq, bgHumLevel = o.bgHumLevel,
        crackleOn = o.crackleOn, crackleAmount = o.crackleAmount, crackleDensity = o.crackleDensity,
        mic = o.mic, micDrive = o.micDrive, micBandwidth = o.micBandwidth, micNoise = o.micNoise, micAgc = o.micAgc,
        micGate = o.micGate,
        fxSound = o.fxSound, fxVolume = o.fxVolume, fxChain = o.fxChain,
        ambA = o.ambA, ambALevel = o.ambALevel, ambAChain = o.ambAChain,
        ambB = o.ambB, ambBLevel = o.ambBLevel, ambBChain = o.ambBChain,
    )

    fun toggle(s: Stage) = copy(off = if (s in off) off - s else off + s)

    /** Der Look, wie er tatsächlich gerechnet wird: abgeschaltete Stufen sind leer bzw. 0. */
    val hasAdjustments get() = listOf(brightness, contrast, sharpness, temperature, shadows, highlights, saturation).any { it != 0f }

    fun resetAdjustments() = copy(brightness = 0f, contrast = 0f, sharpness = 0f, temperature = 0f,
        shadows = 0f, highlights = 0f, saturation = 0f)

    fun effective(): FilmLook = (if (isOn(Stage.BILD)) this else resetAdjustments()).copy(
        vignette = if (isOn(Stage.VIGNETTE)) vignette else 0f,
        lutFile = lutFile.takeIf { isOn(Stage.LUT) },
        grain = grain.takeIf { isOn(Stage.KORN) },
        dust = dust.takeIf { isOn(Stage.STAUB) },
        scratch = scratch.takeIf { isOn(Stage.KRATZER) },
        leak = leak.takeIf { isOn(Stage.LEAK) },
        frame = frame.takeIf { isOn(Stage.RAHMEN) },
        halation = if (isOn(Stage.HALATION)) halation else 0f,
        soften = if (isOn(Stage.WEICH)) soften else 0f,
        diffusion = if (isOn(Stage.WEICH)) diffusion else 0f,
        blurPx = if (isOn(Stage.WEICH)) blurPx else 0f,
        flicker = if (isOn(Stage.FLACKERN)) flicker else 0f,
        weave = if (isOn(Stage.WACKELN)) weave else 0f,
    )
}

/** Die drei Looks aus der PC-Vorschau (filmlook.py), auf die App-Regler übertragen. */
object Presets {
    const val AUS = "Aus"
    val names = listOf(AUS, "Kodachrome", "Expired", "Silent Film")

    fun build(name: String, c: AssetCatalog, base0: FilmLook): FilmLook {
        // benannte Presets schalten alle Stufen ein; "Aus" schaltet alles ab und behält die Auswahl
        val base = base0.copy(off = emptySet())
        return when (name) {
        AUS -> base0.copy(
            off = Stage.entries.toSet(), mono = false, burnEnd = null, countdown = false,
            audioMode = AudioMode.ORIGINAL, projector = false,
            bgNoiseOn = false, bgHumOn = false, crackleOn = false, mic = null,
        )
        "Kodachrome" -> base.copy(
            lutFile = c.lut("35_vintage_luts_vintage20")?.file, lutMix = 0.85f,
            grain = c.find("filmgrain_4kdci_8mm_24fps"), grainAmount = 0.45f,
            dust = c.find("overlay_04"), dustAmount = 0.8f,
            scratch = c.find("white_scratches_05"), scratchAmount = 0.5f,
            leak = c.find("leak_strahl_orange"), leakAmount = 0.8f, leakFrequency = 0.35f,
            frame = c.find("8mm_frame_01"), frameMode = FrameMode.SCAN, frameTint = 0.3f, frameBlack = 0.4f,
            halation = 0.45f, soften = 0.35f, flicker = 0.35f, weave = 0.4f, mono = false,
            audioMode = AudioMode.ORIGINAL, projector = true, projectorVolume = 0.3f,
        )
        "Expired" -> base.copy(
            lutFile = c.lut("rs_35_free_luts_paladin_1875")?.file, lutMix = 1f,
            grain = c.find("filmgrain_4kdci_8mm_24fps_heavy"), grainAmount = 0.6f,
            dust = c.find("overlay_05"), dustAmount = 1f,
            scratch = c.find("white_scratches_12"), scratchAmount = 0.6f,
            leak = c.find("280977_medium"), leakAmount = 0.7f, leakFrequency = 0.5f,
            frame = c.find("8mm_frame_08"), frameMode = FrameMode.SCAN, frameTint = 0.6f, frameBlack = 0.5f,
            halation = 0.35f, soften = 0.5f, flicker = 0.6f, weave = 0.6f, mono = false,
            audioMode = AudioMode.ALT, aging = 0.5f, projector = true, projectorVolume = 0.3f,
        )
        "Silent Film" -> base.copy(
            lutFile = c.lut("cinecolor_monochrome_film_cube_monochrome_film_01")?.file, lutMix = 1f,
            grain = c.find("filmgrain_4kdci_16mm_24fps_heavy"), grainAmount = 0.6f,
            dust = c.find("overlay_03"), dustAmount = 1f,
            scratch = c.find("white_scratches_07"), scratchAmount = 0.7f,
            leak = null,
            frame = c.find("8mm_frame_01"), frameMode = FrameMode.PROJEKTION, frameTint = 0f, frameBlack = 0.55f,
            halation = 0f, soften = 0.4f, flicker = 0.8f, weave = 0.7f, mono = true,
            audioMode = AudioMode.STUMM, projector = true, projectorVolume = 0.6f,
        )
        else -> base
        }
    }
}

/**
 * Regler „Analog“: nimmt dem Handybild das Digital-Knackige – echte Scheiben-Unschärfe statt Nachschärfung,
 * Schimmer um Lichter und Lichthof. [a] = Stärke 0…1, [factor] = Verstärkung (1…100), beides multipliziert.
 * Wackeln, Flackern und Vignette sind eigene Regler (0…1). [filmHalation]: der Film selbst hat einen Lichthof.
 */
fun FilmLook.withAnalog(a: Float, factor: Float, filmHalation: Boolean, weave: Float, flicker: Float, vignette: Float): FilmLook {
    val s = a.coerceIn(0f, 1f) * factor.coerceIn(1f, 100f)
    return copy(
        soften = 0f,
        sharpness = 0f,
        blurPx = 4f * kotlin.math.sqrt(s),                          // 1× → 4 px, 10× → 13 px, 100× → 40 px
        diffusion = (0.55f * s).coerceAtMost(1f),
        diffusionSize = 1f + 0.6f * kotlin.math.ln(maxOf(1f, 0.55f * s)),
        halation = maxOf(if (filmHalation) 0.5f else 0f, (0.35f * s).coerceAtMost(1f)),
        weave = weave, flicker = flicker, vignette = vignette,
    )
}
