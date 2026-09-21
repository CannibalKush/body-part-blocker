package dev.bodyblock.prototype

import android.app.*
import android.content.*
import android.graphics.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.*
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

class MainActivity: Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var tab="Home"
    private var displayedActive=false
    private var statusView: TextView?=null
    private var statsView: TextView?=null
    private val handler=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val cancelled=AtomicBoolean(false)
    private var exporting=false
    private var deleteOriginals=false
    private var exportStatus="Choose images or a folder. Outputs are saved in Pictures/BodyBlock."
    private var exportLabel: TextView?=null
    private var preview: ImageView?=null
    private val ticker=object: Runnable { override fun run() {
        if(tab=="Home" && displayedActive!=LiveState.active) { displayedActive=LiveState.active; draw() }
        statusView?.text=LiveState.status
        val seconds=if(LiveState.active) (SystemClock.elapsedRealtime()-LiveState.started)/1000 else 0
        statsView?.text="${LiveState.blocks} blocks     %02d:%02d session\n${LiveState.boxes} regions  ·  ${LiveState.inferenceMs} ms  ·  %.1f FPS".format(seconds/60,seconds%60,LiveState.fps)
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(state: Bundle?) { super.onCreate(state); store=Store(this); tab=state?.getString("tab") ?: "Home"; draw(); handler.post(ticker) }
    override fun onResume() { super.onResume(); if(::store.isInitialized) draw() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("tab",tab); super.onSaveInstanceState(out) }
    private fun change(f: (Config)->Config) { store.save(f(store.config())) }
    private fun notify(s: String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
    private fun draw() {
        statusView=null; statsView=null; exportLabel=null; preview=null
        root=Ui.root(this)
        val header=Ui.column(this).apply { setPadding(Ui.dp(context,24),Ui.dp(context,20),Ui.dp(context,24),Ui.dp(context,16)) }
        header.addView(Ui.text(this,"BODYBLOCK",30f,Ui.white,true).apply { letterSpacing=.15f })
        header.addView(Ui.text(this,"ANDROID PROTOTYPE  /  ON-DEVICE",11f,Ui.pink,true).apply { letterSpacing=.15f })
        root.addView(header)
        val nav=LinearLayout(this)
        listOf("Home","Settings","Browser","Export","Help").forEach { name -> nav.addView(TextView(this).apply {
            text=name; textSize=12f; gravity=Gravity.CENTER; setTextColor(if(tab==name) Ui.pink else Ui.muted)
            typeface=Typeface.DEFAULT_BOLD; setPadding(0,Ui.dp(context,15),0,Ui.dp(context,15)); background=Ui.shape(if(tab==name) Ui.card else Ui.bg,if(tab==name) Ui.pink else Ui.bg,0f)
            setOnClickListener { if(name=="Browser") openBrowser(false) else { tab=name; draw() } }
        },LinearLayout.LayoutParams(0,-2,1f)) }
        root.addView(nav)
        content=Ui.column(this).apply { setPadding(Ui.dp(context,18),Ui.dp(context,20),Ui.dp(context,18),Ui.dp(context,24)) }
        root.addView(ScrollView(this).apply { isFillViewport=true; addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        when(tab) { "Settings" -> settings(); "Export" -> exports(); "Help" -> help(); else -> home() }
    }
    private fun home() {
        val hero=Ui.card(content)
        hero.addView(Ui.text(this,"Your screen.\nYour boundaries.",29f,Ui.white,true))
        Ui.gap(hero,8); hero.addView(Ui.text(this,"Choose what stays hidden. Everything is processed on this device.",14f,Ui.muted))
        Ui.gap(hero,16)
        hero.addView(Ui.button(this,if(LiveState.active) "■  Stop protection" else "▶  Start protection",true) {
            if(LiveState.active) { stopService(Intent(this,CaptureService::class.java)); handler.postDelayed({ draw() },200) } else startProtection()
        },LinearLayout.LayoutParams(-1,Ui.dp(this,60)))
        statusView=Ui.text(this,LiveState.status,12f,Ui.pink,true).apply { gravity=Gravity.CENTER; setPadding(0,Ui.dp(context,14),0,0) }; hero.addView(statusView)
        val mode=Ui.card(content,"ONE APP CAPTURE")
        mode.addView(Ui.text(this,"Protect a social app or gallery. Select one app in Android’s sharing prompt. Use Browser for web content.",14f,Ui.muted))
        Ui.gap(mode); mode.addView(Ui.text(this,"Accessibility overlay: ${if(MaskAccessibilityService.instance!=null) "connected" else "setup required"}",13f,Ui.white))
        val stats=Ui.card(content,"THIS SESSION")
        statsView=Ui.text(this,"0 blocks     00:00 session",20f,Ui.white,true); stats.addView(statsView)
        val achievements=Ui.card(content,"ACHIEVEMENTS")
        val blocks=store.count("blocks"); val goal=listOf(10L,100L,1000L,3000L,10000L).firstOrNull { it>blocks } ?: 10000L
        achievements.addView(Ui.text(this,"$blocks lifetime blocks  ·  next milestone $goal",13f,Ui.muted))
        achievements.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=goal.toInt(); progress=blocks.toInt().coerceAtMost(max); progressTintList=android.content.res.ColorStateList.valueOf(Ui.pink) })
        val badges=listOf("First block" to (blocks>0), "100 blocks" to (blocks>=100), "First hour" to (store.count("seconds")>=3600), "Profile maker" to (store.count("profiles")>0), "Exporter" to (store.count("exports")>0), "Explorer" to (store.count("browser")>0), "Ten sessions" to (store.count("sessions")>=10), "Pack maker" to (store.count("packs")>0))
        badges.chunked(2).forEach { row -> val line=LinearLayout(this); row.forEach { (name,unlocked) -> line.addView(Ui.text(this,"${if(unlocked) "◆" else "◇"}  $name",13f,if(unlocked) Ui.pink else Ui.muted,true).apply { setPadding(0,Ui.dp(context,14),0,Ui.dp(context,8)) },LinearLayout.LayoutParams(0,-2,1f)) }; achievements.addView(line) }
        val local=Ui.card(content,"PRIVATE BY DESIGN")
        local.addView(Ui.text(this,"Bundled model • Offline filtering • No account\nFiltering is reactive and can miss content.",13f,Ui.muted))
    }
    private fun startProtection() {
        if(MaskAccessibilityService.instance==null) {
            AlertDialog.Builder(this).setTitle("Enable censor overlays").setMessage("BodyBlock needs its Accessibility service to draw opaque masks while keeping the selected app touchable. It uses window bounds for alignment. It does not type, tap, read passwords, or send screen content anywhere.\n\nEnable BodyBlock in Installed apps, return here, then start protection. Android may require Allow restricted settings in App info for a sideloaded APK.").setPositiveButton("Open settings") { _,_ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.setNegativeButton("Cancel",null).show(); return
        }
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),99)
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),100)
    }
    private fun openBrowser(privateMode: Boolean) {
        store.event("browser")
        startActivity(Intent(this,if(privateMode) PrivateBrowserActivity::class.java else BrowserActivity::class.java).putExtra("config",store.config().json().toString()))
    }
    private fun settings() {
        val c=store.config()
        val profiles=Ui.card(content,"PROFILE")
        val names=store.profiles().keys().asSequence().toList().sorted()
        if(names.isNotEmpty()) Ui.select(profiles,"Load saved profile",listOf("Choose…")+names,"Choose…") { name -> if(name!="Choose…") { store.save(Config.parse(store.profiles().getJSONObject(name))); store.prefs.edit().putString("profile",name).apply(); draw() } }
        profiles.addView(Ui.text(this,"Current: ${store.prefs.getString("profile","Custom")}",13f,Ui.muted))
        profiles.addView(Ui.button(this,"Save as profile") { val input=EditText(this).apply { hint="Profile name"; setText(store.prefs.getString("profile","My profile")) }; AlertDialog.Builder(this).setTitle("Save complete configuration").setView(input).setPositiveButton("Save") { _,_ -> val n=input.text.toString().trim(); if(n.isNotEmpty()) { store.saveProfile(n,store.config()); draw() } }.setNegativeButton("Cancel",null).show() })
        if(names.isNotEmpty()) profiles.addView(Ui.button(this,"Delete a profile") { AlertDialog.Builder(this).setTitle("Delete profile").setItems(names.toTypedArray()) { _,i -> store.deleteProfile(names[i]); draw() }.show() })
        val appearance=Ui.card(content,"CENSOR APPEARANCE")
        Ui.select(appearance,"Style",Config.styles,c.style) { v -> change { it.copy(style=v) } }
        Ui.slider(appearance,"Intensity",c.intensity,1,100) { v -> change { it.copy(intensity=v) } }
        Ui.select(appearance,"Border",listOf("None","Line","Glow"),c.border) { v -> change { it.copy(border=v) } }
        val colors=linkedMapOf("Ink" to 0xFF08060B.toInt(),"Pink" to Ui.pink,"Violet" to 0xFF7429BE.toInt(),"White" to Color.WHITE,"Red" to 0xFFC01E3D.toInt())
        Ui.select(appearance,"Colour",colors.keys.toList(),colors.entries.firstOrNull { it.value==c.color }?.key ?: "Ink") { v -> change { it.copy(color=colors.getValue(v)) } }
        Ui.toggle(appearance,"Animate effects",c.animate) { v -> change { it.copy(animate=v) } }
        Ui.toggle(appearance,"Reverse censor",c.reverse) { v -> change { it.copy(reverse=v) }; if(v) notify("Reverse mode obscures the background and leaves detected regions visible. No detections means the whole content is covered.") }
        Ui.slider(appearance,"Reverse strength",c.reverseStrength,1,100) { v -> change { it.copy(reverseStrength=v) } }
        appearance.addView(Ui.button(this,"Preview current effect") { effectPreview() })
        val text=Ui.card(content,"WORDS & CUSTOM IMAGES")
        Ui.select(text,"Text on masks",listOf("Yes","No","Part"),c.maskText) { v -> change { it.copy(maskText=v) } }
        Ui.select(text,"Phrase category",listOf("Custom","Minimal","Playful"),c.phraseCategory) { v -> change { it.copy(phraseCategory=v) } }
        Ui.slider(text,"Text change · seconds",c.phraseSeconds,1,30) { v -> change { it.copy(phraseSeconds=v) } }
        text.addView(Ui.button(this,"Edit custom phrases") { val input=EditText(this).apply { setText(store.config().phrases); minLines=3; hint="One phrase per line" }; AlertDialog.Builder(this).setTitle("Custom phrases").setView(input).setPositiveButton("Save") { _,_ -> change { it.copy(phrases=input.text.toString().take(2000)) } }.setNegativeButton("Cancel",null).show() })
        text.addView(Ui.button(this,"Import custom image (${c.images.size} enabled)") { pick("image/*",102,false) })
        val assets=filesDir.listFiles()?.filter { it.name.startsWith("asset_") && it.name.endsWith(".png") }.orEmpty()
        assets.forEachIndexed { i,f -> Ui.toggle(text,"Image ${i+1} · ${f.name.takeLast(12)}",f.name in c.images) { on -> change { it.copy(images=if(on) (it.images+f.name).distinct().takeLast(20) else it.images-f.name) } } }
        Categories.groups.forEach { (group,ids) ->
            val panel=Ui.card(content,group)
            if(group=="FACES & EYES") Ui.toggle(panel,"All faces",1 in c.enabled && 12 in c.enabled) { on -> change { it.copy(enabled=if(on) it.enabled+setOf(1,12) else it.enabled-setOf(1,12)) }; draw() }
            ids.forEach { id -> Ui.toggle(panel,Categories.labels[id],id in c.enabled) { on -> change { it.copy(enabled=if(on) it.enabled+id else it.enabled-id) } } }
        }
        val performance=Ui.card(content,"DETECTION & PERFORMANCE")
        Ui.select(performance,"Performance preset",listOf("Low","Medium","High","Ultra"),c.preset) { v -> change { it.copy(preset=v) } }
        performance.addView(Ui.text(this,"Presets change capture size and scan frequency. Restart live protection after changing capture size.",12f,Ui.muted))
        Ui.slider(performance,"Confidence %",c.confidence,10,95) { v -> change { it.copy(confidence=v) } }
        Ui.slider(performance,"Mask padding %",c.padding,0,50) { v -> change { it.copy(padding=v) } }
        Ui.toggle(performance,"Browser diagnostics",c.diagnostics) { v -> change { it.copy(diagnostics=v) } }
        Ui.slider(performance,"Horizontal calibration · px",c.offsetX,-150,150) { v -> change { it.copy(offsetX=v) } }
        Ui.slider(performance,"Vertical calibration · px",c.offsetY,-150,150) { v -> change { it.copy(offsetY=v) } }
        val packs=Ui.card(content,"PROFILE PACKS")
        packs.addView(Ui.text(this,"Portable BodyBlock packs include settings and selected images. Original-app packs use an unverified format.",13f,Ui.muted))
        packs.addView(Ui.button(this,"Export pack") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"BodyBlock-profile.zip").addCategory(Intent.CATEGORY_OPENABLE),103) })
        packs.addView(Ui.button(this,"Import pack") { pick("*/*",104,false) })
    }
    private fun effectPreview() {
        val source=Bitmap.createBitmap(640,420,Bitmap.Config.ARGB_8888)
        Canvas(source).apply {
            drawColor(Color.rgb(24,24,45)); val p=Paint(Paint.ANTI_ALIAS_FLAG)
            for(i in 0..12) { p.color=Color.rgb(30+i*12,30+i*5,100+i*8); drawCircle(i*58f,210f,140f,p) }
            p.color=Color.WHITE; p.textSize=24f; drawText("EFFECT PREVIEW · SAMPLE REGIONS",25f,45f,p)
        }
        val renderer=EffectRenderer(this); val mask=renderer.render(source,listOf(Box(100f,100f,280f,330f,1,1f,1),Box(350f,150f,580f,290f,1,1f,2)),store.config())
        Canvas(source).drawBitmap(mask,0f,0f,null); mask.recycle(); renderer.close()
        AlertDialog.Builder(this).setTitle("Current effect").setView(ImageView(this).apply { setImageBitmap(source); adjustViewBounds=true }).setPositiveButton("Done",null).show().setOnDismissListener { source.recycle() }
    }
    private fun exports() {
        val card=Ui.card(content,"PERMANENT IMAGE CENSORING")
        card.addView(Ui.text(this,"Use your current profile on individual images or a folder. Saved PNGs contain the masks permanently.",14f,Ui.muted))
        Ui.gap(card)
        Ui.toggle(card,"Replace originals after successful export",deleteOriginals) { on ->
            if(on) AlertDialog.Builder(this).setTitle("Permanently delete originals?").setMessage("After each censored output is saved, BodyBlock will request deletion of its source document. This may be irreversible. Files without deletion permission remain intact.").setPositiveButton("Enable replacement") { _,_ -> deleteOriginals=true; draw() }.setNegativeButton("Keep originals") { _,_ -> deleteOriginals=false; draw() }.setOnCancelListener { deleteOriginals=false; draw() }.show() else deleteOriginals=false
        }
        card.addView(Ui.button(this,"Choose images",true) { if(!exporting) pick("image/*",101,true) else notify("An export is already running") })
        card.addView(Ui.button(this,"Choose folder") { if(!exporting) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),105) })
        card.addView(Ui.button(this,"Cancel remaining exports") { cancelled.set(true) })
        exportLabel=Ui.text(this,exportStatus,14f,Ui.pink); card.addView(exportLabel)
        preview=ImageView(this).apply { adjustViewBounds=true; contentDescription="Most recently censored image" }; card.addView(preview)
        val note=Ui.card(content,"OUTPUT DETAILS")
        note.addView(Ui.text(this,"Up to 4096 pixels on the longest edge. Metadata is omitted. Animated images are exported as a still. Folder processing includes immediate child images. Originals are kept unless replacement is explicitly enabled.",13f,Ui.muted))
    }
    private fun help() {
        listOf(
            "GET STARTED" to "1. Choose body parts and an effect in Settings.\n2. Enable the BodyBlock Accessibility service.\n3. Tap Start protection and choose ONE APP.\n4. Open that app. Stop from Home or the ongoing notification.",
            "BROWSER" to "Use the built-in browser for filtered browsing, bookmarks and censored image downloads. Private browsing runs in a separate process and clears its browsing data on exit. Long-press an image to save a censored copy.",
            "WHAT THE PROTOTYPE CAN DO" to "18 body and face categories plus eye landmarks; eight effects; reverse censoring; profiles and portable packs; image pools and phrases; live app capture; browser tabs, bookmarks and a basic ad-domain blocklist; batch exports; local statistics and eight achievement milestones.",
            "LIMITATIONS" to "Filtering reacts after content appears and can miss it. Protected apps may refuse capture. Use fullscreen app capture; multi-window and vendor layouts need device testing. The browser cannot guarantee video, DRM or canvas coverage. Blur uses a fast filtered downsampling effect. No video recording/export. Original-app packs and its full achievement catalogue are not reproduced. UI is currently English.",
            "PRIVACY" to "Inference is local. No analytics or account is added. Captured frames are not saved by live protection. Only exports and custom assets are stored. Browsing makes ordinary website requests; third-party libraries may have their own diagnostics behaviour. Network auditing remains part of device validation.",
            "MODEL & LICENCES" to "NudeNet 320n model, ONNX Runtime, and bundled ML Kit face landmarks. NudeNet is distributed under its upstream AGPL licence; source and third-party notices are included with this prototype repository. This is an independent prototype, not an official Beta Blocker release."
        ).forEach { (title,body) -> Ui.card(content,title).addView(Ui.text(this,body,14f,Ui.muted)) }
        val browser=Ui.card(content,"PRIVATE BROWSER")
        browser.addView(Ui.button(this,"Open private window") { openBrowser(true) })
    }
    private fun pick(type: String,request: Int,multiple: Boolean) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),request)
    }
    @Deprecated("Platform result API") override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(result!=RESULT_OK || data==null) return
        if(request==100) { startForegroundService(Intent(this,CaptureService::class.java).putExtra("consent",data)); handler.postDelayed({ draw() },300); return }
        val uri=data.data
        when(request) {
            101 -> { val uris=data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(uri); export(uris) }
            102 -> if(uri!=null) background("Importing image") { val name=MediaFiles.importImage(this,uri); change { it.copy(images=(it.images+name).takeLast(20)) }; "Custom image imported" }
            103 -> if(uri!=null) background("Writing pack") { MediaFiles.exportPack(this,uri,store.config()); store.event("packs"); "Pack saved" }
            104 -> if(uri!=null) background("Reading pack") { store.save(MediaFiles.importPack(this,uri)); "Pack imported" }
            105 -> if(uri!=null) {
                io.execute {
                    try {
                        val child=DocumentsContract.buildChildDocumentsUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri)); val list=mutableListOf<Uri>()
                        contentResolver.query(child,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { cursor -> while(cursor.moveToNext()) if(cursor.getString(1)?.startsWith("image/")==true) list.add(DocumentsContract.buildDocumentUriUsingTree(uri,cursor.getString(0))) }
                        runOnUiThread { export(list) }
                    } catch(e: Exception) { runOnUiThread { notify("Cannot read folder: ${e.message}") } }
                }
            }
        }
    }
    private fun background(label: String,block: ()->String) { notify(label); io.execute { val result=runCatching(block).getOrElse { "Failed: ${it.message}" }; runOnUiThread { notify(result); if(!isFinishing) draw() } } }
    private fun export(uris: List<Uri>) {
        if(exporting || uris.isEmpty()) { notify(if(exporting) "Export already running" else "No images selected"); return }
        exporting=true; cancelled.set(false); tab="Export"; exportStatus="Preparing ${uris.size} images…"; draw()
        val c=store.config(); val remove=deleteOriginals
        io.execute {
            var saved=0; var failures=0; var retained=0; val errors=mutableListOf<String>()
            try {
                Detector(this).use { detector ->
                    val renderer=EffectRenderer(this)
                    try {
                        for((index,uri) in uris.withIndex()) {
                            if(cancelled.get()) break
                            var source: Bitmap?=null; var mask: Bitmap?=null
                            try {
                                source=MediaFiles.decode(this,uri)
                                mask=renderer.render(source,detector.detect(source,c),c)
                                val output=source.copy(Bitmap.Config.ARGB_8888,true)
                                try { Canvas(output).drawBitmap(mask,0f,0f,null); val savedUri=MediaFiles.save(this,output)
                                    saved++; store.event("exports")
                                    if(remove && !runCatching { DocumentsContract.deleteDocument(contentResolver,uri) }.getOrDefault(false)) retained++
                                    runOnUiThread { preview?.setImageURI(savedUri) }
                                } finally { output.recycle() }
                            } catch(e: Exception) { failures++; errors.add("Image ${index+1}: ${e.message?.take(100)}") }
                            finally { source?.recycle(); mask?.recycle() }
                            val progress="${index+1}/${uris.size} processed · $saved saved · $failures failed"
                            runOnUiThread { exportStatus=progress; exportLabel?.text=progress }
                        }
                    } finally { renderer.close() }
                }
            } catch(e: Exception) { errors.add(e.message ?: "Cannot start detector") }
            val summary="${if(cancelled.get()) "Cancelled" else "Finished"} · $saved saved · $failures failed"+(if(retained>0) "\n$retained originals retained (deletion not permitted)." else "")+errors.take(3).joinToString("\n",if(errors.isNotEmpty()) "\n" else "")
            runOnUiThread { exporting=false; exportStatus=summary; exportLabel?.text=summary; notify(summary) }
        }
    }
    override fun onDestroy() { cancelled.set(true); handler.removeCallbacksAndMessages(null); io.shutdown(); super.onDestroy() }
}
