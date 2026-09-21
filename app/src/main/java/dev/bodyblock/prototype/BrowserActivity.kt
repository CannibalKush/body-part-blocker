package dev.bodyblock.prototype

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.view.*
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

open class BrowserActivity: Activity() {
    private val privateMode get()=this is PrivateBrowserActivity
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var frame: FrameLayout
    private lateinit var status: TextView
    private lateinit var overlay: ImageView
    private lateinit var config: Config
    private lateinit var store: Store
    private val tabs=mutableListOf<WebView>()
    private var selected=0
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val downloads=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private var detector: Detector?=null
    private var renderer: EffectRenderer?=null
    private var currentMask: Bitmap?=null
    private var generation=0
    private var trackerGeneration=-1
    private var foreground=false
    private var closed=false
    private var filtering=true
    private var lastStatus=""
    private val tracker=Tracker()
    private val scan=object: Runnable { override fun run() { if(foreground && filtering) capture(); handler.postDelayed(this,config.intervalMs) } }
    private val current get()=tabs.getOrNull(selected)
    override fun onCreate(saved: Bundle?) {
        if(privateMode && !privateWebViewInitialized) { WebView.setDataDirectorySuffix("private"); privateWebViewInitialized=true }
        super.onCreate(saved)
        if(privateMode) { CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData() }
        store=Store(this); config=runCatching { Config.parse(JSONObject(intent.getStringExtra("config") ?: "{}")) }.getOrDefault(Config())
        root=Ui.root(this)
        val top=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(Ui.dp(context,8),0,Ui.dp(context,8),0) }
        top.addView(Ui.button(this,"‹") { finish() },LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)))
        top.addView(Ui.text(this,if(privateMode) "PRIVATE BROWSER" else "SAFE BROWSER",15f,Ui.white,true),LinearLayout.LayoutParams(0,-2,1f))
        top.addView(Ui.button(this,"☰") { menu() },LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,48))); root.addView(top)
        val location=LinearLayout(this).apply { setPadding(Ui.dp(context,12),0,Ui.dp(context,12),0) }
        address=EditText(this).apply {
            isSingleLine=true; textSize=14f; setTextColor(Ui.white); setHintTextColor(Ui.muted); hint="Search or enter HTTPS address"
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _,_,_ -> navigate(text.toString()); true }
        }
        location.addView(address,LinearLayout.LayoutParams(0,Ui.dp(this,52),1f))
        location.addView(Ui.button(this,"Go") { navigate(address.text.toString()) },LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,48))); root.addView(location)
        status=Ui.text(this,"●  FILTERING ON DEVICE",11f,Ui.pink,true).apply { setPadding(Ui.dp(context,16),Ui.dp(context,8),0,Ui.dp(context,8)) }; root.addView(status)
        frame=FrameLayout(this); root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        overlay=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_XY; isClickable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addTab("https://example.com")
        handler.post(scan)
    }
    @Suppress("SetJavaScriptEnabled") private fun addTab(url: String) {
        if(tabs.size>=8) { toast("Close a tab first (maximum 8)"); return }
        val web=WebView(this)
        web.setBackgroundColor(Color.WHITE)
        web.settings.apply {
            javaScriptEnabled=true; domStorageEnabled=true
            allowFileAccess=false; allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true); builtInZoomControls=true; displayZoomControls=false
            mediaPlaybackRequiresUserGesture=true; safeBrowsingEnabled=true
            cacheMode=if(privateMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
        web.webViewClient=object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView,request: WebResourceRequest): Boolean {
                val scheme=request.url.scheme
                if(scheme!="https" && scheme!="http") { toast("Only web links are supported"); return true }
                return false
            }
            override fun shouldInterceptRequest(view: WebView,request: WebResourceRequest): WebResourceResponse? {
                val host=request.url.host.orEmpty().lowercase()
                if(config.adBlock && blockedDomains.any { host==it || host.endsWith(".$it") }) return WebResourceResponse("text/plain","UTF-8","".byteInputStream())
                return null
            }
            override fun onPageStarted(view: WebView,url: String?,favicon: Bitmap?) { if(view===current) { clearMask(); address.setText(url); status.text="Loading · filtering is reactive" } }
            override fun onPageFinished(view: WebView,url: String?) { if(view===current) { address.setText(url); lastStatus="" } }
            override fun onReceivedError(view: WebView,request: WebResourceRequest,error: WebResourceError) { if(request.isForMainFrame) status.text="Page unavailable: ${error.description}" }
        }
        web.webChromeClient=WebChromeClient()
        web.setOnScrollChangeListener { _,_,_,_,_ -> if(web===current) clearMask() }
        web.setDownloadListener { url,_,_,mime,_ -> if(mime?.startsWith("image/")==true) saveImage(url) else toast("Only censored image downloads are supported") }
        web.setOnLongClickListener {
            val hit=web.hitTestResult
            if(hit.type==WebView.HitTestResult.IMAGE_TYPE || hit.type==WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                hit.extra?.let { url -> AlertDialog.Builder(this).setItems(arrayOf("Save censored image")) { _,_ -> saveImage(url) }.show() }; true
            } else false
        }
        tabs.add(web); showTab(tabs.lastIndex); web.loadUrl(url)
    }
    private fun showTab(index: Int) {
        clearMask(); current?.onPause(); selected=index; frame.removeAllViews()
        frame.addView(current,FrameLayout.LayoutParams(-1,-1)); frame.addView(overlay,FrameLayout.LayoutParams(-1,-1)); current?.onResume()
        address.setText(current?.url.orEmpty())
    }
    private fun navigate(raw: String) {
        val trimmed=raw.trim(); if(trimmed.isEmpty()) return
        val url=when { trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed; trimmed.contains('.') && !trimmed.contains(' ') && !trimmed.contains(':') -> "https://$trimmed"; else -> "https://duckduckgo.com/?q=${Uri.encode(trimmed)}" }
        current?.loadUrl(url); address.clearFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken,0)
    }
    private fun clearMask() { generation++; overlay.setImageDrawable(null); currentMask?.recycle(); currentMask=null }
    private fun capture() {
        val web=current ?: return
        if(web.width<1 || web.height<1 || closed || !busy.compareAndSet(false,true)) return
        val g=generation
        val width=kotlin.math.min(config.captureWidth,web.width); val height=(web.height.toLong()*width/web.width).toInt().coerceAtLeast(1)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(bitmap); canvas.scale(width.toFloat()/web.width,height.toFloat()/web.height); web.draw(canvas) }
        catch(e: Exception) { bitmap.recycle(); busy.set(false); status.text="Capture unavailable"; return }
        val snapshot=config
        worker.execute {
            try {
                val started=SystemClock.elapsedRealtime()
                val engine=detector ?: Detector(this).also { detector=it }; val effects=renderer ?: EffectRenderer(this).also { renderer=it }
                if(trackerGeneration!=g) { tracker.clear(); trackerGeneration=g }
                val (boxes,fresh)=tracker.update(engine.detect(bitmap,snapshot)); val mask=effects.render(bitmap,boxes,snapshot)
                val ms=SystemClock.elapsedRealtime()-started
                runOnUiThread {
                    if(!closed && foreground && filtering && generation==g) {
                        overlay.setImageBitmap(mask); val old=currentMask; currentMask=mask; old?.recycle()
                        if(!privateMode) store.event("blocks",fresh.toLong())
                        val label="●  ${boxes.size} REGIONS HIDDEN"+if(config.diagnostics) "  ·  $ms ms  ·  CPU / FP32" else "  ·  LOCAL"
                        if(label!=lastStatus) { status.text=label; lastStatus=label }
                    } else mask.recycle()
                }
            } catch(e: Exception) { runOnUiThread { if(!closed) { clearMask(); status.text="Filter error: ${e.message?.take(100)}" } } }
            finally { bitmap.recycle(); busy.set(false) }
        }
    }
    private fun menu() {
        val items=arrayOf("New tab (${tabs.size}/8)","Switch tab","Close current tab","Bookmarks","Bookmark this page",if(filtering) "Pause filtering" else "Resume filtering","Censor visible images now",if(config.adBlock) "Ad blocking: on" else "Ad blocking: off",if(privateMode) "Exit private browser" else "Open private browser")
        AlertDialog.Builder(this).setTitle("Browser").setItems(items) { _,i -> when(i) {
            0 -> addTab("https://example.com")
            1 -> AlertDialog.Builder(this).setTitle("Tabs").setItems(tabs.mapIndexed { n,w -> "${n+1}. ${w.title ?: w.url ?: "New tab"}" }.toTypedArray()) { _,n -> showTab(n) }.show()
            2 -> if(tabs.size==1) finish() else { clearMask(); val old=tabs.removeAt(selected); frame.removeView(old); old.destroy(); selected=0; showTab(0) }
            3 -> bookmarks()
            4 -> if(privateMode) toast("Bookmarks are disabled in private mode") else { val url=current?.url ?: return@setItems; val bookmarks=JSONObject(store.prefs.getString("bookmarks","{}")!!); bookmarks.put(url,current?.title ?: url); store.prefs.edit().putString("bookmarks",bookmarks.toString()).apply(); toast("Bookmark saved") }
            5 -> { filtering=!filtering; clearMask(); status.text=if(filtering) "Filtering resumed" else "FILTERING PAUSED" }
            6 -> { filtering=true; clearMask(); capture() }
            7 -> { config=config.copy(adBlock=!config.adBlock); current?.reload() }
            8 -> if(privateMode) finish() else startActivity(Intent(this,PrivateBrowserActivity::class.java).putExtra("config",config.json().toString()))
        } }.show()
    }
    private fun bookmarks() {
        if(privateMode) { toast("Bookmarks are disabled in private mode"); return }
        val bookmarks=JSONObject(store.prefs.getString("bookmarks","{}")!!); val urls=bookmarks.keys().asSequence().toList()
        if(urls.isEmpty()) { toast("No bookmarks yet"); return }
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(urls.map { bookmarks.getString(it) }.toTypedArray()) { _,i -> current?.loadUrl(urls[i]) }.setNeutralButton("Clear bookmarks") { _,_ -> store.prefs.edit().remove("bookmarks").apply() }.show()
    }
    private fun saveImage(url: String) {
        if(!url.startsWith("https://")) { toast("This prototype saves HTTPS images only"); return }
        toast("Processing image locally…")
        val c=config
        val userAgent=current?.settings?.userAgentString.orEmpty()
        val referer=current?.url
        downloads.execute {
            try {
                val bytes=downloadImage(url,userAgent,referer)
                runOnUiThread { if(!closed) worker.execute { saveDownloadedImage(bytes,c) } }
            } catch(e: Exception) { runOnUiThread { if(!closed) toast("Image not saved: ${e.message}") } }
        }
    }
    private fun saveDownloadedImage(bytes: ByteArray,c: Config) {
        var source: Bitmap?=null; var mask: Bitmap?=null; var output: Bitmap?=null
        try {
            source=MediaFiles.decode(bytes)
            val engine=detector ?: Detector(this).also { detector=it }; val effects=renderer ?: EffectRenderer(this).also { renderer=it }
            mask=effects.render(source,engine.detect(source,c),c); output=source.copy(Bitmap.Config.ARGB_8888,true); Canvas(output).drawBitmap(mask,0f,0f,null)
            MediaFiles.save(this,output); if(!privateMode) store.event("exports")
            runOnUiThread { if(!closed) toast("Censored image saved to Pictures/BodyBlock") }
        } catch(e: Exception) { runOnUiThread { if(!closed) toast("Image not saved: ${e.message}") } }
        finally { source?.recycle(); mask?.recycle(); output?.recycle() }
    }
    private fun downloadImage(initial: String,userAgent: String,referer: String?): ByteArray {
        var destination=URL(initial)
        repeat(6) {
            require(destination.protocol=="https") { "Only HTTPS image downloads are supported" }
            val connection=destination.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false; connection.connectTimeout=15000; connection.readTimeout=15000
            connection.setRequestProperty("User-Agent",userAgent)
            CookieManager.getInstance().getCookie(destination.toString())?.let { connection.setRequestProperty("Cookie",it) }
            if(referer!=null && Uri.parse(referer).host==destination.host) connection.setRequestProperty("Referer",referer)
            try {
                val code=connection.responseCode
                if(code in 300..399) { destination=URL(destination,connection.getHeaderField("Location") ?: error("Missing redirect")) }
                else {
                    require(code in 200..299) { "Download failed ($code)" }
                    return connection.inputStream.use { input ->
                        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                        while(true) { val n=input.read(buffer); if(n<0) break; require(out.size()+n<=30*1024*1024) { "Image exceeds 30 MB" }; out.write(buffer,0,n) }
                        out.toByteArray()
                    }
                }
            } finally { connection.disconnect() }
        }
        error("Too many image redirects")
    }
    private fun toast(s: String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
    override fun onResume() { super.onResume(); foreground=true; current?.onResume() }
    override fun onPause() { foreground=false; clearMask(); current?.onPause(); super.onPause() }
    @Deprecated("Platform navigation") override fun onBackPressed() { if(current?.canGoBack()==true) current?.goBack() else super.onBackPressed() }
    override fun onDestroy() {
        closed=true; downloads.shutdownNow(); handler.removeCallbacksAndMessages(null); clearMask()
        tabs.forEach { it.stopLoading(); if(privateMode) { it.clearCache(true); it.clearHistory(); it.clearFormData() }; it.destroy() }
        if(privateMode) { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); WebStorage.getInstance().deleteAllData() }
        worker.execute { detector?.close(); renderer?.close() }; worker.shutdown()
        super.onDestroy()
    }
    companion object { private var privateWebViewInitialized=false; val blockedDomains=setOf("doubleclick.net","googlesyndication.com","googleadservices.com","adnxs.com","adsystem.com","amazon-adsystem.com","taboola.com","outbrain.com","scorecardresearch.com") }
}
class PrivateBrowserActivity: BrowserActivity()
