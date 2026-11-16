package com.itexpert120.yomu.feature.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLuminanceDecoderTest {
    private val content =
        "yomu-sync:v1:abandon ability able about above absent absorb abstract absurd abuse access accident"

    /** A camera-like frame: the code centred on a light background, rows padded to [stride]. */
    private fun frame(width: Int, height: Int, stride: Int): ByteArray {
        val side = 300
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, side, side)
        val pixels = ByteArray(stride * height) { 0xD0.toByte() }
        val left = (width - side) / 2
        val top = (height - side) / 2
        for (y in 0 until side) {
            for (x in 0 until side) {
                if (matrix[x, y]) pixels[(top + y) * stride + left + x] = 0x18
            }
        }
        return pixels
    }

    @Test fun decodesACodeFromAPaddedLuminancePlane() {
        assertEquals(content, QrLuminanceDecoder().decode(frame(640, 480, 672), 672, 640, 480))
    }

    @Test fun returnsNullWhenTheFrameHasNoCode() {
        val decoder = QrLuminanceDecoder()
        assertNull(decoder.decode(ByteArray(640 * 480) { 0x80.toByte() }, 640, 640, 480))
        // The reader is reusable after a miss.
        assertEquals(content, decoder.decode(frame(640, 480, 640), 640, 640, 480))
    }
}
