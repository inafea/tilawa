package app.quran.tilawa

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/**
 * آلية التزامن: كل آية ملف صوتي مستقل، تُرصّ في قائمة تشغيل واحدة للسورة.
 * معرّف كل عنصر "سورة:آية" (والبسملة آية رقم 0)، فعند انتقال المشغّل إلى العنصر التالي
 * نعرف تماماً الآية التي يتلوها القارئ ونبرزها في الشاشة.
 */
class QuranViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = QuranRepository(app)
    private val prefs = Prefs(app)

    // ---------- حالة الواجهة ----------
    var query by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    var reciter by mutableStateOf(Reciters.byId(prefs.reciterId))
        private set
    var fontSize by mutableFloatStateOf(prefs.fontSize)
        private set
    var autoNext by mutableStateOf(prefs.autoNext)
        private set
    var mushafFont by mutableStateOf(MushafFonts.get(prefs.mushafFont))
        private set
    /** 0 = حسب النظام، 1 = فاتح، 2 = داكن */
    var themeMode by mutableIntStateOf(prefs.themeMode)
        private set

    /** شاشة المصحف مفتوحة؟ */
    var readerOpen by mutableStateOf(false)
        private set
    /** السورة المعروضة حالياً في المصحف (سورة أول آية في الصفحة) */
    var openSurah by mutableStateOf<Int?>(null)
        private set
    /** الصفحة المعروضة حالياً (١..٦٠٤) */
    var currentPage by mutableIntStateOf(1)
        private set
    /** طلب انتقال الصفحات؛ كائن جديد في كل مرة حتى يتكرر الطلب لنفس الصفحة */
    var jump by mutableStateOf<PageJump?>(null)
        private set
    val pages = mutableStateMapOf<Int, List<PageAyah>>()
    val pageErrors = mutableStateMapOf<Int, Boolean>()
    private val loadingPages = mutableSetOf<Int>()
    private val surahPages = HashMap<Int, IntArray>()
    var downloading by mutableStateOf(false)
        private set

    // ---------- حالة التشغيل ----------
    var playingSurah by mutableStateOf<Int?>(null)
        private set
    var playingAyah by mutableIntStateOf(-1) // 0 = البسملة
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var repeatOne by mutableStateOf(false)
        private set

    var lastSurah by mutableIntStateOf(prefs.lastSurah)
        private set
    var lastAyah by mutableIntStateOf(prefs.lastAyah)
        private set

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateCurrent(mediaItem)
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onRepeatModeChanged(repeatMode: Int) { repeatOne = repeatMode == Player.REPEAT_MODE_ONE }
        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) onSurahFinished()
        }
        override fun onPlayerError(error: PlaybackException) {
            isBuffering = false
            message = "تعذّر تشغيل التلاوة، تحقّق من الاتصال بالإنترنت"
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            // استعادة الحالة إن كانت التلاوة مستمرة في الخلفية
            isPlaying = c.isPlaying
            repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
            updateCurrent(c.currentMediaItem)
        }, ContextCompat.getMainExecutor(app))
    }

    // ---------- التصفح ----------
    fun openSurah(number: Int, scrollToAyah: Int = 0) {
        currentPage = Pages.startOf(number)
        openSurah = number
        readerOpen = true
        if (scrollToAyah > 1) {
            viewModelScope.launch {
                val p = pageOf(number, scrollToAyah)
                if (readerOpen && p != currentPage) jump = PageJump(p, animate = false)
            }
        }
    }

    /** سورة طُلبت مراجعتها من القائمة؛ تبدأها شاشة المصحف ثم تمسحها */
    var reviewRequest by mutableStateOf<Int?>(null)
        private set

    fun requestReview(surah: Int) {
        if (isPlaying) togglePlay()
        openSurah(surah)
        reviewRequest = surah
    }

    fun reviewRequestHandled() {
        reviewRequest = null
    }

    fun closeSurah() {
        readerOpen = false
        openSurah = null
    }

    /** تستدعيها الواجهة عند استقرار الصفحة المعروضة */
    fun onPageShown(page: Int) {
        currentPage = page
        openSurah = pages[page]?.firstOrNull()?.surah ?: Pages.surahAt(page)
        loadPage(page)
    }

    fun loadPage(page: Int) {
        if (page !in 1..Pages.COUNT || pages.containsKey(page) || !loadingPages.add(page)) return
        pageErrors.remove(page)
        viewModelScope.launch {
            runCatching { repo.getPage(page) }
                .onSuccess {
                    pages[page] = it
                    if (page == currentPage) openSurah = it.firstOrNull()?.surah ?: openSurah
                }
                .onFailure { pageErrors[page] = true }
            loadingPages.remove(page)
        }
    }

    /** رقم الصفحة التي فيها الآية (البسملة = الآية ١) */
    private suspend fun pageOf(surah: Int, ayah: Int): Int {
        val map = surahPages[surah] ?: runCatching {
            repo.getAyahs(surah).map { it.page }.toIntArray().also { surahPages[surah] = it }
        }.getOrNull()
        return map?.getOrNull(ayah.coerceAtLeast(1) - 1)?.takeIf { it > 0 } ?: Pages.startOf(surah)
    }

    // ---------- التشغيل ----------
    fun playFrom(surah: Int, ayah: Int) {
        viewModelScope.launch {
            val c = controller
            if (c == null) {
                message = "جارٍ تهيئة المشغّل، حاول مرة أخرى"
                return@launch
            }
            isBuffering = true
            val urls = runCatching { repo.getAudioUrls(surah, reciter) }.getOrElse {
                isBuffering = false
                message = "تعذّر جلب التلاوة، تحقّق من الاتصال بالإنترنت"
                return@launch
            }
            val info = Surahs.get(surah)
            val items = mutableListOf<MediaItem>()
            if (info.hasSeparateBasmala) {
                // البسملة = الآية الأولى من الفاتحة (الرقم العام 1) بصوت نفس القارئ
                val basmalaUrl = urls.first().replace(Regex("/\\d+\\.mp3$"), "/1.mp3")
                items += mediaItem(surah, 0, basmalaUrl, "سورة ${info.name} • البسملة")
            }
            urls.forEachIndexed { i, url ->
                items += mediaItem(surah, i + 1, url, "سورة ${info.name} • الآية ${Arabic.digits(i + 1)}")
            }
            val start = when {
                info.hasSeparateBasmala && ayah <= 1 -> 0
                info.hasSeparateBasmala -> ayah
                else -> ayah - 1
            }.coerceIn(0, items.lastIndex)

            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
            playingSurah = surah
            playingAyah = if (info.hasSeparateBasmala && ayah <= 1) 0 else ayah
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        val viewing = openSurah
        // في المصحف: إن لم تكن التلاوة الجارية في الصفحة المعروضة نبدأ من أول آية فيها
        val onPage = pages[currentPage]
        if (readerOpen && onPage != null && onPage.none { it.surah == playingSurah && it.numberInSurah == playingAyah.coerceAtLeast(1) }) {
            val first = onPage.first()
            playFrom(first.surah, first.numberInSurah)
            return
        }
        if (viewing != null && playingSurah != viewing) {
            playFrom(viewing, 1)
            return
        }
        if (c.mediaItemCount == 0) {
            val s = viewing ?: lastSurah.takeIf { it > 0 } ?: 1
            playFrom(s, if (s == lastSurah) lastAyah.coerceAtLeast(1) else 1)
            return
        }
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        }
    }

    fun selectReciter(r: Reciter) {
        if (r.id == reciter.id) return
        reciter = r
        prefs.reciterId = r.id
        val s = playingSurah
        if (isPlaying && s != null) {
            playFrom(s, playingAyah.coerceAtLeast(1)) // نكمل من نفس الآية بصوت القارئ الجديد
        } else {
            controller?.clearMediaItems()
            playingSurah = null
            playingAyah = -1
        }
    }

    fun changeFontSize(delta: Float) {
        fontSize = (fontSize + delta).coerceIn(18f, 46f)
        prefs.fontSize = fontSize
    }

    fun selectMushafFont(f: MushafFontOption) {
        mushafFont = f
        prefs.mushafFont = f.id
    }

    /** التبديل الدوري: تلقائي ← فاتح ← داكن ← تلقائي */
    fun cycleTheme() {
        themeMode = (themeMode + 1) % 3
        prefs.themeMode = themeMode
    }

    fun toggleAutoNext() {
        autoNext = !autoNext
        prefs.autoNext = autoNext
    }

    fun downloadAllText() {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            message = runCatching { repo.downloadAllText() }.fold(
                onSuccess = { "تم حفظ نص المصحف كاملاً، يمكنك القراءة دون إنترنت" },
                onFailure = { "فشل التحميل، تحقّق من الاتصال بالإنترنت" },
            )
            downloading = false
        }
    }

    // ---------- داخلي ----------
    private fun mediaItem(surah: Int, ayah: Int, url: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId("$surah:$ayah")
            .setUri(url)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(reciter.name)
                    .setAlbumTitle("القرآن الكريم")
                    .build()
            )
            .build()

    private fun updateCurrent(item: MediaItem?) {
        val parts = item?.mediaId?.split(":") ?: return
        val s = parts.getOrNull(0)?.toIntOrNull() ?: return
        val a = parts.getOrNull(1)?.toIntOrNull() ?: return
        playingSurah = s
        playingAyah = a
        // التزامن: ننتقل إلى صفحة الآية التي يتلوها القارئ
        if (readerOpen) {
            viewModelScope.launch {
                val p = pageOf(s, a)
                if (readerOpen && p != currentPage && playingSurah == s && playingAyah == a) jump = PageJump(p, animate = true)
            }
        }
        if (a > 0) {
            prefs.saveLast(s, a)
            lastSurah = s
            lastAyah = a
        }
    }

    private fun onSurahFinished() {
        val s = playingSurah ?: return
        if (!autoNext || s >= 114) return
        playFrom(s + 1, 1)
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}

class PageJump(val page: Int, val animate: Boolean)
