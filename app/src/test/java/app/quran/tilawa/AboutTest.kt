package app.quran.tilawa

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutTest {
    @Test
    fun privacyUrlIsHttpsAndReal() {
        assertTrue(Donation.isValidUrl(About.PRIVACY_URL))
        assertTrue(!Donation.isPlaceholder(About.PRIVACY_URL))
        assertTrue(About.PRIVACY_URL.endsWith("/privacy.html"))
    }

    @Test
    fun creditsCoverTextFontsAndAudio() {
        val all = About.credits.joinToString(" ")
        assertTrue(all.contains("Tanzil"))
        assertTrue(all.contains("OFL"))
        assertTrue(all.contains("MP3Quran"))
    }

    @Test
    fun privacyPageExistsInDocs() {
        // صفحة السياسة يجب أن تكون ضمن مجلد docs ليُنشرها GitHub Pages
        val page = listOf(File("../docs/privacy.html"), File("docs/privacy.html")).firstOrNull { it.exists() }
        assertTrue("docs/privacy.html مفقودة", page != null)
        val html = page!!.readText()
        assertTrue(html.contains("RECORD_AUDIO") || html.contains("الميكروفون"))
    }
}
