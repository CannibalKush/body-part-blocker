package dev.bodyblock.prototype

import android.graphics.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun bundledModelDetectsFaceAndEyesOffline() {
        val testContext=InstrumentationRegistry.getInstrumentation().context
        val image=testContext.assets.open("astronaut.png").use { BitmapFactory.decodeStream(it) }
        Detector(context).use { detector ->
            val boxes=detector.detect(image,Config(enabled=setOf(1,12,18),confidence=20))
            assertTrue("Expected a detected face: $boxes",boxes.any { it.category==1 || it.category==12 })
            assertTrue("Expected eye landmarks: $boxes",boxes.any { it.category==18 })
        }
        image.recycle()
    }
    @Test fun allEffectsRenderAndReversePreservesSelectedHoles() {
        val source=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val boxes=listOf(Box(20f,20f,60f,60f,1,1f))
        val renderer=EffectRenderer(context)
        for(style in Config.styles) {
            val c=Config(style=style,padding=0,border="None",maskText="No")
            val normal=renderer.render(source,boxes,c)
            assertEquals("$style outside",0,Color.alpha(normal.getPixel(5,5)))
            assertEquals("$style inside",255,Color.alpha(normal.getPixel(40,40)))
            val reverse=renderer.render(source,boxes,c.copy(reverse=true))
            assertEquals("$style reverse outside",255,Color.alpha(reverse.getPixel(5,5)))
            assertEquals("$style reverse hole",0,Color.alpha(reverse.getPixel(40,40)))
            normal.recycle(); reverse.recycle()
        }
        renderer.close(); source.recycle()
    }
    @Test fun exportedImageHasPermanentMasks() {
        val source=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val renderer=EffectRenderer(context)
        val mask=renderer.render(source,listOf(Box(20f,20f,60f,60f,1,1f)),Config(color=Color.BLACK,padding=0,border="None",maskText="No"))
        Canvas(source).drawBitmap(mask,0f,0f,null)
        val uri=MediaFiles.save(context,source)
        try {
            val saved=MediaFiles.decode(context,uri)
            assertEquals(Color.BLACK,saved.getPixel(40,40)); assertEquals(Color.BLUE,saved.getPixel(5,5))
            saved.recycle()
        } finally { context.contentResolver.delete(uri,null,null); mask.recycle(); source.recycle(); renderer.close() }
    }
    @Test fun packsRoundTripAndRejectTraversal() {
        val pack=File(context.cacheDir,"test-pack.zip")
        val config=Config(enabled=setOf(1,18),style="Glitch",reverse=true,intensity=91)
        MediaFiles.exportPack(context,Uri.fromFile(pack),config)
        assertEquals(config,MediaFiles.importPack(context,Uri.fromFile(pack)))
        ZipOutputStream(pack.outputStream()).use { it.putNextEntry(ZipEntry("../outside.txt")); it.write("bad".toByteArray()); it.closeEntry() }
        try { MediaFiles.importPack(context,Uri.fromFile(pack)); fail("Traversal pack accepted") } catch(expected: Exception) { assertTrue(expected is IllegalArgumentException || expected is java.util.zip.ZipException) }
        assertFalse(File(context.filesDir.parentFile,"outside.txt").exists()); pack.delete()
    }
}
