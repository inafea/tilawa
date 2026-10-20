package app.quran.tilawa

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// =====================================================================
// مراجعة الحفظ: آيات السورة مخفية، ويقرأ المستخدم فتظهر كل آية بعد تلاوتها
// =====================================================================

/** نتيجة آية: مواضع الكلمات التي لم تُقرأ (في الكلمات المعروضة)، ونسبة الصواب، وهل كُشفت بطلب المستخدم */
class AyahResult(val missed: Set<Int>, val total: Int, val helped: Boolean) {
    val correct get() = total - missed.size
    val score get() = if (total == 0) 1f else correct.toFloat() / total
}

/**
 * مراجعة سورة كاملة. نص الآيات يأتي من صفحات المصحف المحمّلة ([textOf])،
 * فإن لم تُحمّل صفحة الآية المنتظرة بعد ننتظرها.
 */
class ReviewSession(
    context: Context,
    val surah: Int,
    val ayahCount: Int,
    private val textOf: (ayah: Int) -> String?,
) : RecognitionListener {
    private val expected = HashMap<Int, List<Pair<Int, String>>>()

    /** كلمات الآية ذات الترتيب [i] (من صفر) أو null إن لم تُحمّل صفحتها */
    private fun expectedAt(i: Int) = expected[i] ?: textOf(i + 1)?.let { Recitation.words(it).also { w -> expected[i] = w } }

    /** الآية الجاري انتظار تلاوتها؛ ما قبلها مكشوف وما بعدها مخفي */
    var current by mutableIntStateOf(0)
        private set
    val results = mutableStateListOf<AyahResult>()
    var listening by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** آخر ما سمعته الخدمة، يُعرض للمستخدم ليطمئن أنها تسمعه */
    var heardText by mutableStateOf("")
        private set
    val finished get() = current >= ayahCount

    // المسموع على دفعات: كل جلسة تعرّف تنتهي عند السكوت فنبدأ غيرها.
    // pending: كلمات جلسات سابقة لم تُنسب لآية بعد، sessionSkip: ما نُسب من كلمات الجلسة الحالية
    private var pending = emptyList<String>()
    private var partial = emptyList<String>()
    private var sessionSkip = 0

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // مهلة أطول للسكوت بين الآيات حتى لا تنقطع الجلسة عند كل وقف
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
    }

    fun indexOf(a: PageAyah) = if (a.surah == surah) a.numberInSurah - 1 else -1

    fun start() {
        error = null
        active = true
        listen()
    }

    private fun listen() {
        if (active && !finished) recognizer.startListening(intent)
    }

    fun stop() {
        active = false
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer.cancel()
    }

    fun destroy() {
        stop()
        recognizer.destroy()
    }

    /** كشف الآية الحالية لمن توقف عندها؛ تُحسب ما قُرئ منها حتى الآن ويُهمل باقي المسموع */
    fun reveal() {
        val words = if (finished) return else expectedAt(current) ?: return
        complete(Recitation.progress(words.map { it.second }, heard()).matched, helped = true)
        pending = emptyList()
        sessionSkip = partial.size
        if (finished) stop()
    }

    private fun heard() = pending + partial.drop(sessionSkip)

    private fun consume(n: Int) {
        val fromPending = minOf(n, pending.size)
        pending = pending.drop(fromPending)
        sessionSkip += n - fromPending
    }

    private fun complete(matched: IntArray, helped: Boolean) {
        val words = expectedAt(current) ?: return
        val missed = words.indices.filter { matched.getOrElse(it) { -1 } < 0 }.map { words[it].first }.toSet()
        results += AyahResult(missed, words.size, helped)
        current++
        // التلاوة الصحيحة بلا صوت، والخطأ بتنبيه خفيف (لا تنبيه عند كشف الآية بطلب المستخدم)
        if (!helped && missed.isNotEmpty()) SoftWarning.play()
    }

    /** قد تُتلى عدة آيات في جلسة واحدة، فنكشف كل ما اكتمل منها */
    private fun update() {
        while (!finished) {
            val words = expectedAt(current) ?: break
            val p = Recitation.progress(words.map { it.second }, heard())
            if (!p.complete) break
            complete(p.matched, helped = false)
            consume(p.consumed)
        }
        if (finished) stop()
    }

    private fun Bundle.text() = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    override fun onReadyForSpeech(params: Bundle?) { listening = true }

    override fun onPartialResults(partialResults: Bundle) {
        val text = partialResults.text()
        if (text.isBlank()) return
        heardText = text
        partial = Recitation.heardWords(text)
        update()
    }

    override fun onResults(results: Bundle) {
        val text = results.text()
        if (text.isNotBlank()) heardText = text
        val final = Recitation.heardWords(text)
        pending = pending + final.drop(sessionSkip)
        partial = emptyList()
        sessionSkip = 0
        update()
        listen()
    }

    override fun onError(code: Int) {
        listening = false
        if (!active) return
        when (code) {
            // سكوت أو كلام غير مفهوم: نواصل الاستماع
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT ->
                handler.postDelayed({ recognizer.cancel(); listen() }, 400)
            else -> {
                active = false
                error = when (code) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يلزم إذن الميكروفون"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        "تعذّر الاتصال بخدمة التعرف على الكلام. تحقّق من الإنترنت"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        "اللغة العربية غير متاحة في خدمة التعرف على الكلام على هذا الجهاز"
                    SpeechRecognizer.ERROR_AUDIO -> "تعذّر تسجيل الصوت"
                    else -> "توقف التعرف على الكلام (خطأ $code)"
                }
            }
        }
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

