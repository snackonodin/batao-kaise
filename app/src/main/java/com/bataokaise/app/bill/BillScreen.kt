package com.bataokaise.app.bill

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bataokaise.app.BataoApp
import com.bataokaise.app.Route
import com.bataokaise.app.ui.BigButton
import com.bataokaise.app.ui.C
import com.bataokaise.app.ui.DemoChip
import com.bataokaise.app.ui.ScreenHeader
import com.bataokaise.app.voice.Speaker
import kotlinx.coroutines.launch

private sealed class BillStage {
    data object Choose : BillStage()
    data object Camera : BillStage()
    data object Reading : BillStage()
    data class Result(val bmp: Bitmap, val r: BillResult, val demo: Boolean) : BillStage()
    data class Error(val msg: String) : BillStage()
}

@Composable
fun BillScreen() {
    val app = BataoApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<BillStage>(BillStage.Choose) }

    fun process(bmp: Bitmap, demo: Boolean) {
        stage = BillStage.Reading
        app.assistant.say("Bill padh raha hoon.")
        scope.launch {
            when (val out = BillOcr.read(bmp)) {
                is BillOcr.Out.Ok -> {
                    val r = BillParser.parse(out.lines, bmp.width, bmp.height)
                    stage = BillStage.Result(bmp, r, demo)
                    readAloud(r)
                }
                is BillOcr.Out.Err -> {
                    val m = if (out.modelMissing) "Padhne wala model download ho raha hai. Thodi der baad koshish kariye."
                    else "Bill padh nahi paaya. Roshni mein seedhi photo lijiye."
                    stage = BillStage.Error(m); app.assistant.say(m)
                }
            }
        }
    }

    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) stage = BillStage.Camera else app.assistant.say("Camera ki ijazat nahi. Demo bill chuniye.")
    }

    ScreenHeader("Bill padho", onBack = { app.nav.go(Route.HOME) }) { if (app.settings.demoMode) DemoChip() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val s = stage) {
            BillStage.Choose -> {
                Text("Bill ki photo lijiye. Main padh kar sunaunga.", style = MaterialTheme.typography.headlineSmall)
                BigButton("Photo lijiye", Modifier.fillMaxWidth(), icon = "📷") {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) stage = BillStage.Camera
                    else camPerm.launch(Manifest.permission.CAMERA)
                }
                BigButton("Demo bill (gallery)", Modifier.fillMaxWidth(), icon = "🧾", secondary = true) {
                    val bmp = runCatching { ctx.assets.open("sample_bill.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                    if (bmp == null) { stage = BillStage.Error("Demo bill nahi mila."); app.assistant.say("Demo bill nahi mila.") }
                    else process(bmp, demo = true)
                }
                Text("Photo phone mein hi padhi jaati hai. Kahin nahi bheji jaati.", style = MaterialTheme.typography.bodySmall)
            }
            BillStage.Camera -> CameraCapture(onPhoto = { process(it, demo = false) }, onCancel = { stage = BillStage.Choose })
            BillStage.Reading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = C.Green)
                    Text("Padh raha hoon…", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
            is BillStage.Result -> {
                BillWithBoxes(s.bmp, s.r) { f -> speakField(f, s.r) }
                Text("Kisi bhi dabbe ko dabaiye, main phir se padhunga.", style = MaterialTheme.typography.bodyMedium, color = C.Muted)
                FieldKind.entries.forEach { k ->
                    val f = s.r[k]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).padding(2.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(if (f?.readable == true) Color(k.color) else Color.Gray) } }
                        Text("  ${k.label}: ", style = MaterialTheme.typography.titleMedium)
                        Text(display(f), style = MaterialTheme.typography.titleLarge)
                    }
                }
                BigButton("Bharne ka tarika bataiye", Modifier.fillMaxWidth(), icon = "👉") { offerPay(s.r) }
                BigButton("Nayi photo", Modifier.fillMaxWidth(), secondary = true) { stage = BillStage.Choose }
            }
            is BillStage.Error -> {
                Text(s.msg, style = MaterialTheme.typography.headlineSmall)
                BigButton("Phir se", Modifier.fillMaxWidth()) { stage = BillStage.Choose }
            }
        }
    }
}

private fun display(f: BillField?): String = when {
    f?.value == null -> "Saaf nahi"
    f.kind == FieldKind.ACCOUNT -> f.value.chunked(4).joinToString(" ")
    f.kind == FieldKind.AMOUNT -> "₹" + f.value
    else -> f.value
}

