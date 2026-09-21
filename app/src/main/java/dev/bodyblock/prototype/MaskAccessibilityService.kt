package dev.bodyblock.prototype

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.*
import android.view.*
import android.view.accessibility.AccessibilityEvent

class MaskView(context: Context): View(context) {
    var bitmap: Bitmap?=null
        set(value) { val old=field; field=value; invalidate(); if(old!==value) old?.recycle() }
    var destination=RectF()
    private val bitmapPaint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val location=IntArray(2)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(location)
        canvas.save(); canvas.translate(-location[0].toFloat(),-location[1].toFloat())
        bitmap?.let { if(!it.isRecycled) canvas.drawBitmap(it,null,destination,bitmapPaint) }
        canvas.restore()
    }
}

class MaskAccessibilityService: AccessibilityService() {
    companion object { @Volatile var instance: MaskAccessibilityService?=null }
    private var mask: MaskView?=null
    private val wm get()=getSystemService(WindowManager::class.java)
    override fun onServiceConnected() { instance=this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(LiveState.active && CaptureService.targetPackage!=null && foregroundPackage()!=CaptureService.targetPackage) clear()
    }
    fun foregroundPackage(): String?=rootInActiveWindow?.packageName?.toString()
    fun contentBounds(): Rect? = windows.filter {
        it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString()==CaptureService.targetPackage
    }.map { window -> Rect().also { window.getBoundsInScreen(it) } }.maxByOrNull { it.width().toLong()*it.height() }
    fun display(bitmap: Bitmap, capturedWidth: Int, capturedHeight: Int, config: Config) {
        val bounds=contentBounds()
        if(bounds==null || bounds.isEmpty || foregroundPackage()!=CaptureService.targetPackage) { bitmap.recycle(); clear(); return }
        val ratio=bounds.width().toFloat()/bounds.height()
        if(kotlin.math.abs(ratio-capturedWidth.toFloat()/capturedHeight)>.16f) { bitmap.recycle(); clear(); LiveState.status="Window changed · waiting for alignment"; return }
        val view=mask ?: MaskView(this).also {
            val p=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
            p.gravity=Gravity.TOP or Gravity.LEFT; p.setFitInsetsTypes(0)
            p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            wm.addView(it,p); mask=it
        }
        view.destination=RectF(bounds).apply { offset(config.offsetX.toFloat(),config.offsetY.toFloat()) }
        view.bitmap=bitmap
    }
    fun clear() { mask?.bitmap=null }
    fun remove() { mask?.let { it.bitmap=null; runCatching { wm.removeView(it) } }; mask=null }
    override fun onInterrupt() { clear() }
    override fun onDestroy() { instance=null; remove(); stopService(android.content.Intent(this,CaptureService::class.java)); super.onDestroy() }
}
