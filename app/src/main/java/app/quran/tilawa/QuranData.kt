package app.quran.tilawa

data class Surah(
    val number: Int,
    val name: String,
    val ayahCount: Int,
    val medinan: Boolean,
) {
    val revelation: String get() = if (medinan) "مدنية" else "مكية"
    /** البسملة تُعرض وتُتلى قبل كل السور عدا الفاتحة (هي آيتها الأولى) والتوبة */
    val hasSeparateBasmala: Boolean get() = number != 1 && number != 9
}

data class Ayah(
    val numberInSurah: Int,
    val globalNumber: Int,
    val text: String,
    val juz: Int,
    val page: Int,
)

/** آية ضمن صفحة من المصحف (الصفحة قد تجمع أكثر من سورة) */
data class PageAyah(
    val surah: Int,
    val numberInSurah: Int,
    val text: String,
    val juz: Int,
    val page: Int,
    val hizbQuarter: Int,
    /** رموز كلمات الآية بترتيبها ثم علامة نهايتها، في خط صفحتها من خطوط المصحف المطبوع (QPC V2) */
    val words: List<String> = emptyList(),
    /** رقم سطر كل عنصر في words داخل صفحة المصحف المطبوع (١-١٥) */
    val lines: List<Int> = emptyList(),
)

/** ترقيم صفحات مصحف المدينة (٦٠٤ صفحات) */
object Pages {
    const val COUNT = 604

    private val surahStart = intArrayOf(
        1, 2, 50, 77, 106, 128, 151, 177, 187, 208, 221, 235, 249, 255, 262, 267, 282, 293, 305,
        312, 322, 332, 342, 350, 359, 367, 377, 385, 396, 404, 411, 415, 418, 428, 434, 440, 446,
        453, 458, 467, 477, 483, 489, 496, 499, 502, 507, 511, 515, 518, 520, 523, 526, 528, 531,
        534, 537, 542, 545, 549, 551, 553, 554, 556, 558, 560, 562, 564, 566, 568, 570, 572, 574,
        575, 577, 578, 580, 582, 583, 585, 586, 587, 587, 589, 590, 591, 591, 592, 593, 594, 595,
        595, 596, 596, 597, 597, 598, 598, 599, 599, 600, 600, 601, 601, 601, 602, 602, 602, 603,
        603, 603, 604, 604, 604,
    )

    fun startOf(surah: Int): Int = surahStart[surah - 1]

    /** السورة التي تبدأ بها الصفحة تقريبياً (قبل تحميل نصها) */
    fun surahAt(page: Int): Int = surahStart.indexOfLast { it <= page } + 1

    /** "الحزب ٢٩" أو "ربع الحزب ٤١" أو "نصف الحزب ..." أو "ثلاثة أرباع الحزب ..." */
    fun hizbLabel(hizbQuarter: Int): String {
        if (hizbQuarter <= 0) return ""
        val hizb = Arabic.digits((hizbQuarter - 1) / 4 + 1)
        return when ((hizbQuarter - 1) % 4) {
            0 -> "الحزب $hizb"
            1 -> "ربع الحزب $hizb"
            2 -> "نصف الحزب $hizb"
            else -> "ثلاثة أرباع الحزب $hizb"
        }
    }
}

data class Reciter(
    val id: String,
    val name: String,
    /** معرّف الإصدار الصوتي (آية بآية) في api.alquran.cloud */
    val edition: String,
    /**
     * للقرّاء غير المتوفرين آية بآية: رقم القارئ في mp3quran.net ومجلد ملفات السور فيه.
     * تُشغَّل كل آية مقطعاً من ملف السورة بحسب توقيتات الآيات من الموقع نفسه.
     */
    val mp3quranRead: Int? = null,
    val mp3quranFolder: String? = null,
)

/** صوت آية: ملفها، أو مقطع من ملف السورة (بالملّي ثانية؛ النهاية null = حتى آخر الملف) */
class AyahAudio(val url: String, val startMs: Long = 0, val endMs: Long? = null)

/** صوت سورة: البسملة (إن كانت لها بسملة منفصلة) ثم الآيات بالترتيب */
class SurahAudio(val basmala: AyahAudio?, val ayahs: List<AyahAudio>)

