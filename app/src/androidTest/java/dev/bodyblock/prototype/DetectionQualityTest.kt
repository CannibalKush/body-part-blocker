package dev.bodyblock.prototype

import android.graphics.*
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class DetectionQualityTest {
    @Test fun detailedScansFindFacesOnTallScreensAndPreserveCoordinates() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val fixture=instrumentation.context.assets.open("astronaut.png").use { BitmapFactory.decodeStream(it) }
        val screen=Bitmap.createBitmap(640,1400,Bitmap.Config.ARGB_8888)
        Canvas(screen).apply { drawColor(Color.DKGRAY); drawBitmap(fixture,null,Rect(160,500,480,820),null) }
        Detector(instrumentation.targetContext).use { detector ->
            val config=Config(enabled=setOf(1,12),confidence=20)
            detector.detect(screen,config.copy(quality="Fast")) // Warm the model.
            for(quality in listOf("Fast","Detailed")) {
                val start=SystemClock.elapsedRealtime()
                val boxes=detector.detect(screen,config.copy(quality=quality))
                Log.i("BodyBlockQuality","$quality: ${SystemClock.elapsedRealtime()-start} ms, ${boxes.size} faces, scores=${boxes.map { it.score }}")
                if(quality=="Detailed") {
                    assertTrue("Expected the face in the inset fixture",boxes.any { it.left>=160 && it.right<=480 && it.top>=500 && it.bottom<=820 })
                }
            }
            val pending=com.google.android.gms.tasks.TaskCompletionSource<List<com.google.mlkit.vision.face.Face>>()
            Detector::class.java.getDeclaredField("eyeTask").apply { isAccessible=true }.set(detector,pending.task)
            val bodyWhileEyesBusy=detector.detect(screen,config.copy(enabled=setOf(1,12,18)))
            assertTrue("A pending eye task must not discard body detections",bodyWhileEyesBusy.any { it.category==1 || it.category==12 })
            pending.setResult(emptyList())
        }
        screen.recycle(); fixture.recycle()
        val config=Config(quality="Fast",preset="Ultra")
        assertEquals("Fast",Config.parse(config.json()).quality)
        assertEquals(config.captureWidth,config.copy(preset="Low").captureWidth)
    }
}
