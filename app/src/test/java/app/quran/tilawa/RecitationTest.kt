package app.quran.tilawa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationTest {
    // البقرة ٣ بنص مجمع الملك فهد (text_qpc_hafs) كما تعرضه الصفحة
    private val baqarah3 = "ٱلَّذِينَ يُؤۡمِنُونَ بِٱلۡغَيۡبِ وَيُقِيمُونَ ٱلصَّلَوٰةَ وَمِمَّا رَزَقۡنَٰهُمۡ يُنفِقُونَ ٣"
    private val expected = Recitation.words(baqarah3).map { it.second }

    @Test
    fun ayahNumberIsNotAWord() {
        assertEquals(8, expected.size)
        assertEquals(8, Recitation.displayWords(baqarah3).size)
    }

    @Test
    fun uthmaniSpellingMatchesModernSpelling() {
        assertTrue(Recitation.similar(Recitation.normalize("ٱلصَّلَوٰةَ"), Recitation.normalize("الصلاة")))
        assertTrue(Recitation.similar(Recitation.normalize("ٱلۡكِتَٰبُ"), Recitation.normalize("الكتاب")))
        assertTrue(Recitation.similar(Recitation.normalize("رَزَقۡنَٰهُمۡ"), Recitation.normalize("رزقناهم")))
        assertFalse(Recitation.similar(Recitation.normalize("ٱلصَّلَوٰةَ"), Recitation.normalize("الزكاة")))
    }

    @Test
    fun fullRecitationCompletesAyah() {
        val heard = Recitation.heardWords("الذين يؤمنون بالغيب ويقيمون الصلاة ومما رزقناهم ينفقون")
        val p = Recitation.progress(expected, heard)
        assertTrue(p.complete)
        assertTrue(p.matched.all { it >= 0 })
        assertEquals(heard.size, p.consumed)
    }

    @Test
    fun partialRecitationDoesNotComplete() {
        val p = Recitation.progress(expected, Recitation.heardWords("الذين يؤمنون بالغيب"))
        assertFalse(p.complete)
    }

    @Test
    fun skippedWordIsReportedAsMissed() {
        val p = Recitation.progress(expected, Recitation.heardWords("الذين يؤمنون بالغيب الصلاة ومما رزقناهم ينفقون"))
        assertTrue(p.complete)
        assertEquals(-1, p.matched[3]) // ويقيمون
        assertEquals(7, p.matched.count { it >= 0 })
    }

    @Test
    fun wordsAfterTheAyahAreLeftForTheNextOne() {
        val heard = Recitation.heardWords("الذين يؤمنون بالغيب ويقيمون الصلاة ومما رزقناهم ينفقون والذين يؤمنون")
        val p = Recitation.progress(expected, heard)
        assertTrue(p.complete)
        assertEquals(8, p.consumed)
    }
}
