package dev.wido.tv.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.wido.tv.R
import java.util.concurrent.Executors

/**
 * Scanner de QR code de l'appli du téléphone : les codes affichés par la TV (lier le téléphone, écrire sur la TV,
 * rapport) s'ouvrent ici, sans passer par un navigateur. Plein écran : l'image de l'appareil photo, un viseur
 * arrondi, une phrase. Le premier code lu est rendu à [onResult] ; Retour ou ✕ ferme.
 */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var refused by remember { mutableStateOf(false) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            refused = !ok
        }
        LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) CameraPreview(onResult)
            Viewfinder()
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 28.dp, end = 28.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(if (refused) R.string.scan_no_camera else R.string.scan_hint),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                ActionButton(stringResource(R.string.action_close), onDismiss, primary = false)
            }
            Text(
                stringResource(R.string.scan_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(24.dp),
            )
        }
    }
}

/** L'écran assombri autour d'un carré arrondi clair, aux coins soulignés. */
@Composable
private fun Viewfinder() {
    val accent = Palette.Accent
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val side = minOf(size.width, size.height) * 0.68f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2 - size.height * 0.05f)
        val r = CornerRadius(side * 0.08f)
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), r, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White.copy(alpha = 0.9f), topLeft, Size(side, side), r, style = Stroke(width = 3.dp.toPx()))
        drawRoundRect(accent.copy(alpha = 0.35f), Offset(topLeft.x - 6.dp.toPx(), topLeft.y - 6.dp.toPx()), Size(side + 12.dp.toPx(), side + 12.dp.toPx()), CornerRadius(r.x + 6.dp.toPx()), style = Stroke(width = 6.dp.toPx()))
    }
}

/** L'appareil photo arrière, analysé image par image (la plus récente seulement) jusqu'au premier QR code lu. */
@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onResult)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var done = false
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val usePreview = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    if (done) return@setAnalyzer
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                    val text = runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                    reader.reset()
                    if (!text.isNullOrBlank()) {
                        done = true
                        ContextCompat.getMainExecutor(context).execute { latest(text) }
                    }
                } finally {
                    image.close()
                }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, usePreview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            done = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView({ preview }, Modifier.fillMaxSize())
}
