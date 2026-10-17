package com.linkcontrol.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Generate a QR bitmap (on-device, no network) for the given payload. */
fun qrImage(payload: String, sizePx: Int = 640): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    val cyan = Color.rgb(0x28, 0xE0, 0xF0)
    val bg = Color.rgb(0x0E, 0x16, 0x30)
    for (x in 0 until sizePx) for (y in 0 until sizePx) {
        bmp.setPixel(x, y, if (matrix[x, y]) cyan else bg)
    }
    return bmp.asImageBitmap()
}
