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
    private val fontBase = "https://verses.quran.foundation/fonts/quran/hafs/v2/ttf"
    private val dir = File(context.filesDir, "quran").apply { mkdirs() }
    private val fontsDir = File(dir, "fonts").apply { mkdirs() }

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
     * ومع كل آية رموز كلماتها في خط الصفحة وأرقام أسطرها، حتى تُعرض الصفحة سطراً بسطر بخط المصحف المطبوع.
     */
    suspend fun getPage(page: Int): List<PageAyah> = withContext(Dispatchers.IO) {
        val json = cachedOrFetch(pageFile(page)) { fetchQpcPage(page) }
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
                words = o.optJSONArray("words")?.let { w -> List(w.length()) { w.getString(it) } }.orEmpty(),
                lines = o.optJSONArray("lines")?.let { l -> List(l.length()) { l.getInt(it) } }.orEmpty(),
            )
        }
    }

    /** يجلب الصفحة من api.quran.com ويحوّلها لنفس شكل استجابة alquran.cloud ليُحفظ ويُقرأ بطريقة واحدة */
    /**
     * صفحة المصحف بتقسيم خطوط V2. تقسيم الصفحات في الواجهة (by_page) قديم ويخالف V2 في بعض الآيات
     * (٥٦ آية في ٣٥ صفحة، أكثرها في جزء عمّ)، فرموزها من خط صفحة مجاورة وتُرسم بخط هذه الصفحة كلمات أخرى.
     * لذلك نجمع آيات الصفحة والصفحتين المجاورتين ونأخذ منها ما صفحته في V2 هي هذه الصفحة.
     */
    private fun fetchQpcPage(page: Int): String {
        val ayahs = JSONArray()
        for (source in maxOf(1, page - 1)..minOf(Pages.COUNT, page + 1)) {
            var p = 1
            while (true) {
                val root = JSONObject(httpGet("$qpcBase/verses/by_page/$source?fields=text_qpc_hafs&words=true&word_fields=code_v2,line_v2,v2_page&per_page=50&page=$p"))
                val verses = root.getJSONArray("verses")
                for (i in 0 until verses.length()) {
                    val v = verses.getJSONObject(i)
                    val words = v.getJSONArray("words")
                    if (words.getJSONObject(0).getInt("v2_page") != page) continue
                    val (surah, n) = v.getString("verse_key").split(':').map { it.toInt() }
                    ayahs.put(
                        qpcAyah(surah, n, v.getString("text_qpc_hafs"), v.optInt("juz_number"), page, v.optInt("rub_el_hizb_number"))
                            .put("words", JSONArray(List(words.length()) { words.getJSONObject(it).getString("code_v2") }))
                            .put("lines", JSONArray(List(words.length()) { words.getJSONObject(it).getInt("line_v2") })),
                    )
                }
                p = root.getJSONObject("pagination").optInt("next_page", 0).takeIf { it > 0 } ?: break
            }
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

    /** ملف الصفحة مع رموز الكلمات وأرقام الأسطر (اسم جديد حتى لا تُقرأ ملفات النسخ السابقة، وفيها آيات في غير صفحتها) */
    private fun pageFile(page: Int) = "mushaf_v2p_page_$page.json"

    /** خط الصفحة من خطوط المصحف المطبوع (QPC V2 من مجمع الملك فهد)؛ لكل صفحة خط خاص بها */
    suspend fun getPageFont(page: Int): File = withContext(Dispatchers.IO) {
        val file = File(fontsDir, "p$page.ttf")
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(fontsDir, "p$page.ttf.tmp")
            val conn = URL("$fontBase/p$page.ttf").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            check(tmp.renameTo(file)) { "Cannot save font" }
        }
        file
    }

    private fun wrap(ayahs: JSONArray) = JSONObject().put("code", 200).put("data", JSONObject().put("ayahs", ayahs))

    /** صوت السورة بصوت القارئ: البسملة ثم الآيات مرتبة حسب رقمها في السورة */
    suspend fun getAudio(surah: Int, reciter: Reciter): SurahAudio = withContext(Dispatchers.IO) {
        val hasBasmala = Surahs.get(surah).hasSeparateBasmala
        val read = reciter.mp3quranRead
        val folder = reciter.mp3quranFolder
        if (read != null && folder != null) {
            // ملف واحد للسورة، وكل آية مقطع منه؛ ما قبل الآية الأولى هو البسملة
            val json = cachedOrFetch("audio_${reciter.edition}_$surah.json") { fetchTimings(surah, read) }
            val arr = json.getJSONObject("data").getJSONArray("ayahs")
            val url = folder + "%03d.mp3".format(java.util.Locale.ROOT, surah)
            val ayahs = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AyahAudio(url, o.getLong("start"), o.getLong("end").takeIf { i < arr.length() - 1 })
            }
            return@withContext SurahAudio(
                basmala = if (hasBasmala) AyahAudio(url, 0, ayahs.first().startMs) else null,
                ayahs = ayahs,
            )
        }
        val json = cachedOrFetch("audio_${reciter.edition}_$surah.json", "$base/surah/$surah/${reciter.edition}")
        val arr = json.getJSONObject("data").getJSONArray("ayahs")
        val urls = List(arr.length()) { i -> arr.getJSONObject(i).getString("audio") }
        // البسملة = الآية الأولى من الفاتحة (الرقم العام 1) بصوت نفس القارئ
        SurahAudio(
            basmala = if (hasBasmala) AyahAudio(urls.first().replace(Regex("/\\d+\\.mp3$"), "/1.mp3")) else null,
            ayahs = urls.map { AyahAudio(it) },
        )
    }

    /** توقيتات آيات السورة من mp3quran.net بنفس شكل الملفات المحفوظة الأخرى */
    private fun fetchTimings(surah: Int, read: Int): String {
        val arr = JSONArray(httpGet("https://mp3quran.net/api/v3/ayat_timing?surah=$surah&read=$read"))
        val ayahs = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getInt("ayah") < 1) continue
            ayahs.put(JSONObject().put("ayah", o.getInt("ayah")).put("start", o.getLong("start_time")).put("end", o.getLong("end_time")))
        }
        check(ayahs.length() == Surahs.get(surah).ayahCount) { "Incomplete timings" }
        return wrap(ayahs).toString()
    }

    /**
     * تحميل نص المصحف كاملاً: ملفات السور (للفهرسة) من alquran.cloud،
     * وملفات الصفحات وخطوطها (للعرض) من api.quran.com.
     */
    suspend fun downloadAllText() = withContext(Dispatchers.IO) {
        val root = JSONObject(httpGet("$base/quran/quran-uthmani"))
        check(root.optInt("code") == 200) { "Unexpected response" }
        val surahs = root.getJSONObject("data").getJSONArray("surahs")
        for (i in 0 until surahs.length()) {
            val s = surahs.getJSONObject(i)
            File(dir, "text_${s.getInt("number")}.json").writeText(wrap(s.getJSONArray("ayahs")).toString())
        }
        // أرقام الأسطر لا تأتي إلا مع الصفحة، فتُحمّل كل صفحة لم تُحفظ بعد
        for (page in 1..Pages.COUNT) {
            cachedOrFetch(pageFile(page)) { fetchQpcPage(page) }
            getPageFont(page)
        }
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
