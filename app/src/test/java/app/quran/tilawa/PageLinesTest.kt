package app.quran.tilawa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLinesTest {

    /** آية بكلماتها الموزّعة على أسطرها؛ كل كلمة سطر واحد */
    private fun ayah(surah: Int, n: Int, lines: List<Int>) = PageAyah(
        surah = surah,
        numberInSurah = n,
        text = "",
        juz = 1,
        page = 77,
        hizbQuarter = 1,
        words = lines.map { "w" },
        lines = lines,
    )

    private fun surahStartsLines(lines: List<PageLine>) = lines.mapIndexedNotNull { i, l ->
        if (l is PageLine.Frame) i + 1 to l.surah else null
    }

    @Test
    fun surahStartWithSingleEmptyLineStillGetsFrame() {
        // مثل النساء في الصفحة ٧٧: السطر الأول فارغ وأول آية في السطر الثاني
        val page = listOf(ayah(4, 1, listOf(2, 2, 2)), ayah(4, 2, listOf(4, 4, 4)))
        val lines = pageLines(77, page)

        assertTrue("السطر الأول يجب أن يكون إطار السورة", lines[0] is PageLine.Frame)
        assertEquals(4, (lines[0] as PageLine.Frame).surah)
    }

    @Test
    fun surahStartWithTwoEmptyLinesGetsFrameThenBasmala() {
        val page = listOf(ayah(4, 1, listOf(3, 3)), ayah(4, 2, listOf(5, 5)))
        val lines = pageLines(77, page)

        assertTrue(lines[0] is PageLine.Frame)
        assertTrue(lines[1] is PageLine.Basmala)
        assertEquals(4, (lines[1] as PageLine.Basmala).surah)
    }

    @Test
    fun everySurahStartOnPageGetsFrame() {
        // آخر آية من سورة قبل السطر الفارغ، ثم بداية سورة جديدة في منتصف الصفحة
        val page = listOf(
            ayah(2, 285, listOf(1, 1)),
            ayah(3, 1, listOf(3, 3)),
            ayah(3, 2, listOf(4, 4)),
        )
        val lines = pageLines(77, page)

        assertEquals(listOf(2 to 3), surahStartsLines(lines))
    }

    @Test
    fun pageWithoutSurahStartHasNoFrame() {
        val page = listOf(ayah(4, 5, listOf(2, 2)), ayah(4, 6, listOf(3, 3)))
        val lines = pageLines(77, page)

        assertTrue(lines.none { it is PageLine.Frame || it is PageLine.Basmala })
    }
}
