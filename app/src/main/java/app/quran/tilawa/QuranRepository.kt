package app.quran.tilawa

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * مصدر البيانات: api.alquran.cloud (نص المصحف بالرسم العثماني من مشروع تنزيل، وروابط التلاوات آية بآية).
 * كل استجابة تُحفظ محلياً بعد أول تحميل، فيعمل النص بعدها دون إنترنت.
 */
class QuranRepository(context: Context) {
    private val base = "https://api.alquran.cloud/v1"
    private val dir = File(context.filesDir, "quran").apply { mkdirs() }

    suspend fun getAyahs(surah: Int): List<Ayah> = withContext(Dispatchers.IO) {
        val json = cachedOrFetch("text_$surah.json", "$base/surah/$surah/quran-uthmani")
        val arr = json.getJSONObject("data").getJSONArray("ayahs")
        val hasBasmala = Surahs.get(surah).hasSeparateBasmala
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val n = o.getInt("numberInSurah")
            val raw = o.getString("text")
            Ayah(
                numberInSurah = n,
                globalNumber = o.getInt("number"),
                text = if (n == 1 && hasBasmala) Arabic.stripBasmala(raw) else raw.replace("﻿", ""),
                juz = o.optInt("juz"),
                page = o.optInt("page"),
            )
        }
    }

    /** روابط ملفات الصوت مرتبة حسب رقم الآية داخل السورة */
    suspend fun getAudioUrls(surah: Int, reciter: Reciter): List<String> = withContext(Dispatchers.IO) {
        val json = cachedOrFetch("audio_${reciter.edition}_$surah.json", "$base/surah/$surah/${reciter.edition}")
        val arr = json.getJSONObject("data").getJSONArray("ayahs")
        List(arr.length()) { i -> arr.getJSONObject(i).getString("audio") }
    }

    /** تحميل نص المصحف كاملاً في طلب واحد وتقسيمه إلى ملفات السور */
    suspend fun downloadAllText() = withContext(Dispatchers.IO) {
        val root = JSONObject(httpGet("$base/quran/quran-uthmani"))
        check(root.optInt("code") == 200) { "Unexpected response" }
        val surahs = root.getJSONObject("data").getJSONArray("surahs")
        for (i in 0 until surahs.length()) {
            val s = surahs.getJSONObject(i)
            val wrapped = JSONObject()
                .put("code", 200)
                .put("data", JSONObject().put("ayahs", s.getJSONArray("ayahs")))
            File(dir, "text_${s.getInt("number")}.json").writeText(wrapped.toString())
        }
    }

    fun isTextCached(surah: Int) = File(dir, "text_$surah.json").let { it.exists() && it.length() > 0 }

    private fun cachedOrFetch(name: String, url: String): JSONObject {
        val file = File(dir, name)
        if (file.exists() && file.length() > 0) {
            runCatching { return JSONObject(file.readText()) }
            file.delete() // ملف تالف: نعيد التحميل
        }
        val body = httpGet(url)
        val json = JSONObject(body)
        check(json.optInt("code") == 200) { "Unexpected response" }
        val ayahs: JSONArray = json.getJSONObject("data").getJSONArray("ayahs")
        check(ayahs.length() > 0) { "Empty response" }
        file.writeText(body)
        return json
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tilawa", Context.MODE_PRIVATE)

    var reciterId: String?
        get() = sp.getString("reciter", null)
        set(v) = sp.edit().putString("reciter", v).apply()

    var fontSize: Float
        get() = sp.getFloat("font_size", 26f)
        set(v) = sp.edit().putFloat("font_size", v).apply()

    var autoNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) = sp.edit().putBoolean("auto_next", v).apply()

    val lastSurah: Int get() = sp.getInt("last_surah", 0)
    val lastAyah: Int get() = sp.getInt("last_ayah", 0)

    fun saveLast(surah: Int, ayah: Int) {
        sp.edit().putInt("last_surah", surah).putInt("last_ayah", ayah).apply()
    }
}
