package dev.bodyblock.prototype

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.WindowManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class CaptureService: Service() {
    companion object { @Volatile var targetPackage: String?=null }
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private var projection: MediaProjection?=null
    private var display: VirtualDisplay?=null
    private var reader: ImageReader?=null
    private var detector: Detector?=null
    private var renderer: EffectRenderer?=null
    private val tracker=Tracker()
    private var lastFrame=0L
    private var visible=false
    private val launcherPackage by lazy { packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName }
    private var generation=0
    private var trackerGeneration=-1
    private var captureWidth=1; private var captureHeight=1
    private lateinit var store: Store
    private var stopping=false
    override fun onBind(intent: Intent?)=null
    override fun onCreate() { super.onCreate(); store=Store(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="STOP") { stopSelf(); return START_NOT_STICKY }
        if(LiveState.active) return START_NOT_STICKY
        val consent=intent?.getParcelableExtra("consent",Intent::class.java)
        if(consent==null || MaskAccessibilityService.instance==null) { stopSelf(); return START_NOT_STICKY }
        try {
            val notifications=getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel("protection","Live protection",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
            val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            startForeground(1,Notification.Builder(this,"protection").setSmallIcon(R.drawable.ic_shield).setContentTitle("BodyBlock is filtering your selected app").setContentText("Local processing · tap Stop to end capture").setContentIntent(open).addAction(Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,consent)
            projection!!.registerCallback(object: MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
                override fun onCapturedContentResize(width: Int,height: Int) { if(!stopping) resize(width,height) }
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) { visible=isVisible; if(!isVisible) { generation++; MaskAccessibilityService.instance?.clear() } }
            },main)
            val b=getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            prepareReader(b.width(),b.height())
            display=projection!!.createVirtualDisplay("BodyBlockCapture",reader!!.width,reader!!.height,resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,main)
            LiveState.active=true; LiveState.started=SystemClock.elapsedRealtime(); LiveState.blocks=0; LiveState.status="Waiting for selected app"; targetPackage=null
            store.event("sessions")
        } catch(e: Exception) { LiveState.status="Capture failed: ${e.message}"; stopSelf() }
        return START_NOT_STICKY
    }
    private fun resize(w: Int,h: Int) {
        if(w<=0 || h<=0) return
        generation++; MaskAccessibilityService.instance?.clear()
        display?.surface=null; reader?.close(); prepareReader(w,h)
        display?.resize(reader!!.width,reader!!.height,resources.displayMetrics.densityDpi); display?.surface=reader!!.surface
    }
    private fun prepareReader(w: Int,h: Int) {
        captureWidth=w; captureHeight=h
        val scale=min(1f,store.config().captureWidth.toFloat()/w)
        reader=ImageReader.newInstance(max(1,(w*scale).roundToInt()),max(1,(h*scale).roundToInt()),PixelFormat.RGBA_8888,2).also { r -> r.setOnImageAvailableListener({ receive(it) },main) }
    }
    private fun receive(r: ImageReader) {
        val image=runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        val now=SystemClock.elapsedRealtime(); val c=store.config(); val accessibility=MaskAccessibilityService.instance
        val foreground=accessibility?.foregroundPackage()
        if(visible && targetPackage==null && foreground!=null && foreground !in setOf(packageName,launcherPackage,"com.android.systemui","com.android.permissioncontroller","com.google.android.permissioncontroller")) targetPackage=foreground
        if(stopping || !visible || foreground!=targetPackage || targetPackage==null || now-lastFrame<c.intervalMs || !busy.compareAndSet(false,true)) { image.close(); return }
        val frameGeneration=generation; val cw=captureWidth; val ch=captureHeight
        val bitmap: Bitmap
        try {
            val plane=image.planes[0]; val paddedWidth=plane.rowStride/plane.pixelStride
            val padded=Bitmap.createBitmap(paddedWidth,image.height,Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            bitmap=Bitmap.createBitmap(padded,0,0,image.width,image.height)
            if(padded!==bitmap) padded.recycle()
        } catch(e: Exception) { busy.set(false); image.close(); LiveState.status="Frame unavailable"; return }
        image.close(); val previous=lastFrame; lastFrame=now
        worker.execute {
            try {
                val engine=detector ?: Detector(this).also { detector=it }
                val effects=renderer ?: EffectRenderer(this).also { renderer=it }
                val started=SystemClock.elapsedRealtime()
                if(trackerGeneration!=frameGeneration) { tracker.clear(); trackerGeneration=frameGeneration }
                val (boxes,fresh)=tracker.update(engine.detect(bitmap,c))
                val overlay=effects.render(bitmap,boxes,c)
                val elapsed=SystemClock.elapsedRealtime()-started
                main.post {
                    if(!stopping && frameGeneration==generation && visible && accessibility===MaskAccessibilityService.instance) {
                        LiveState.boxes=boxes.size; LiveState.inferenceMs=elapsed
                        LiveState.fps=if(previous>0) 1000f/max(1,now-previous) else 0f
                        LiveState.blocks+=fresh; store.event("blocks",fresh.toLong())
                        LiveState.status=if(boxes.isEmpty()) "ACTIVE · CLEAR" else "ACTIVE · ${boxes.size} REGIONS"
                        accessibility?.display(overlay,cw,ch,c) ?: overlay.recycle()
                    } else overlay.recycle()
                }
            } catch(e: Exception) { main.post { LiveState.status="Detection error: ${e.message?.take(100)}"; accessibility?.clear() } }
            finally { bitmap.recycle(); busy.set(false) }
        }
    }
    override fun onDestroy() {
        stopping=true; generation++
        if(LiveState.active) store.event("seconds",(SystemClock.elapsedRealtime()-LiveState.started)/1000)
        LiveState.active=false; LiveState.boxes=0; targetPackage=null
        if(!LiveState.status.contains("failed")) LiveState.status="PROTECTION STOPPED"
        MaskAccessibilityService.instance?.remove()
        reader?.setOnImageAvailableListener(null,null); display?.release(); reader?.close(); projection?.stop()
        worker.execute { detector?.close(); renderer?.close() }; worker.shutdown()
        super.onDestroy()
    }
}