private fun percent(x: Float) = "${Arabic.digits((x * 100).roundToInt())}٪"

/** شريط المراجعة أسفل الصفحة: رقم الآية المنتظرة وما يُسمع، مع كشف الآية وإنهاء المراجعة */
@Composable
fun ReviewBar(s: ReviewSession, onClose: () -> Unit) {
    val onBar = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .islamicPattern()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (s.listening) Icons.Filled.Mic else Icons.Filled.MicOff,
                null,
                tint = if (s.listening) PatternGold else onBar.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.finished) "سورة ${Surahs.get(s.surah).name}"
                    else "مراجعة سورة ${Surahs.get(s.surah).name} • الآية ${Arabic.digits(s.current + 1)} من ${Arabic.digits(s.ayahCount)}",
                    color = onBar,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    s.error ?: if (s.finished) "انتهت المراجعة" else s.heardText.ifBlank { "ابدأ التلاوة…" },
                    color = if (s.error != null) PatternGold else onBar.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (s.error != null) {
                TextButton(onClick = { s.start() }) { Text("إعادة المحاولة", color = PatternGold) }
            } else if (!s.finished) {
                IconButton(onClick = { s.reveal() }) { Icon(Icons.Filled.Visibility, "أظهر الآية", tint = onBar) }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إنهاء المراجعة", tint = onBar) }
        }
    }
}

@Composable
fun ReviewResultDialog(s: ReviewSession, onRetry: () -> Unit, onClose: () -> Unit) {
    val total = s.results.sumOf { it.total }
    val correct = s.results.sumOf { it.correct }
    val overall = if (total == 0) 1f else correct.toFloat() / total
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("نتيجة مراجعة سورة ${Surahs.get(s.surah).name}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(percent(overall), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${Arabic.digits(correct)} كلمة صحيحة من ${Arabic.digits(total)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(4.dp))
                HorizontalDivider()
                s.results.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("الآية ${Arabic.digits(i + 1)}" + if (r.helped) " (كُشفت)" else "")
                        Text(
                            percent(r.score),
                            color = if (r.score >= 0.9f && !r.helped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    "الكلمات التي لم تُقرأ مُعلَّمة باللون الأحمر في المصحف.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
        dismissButton = { TextButton(onClick = onRetry) { Text("إعادة المراجعة") } },
    )
}

/** تنبيه خطأ خفيف: نغمتان هابطتان قصيرتان بصوت منخفض، تُولَّدان في التطبيق */
object SoftWarning {
    private const val RATE = 22050

    private val pcm: ShortArray by lazy {
        val notes = listOf(660.0 to 0.13, 494.0 to 0.18)
        val out = ArrayList<Short>()
        for ((freq, seconds) in notes) {
            val n = (RATE * seconds).toInt()
            for (i in 0 until n) {
                // غلاف جيبي يمنع النقرة في بداية النغمة ونهايتها
                val env = Math.sin(Math.PI * i / n)
                val v = Math.sin(2 * Math.PI * freq * i / RATE) * env * 0.22
                out += (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        out.toShortArray()
    }

    fun play() {
        val data = pcm
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        track.write(data, 0, data.size)
        track.play()
        Handler(Looper.getMainLooper()).postDelayed({ track.release() }, data.size * 1000L / RATE + 200)
    }
}

/** اختيار السورة المراد مراجعتها، مع البحث بالاسم أو الرقم */
@Composable
fun SurahPickerDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val surahs = remember(query) { Surahs.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة") },
        text = {
            Column {
                Text("اختر السورة التي تريد مراجعة حفظها", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث باسم السورة أو رقمها") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(surahs, key = { it.number }) { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(s.number) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Arabic.digits(s.number), color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                            Text("سورة ${s.name}", fontFamily = QuranFont, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(Arabic.ayahCountLabel(s.ayahCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
