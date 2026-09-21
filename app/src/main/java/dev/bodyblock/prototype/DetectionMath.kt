package dev.bodyblock.prototype

import kotlin.math.*

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val category: Int, val score: Float, val id: Long = 0) {
    val width get() = max(0f,right-left)
    val height get() = max(0f,bottom-top)
    fun iou(b: Box): Float {
        val intersection = max(0f,min(right,b.right)-max(left,b.left))*max(0f,min(bottom,b.bottom)-max(top,b.top))
        return intersection / max(0.00001f,width*height+b.width*b.height-intersection)
    }
}

object DetectionMath {
    fun decode(output: Array<FloatArray>, width: Int, height: Int, threshold: Float): List<Box> {
        require(output.size == 22) { "Unexpected model output: ${output.size} channels" }
        val scale = max(width,height)/320f
        val candidates = ArrayList<Box>()
        for(i in output[0].indices) {
            var category=0; var score=output[4][i]
            for(c in 1 until 18) if(output[c+4][i] > score) { score=output[c+4][i]; category=c }
            if(!score.isFinite() || score < threshold) continue
            val cx=output[0][i]*scale; val cy=output[1][i]*scale
            val w=output[2][i]*scale; val h=output[3][i]*scale
            if(!cx.isFinite() || !cy.isFinite() || !w.isFinite() || !h.isFinite()) continue
            val box=Box((cx-w/2).coerceIn(0f,width.toFloat()),(cy-h/2).coerceIn(0f,height.toFloat()),(cx+w/2).coerceIn(0f,width.toFloat()),(cy+h/2).coerceIn(0f,height.toFloat()),category,score)
            if(box.width>1 && box.height>1) candidates.add(box)
        }
        val selected=ArrayList<Box>()
        for(b in candidates.sortedByDescending { it.score }) {
            if(selected.none { it.iou(b)>0.45f }) selected.add(b)
            if(selected.size>=100) break
        }
        return selected
    }
}

class Tracker {
    private var previous = emptyList<Box>()
    private var nextId=1L
    fun update(boxes: List<Box>): Pair<List<Box>,Int> {
        val available=previous.toMutableList(); var fresh=0
        val tracked=boxes.map { b ->
            val old=available.filter { it.category==b.category }.maxByOrNull { it.iou(b) }?.takeIf { it.iou(b)>0.12f }
            if(old==null) { fresh++; b.copy(id=nextId++) } else {
                available.remove(old)
                // Keep most of the new position to avoid masks trailing fast scrolling.
                b.copy(left=b.left*.85f+old.left*.15f,top=b.top*.85f+old.top*.15f,right=b.right*.85f+old.right*.15f,bottom=b.bottom*.85f+old.bottom*.15f,id=old.id)
            }
        }
        previous=tracked
        return tracked to fresh
    }
    fun clear() { previous=emptyList() }
}
