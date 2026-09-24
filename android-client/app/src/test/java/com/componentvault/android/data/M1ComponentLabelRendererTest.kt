package com.componentvault.android.data

import android.graphics.Bitmap
import android.graphics.Color
import com.componentvault.android.model.ComponentLabelSeed
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class M1ComponentLabelRendererTest {
    private val seed = ComponentLabelSeed("C70565", "贴片电阻 RC0603FR", "电阻", "0603", "A01", 15, 1,
        model = "RC0603FR", brand = "Example")

    @Test fun actualPaperRasterDecodesTheOriginalPayloadInAllRotations() {
        for (rotation in listOf(0, 90, 180, 270)) {
            for (template in listOf(ComponentLabelTemplate.Qr10x40, ComponentLabelTemplate.Qr30x40)) {
                val bitmap = M1ComponentLabelRenderer.render(seed, template, ComponentTextLabelTemplate.default,
                    M1TestPaperProfile(40f, 60f, rotation))
                try {
                    assertEquals(320, bitmap.width)
                    assertEquals(480, bitmap.height)
                    assertEquals(ComponentLabelCodec.buildQrPayload(seed, template)!!.rawValue, decode(bitmap))
                    val packed = M1SppPrintProtocol.frames(bitmap)
                    assertEquals(480, packed.sumOf { it.rows })
                    assertTrue(packed.all { it.rawBytes <= 1024 })
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun offsetsPreserveQrQuietZoneAndDoNotClipThePaper() {
        val template = ComponentLabelTemplate.Qr10x40
        val bitmap = M1ComponentLabelRenderer.render(seed, template, ComponentTextLabelTemplate.default,
            M1TestPaperProfile(40f, 60f, 90, 0.5f, -0.5f))
        try {
            assertEquals(ComponentLabelCodec.buildQrPayload(seed, template)!!.rawValue, decode(bitmap))
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
        } finally { bitmap.recycle() }
    }

    @Test fun horizontalOffsetTranslatesInkExactlyWithoutResizingAndPaperEdgesRemainWhite() {
        val template = ComponentLabelTemplate.Qr10x40
        val original = M1ComponentLabelRenderer.render(seed, template, ComponentTextLabelTemplate.default,
            M1TestPaperProfile(40f, 60f))
        val shifted = M1ComponentLabelRenderer.render(seed, template, ComponentTextLabelTemplate.default,
            M1TestPaperProfile(40f, 60f, offsetXmm = 0.5f))
        try {
            for (y in 0 until original.height) for (x in 0 until original.width - 4) {
                assertEquals(original.getPixel(x, y), shifted.getPixel(x + 4, y))
            }
            for (x in 0 until original.width) {
                assertEquals(Color.WHITE, original.getPixel(x, 0))
                assertEquals(Color.WHITE, original.getPixel(x, original.height - 1))
            }
            for (y in 0 until original.height) {
                assertEquals(Color.WHITE, original.getPixel(0, y))
                assertEquals(Color.WHITE, original.getPixel(original.width - 1, y))
            }
        } finally { original.recycle(); shifted.recycle() }
    }

    @Test fun impossibleOffsetAndDenseTinyQrAreRejectedBeforeSending() {
        assertFailsWith<IllegalArgumentException> {
            M1ComponentLabelRenderer.render(seed, ComponentLabelTemplate.default, ComponentTextLabelTemplate.default,
                M1TestPaperProfile(40f, 60f, offsetXmm = 25f))
        }
        val dense = seed.copy(name = "元件参数".repeat(90))
        assertFailsWith<IllegalArgumentException> {
            M1ComponentLabelRenderer.render(dense, ComponentLabelTemplate.default, ComponentTextLabelTemplate.default,
                M1TestPaperProfile(20f, 10f))
        }
    }

    @Test fun textOnlyLabelsUseTheSelectedTextFields() {
        val template = ComponentLabelTemplate.entries.first { !it.isQrLabel }
        val bitmap = M1ComponentLabelRenderer.render(seed, template, ComponentTextLabelTemplate.NamePackageSku,
            M1TestPaperProfile(40f, 60f))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { Color.red(it) < 128 })
            assertEquals(320, bitmap.width)
        } finally { bitmap.recycle() }
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
            mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }
}
