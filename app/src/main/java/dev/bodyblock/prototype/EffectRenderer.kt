package dev.bodyblock.prototype

import android.content.Context
import android.graphics.*
import java.io.File
import kotlin.math.*
import kotlin.random.Random

/** Produces transparent masks, shared by live capture, browser and permanent exports. */
class EffectRenderer(private val context: Context) {
    private val images=linkedMapOf<String,Bitmap>()
    fun close() { images.values.forEach { it.recycle() }; images.clear() }
    fun render(source: Bitmap, boxes: List<Box>, config: Config, time: Long = System.currentTimeMillis()): Bitmap {
        val result=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result)
        val bounds=RectF(0f,0f,source.width.toFloat(),source.height.toFloat())
        val regions=boxes.map { b ->
            val px=b.width*config.padding/100f; val py=b.height*config.padding/100f
            RectF(max(0f,b.left-px),max(0f,b.top-py),min(bounds.right,b.right+px),min(bounds.bottom,b.bottom+py)) to b
        }
        if(config.reverse) {
            drawEffect(canvas,source,bounds,config,0,time)
            if(config.reverseStrength<100) {
                canvas.drawColor(Color.argb((255*(1-config.reverseStrength/100f)).toInt(),0,0,0),PorterDuff.Mode.DST_OUT)
            }
            val clear=Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            regions.forEach { canvas.drawRect(it.first,clear) }
        } else regions.forEach { (r,b) -> drawEffect(canvas,source,r,config,b.id,time) }
        return result
    }
    private fun drawEffect(canvas: Canvas, source: Bitmap, r: RectF, c: Config, id: Long, time: Long) {
        if(r.width()<1 || r.height()<1) return
        canvas.save(); canvas.clipRect(r)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=c.color }
        when(c.style) {
            "Blur", "Pixelate" -> {
                val block=if(c.style=="Blur") 3+c.intensity/5 else 2+c.intensity/3
                val w=max(1,r.width().toInt()/block); val h=max(1,r.height().toInt()/block)
                val tiny=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                Canvas(tiny).drawBitmap(source,Rect(r.left.toInt(),r.top.toInt(),ceil(r.right).toInt().coerceAtMost(source.width),ceil(r.bottom).toInt().coerceAtMost(source.height)),Rect(0,0,w,h),Paint(Paint.FILTER_BITMAP_FLAG))
                // ponytail: filtered downsampling gives a low-cost blur; replace with GPU Gaussian blur if visual parity requires it.
                paint.isFilterBitmap=c.style=="Blur"
                canvas.drawBitmap(tiny,null,r,paint); tiny.recycle()
            }
            "Custom image" -> {
                canvas.drawRect(r,paint)
                val name=c.images.getOrNull((id.mod(max(1,c.images.size))).toInt())
                val image=name?.let { n -> images[n] ?: BitmapFactory.decodeFile(File(context.filesDir,n).path)?.also { images[n]=it } }
                if(image!=null) { paint.isFilterBitmap=true; canvas.drawBitmap(image,null,r,paint) }
            }
            "Static" -> {
                canvas.drawRect(r,paint)
                val random=Random((id+(if(c.animate) time/120 else 0)).toInt())
                val step=max(4f,r.width()/35)
                var y=r.top
                while(y<r.bottom) { var x=r.left; while(x<r.right) { val v=random.nextInt(30,220); paint.color=Color.rgb(v,v,v); canvas.drawRect(x,y,x+step,y+step,paint); x+=step }; y+=step }
            }
            "Glitch" -> {
                canvas.drawRect(r,paint)
                val random=Random((id+(if(c.animate) time/160 else 0)).toInt())
                repeat(12) { paint.color=if(it%2==0) Color.rgb(255,0,140) else Color.rgb(0,210,220); paint.alpha=120; val y=r.top+random.nextFloat()*r.height(); canvas.drawRect(r.left,y,r.right,y+max(2f,r.height()/25),paint) }
            }
            "Tape" -> {
                paint.color=Color.rgb(245,196,50); canvas.drawRect(r,paint)
                paint.color=Color.BLACK; paint.strokeWidth=max(8f,r.width()/14)
                var x=r.left-r.height()
                while(x<r.right) { canvas.drawLine(x,r.top,x+r.height(),r.bottom,paint); x+=paint.strokeWidth*2.8f }
                paint.color=Color.BLACK; canvas.drawRect(r.left,r.centerY()-r.height()*.22f,r.right,r.centerY()+r.height()*.22f,paint)
            }
            "Error popup" -> {
                paint.color=Color.rgb(28,19,35); canvas.drawRect(r,paint)
                paint.color=Color.rgb(255,0,140); canvas.drawRect(r.left,r.top,r.right,r.top+max(8f,r.height()*.22f),paint)
            }
            else -> canvas.drawRect(r,paint)
        }
        if(c.border!="None") {
            paint.xfermode=null; paint.alpha=255; paint.color=Color.rgb(255,0,140); paint.style=Paint.Style.STROKE
            paint.strokeWidth=if(c.border=="Glow") 5f else 2f
            canvas.drawRoundRect(r,5f,5f,paint); paint.style=Paint.Style.FILL
        }
        if(c.showText && r.width()>30 && r.height()>20) {
            val phrases=when(c.phraseCategory) { "Minimal" -> listOf("HIDDEN","PROTECTED"); "Playful" -> listOf("NOT TODAY","NICE TRY","LOOK AWAY"); else -> c.phrases.lines().filter { it.isNotBlank() } }
            val text=phrases.getOrNull(((id+time/(1000*c.phraseSeconds)).mod(max(1,phrases.size))).toInt()).orEmpty().take(80)
            if(text.isNotEmpty()) {
                paint.reset(); paint.isAntiAlias=true; paint.color=Color.WHITE; paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
                paint.textSize=min(22f,r.height()*.22f); paint.textAlign=Paint.Align.CENTER
                if(paint.measureText(text)>r.width()*.9f) paint.textSize*=r.width()*.9f/paint.measureText(text)
                paint.setShadowLayer(2f,0f,1f,Color.BLACK)
                canvas.drawText(text,r.centerX(),r.centerY()-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        canvas.restore()
    }
}
