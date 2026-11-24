package app.quran.tilawa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationTest {
    @Test
    fun configuredUrlIsValid() {
        assertTrue(Donation.isValidUrl(Donation.URL))
    }

    @Test
    fun acceptsHttpsUrls() {
        assertTrue(Donation.isValidUrl("https://ko-fi.com/tilawa"))
        assertTrue(Donation.isValidUrl("https://paypal.me/tilawa"))
    }

    @Test
    fun rejectsInvalidUrls() {
        assertFalse(Donation.isValidUrl(""))
        assertFalse(Donation.isValidUrl("https://"))
        assertFalse(Donation.isValidUrl("http://example.com/donate"))
        assertFalse(Donation.isValidUrl("ko-fi.com/tilawa"))
        assertFalse(Donation.isValidUrl("https://example.com/my donate"))
    }

    @Test
    fun placeholderUrlDisablesDonation() {
        assertTrue(Donation.isPlaceholder("https://example.com/donate"))
        assertTrue(Donation.isPlaceholder("https://www.example.com/x"))
        assertFalse(Donation.isEnabled("https://example.com/donate"))
        assertFalse(Donation.isEnabled(""))
    }

    @Test
    fun realUrlEnablesDonation() {
        assertFalse(Donation.isPlaceholder("https://ko-fi.com/tilawa"))
        assertTrue(Donation.isEnabled("https://ko-fi.com/tilawa"))
    }

    @Test
    fun currentPlaceholderKeepsMenuHidden() {
        // حتى يُستبدل الرابط المؤقت يبقى خيار التبرع مخفياً
        if (Donation.isPlaceholder(Donation.URL)) assertFalse(Donation.isEnabled())
    }
}
