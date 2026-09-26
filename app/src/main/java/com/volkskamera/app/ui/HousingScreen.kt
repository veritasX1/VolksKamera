package com.volkskamera.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volkskamera.app.ui.theme.ButtonStyle
import com.volkskamera.app.ui.theme.FilmAccent
import com.volkskamera.app.ui.theme.FilmWhite
import com.volkskamera.app.ui.theme.Housing
import com.volkskamera.app.ui.theme.MetalFinish

@Composable
fun HousingScreen(vm: FilmViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val h = vm.housing
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateHousing(h.copy(bgAsset = "", customUri = uri.toString()))
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                .clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("‹ Zurück", color = FilmWhite, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text("Gehäuse", color = FilmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        HousingSection("Material")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // eigenes Bild aus der Galerie
            SwatchBox(selected = h.customUri != null && h.bgAsset.isBlank(),
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                h.customUri?.let { CustomThumb(it) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("＋\nGalerie", color = FilmWhite, fontSize = 12.sp)
                }
            }
        }
        // Material-Texturen (ambientCG, CC0) nach Kategorie
        val textures = remember { com.volkskamera.app.data.HousingTextures.all(context) }
        var kat by remember { mutableStateOf(textures.firstOrNull { it.id == h.bgAsset }?.kategorie ?: "Leder") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.volkskamera.app.data.HousingTextures.kategorien(context).forEach { k -> Pill(k, selected = kat == k, small = true) { kat = k } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            textures.filter { it.kategorie == kat }.forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                    SwatchBox(selected = h.bgAsset == t.id, onClick = { vm.updateHousing(h.copy(bgAsset = t.id, customUri = null)) }) {
                        AssetThumb(com.volkskamera.app.data.HousingTextures.previewPath(t.id))
                    }
                    Text(t.name, color = FilmWhite.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Text("Texturen: ambientCG.com, CC0 (gemeinfrei)", color = FilmWhite.copy(alpha = 0.4f), fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp))

        // ---------- Licht ----------
        HousingSection("Licht")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("Licht folgt der Bewegung", selected = h.light, small = true) { vm.updateHousing(h.copy(light = !h.light)) }
            Spacer(Modifier.width(10.dp))
            Text(if (h.light) "Lampe steht im Raum: Neigen und Schwenken verändern Glanz, Relief und Schatten"
                 else "Licht fest von oben links",
                color = FilmWhite.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stärke", color = FilmWhite, fontSize = 13.sp, modifier = Modifier.width(80.dp))
            androidx.compose.material3.Slider(value = h.lightStrength, onValueChange = { vm.updateHousing(h.copy(lightStrength = it)) },
                valueRange = 0f..1.5f, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = FilmAccent, activeTrackColor = FilmAccent))
            Text("${(h.lightStrength * 100).toInt()} %", color = FilmWhite, fontSize = 12.sp, modifier = Modifier.width(52.dp).padding(start = 8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.volkskamera.app.ui.theme.LightColor.entries.forEach { c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color.White, c.color, c.color.copy(alpha = 0.6f))))
                        .then(if (h.lightColor == c) Modifier.border(3.dp, FilmAccent, CircleShape) else Modifier)
                        .clickable { vm.updateHousing(h.copy(lightColor = c)) })
                    Text(c.label, color = FilmWhite.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        // Spiegelung: Studio oder eigenes Frontkamera-Foto
        Spacer(Modifier.height(10.dp))
        var capturing by remember { mutableStateOf(false) }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val hasEnv = remember(h.ownEnvironment, capturing) { com.volkskamera.app.ui.theme.Environment.file(context).isFile }
        Text("Spiegelung", color = FilmWhite, fontSize = 13.sp)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Studio", selected = !h.ownEnvironment, small = true) { vm.updateHousing(h.copy(ownEnvironment = false)) }
            if (hasEnv) Pill("Eigenes Foto", selected = h.ownEnvironment, small = true) { vm.updateHousing(h.copy(ownEnvironment = true)) }
            Pill(if (capturing) "Aufnahme …" else "📷 Frontkamera-Foto aufnehmen", selected = false, small = true, enabled = !capturing) {
                capturing = true
                captureEnvironment(context, lifecycleOwner) { ok ->
                    capturing = false
                    if (ok) vm.updateHousing(vm.housing.copy(ownEnvironment = true))
                }
            }
        }
        Text("Metall und glatte Flächen spiegeln die Umgebung. Mit dem Frontkamera-Foto spiegelt sich dein Raum im Gehäuse.",
            color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

        HousingSection("Metall")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetalFinish.entries.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(m.brush())
                        .then(if (h.metal == m) Modifier.border(3.dp, FilmAccent, CircleShape) else Modifier)
                        .clickable { vm.updateHousing(h.copy(metal = m)) })
                    Text(m.label, color = FilmWhite.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }

        HousingSection("Schriftzug")
        var titleDraft by remember { mutableStateOf(h.titleText) }
        androidx.compose.material3.OutlinedTextField(
            value = titleDraft, singleLine = true,
            onValueChange = { titleDraft = it.take(24); vm.updateHousing(vm.housing.copy(titleText = titleDraft)) },
            label = { Text("Text (Standard: ${com.volkskamera.app.ui.theme.Housing.DEFAULT_TITLE})") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = FilmWhite, unfocusedTextColor = FilmWhite,
                focusedBorderColor = FilmAccent, focusedLabelColor = FilmAccent, cursorColor = FilmAccent),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.volkskamera.app.ui.theme.TitleFont.entries.forEach { f ->
                Pill(f.label, selected = h.titleFont == f, small = true) { vm.updateHousing(h.copy(titleFont = f)) }
            }
            if (h.titleText != com.volkskamera.app.ui.theme.Housing.DEFAULT_TITLE)
                Pill("Zurücksetzen", selected = false, small = true) {
                    titleDraft = com.volkskamera.app.ui.theme.Housing.DEFAULT_TITLE
                    vm.updateHousing(h.copy(titleText = titleDraft))
                }
        }
        Text("Material der Schrift", color = FilmWhite.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetalFinish.entries.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(m.brush())
                        .then(if (h.titleMetal == m) Modifier.border(3.dp, FilmAccent, CircleShape) else Modifier)
                        .clickable { vm.updateHousing(h.copy(titleMetal = m)) })
                    Text(m.label, color = FilmWhite.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }

        HousingSection("Auslöser")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ButtonStyle.entries.forEach { b ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mod = Modifier.size(52.dp).clip(CircleShape)
                        .then(if (b.metal != null) Modifier.background(b.metal.brush())
                              else Modifier.background(b.plastic ?: Color.Red))
                        .then(if (h.button == b) Modifier.border(3.dp, FilmAccent, CircleShape) else Modifier)
                        .clickable { vm.updateHousing(h.copy(button = b)) }
                    Box(mod)
                    Text(b.label, color = FilmWhite.copy(alpha = 0.8f), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp).widthIn(max = 60.dp))
                }
            }
        }

        HousingSection("App-Symbol")
        var icon by remember { mutableStateOf(com.volkskamera.app.ui.theme.AppIcon.current(context)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.volkskamera.app.ui.theme.AppIcon.entries.forEach { ic ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(ic.preview), ic.label,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                            .then(if (icon == ic) Modifier.border(3.dp, FilmAccent, RoundedCornerShape(18.dp)) else Modifier)
                            .clickable { com.volkskamera.app.ui.theme.AppIcon.set(context, ic); icon = ic })
                    Text(ic.label, color = FilmWhite, fontSize = 12.sp, fontWeight = if (icon == ic) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp))
                    Text(ic.hint, color = FilmWhite.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Text("Der Startbildschirm übernimmt das neue Symbol nach einigen Sekunden.",
            color = FilmWhite.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun HousingSection(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, color = FilmAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable private fun SwatchBox(selected: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(76.dp, 56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))
            .then(if (selected) Modifier.border(3.dp, FilmAccent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

@Composable private fun AssetThumb(asset: String) {
    val context = LocalContext.current
    val bmp = remember(asset) {
        runCatching { context.assets.open(asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }
    bmp?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

@Composable private fun CustomThumb(uri: String) {
    val context = LocalContext.current
    val bmp = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri)).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }.getOrNull()
    }
    bmp?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

/**
 * Ein Foto mit der Frontkamera als Spiegel-Umgebung aufnehmen (ohne Vorschau, kurz gebunden).
 * Die Kamera-Ansicht ist in diesem Moment nicht aktiv, daher gibt es keinen Konflikt.
 */
private fun captureEnvironment(context: android.content.Context, owner: androidx.lifecycle.LifecycleOwner, done: (Boolean) -> Unit) {
    val main = androidx.core.content.ContextCompat.getMainExecutor(context)
    val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = runCatching { future.get() }.getOrNull() ?: return@addListener done(false)
        val capture = androidx.camera.core.ImageCapture.Builder()
            .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        runCatching {
            provider.bindToLifecycle(owner, androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA, capture)
        }.onFailure { done(false); return@addListener }
        // kurz warten, damit die Belichtung sich einpendelt
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            capture.takePicture(main, object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    val rot = image.imageInfo.rotationDegrees
                    val bmp = runCatching { image.toBitmap() }.getOrNull()
                    image.close()
                    provider.unbind(capture)
                    if (bmp == null) { done(false); return }
                    Thread {
                        val ok = runCatching { com.volkskamera.app.ui.theme.Environment.save(context, bmp, rot) }.isSuccess
                        main.execute { done(ok) }
                    }.start()
                }
                override fun onError(e: androidx.camera.core.ImageCaptureException) {
                    provider.unbind(capture); done(false)
                }
            })
        }, 700)
    }, main)
}
