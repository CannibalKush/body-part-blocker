package dev.bodyblock.prototype

import android.content.Context
import android.content.ContextWrapper
import android.media.ImageReader
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ExecutorService

class CaptureResizeTest {
    @Test fun duplicateContentSizeCallbacksKeepTheExistingCaptureSurface() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val service=CaptureService()
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java).apply { isAccessible=true }.invoke(service,instrumentation.targetContext)
            service.onCreate()
            val prepare=CaptureService::class.java.getDeclaredMethod("prepareReader",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
            val resize=CaptureService::class.java.getDeclaredMethod("resize",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
            val field=CaptureService::class.java.getDeclaredField("reader").apply { isAccessible=true }
            val worker=CaptureService::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(service) as ExecutorService
            try {
                prepare.invoke(service,1080,2340)
                val original=field.get(service)
                repeat(3) { resize.invoke(service,1080,2340); assertSame("Repeated size callbacks must not abandon the existing surface",original,field.get(service)) }
                resize.invoke(service,2340,1080)
                val rotated=field.get(service)
                assertNotSame("A real orientation change must resize capture",original,rotated)
                resize.invoke(service,2340,1080)
                assertSame(rotated,field.get(service))
            } finally { (field.get(service) as? ImageReader)?.close(); worker.shutdownNow() }
        }
    }
}
