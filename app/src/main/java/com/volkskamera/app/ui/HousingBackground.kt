package com.volkskamera.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.volkskamera.app.data.HousingTextures
import com.volkskamera.app.ui.theme.Housing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gehäuse-Hintergrund. Mitgelieferte PBR-Texturen (ambientCG) werden gekachelt und mit Licht
 * gerechnet: Relief aus der Normalenkarte, Glanz nach Rauheit, Metalle spiegeln in ihrer Farbe.
 * Das Licht kommt aus [com.volkskamera.app.ui.theme.LocalLight] (Lampe im Raum, folgt Neigen und
 * Schwenken, oder fest von oben links); Farbe und Stärke aus dem Gehäuse. Eigene Galeriebilder werden unverändert (ohne Licht) gezeigt.
 */
@Composable
fun HousingBackground(housing: Housing, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val custom = housing.customUri?.takeIf { housing.bgAsset.isBlank() }
    if (custom != null) {
        val bmp by produceState<Bitmap?>(null, custom) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(custom)).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
        }
        bmp?.let { Image(it.asImageBitmap(), null, modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        return
    }
    val tex = HousingTextures.byId(context, housing.bgAsset) ?: HousingTextures.byId(context, Housing.DEFAULT_TEXTURE) ?: return
    val maps by produceState<Pair<Bitmap, Bitmap>?>(null, tex.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { HousingTextures.colorMap(context, tex.id) to HousingTextures.surfaceMap(context, tex.id) }.getOrNull()
        }
    }
    val (color, surface) = maps ?: return

    // Kachelgröße auf dem Bildschirm: eine 1024er Kachel etwa 60 % so groß anzeigen (feineres Korn)
    val scale = 0.6f
    fun tiled(b: Bitmap) = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        .apply { setLocalMatrix(Matrix().apply { setScale(scale, scale) }) }

    val env = remember(housing.ownEnvironment) {
        if (housing.ownEnvironment) com.volkskamera.app.ui.theme.Environment.load(context)
        else com.volkskamera.app.ui.theme.Environment.studio()
    }
    val runtime = remember(color, surface, env) {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(LIGHT_SHADER).apply {
            setInputShader("farbe", tiled(color))
            setInputShader("flaeche", tiled(surface))
            setInputShader("umgebung", BitmapShader(env, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR))
            setFloatUniform("umgGroesse", env.width.toFloat(), env.height.toFloat())
        } else null
    }
    val plain = remember(color) { tiled(color) }
    val holder = com.volkskamera.app.ui.theme.LocalLight.current
    val lc = housing.lightColor

    Box(modifier.fillMaxSize().drawBehind {
        if (runtime != null && Build.VERSION.SDK_INT >= 33) {
            val l = holder.light
            val w = size.width; val h = size.height
            runtime.setFloatUniform("licht", l.x, l.y, l.z)
            runtime.setFloatUniform("lichtFarbe", lc.r, lc.g, lc.b)
            runtime.setFloatUniform("staerke", housing.lightStrength * l.level)
            // Betrachter vor der Bildschirmmitte, etwa eine Armlänge entfernt
            runtime.setFloatUniform("auge", w / 2, h / 2, maxOf(w, h) * 1.4f)
            runtime.setFloatUniform("umgVersatz", l.envX, l.envY)
            runtime.setFloatUniform("metall", tex.metall)
            runtime.setFloatUniform("groesse", w, h)
            drawRect(ShaderBrush(runtime))
        } else {
            drawRect(ShaderBrush(plain))
        }
    })
}

/**
 * AGSL: Blinn-Phong mit gerichtetem Licht (Lampe im Raum) und Blick von der Bildschirmmitte,
 * dazu Spiegelung der Umgebung (Frontkamera-Foto oder Studio) – stark bei Metall und glatten Flächen.
 */
private const val LIGHT_SHADER = """
uniform shader farbe;
uniform shader flaeche;
uniform shader umgebung;
uniform float2 umgGroesse;
uniform float2 umgVersatz;
uniform float3 licht;
uniform float3 lichtFarbe;
uniform float staerke;
uniform float3 auge;
uniform float metall;
uniform float2 groesse;

half4 main(float2 p) {
    half3 c = farbe.eval(p).rgb;
    half3 s = flaeche.eval(p).rgb;
    // Normalenkarte: Grün zeigt nach oben (OpenGL), Bildschirm-y zeigt nach unten
    float3 n = float3(s.r * 2.0 - 1.0, 1.0 - s.g * 2.0, 0.9);
    // Gehäusekanten links und rechts leicht gerundet: die Normale kippt zur Kante hin nach außen
    float band = groesse.x * 0.025;
    float bl = clamp(1.0 - p.x / band, 0.0, 1.0);
    float br = clamp(1.0 - (groesse.x - p.x) / band, 0.0, 1.0);
    float bend = br * br - bl * bl;
    n = normalize(n + float3(bend * 2.2, 0.0, 0.0));
    half kante = half(1.0 - 0.35 * max(bl * bl, br * br));
    half rough = s.b;
    half m = half(metall);
    half3 lf = half3(lichtFarbe);
    half k = half(staerke);
    float3 L = normalize(licht);
    float3 V = normalize(float3(auge.xy - p, auge.z));
    float3 H = normalize(L + V);
    half ndl = half(max(dot(n, L), 0.0));
    half ndh = half(max(dot(n, H), 0.0));
    half shin = mix(140.0, 6.0, rough);
    half spec = pow(ndh, shin) * (1.0 - rough * 0.9);
    // Spiegelung: Reflexionsrichtung -> Umgebungsbild (verschiebt sich mit Neigen/Schwenken)
    float3 R = reflect(-V, n);
    float2 uv = (float2(0.5, 0.5) + R.xy * 0.5 + umgVersatz) * umgGroesse;
    half3 env = umgebung.eval(uv).rgb;
    half glatt = (1.0 - rough) * (1.0 - rough);
    half3 diff = c * (0.30 + 0.85 * ndl * k * lf) * (1.0 - 0.6 * m);
    half3 glanz = mix(lf * 0.9, c * lf, m) * spec * mix(0.45, 1.2, m) * k;
    half3 spiegel = env * mix(half3(0.06), c * 0.9, m) * mix(0.3, 1.0, glatt) * (0.4 + 0.6 * k);
    // Metall insgesamt etwas dunkler (sonst ist poliertes Messing fast weiß), weiche Schulter statt hartem Abschneiden
    half3 col = (diff + glanz + spiegel) * mix(1.0, 0.8, m) * kante;
    col = col / (1.0 + 0.35 * col) * 1.35;
    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""
