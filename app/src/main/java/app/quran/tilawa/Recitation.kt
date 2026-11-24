package app.quran.tilawa

import kotlin.math.ceil

/**
 * مقارنة تلاوة المستخدم (نص خدمة التعرف على الكلام) بنص الآية بالرسم العثماني.
 * المقارنة كلمة بكلمة بعد التوحيد، مع تسامح في الفروق الإملائية بين الرسم العثماني
 * والإملاء الحديث الذي تكتب به خدمة التعرف (الصلوة/الصلاة، الكتب/الكتاب).
 */
object Recitation {
    private val AYAH_NUMBER = Regex("""\s*[٠-٩]+$""")
    private val nonLetters = Regex("[^\\u0621-\\u064A]")
    private val spaces = Regex("\\s+")

    /** كلمات الآية كما تُعرض (دون رقم الآية)، بالترتيب نفسه الذي يرسمه المصحف */
    fun displayWords(text: String): List<String> = text.replace(AYAH_NUMBER, "").split(' ')

    /** كلمات الآية للمقارنة: (موضع الكلمة المعروضة، الكلمة بعد التوحيد)، وتُستبعد علامات الوقف المنفصلة */
    fun words(text: String): List<Pair<Int, String>> =
        displayWords(text).mapIndexedNotNull { i, w -> normalize(w).takeIf { it.isNotEmpty() }?.let { i to it } }

    fun heardWords(s: String): List<String> = s.split(spaces).map(::normalize).filter { it.isNotEmpty() }

    fun normalize(w: String): String = Arabic.normalize(w).replace(nonLetters, "")

    /** هيكل الكلمة دون الألف والهمزة المفردة: الرسم العثماني يحذف الألف كثيراً */
    private fun skeleton(w: String) = w.filter { it != 'ا' && it != 'ء' }

    fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val x = skeleton(a)
        val y = skeleton(b)
        if (x == y) return true
        val len = maxOf(x.length, y.length)
        val allowed = when {
            len <= 3 -> 0
            len <= 6 -> 1
            else -> 2
        }
        return distance(x, y) <= allowed
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** لكل كلمة متوقعة: موضع الكلمة المسموعة المطابقة لها أو -1 (أطول تتابع مشترك مع التسامح) */
    fun align(expected: List<String>, heard: List<String>): IntArray {
        val n = expected.size
        val m = heard.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (similar(expected[i], heard[j])) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = IntArray(n) { -1 }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                similar(expected[i], heard[j]) -> { out[i] = j; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }

    /**
     * [matched]: لكل كلمة متوقعة موضع مطابقتها أو -1.
     * [complete]: قُرئت الكلمة الأخيرة ومعظم الآية (٦٠٪ فأكثر)، فتُكشف الآية وينتقل إلى التالية.
     * [consumed]: عدد الكلمات المسموعة التي تخص هذه الآية؛ ما بعدها بداية الآية التالية.
     */
    class Progress(val matched: IntArray, val complete: Boolean, val consumed: Int)

    fun progress(expected: List<String>, heard: List<String>): Progress {
        if (expected.isEmpty()) return Progress(IntArray(0), complete = true, consumed = 0)
        val a = align(expected, heard)
        val count = a.count { it >= 0 }
        val last = a.last()
        val complete = last >= 0 && count >= ceil(expected.size * 0.6).toInt()
        return Progress(a, complete, if (complete) last + 1 else 0)
    }
}
