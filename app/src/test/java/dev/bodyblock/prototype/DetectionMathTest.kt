package dev.bodyblock.prototype

import org.junit.Assert.*
import org.junit.Test

class DetectionMathTest {
    @Test fun decodeMapsPaddingAndSuppressesDuplicateBoxes() {
        val raw=Array(22) { FloatArray(3) }
        for(i in 0..1) { raw[0][i]=160f; raw[1][i]=80f; raw[2][i]=80f; raw[3][i]=40f; raw[5][i]=.9f-i*.1f }
        raw[0][2]=300f; raw[1][2]=300f; raw[2][2]=20f; raw[3][2]=20f; raw[5][2]=.8f
        val result=DetectionMath.decode(raw,640,320,.3f)
        assertEquals(1,result.size)
        assertEquals(240f,result[0].left,.001f); assertEquals(120f,result[0].top,.001f)
        assertEquals(400f,result[0].right,.001f); assertEquals(200f,result[0].bottom,.001f)
        assertEquals(1,result[0].category)
    }
    @Test fun thresholdAndNonFiniteResultsAreRejected() {
        val raw=Array(22) { FloatArray(2) }
        raw[4][0]=.2f; raw[4][1]=.9f; raw[0][1]=Float.NaN
        assertTrue(DetectionMath.decode(raw,320,320,.3f).isEmpty())
    }
    @Test fun trackingCountsEncountersRatherThanFrames() {
        val t=Tracker(); val box=Box(10f,10f,110f,110f,1,.9f)
        val first=t.update(listOf(box)); val second=t.update(listOf(box.copy(left=15f)))
        assertEquals(1,first.second); assertEquals(0,second.second); assertEquals(first.first[0].id,second.first[0].id)
        t.update(emptyList()); assertEquals(1,t.update(listOf(box)).second)
    }
}
