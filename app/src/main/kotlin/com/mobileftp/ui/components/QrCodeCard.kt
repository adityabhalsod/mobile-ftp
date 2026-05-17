package com.mobileftp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import kotlinx.coroutines.delay

/**
 * QR card that encodes the FTP connection URL.
 * - Tap to copy full string to clipboard with brief AccentGreen border flash 300ms.
 */
@Composable
fun QrCodeCard(
    connectionUrl: String,
    displayCaption: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current
    val context = LocalContext.current
    var flashing by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (flashing) colors.AccentGreen else colors.BorderDefault,
        animationSpec = tween(300),
        label = "qr-flash"
    )
    val bitmap = remember(connectionUrl) { generateQrBitmap(connectionUrl, 512) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(flashing) {
        if (flashing) {
            delay(300L)
            flashing = false
        }
    }

    val shape = RoundedCornerShape(RaycastShapes.CardRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.SurfaceElevated)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                copyToClipboard(context, connectionUrl)
                flashing = true
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RaycastSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp)
                )
            }
            Text(displayCaption, style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
            Text(
                if (flashing) "Copied!" else "Tap to copy",
                style = RaycastType.MetaMono.copy(
                    color = if (flashing) colors.AccentGreen else colors.TextTertiary
                ),
                modifier = Modifier
                    .clip(shape)
                    .background(colors.SurfaceCard)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    } catch (e: Exception) {
        null
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("MobileFTP URL", value))
}