private fun line(f: BillField, r: BillResult): String {
    val pos = f.box?.let { BillParser.position(it, r.width, r.height) } ?: ""
    val v = f.value ?: return "${f.kind.label} saaf nahi dikh raha."
    return when (f.kind) {
        FieldKind.ACCOUNT -> "$pos aapka account number hai: ${Speaker.digits(v)}."
        FieldKind.AMOUNT -> "$pos rakam hai: ${v.replace(",", "")} rupaye."
        FieldKind.DUE -> "$pos aakhri tareekh hai: ${BillParser.spokenDate(v)}."
    }.trim()
}

private fun speakField(f: BillField, r: BillResult) = BataoApp.instance.assistant.say(line(f, r))

/** Read each field in turn, then offer to guide the payment. */
private fun readAloud(r: BillResult) {
    val a = BataoApp.instance.assistant
    val lines = FieldKind.entries.mapNotNull { r[it] }.map { line(it, r) }
    fun next(i: Int) {
        if (i >= lines.size) { offerPay(r); return }
        a.say(lines[i]) { next(i + 1) }
    }
    next(0)
}

private fun offerPay(r: BillResult) {
    val app = BataoApp.instance
    val a = app.assistant
    a.askYesNo("Bharne ka tarika bataun?", onYes = {
        val acct = r.account
        if (acct == null) a.say("Account number saaf nahi tha. Bill se dekh kar likhiye.") { app.tasks.startBillJourney("", r.amount ?: 0) }
        else app.tasks.startBillJourney(acct, r.amount ?: 0)
    }, onNo = { a.say("Theek hai. Jab chahein, mic dabaiye.") })
}

@Composable
private fun BillWithBoxes(bmp: Bitmap, r: BillResult, onTap: (BillField) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wPx = with(density) { maxWidth.toPx() }
        val scale = wPx / bmp.width
        val hDp = with(density) { (bmp.height * scale).toDp() }
        val fields = r.fields.values.filter { it.box != null }
        Box(Modifier.fillMaxWidth().height(hDp).pointerInput(r) {
            detectTapGestures { p ->
                fields.firstOrNull { f -> val b = f.box!!; p.x in b[0] * scale..b[2] * scale && p.y in b[1] * scale..b[3] * scale }?.let(onTap)
            }
        }) {
            Image(bmp.asImageBitmap(), "Bill ki photo", Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
            Canvas(Modifier.fillMaxSize()) {
                val label = android.graphics.Paint().apply { textSize = 15.dp.toPx(); isFakeBoldText = true; color = android.graphics.Color.WHITE }
                fields.forEach { f ->
                    val b = f.box!!
                    val col = if (f.readable) Color(f.kind.color) else Color.Gray
                    val tl = Offset(b[0] * scale - 6, b[1] * scale - 6)
                    val sz = Size((b[2] - b[0]) * scale + 12, (b[3] - b[1]) * scale + 12)
                    drawRect(col.copy(alpha = 0.15f), tl, sz)
                    drawRect(col, tl, sz, style = Stroke(3.dp.toPx()))
                    val text = if (f.readable) f.kind.label else "${f.kind.label}: Saaf nahi"
                    val tw = label.measureText(text) + 16
                    val ty = (tl.y - 22.dp.toPx()).coerceAtLeast(0f)
                    drawRect(col, Offset(tl.x, ty), Size(tw, 22.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(text, tl.x + 8, ty + 16.dp.toPx(), label)
                }
            }
        }
    }
}

@Composable
private fun CameraCapture(onPhoto: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    Box(Modifier.fillMaxWidth().height(460.dp)) {
        AndroidView(factory = { c ->
            PreviewView(c).also { pv ->
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }
                }, ContextCompat.getMainExecutor(c))
            }
        }, modifier = Modifier.fillMaxSize())
    }
    Text("Poora bill dabbe mein laiye.", style = MaterialTheme.typography.titleLarge)
    BigButton("Photo khinchiye", Modifier.fillMaxWidth(), icon = "📸") {
        capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = image.toBitmap().rotated(image.imageInfo.rotationDegrees).downscaled(1600)
                image.close()
                onPhoto(bmp)
            }
            override fun onError(e: ImageCaptureException) { BataoApp.instance.assistant.say("Photo nahi khinchi. Phir se dabaiye.") }
        })
    }
    BigButton("Wapas", Modifier.fillMaxWidth(), secondary = true, onClick = onCancel)
}

private fun Bitmap.rotated(deg: Int): Bitmap = if (deg == 0) this
else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(deg.toFloat()) }, true)

private fun Bitmap.downscaled(max: Int): Bitmap {
    val m = maxOf(width, height); if (m <= max) return this
    val s = max.toFloat() / m
    return Bitmap.createScaledBitmap(this, (width * s).toInt(), (height * s).toInt(), true)
}
