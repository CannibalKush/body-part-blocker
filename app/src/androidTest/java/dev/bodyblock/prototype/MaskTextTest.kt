package dev.bodyblock.prototype

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaskTextTest {
    @Test fun textModesPreservePreferencesAndRenderDetectionLabels() {
        assertEquals("Yes",Config.parse(JSONObject("{\"showText\":true}")).maskText)
        assertEquals("No",Config.parse(JSONObject("{\"showText\":false}")).maskText)
        assertEquals("Part",Config.parse(Config(maskText="Part").json()).maskText)
        val renderer=EffectRenderer(InstrumentationRegistry.getInstrumentation().targetContext)
        val box=Box(0f,0f,240f,120f,7,.87f)
        assertEquals("Feet · 87%",renderer.partLabel(box))
        assertEquals("Eyes",renderer.partLabel(box.copy(category=18,score=1f)))
        assertEquals("",renderer.partLabel(null))
        val source=Bitmap.createBitmap(240,120,Bitmap.Config.ARGB_8888)
        val c=Config(maskText="No",border="None",padding=0)
        val none=renderer.render(source,listOf(box),c)
        val part=renderer.render(source,listOf(box),c.copy(maskText="Part"))
        val yes=renderer.render(source,listOf(box),c.copy(maskText="Yes"))
        assertFalse(none.sameAs(part))
        assertFalse(part.sameAs(yes))
        listOf(source,none,part,yes).forEach { it.recycle() }; renderer.close()
    }
}
