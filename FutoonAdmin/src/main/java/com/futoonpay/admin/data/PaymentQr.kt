package com.futoonpay.admin.data

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun paymentQr(content: String, pixels: Int = 600): Bitmap? {
    if (content.isBlank() || content.length > 2500) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
        val colors = IntArray(pixels * pixels) { index -> if (matrix[index % pixels, index / pixels]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        Bitmap.createBitmap(colors, pixels, pixels, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