object Reciters {
    // لإضافة قارئ جديد: أضف سطراً بمعرّف إصدار صوتي من نوع "آية بآية"
    // مثل ar.husary أو ar.alafasy أو ar.minshawimujawwad
    val all = listOf(
        Reciter("minshawi", "محمد صديق المنشاوي", "ar.minshawi"),
        Reciter("abdulbasit", "عبد الباسط عبد الصمد", "ar.abdulbasitmurattal"),
        Reciter("husary", "محمود خليل الحصري", "ar.husary"),
        Reciter(
            "raad_kurdi", "رعد محمد الكردي", "mp3quran.raad-kurdi",
            mp3quranRead = 221, mp3quranFolder = "https://cdn.mp3quran.net/audio/raad-kurdi/r1/",
        ),
    )

    fun byId(id: String?): Reciter = all.firstOrNull { it.id == id } ?: all.first()
}

object Surahs {
    private val names = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
        "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
        "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
        "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
        "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
        "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
        "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
        "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
        "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
        "النبأ", "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج",
        "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح",
        "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر",
        "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس",
    )

    private val counts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98,
        135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11,
        11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25,
        22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6,
        3, 5, 4, 5, 6,
    )

    private val medinan = setOf(
        2, 3, 4, 5, 8, 9, 13, 22, 24, 33, 47, 48, 49, 55, 57, 58, 59, 60, 61, 62, 63, 64, 65,
        66, 76, 98, 99, 110,
    )

    val all: List<Surah> = names.mapIndexed { i, name ->
        Surah(i + 1, name, counts[i], (i + 1) in medinan)
    }

    fun get(number: Int): Surah = all[number - 1]

    fun search(query: String): List<Surah> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val asNumber = Arabic.parseNumber(q)
        if (asNumber != null) return all.filter { it.number.toString().startsWith(asNumber.toString()) }
        val nq = Arabic.normalize(q)
        return all.filter { Arabic.normalize(it.name).contains(nq) || Arabic.normalize("سورة " + it.name).contains(nq) }
    }
}

object Arabic {
    private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /** إزالة التشكيل وتوحيد أشكال الألف والتاء المربوطة والياء لأغراض البحث والمقارنة */
    fun normalize(s: String): String = s
        .replace("﻿", "")
        .replace(diacritics, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .trim()

    fun digits(n: Int): String = n.toString().map { c -> ('٠' + (c - '0')) }.joinToString("")

    fun parseNumber(s: String): Int? {
        val latin = s.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")
        return latin.toIntOrNull()
    }

    fun ayahCountLabel(n: Int): String = if (n in 3..10) "${digits(n)} آيات" else "${digits(n)} آية"

    /** في الرسم العثماني، الآية الأولى من كل سورة (عدا الفاتحة والتوبة) تبدأ بالبسملة؛ نفصلها لتُعرض كترويسة */
    fun stripBasmala(text: String): String {
        val clean = text.replace("﻿", "").trim()
        val words = clean.split(' ').filter { it.isNotBlank() }
        if (words.size > 4 && normalize(words.take(4).joinToString(" ")) == "بسم الله الرحمن الرحيم") {
            return words.drop(4).joinToString(" ")
        }
        return clean
    }

    /** بترميز مجمع الملك فهد (السكون U+06E1) ليتطابق مع خط المصحف */
    const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
}

/** أجزاء المصحف الثلاثون وبدايتها (السورة والآية) */
object Juzs {
    const val COUNT = 30
    private val starts = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    )

    /** بداية الجزء: (السورة، الآية) */
    fun start(juz: Int): Pair<Int, Int> = starts[juz - 1]

    /** السور التي فيها شيء من الجزء، بترتيبها (السورة المقسومة بين جزأين تظهر في الاثنين) */
    fun surahsOf(juz: Int): List<Surah> {
        val first = starts[juz - 1].first
        val last = starts.getOrNull(juz)?.let { (s, a) -> if (a == 1) s - 1 else s } ?: 114
        return (first..last).map { Surahs.get(it) }
    }
}
