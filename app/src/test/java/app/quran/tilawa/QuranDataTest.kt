package app.quran.tilawa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahsTest {
    @Test
    fun has114SurahsNumberedInOrder() {
        assertEquals(114, Surahs.all.size)
        Surahs.all.forEachIndexed { i, s -> assertEquals(i + 1, s.number) }
    }

    @Test
    fun totalAyahCountIs6236() {
        assertEquals(6236, Surahs.all.sumOf { it.ayahCount })
    }

    @Test
    fun knownSurahs() {
        with(Surahs.get(1)) { assertEquals("الفاتحة", name); assertEquals(7, ayahCount); assertEquals("مكية", revelation) }
        with(Surahs.get(2)) { assertEquals("البقرة", name); assertEquals(286, ayahCount); assertEquals("مدنية", revelation) }
        with(Surahs.get(114)) { assertEquals("الناس", name); assertEquals(6, ayahCount) }
    }

    @Test
    fun medinanCountIs28() {
        assertEquals(28, Surahs.all.count { it.medinan })
    }

    @Test
    fun separateBasmalaExceptFatihaAndTawbah() {
        assertFalse(Surahs.get(1).hasSeparateBasmala)
        assertFalse(Surahs.get(9).hasSeparateBasmala)
        assertTrue(Surahs.get(2).hasSeparateBasmala)
        assertTrue(Surahs.get(114).hasSeparateBasmala)
    }

    @Test
    fun emptySearchReturnsAll() {
        assertEquals(114, Surahs.search("  ").size)
    }

    @Test
    fun searchByLatinOrArabicNumberMatchesPrefix() {
        assertEquals(listOf(11, 110, 111, 112, 113, 114), Surahs.search("11").map { it.number })
        assertEquals(Surahs.search("11"), Surahs.search("١١"))
    }

    @Test
    fun searchByNameIgnoresHamzaAndTaMarbuta() {
        assertEquals(listOf(3), Surahs.search("ال عمران").map { it.number })
        assertEquals(listOf(2), Surahs.search("البقره").map { it.number })
        assertEquals(listOf(14), Surahs.search("ابراهيم").map { it.number })
    }

    @Test
    fun searchWithSurahPrefix() {
        assertEquals(listOf(36), Surahs.search("سورة يس").map { it.number })
    }
}

class RecitersTest {
    @Test
    fun byIdFallsBackToFirst() {
        assertEquals("abdulbasit", Reciters.byId("abdulbasit").id)
        assertEquals(Reciters.all.first(), Reciters.byId(null))
        assertEquals(Reciters.all.first(), Reciters.byId("unknown"))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Reciters.all.size, Reciters.all.map { it.id }.toSet().size)
    }
}

class ArabicTest {
    @Test
    fun normalizeRemovesDiacriticsAndUnifiesLetters() {
        assertEquals("بسم الله الرحمن الرحيم", Arabic.normalize(Arabic.BASMALA))
        assertEquals("امنوا", Arabic.normalize("آمَنُوا۟"))
        assertEquals("الهدي", Arabic.normalize("ٱلْهُدَىٰ"))
        assertEquals("رحمه", Arabic.normalize("رَحْمَةً"))
        assertEquals("ابرهيم", Arabic.normalize("إِبْرَٰهِيمَ"))
    }

    @Test
    fun normalizeStripsBomAndTatweel() {
        assertEquals("الله", Arabic.normalize("﻿اللـه "))
    }

    @Test
    fun digitsConvertsToArabicIndic() {
        assertEquals("٠", Arabic.digits(0))
        assertEquals("٢٨٦", Arabic.digits(286))
    }

    @Test
    fun parseNumberAcceptsBothDigitSets() {
        assertEquals(114, Arabic.parseNumber("114"))
        assertEquals(114, Arabic.parseNumber("١١٤"))
        assertEquals(12, Arabic.parseNumber("1٢"))
        assertNull(Arabic.parseNumber("يس"))
    }

    @Test
    fun ayahCountLabelUsesPluralFor3To10() {
        assertEquals("٧ آيات", Arabic.ayahCountLabel(7))
        assertEquals("١٠ آيات", Arabic.ayahCountLabel(10))
        assertEquals("١١ آية", Arabic.ayahCountLabel(11))
        assertEquals("٢٨٦ آية", Arabic.ayahCountLabel(286))
    }

    @Test
    fun stripBasmalaRemovesLeadingBasmala() {
        val first = "﻿" + Arabic.BASMALA + " الٓمٓ"
        assertEquals("الٓمٓ", Arabic.stripBasmala(first))
    }

    @Test
    fun stripBasmalaKeepsOtherText() {
        val ayah = "ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ"
        assertEquals(ayah, Arabic.stripBasmala(ayah))
    }

    @Test
    fun stripBasmalaKeepsBasmalaAlone() {
        // الفاتحة: الآية الأولى هي البسملة نفسها فلا تُحذف
        assertEquals(Arabic.BASMALA, Arabic.stripBasmala(Arabic.BASMALA))
    }
}
