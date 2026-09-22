package com.componentvault.android.data

import android.app.Application
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class M1SppTestLabelRendererTest {
    @Test
    fun existingPaperSizesProduce203DpiRasterAndReadableQr() {
        listOf(ComponentLabelTemplate.Qr10x40 to 80, ComponentLabelTemplate.Qr30x40 to 240).forEach { (template, expectedHeight) ->
            val bitmap = M1TestLabelRenderer.render(template)
            try {
                assertEquals(320, bitmap.width)
                assertEquals(expectedHeight, bitmap.height)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                assertEquals(M1TestLabelRenderer.QrContent, MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun variableWidthTextStripIsNotSilentlyResized() {
        assertFailsWith<IllegalArgumentException> { M1TestLabelRenderer.render(ComponentLabelTemplate.TextOnly) }
    }
}
