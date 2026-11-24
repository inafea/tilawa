package app.quran.tilawa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler

object About {
    /** صفحة سياسة الخصوصية المنشورة على GitHub Pages (مجلد docs) */
    const val PRIVACY_URL = "https://inafea.github.io/tilawa/privacy.html"

    /** نسب المحتوى والخطوط ومصادر الصوت (متطلبات التراخيص) */
    val credits = listOf(
        "نص المصحف: مشروع تنزيل (Tanzil.net) عبر AlQuran Cloud",
        "بيانات صفحات المصحف والخطوط: Quran.com / Quran Foundation",
        "التلاوات: AlQuran Cloud (Islamic Network) و MP3Quran.net",
        "الخطوط: KFGQPC (مجمع الملك فهد)، Amiri Quran (رخصة OFL)، وخط الثلث لأسماء السور",
    )
}

/** نافذة «حول التطبيق»: الإصدار، سياسة الخصوصية، ونسب المصادر */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حول تلاوة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (version.isNotEmpty()) Text("الإصدار $version")
                Text("مجاني وبلا إعلانات، ولا يجمع أي بيانات شخصية.")
                Text("المصادر:", style = MaterialTheme.typography.titleSmall)
                About.credits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { runCatching { uriHandler.openUri(About.PRIVACY_URL) } }) {
                Text("سياسة الخصوصية")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
