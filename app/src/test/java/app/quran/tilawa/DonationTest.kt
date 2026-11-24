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
}
