package dev.bodyblock.prototype

import android.content.*
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import org.json.JSONArray
import java.io.*
import java.util.zip.*
import java.util.UUID

object MediaFiles {
    fun decode(context: Context,uri: Uri,max: Int=4096): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        val largest=kotlin.math.max(info.size.width,info.size.height)
        if(largest>max) decoder.setTargetSize((info.size.width.toLong()*max/largest).toInt().coerceAtLeast(1),(info.size.height.toLong()*max/largest).toInt().coerceAtLeast(1))
    }
    fun decode(bytes: ByteArray,max: Int=4096): Bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder,info,_ ->
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        val largest=kotlin.math.max(info.size.width,info.size.height)
        if(largest>max) decoder.setTargetSize((info.size.width.toLong()*max/largest).toInt().coerceAtLeast(1),(info.size.height.toLong()*max/largest).toInt().coerceAtLeast(1))
    }
    fun save(context: Context,bitmap: Bitmap): Uri {
        val resolver=context.contentResolver
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"BodyBlock_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BodyBlock"); put(MediaStore.Images.Media.IS_PENDING,1) }
        val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Cannot create output image")
        try {
            resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "Encoding failed" } }
            val checkBounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,checkBounds) }
            check(checkBounds.outWidth==bitmap.width && checkBounds.outHeight==bitmap.height) { "Output verification failed" }
            decode(context,uri,64).recycle() // Decode, not just the header, before permitting source deletion.
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)==1) { "Output publication failed" }
            return uri
        } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
    }
    fun importImage(context: Context,uri: Uri): String {
        val bitmap=decode(context,uri,1024)
        val name="asset_${UUID.randomUUID()}.png"
        try { File(context.filesDir,name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
        return name
    }
    fun exportPack(context: Context,uri: Uri,c: Config) {
        ZipOutputStream(context.contentResolver.openOutputStream(uri)!!).use { zip ->
            zip.putNextEntry(ZipEntry("profile.json")); zip.write(JSONObject().put("format","bodyblock").put("version",1).put("config",c.json()).toString(2).toByteArray()); zip.closeEntry()
            c.images.forEach { name -> val f=File(context.filesDir,name); if(f.isFile) { zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } }
        }
    }
    fun importPack(context: Context,uri: Uri): Config {
        var manifest: JSONObject?=null; var total=0; val entries=linkedMapOf<String,ByteArray>()
        ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                require(entries.size<22) { "Too many pack files" }
                require(entry.name=="profile.json" || entry.name.matches(Regex("asset_[a-zA-Z0-9_-]+\\.png"))) { "Unsupported pack entry" }
                require(!entries.containsKey(entry.name)) { "Duplicate pack entry" }
                val output=ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) { val n=zip.read(buffer); if(n<0) break; total+=n; require(total<=30*1024*1024) { "Pack exceeds 30 MB" }; output.write(buffer,0,n) }
                entries[entry.name]=output.toByteArray()
            }
        }
        manifest=JSONObject(entries["profile.json"]?.toString(Charsets.UTF_8) ?: error("Missing profile.json"))
        require(manifest.getString("format")=="bodyblock" && manifest.getInt("version")==1) { "Unsupported pack version" }
        val c=Config.parse(manifest.getJSONObject("config"))
        require(c.images.all { entries.containsKey(it) }) { "Missing image asset" }
        val imported=mutableListOf<String>()
        try {
            for(name in c.images) {
                val bitmap=decode(entries.getValue(name),1024); val fresh="asset_${UUID.randomUUID()}.png"
                try { File(context.filesDir,fresh).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; imported.add(fresh) } finally { bitmap.recycle() }
            }
            return c.copy(images=imported)
        } catch(e: Exception) { imported.forEach { File(context.filesDir,it).delete() }; throw e }
    }
}
