package app.quran.tilawa

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

object Donation {
    // TODO: ضع رابط صفحة التبرع الحقيقي قبل النشر (يجب أن يبدأ بـ https://)
    const val URL = "https://example.com/donate"

    /** لا يُعرض زر الفتح إلا لرابط https صالح، حتى لا يُفتح رابط ناقص أو غير آمن */
    fun isValidUrl(url: String): Boolean =
        url.startsWith("https://") && url.length > "https://".length && url.none { it.isWhitespace() }

    /** رابط مثال لم يُستبدل بعد (example.com وما شابهه) */
    fun isPlaceholder(url: String): Boolean {
        val host = url.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore(':').lowercase()
        return host == "example.com" || host.endsWith(".example.com") ||
            host == "example.org" || host == "example.net"
    }

    /** يُعرض خيار التبرع فقط عند وجود رابط حقيقي صالح (سياسة Google Play للمدفوعات) */
    fun isEnabled(url: String = URL): Boolean = isValidUrl(url) && !isPlaceholder(url)
}

/** نافذة التبرع: تشرح الغرض وتفتح صفحة التبرع في المتصفح */
@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val valid = Donation.isValidUrl(Donation.URL)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دعم التطبيق") },
        text = {
            Text(
                "التطبيق مجاني وبلا إعلانات. تبرّعك يساعد على استمراره وتطويره، " +
                    "وستُفتح صفحة التبرع في المتصفح. جزاك الله خيراً."
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onDismiss()
                    runCatching { uriHandler.openUri(Donation.URL) }
                },
            ) { Text("تبرّع الآن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لاحقاً") } },
    )
}
