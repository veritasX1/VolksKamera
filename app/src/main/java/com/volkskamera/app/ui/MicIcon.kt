package com.volkskamera.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.volkskamera.app.render.MicShape

/**
 * Mikrofon-Icons als flächige Silhouetten (Stil der Vorlage: Kapsel im Bügel auf Fuß,
 * Handmikro mit Kabel, geschlitzter Korb …). Koordinaten normiert auf 0..1.
 */
@Composable
fun MicIcon(shape: MicShape, color: Color, modifier: Modifier, cut: Color = Color.Black) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2; val oy = (size.height - s) / 2
        fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)
        fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color = color) =
            drawRoundRect(c, o(x, y), sz(w, h), CornerRadius(r * s, r * s))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color = color) =
            drawLine(c, o(x1, y1), o(x2, y2), w * s, StrokeCap.Round)
        /** Bügel (U) + Stiel + Fuß */
        fun stand(top: Float, yokeW: Float, yokeTop: Float) {
            drawArc(color, 0f, 180f, false, o(0.5f - yokeW / 2, yokeTop), sz(yokeW, (top - yokeTop) * 2),
                style = Stroke(0.06f * s, cap = StrokeCap.Round))
            line(0.5f, top + (top - yokeTop) * 0.98f, 0.5f, 0.86f, 0.06f)
            rr(0.30f, 0.84f, 0.40f, 0.07f, 0.03f)
        }
        when (shape) {
            MicShape.CAPSULE -> {   // Vorlage 1: Kapsel im Bügel mit Knöpfen
                stand(0.42f, 0.56f, 0.34f)
                rr(0.33f, 0.08f, 0.34f, 0.52f, 0.17f)
                drawCircle(color, 0.045f * s, o(0.22f, 0.40f)); drawCircle(color, 0.045f * s, o(0.78f, 0.40f))
                line(0.35f, 0.36f, 0.65f, 0.36f, 0.025f, cut)
            }
            MicShape.GRILL -> {     // Vorlage 3: Korb mit Querschlitzen
                stand(0.44f, 0.56f, 0.38f)
                rr(0.33f, 0.06f, 0.34f, 0.54f, 0.17f)
                for (i in 0..4) { val y = 0.17f + i * 0.07f; line(0.36f, y, 0.47f, y, 0.03f, cut); line(0.53f, y, 0.64f, y, 0.03f, cut) }
            }
            MicShape.SLIM -> {      // Vorlage 4: schlanke Kapsel mit Längsschlitzen
                stand(0.44f, 0.46f, 0.40f)
                rr(0.38f, 0.06f, 0.24f, 0.56f, 0.12f)
                for (i in 0..3) { val x = 0.425f + i * 0.05f; line(x, 0.12f, x, 0.26f, 0.022f, cut) }
                line(0.40f, 0.34f, 0.60f, 0.34f, 0.03f, cut)
                for (i in 0..3) { val x = 0.425f + i * 0.05f; line(x, 0.42f, x, 0.54f, 0.022f, cut) }
            }
            MicShape.HANDHELD -> {  // Vorlage 2: Handmikro schräg mit Kabel
                drawCircle(color, 0.13f * s, o(0.66f, 0.24f))
                line(0.56f, 0.20f, 0.73f, 0.34f, 0.025f, cut)
                val body = Path().apply {
                    moveTo(ox + 0.57f * s, oy + 0.32f * s); lineTo(ox + 0.68f * s, oy + 0.40f * s)
                    lineTo(ox + 0.44f * s, oy + 0.74f * s); lineTo(ox + 0.38f * s, oy + 0.70f * s); close()
                }
                drawPath(body, color)
                drawCircle(cut, 0.02f * s, o(0.52f, 0.53f))
                val cable = Path().apply {
                    moveTo(ox + 0.41f * s, oy + 0.73f * s)
                    cubicTo(ox + 0.36f * s, oy + 0.84f * s, ox + 0.10f * s, oy + 0.90f * s, ox + 0.14f * s, oy + 0.70f * s)
                    cubicTo(ox + 0.18f * s, oy + 0.56f * s, ox + 0.32f * s, oy + 0.62f * s, ox + 0.30f * s, oy + 0.74f * s)
                }
                drawPath(cable, color, style = Stroke(0.025f * s, cap = StrokeCap.Round))
            }
            MicShape.ELVIS -> {     // Shure 55: breiter Korb, gerippt
                rr(0.24f, 0.10f, 0.52f, 0.46f, 0.20f)
                for (i in 0..5) { val x = 0.31f + i * 0.076f; line(x, 0.16f, x, 0.50f, 0.022f, cut) }
                line(0.5f, 0.56f, 0.5f, 0.86f, 0.07f)
                rr(0.30f, 0.84f, 0.40f, 0.07f, 0.03f)
            }
            MicShape.RIBBON -> {    // RCA 44: Pille mit Bügel
                stand(0.46f, 0.62f, 0.40f)
                rr(0.30f, 0.06f, 0.40f, 0.58f, 0.20f)
                line(0.30f, 0.35f, 0.70f, 0.35f, 0.04f, cut)
                for (i in 0..2) { val y = 0.16f + i * 0.06f; line(0.38f, y, 0.62f, y, 0.02f, cut) }
            }
            MicShape.BOTTLE -> {    // „Flasche“: langer Zylinder mit Kopf
                rr(0.40f, 0.06f, 0.20f, 0.20f, 0.10f)
                rr(0.39f, 0.22f, 0.22f, 0.54f, 0.04f)
                line(0.39f, 0.30f, 0.61f, 0.30f, 0.02f, cut)
                rr(0.30f, 0.84f, 0.40f, 0.07f, 0.03f); line(0.5f, 0.76f, 0.5f, 0.86f, 0.06f)
            }
            MicShape.CARBON -> {    // Ring an Federn aufgehängte Scheibe
                drawCircle(color, 0.30f * s, o(0.5f, 0.42f), style = Stroke(0.05f * s))
                drawCircle(color, 0.17f * s, o(0.5f, 0.42f))
                for (a in listOf(0.0, 90.0, 180.0, 270.0)) {
                    val r = Math.toRadians(a); val c = kotlin.math.cos(r).toFloat(); val sn = kotlin.math.sin(r).toFloat()
                    line(0.5f + c * 0.17f, 0.42f + sn * 0.17f, 0.5f + c * 0.28f, 0.42f + sn * 0.28f, 0.02f)
                }
                line(0.5f, 0.72f, 0.5f, 0.86f, 0.06f); rr(0.30f, 0.84f, 0.40f, 0.07f, 0.03f)
            }
            MicShape.HORN -> {      // Schalltrichter
                val p = Path().apply {
                    moveTo(ox + 0.08f * s, oy + 0.12f * s)
                    quadraticBezierTo(ox + 0.45f * s, oy + 0.40f * s, ox + 0.72f * s, oy + 0.44f * s)
                    lineTo(ox + 0.72f * s, oy + 0.54f * s)
                    quadraticBezierTo(ox + 0.45f * s, oy + 0.58f * s, ox + 0.08f * s, oy + 0.86f * s); close()
                }
                drawPath(p, color)
                rr(0.72f, 0.40f, 0.18f, 0.18f, 0.04f)
                drawOval(cut, o(0.05f, 0.14f), sz(0.07f, 0.70f))
            }
            MicShape.SHOTGUN -> {   // Richtrohr
                rr(0.08f, 0.42f, 0.84f, 0.12f, 0.06f)
                for (i in 0..5) { val x = 0.16f + i * 0.08f; line(x, 0.45f, x + 0.03f, 0.45f, 0.02f, cut) }
                rr(0.60f, 0.54f, 0.06f, 0.14f, 0.02f)
            }
            MicShape.LAVALIER -> {  // Ansteckmikro mit Klammer
                rr(0.40f, 0.14f, 0.20f, 0.30f, 0.10f)
                rr(0.42f, 0.44f, 0.16f, 0.10f, 0.02f)
                line(0.5f, 0.54f, 0.5f, 0.64f, 0.025f)
                val c = Path().apply {
                    moveTo(ox + 0.5f * s, oy + 0.64f * s)
                    cubicTo(ox + 0.5f * s, oy + 0.90f * s, ox + 0.85f * s, oy + 0.70f * s, ox + 0.80f * s, oy + 0.92f * s)
                }
                drawPath(c, color, style = Stroke(0.025f * s, cap = StrokeCap.Round))
            }
            MicShape.RADIO -> {     // Radio mit Lautsprecher
                rr(0.10f, 0.26f, 0.80f, 0.56f, 0.08f)
                drawCircle(cut, 0.17f * s, o(0.36f, 0.54f))
                drawCircle(color, 0.12f * s, o(0.36f, 0.54f), style = Stroke(0.02f * s))
                drawCircle(cut, 0.05f * s, o(0.72f, 0.44f)); drawCircle(cut, 0.05f * s, o(0.72f, 0.64f))
                line(0.30f, 0.26f, 0.62f, 0.10f, 0.02f)
            }
            MicShape.TAPE -> {      // Spulentonband
                rr(0.08f, 0.52f, 0.84f, 0.32f, 0.04f)
                drawCircle(color, 0.19f * s, o(0.30f, 0.34f)); drawCircle(color, 0.19f * s, o(0.70f, 0.34f))
                for (x in listOf(0.30f, 0.70f)) {
                    drawCircle(cut, 0.05f * s, o(x, 0.34f))
                    for (i in 0..2) {
                        val r = Math.toRadians(90.0 + i * 120.0)
                        drawCircle(cut, 0.035f * s, o(x + kotlin.math.cos(r).toFloat() * 0.11f, 0.34f + kotlin.math.sin(r).toFloat() * 0.11f))
                    }
                }
                rr(0.36f, 0.62f, 0.28f, 0.10f, 0.02f, cut)
            }
            MicShape.CASSETTE -> {  // Kassette
                rr(0.08f, 0.24f, 0.84f, 0.54f, 0.05f)
                rr(0.20f, 0.34f, 0.60f, 0.22f, 0.04f, cut)
                drawCircle(color, 0.06f * s, o(0.34f, 0.45f)); drawCircle(color, 0.06f * s, o(0.66f, 0.45f))
                val p = Path().apply {
                    moveTo(ox + 0.22f * s, oy + 0.78f * s); lineTo(ox + 0.28f * s, oy + 0.64f * s)
                    lineTo(ox + 0.72f * s, oy + 0.64f * s); lineTo(ox + 0.78f * s, oy + 0.78f * s); close()
                }
                drawPath(p, cut)
            }
            MicShape.CAMERA -> {    // Filmkamera mit Spulen
                drawCircle(color, 0.13f * s, o(0.32f, 0.24f)); drawCircle(color, 0.13f * s, o(0.60f, 0.24f))
                drawCircle(cut, 0.04f * s, o(0.32f, 0.24f)); drawCircle(cut, 0.04f * s, o(0.60f, 0.24f))
                rr(0.14f, 0.40f, 0.58f, 0.34f, 0.04f)
                val lens = Path().apply {
                    moveTo(ox + 0.72f * s, oy + 0.50f * s); lineTo(ox + 0.92f * s, oy + 0.42f * s)
                    lineTo(ox + 0.92f * s, oy + 0.72f * s); lineTo(ox + 0.72f * s, oy + 0.64f * s); close()
                }
                drawPath(lens, color)
                rr(0.30f, 0.74f, 0.10f, 0.14f, 0.02f)
            }
            MicShape.PHONE -> {     // Telefonhörer / Funkgerät
                val p = Path().apply {
                    moveTo(ox + 0.20f * s, oy + 0.18f * s)
                    quadraticBezierTo(ox + 0.5f * s, oy + 0.02f * s, ox + 0.80f * s, oy + 0.18f * s)
                    lineTo(ox + 0.72f * s, oy + 0.32f * s)
                    quadraticBezierTo(ox + 0.5f * s, oy + 0.22f * s, ox + 0.28f * s, oy + 0.32f * s); close()
                }
                drawPath(p, color)
                rr(0.12f, 0.16f, 0.20f, 0.14f, 0.05f); rr(0.68f, 0.16f, 0.20f, 0.14f, 0.05f)
                rr(0.22f, 0.44f, 0.56f, 0.42f, 0.08f)
                for (r in 0..2) for (c in 0..2) drawCircle(cut, 0.03f * s, o(0.38f + c * 0.12f, 0.54f + r * 0.1f))
            }
        }
    }
}

