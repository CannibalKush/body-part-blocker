package dev.bodyblock.prototype

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Categories {
    val keys = listOf("FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED", "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE", "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED", "BUTTOCKS_COVERED", "EYES")
    val labels = listOf("Genitals · female, covered", "Female face", "Buttocks", "Breasts · female", "Genitals · female", "Chest · male", "Anus", "Feet", "Belly · covered", "Feet · covered", "Armpits · covered", "Armpits", "Male face", "Belly / stomach", "Genitals · male", "Anus · covered", "Breasts · covered", "Buttocks · covered", "Eyes")
    val defaults = setOf(2, 3, 4, 6, 14)
    val groups = linkedMapOf("EXPOSED" to listOf(4,14,3,2,6), "COVERED" to listOf(0,16,17,15), "FACES & EYES" to listOf(1,12,18), "BODY PARTS" to listOf(13,8,5,7,9,11,10))
}

data class Config(
    val enabled: Set<Int> = Categories.defaults,
    val style: String = "Solid", val intensity: Int = 75,
    val color: Int = 0xFF08060B.toInt(), val border: String = "Glow",
    val reverse: Boolean = false, val reverseStrength: Int = 100,
    val confidence: Int = 30, val padding: Int = 12, val preset: String = "Medium",
    val phrases: String = "PROTECTED\nCONTENT HIDDEN", val phraseCategory: String = "Custom",
    val phraseSeconds: Int = 4, val maskText: String = "Yes", val animate: Boolean = true,
    val diagnostics: Boolean = false, val images: List<String> = emptyList(),
    val adBlock: Boolean = true, val offsetX: Int = 0, val offsetY: Int = 0, val quality: String = "Detailed"
) {
    fun json() = JSONObject().apply {
        put("enabled", JSONArray(enabled.sorted())); put("style", style); put("intensity", intensity)
        put("color", color); put("border", border); put("reverse", reverse); put("reverseStrength", reverseStrength)
        put("confidence", confidence); put("padding", padding); put("preset", preset); put("phrases", phrases)
        put("phraseCategory", phraseCategory); put("phraseSeconds", phraseSeconds); put("maskText", maskText)
        put("animate", animate); put("diagnostics", diagnostics); put("images", JSONArray(images))
        put("quality",quality); put("adBlock", adBlock); put("offsetX", offsetX); put("offsetY", offsetY)
    }
    val intervalMs get() = when(preset) { "Low" -> 500L; "High" -> 120L; "Ultra" -> 65L; else -> 250L }
    val captureWidth get() = if(quality=="Detailed") 1024 else 640
    companion object {
        val styles = listOf("Solid", "Blur", "Pixelate", "Custom image", "Static", "Glitch", "Tape", "Error popup")
        fun parse(o: JSONObject): Config {
            val d = Config()
            return Config(
                o.optJSONArray("enabled")?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { it in Categories.keys.indices }.toSet() } ?: d.enabled,
                o.optString("style", d.style).takeIf { it in styles } ?: d.style, o.optInt("intensity",75).coerceIn(1,100),
                o.optInt("color",d.color) or 0xFF000000.toInt(), o.optString("border","Glow").takeIf { it in listOf("None","Line","Glow") } ?: "Glow",
                o.optBoolean("reverse"), o.optInt("reverseStrength",100).coerceIn(1,100),
                o.optInt("confidence",30).coerceIn(10,95), o.optInt("padding",12).coerceIn(0,50),
                o.optString("preset","Medium").takeIf { it in listOf("Low","Medium","High","Ultra") } ?: "Medium",
                o.optString("phrases",d.phrases).take(2000), o.optString("phraseCategory","Custom"),
                o.optInt("phraseSeconds",4).coerceIn(1,30), o.optString("maskText",if(o.optBoolean("showText",true)) "Yes" else "No").takeIf { it in listOf("Yes","No","Part") } ?: "Yes", o.optBoolean("animate",true),
                o.optBoolean("diagnostics"), o.optJSONArray("images")?.let { a -> (0 until a.length().coerceAtMost(20)).map { a.getString(it) }.filter { it.matches(Regex("asset_[a-zA-Z0-9_-]+\\.png")) } } ?: emptyList(),
                o.optBoolean("adBlock",true), o.optInt("offsetX",0).coerceIn(-150,150), o.optInt("offsetY",0).coerceIn(-150,150),
                o.optString("quality","Detailed").takeIf { it in listOf("Fast","Detailed") } ?: "Detailed"
            )
        }
    }
}

class Store(context: Context) {
    val prefs = context.getSharedPreferences("bodyblock", Context.MODE_PRIVATE)
    fun config(): Config = runCatching { Config.parse(JSONObject(prefs.getString("config", "{}")!!)) }.getOrDefault(Config())
    fun save(c: Config) { prefs.edit().putString("config", c.json().toString()).apply() }
    fun profiles() = runCatching { JSONObject(prefs.getString("profiles", "{}")!!) }.getOrDefault(JSONObject())
    fun saveProfile(name: String, c: Config) { val p=profiles(); p.put(name.take(60), c.json()); prefs.edit().putString("profiles",p.toString()).putString("profile",name.take(60)).apply(); event("profiles") }
    fun deleteProfile(name: String) { val p=profiles(); p.remove(name); prefs.edit().putString("profiles",p.toString()).apply() }
    @Synchronized fun event(key: String, n: Long = 1) { prefs.edit().putLong(key, prefs.getLong(key,0)+n).apply() }
    fun count(key: String) = prefs.getLong(key,0)
}

object LiveState {
    @Volatile var active = false
    @Volatile var status = "READY TO PROTECT"
    @Volatile var boxes = 0
    @Volatile var inferenceMs = 0L
    @Volatile var fps = 0f
    @Volatile var started = 0L
    @Volatile var blocks = 0L
}
