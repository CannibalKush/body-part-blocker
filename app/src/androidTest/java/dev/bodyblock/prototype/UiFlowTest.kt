package dev.bodyblock.prototype

import android.content.*
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=run { Configurator.getInstance().uiAutomationFlags=android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES; UiDevice.getInstance(instrumentation) }
    private fun waitText(text: String) = device.wait(Until.findObject(By.text(text)),10000) ?: error("Missing UI: $text")
    @Test fun screensAndPrivateBrowserCanReopen() {
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitText("Home")
        device.takeScreenshot(File(context.getExternalFilesDir(null),"home.png"))
        waitText("Settings").click(); waitText("PROFILE")
        device.takeScreenshot(File(context.getExternalFilesDir(null),"settings.png"))
        waitText("Export").click(); waitText("PERMANENT IMAGE CENSORING")
        waitText("Help").click(); waitText("GET STARTED")
        repeat(2) {
            context.startActivity(Intent(context,PrivateBrowserActivity::class.java).putExtra("config",Config().json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitText("PRIVATE BROWSER")
            device.pressBack()
            waitText("Home")
        }
        context.startActivity(Intent(context,BrowserActivity::class.java).putExtra("config",Config().json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitText("SAFE BROWSER")
        device.takeScreenshot(File(context.getExternalFilesDir(null),"browser.png"))
        device.pressBack()
    }
    @Test fun browserFiltersVisibleImage() {
        val activity=instrumentation.startActivitySync(Intent(context,BrowserActivity::class.java).putExtra("config",Config(enabled=setOf(1,12),maskText="No").json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val bytes=instrumentation.context.assets.open("astronaut.png").use { it.readBytes() }
        val data=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
        fun findWeb(v: android.view.View): android.webkit.WebView? {
            if(v is android.webkit.WebView) return v
            if(v is android.view.ViewGroup) for(i in 0 until v.childCount) findWeb(v.getChildAt(i))?.let { return it }
            return null
        }
        instrumentation.runOnMainSync {
            val web=findWeb(activity.findViewById(android.R.id.content))!!
            web.loadDataWithBaseURL("https://fixture.example/","<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0'><img width='100%' src='data:image/png;base64,$data'></body></html>","text/html","UTF-8",null)
        }
        val detected=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(".*[1-9][0-9]* REGIONS HIDDEN.*"))),15000)
        assertNotNull("Browser should detect the visible test face",detected)
        device.takeScreenshot(File(context.getExternalFilesDir(null),"browser-detection.png"))
        instrumentation.runOnMainSync { activity.finish() }
    }
    @Test fun liveCaptureMasksAnotherAppAndPreservesTouch() {
        device.executeShellCommand("settings put secure enabled_accessibility_services null")
        device.executeShellCommand("settings put secure enabled_accessibility_services dev.bodyblock.prototype/dev.bodyblock.prototype.MaskAccessibilityService")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        val deadline=SystemClock.elapsedRealtime()+10000
        while(MaskAccessibilityService.instance==null && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
        assertNotNull("Accessibility service must connect",MaskAccessibilityService.instance)
        Store(context).save(Config(enabled=setOf(1,12),style="Solid",color=0xFF08060B.toInt()))
        device.pressBack()
        device.executeShellCommand("am start -n dev.bodyblock.prototype.test/dev.bodyblock.prototype.TestGalleryActivity")
        waitText("BodyBlock Test Gallery")
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitText("▶  Start protection").click()
        waitText("Start").click()
        val gallery=device.wait(Until.findObject(By.desc("dev.bodyblock.prototype.test")),10000) ?: error("Test gallery missing from capture chooser")
        gallery.click()
        try {
            val end=SystemClock.elapsedRealtime()+20000
            while((!LiveState.active || LiveState.boxes<1) && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            assertTrue("Live capture failed: ${LiveState.status}, target=${CaptureService.targetPackage}",LiveState.active && LiveState.boxes>0)
            assertEquals("dev.bodyblock.prototype.test",CaptureService.targetPackage)
            val image=device.findObject(By.desc("NASA astronaut test image")).visibleBounds
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            val x=image.left+(image.width()*224f/512).toInt(); val y=image.top+(image.height()*105f/512).toInt()
            val pixel=screenshot.getPixel(x,y)
            File(context.getExternalFilesDir(null),"live-capture.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
            assertTrue("Mask must cover forehead at $x,$y; actual=$pixel",android.graphics.Color.red(pixel)<40 && android.graphics.Color.green(pixel)<40 && android.graphics.Color.blue(pixel)<40)
            waitText("TAP TO VERIFY TOUCH").click()
            waitText("TOUCH WORKS")
        } finally {
            context.stopService(Intent(context,CaptureService::class.java))
            val end=SystemClock.elapsedRealtime()+5000
            while(LiveState.active && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50)
            assertFalse("Capture must stop",LiveState.active)
        }
    }
}
