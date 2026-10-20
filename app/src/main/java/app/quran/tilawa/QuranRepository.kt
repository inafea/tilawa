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
 * مصادر البيانات: api.alquran.cloud (فهرسة السور والصفحات، وروابط التلاوات آية بآية)،
 * و api.quran.com (نص صفحات المصحف بترميز مجمع الملك فهد).
 * كل استجابة تُحفظ محلياً بعد أول تحميل، فيعمل النص بعدها دون إنترنت.
 */
class QuranRepository(context: Context) {
    private val base = "https://api.alquran.cloud/v1"
    private val qpcBase = "https://api.quran.com/api/v4"
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

    /**
     * آيات صفحة من المصحف بنص مجمع الملك فهد (من api.quran.com) ليُعرض بخط المصحف.
     * نص المجمع لا يتضمن البسملة، وينتهي كل آية برقمها فيرسمه الخط داخل علامة نهاية الآية.
     */
    suspend fun getPage(page: Int): List<PageAyah> = withContext(Dispatchers.IO) {
        val json = cachedOrFetch("qpc_page_$page.json") { fetchQpcPage(page) }
        val arr = json.getJSONObject("data").getJSONArray("ayahs")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PageAyah(
                surah = o.getJSONObject("surah").getInt("number"),
                numberInSurah = o.getInt("numberInSurah"),
                text = o.getString("text"),
                juz = o.optInt("juz"),
                page = o.optInt("page", page),
                hizbQuarter = o.optInt("hizbQuarter"),
            )
        }
    }

    /** يجلب الصفحة من api.quran.com ويحوّلها لنفس شكل استجابة alquran.cloud ليُحفظ ويُقرأ بطريقة واحدة */
    private fun fetchQpcPage(page: Int): String {
        val ayahs = JSONArray()
        var p = 1
        while (true) {
            val root = JSONObject(httpGet("$qpcBase/verses/by_page/$page?fields=text_qpc_hafs&per_page=50&page=$p"))
            val verses = root.getJSONArray("verses")
            for (i in 0 until verses.length()) {
                val v = verses.getJSONObject(i)
                val (surah, n) = v.getString("verse_key").split(':').map { it.toInt() }
                ayahs.put(qpcAyah(surah, n, v.getString("text_qpc_hafs"), v.optInt("juz_number"), v.optInt("page_number", page), v.optInt("rub_el_hizb_number")))
            }
            p = root.getJSONObject("pagination").optInt("next_page", 0).takeIf { it > 0 } ?: break
        }
        return wrap(ayahs).toString()
    }

    private fun qpcAyah(surah: Int, n: Int, text: String, juz: Int, page: Int, hizbQuarter: Int) = JSONObject()
        .put("surah", JSONObject().put("number", surah))
        .put("numberInSurah", n)
        .put("text", text)
        .put("juz", juz)
        .put("page", page)
        .put("hizbQuarter", hizbQuarter)

    private fun wrap(ayahs: JSONArray) = JSONObject().put("code", 200).put("data", JSONObject().put("ayahs", ayahs))

    /** روابط ملفات الصوت مرتبة حسب رقم الآية داخل السورة */
    suspend fun getAudioUrls(surah: Int, reciter: Reciter): List<String> = withContext(Dispatchers.IO) {
        val json = cachedOrFetch("audio_${reciter.edition}_$surah.json", "$base/surah/$surah/${reciter.edition}")
        val arr = json.getJSONObject("data").getJSONArray("ayahs")
        List(arr.length()) { i -> arr.getJSONObject(i).getString("audio") }
    }

    /**
     * تحميل نص المصحف كاملاً وتقسيمه إلى ملفات السور (للفهرسة) وملفات الصفحات (للعرض).
     * بيانات الصفحة والجزء والحزب من alquran.cloud، ونص الآيات من نص مجمع الملك فهد.
     */
    suspend fun downloadAllText() = withContext(Dispatchers.IO) {
        val root = JSONObject(httpGet("$base/quran/quran-uthmani"))
        check(root.optInt("code") == 200) { "Unexpected response" }
        val qpc = JSONObject(httpGet("$qpcBase/quran/verses/qpc_hafs")).getJSONArray("verses")
        val qpcText = HashMap<String, String>(qpc.length())
        for (i in 0 until qpc.length()) qpc.getJSONObject(i).let { qpcText[it.getString("verse_key")] = it.getString("text_qpc_hafs") }
        check(qpcText.size == 6236) { "Incomplete text" }

        val surahs = root.getJSONObject("data").getJSONArray("surahs")
        val pages = HashMap<Int, JSONArray>()
        for (i in 0 until surahs.length()) {
            val s = surahs.getJSONObject(i)
            val number = s.getInt("number")
            val ayahs = s.getJSONArray("ayahs")
            for (j in 0 until ayahs.length()) {
                val a = ayahs.getJSONObject(j)
                val n = a.getInt("numberInSurah")
                val page = a.getInt("page")
                pages.getOrPut(page) { JSONArray() }
                    .put(qpcAyah(number, n, qpcText.getValue("$number:$n"), a.optInt("juz"), page, a.optInt("hizbQuarter")))
            }
            File(dir, "text_$number.json").writeText(wrap(ayahs).toString())
        }
        for ((page, ayahs) in pages) File(dir, "qpc_page_$page.json").writeText(wrap(ayahs).toString())
    }

    fun isTextCached(surah: Int) = File(dir, "text_$surah.json").let { it.exists() && it.length() > 0 }

    private fun cachedOrFetch(name: String, url: String): JSONObject = cachedOrFetch(name) { httpGet(url) }

    private fun cachedOrFetch(name: String, fetch: () -> String): JSONObject {
        val file = File(dir, name)
        if (file.exists() && file.length() > 0) {
            runCatching { return JSONObject(file.readText()) }
            file.delete() // ملف تالف: نعيد التحميل
        }
        val body = fetch()
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

    var mushafFont: String?
        get() = sp.getString("mushaf_font", null)
        set(v) = sp.edit().putString("mushaf_font", v).apply()

    /** 0 = حسب النظام، 1 = فاتح، 2 = داكن */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var autoNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) = sp.edit().putBoolean("auto_next", v).apply()

    val lastSurah: Int get() = sp.getInt("last_surah", 0)
    val lastAyah: Int get() = sp.getInt("last_ayah", 0)

    fun saveLast(surah: Int, ayah: Int) {
        sp.edit().putInt("last_surah", surah).putInt("last_ayah", ayah).apply()
    }
}
