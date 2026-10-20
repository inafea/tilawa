package app.quran.tilawa

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// =====================================================================
// شاشة المصحف: صفحات مصحف المدينة (٦٠٤) يُقلَّب بينها بالسحب
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MushafScreen(vm: QuranViewModel, snackbar: SnackbarHostState) {
    val pager = rememberPagerState(initialPage = vm.currentPage - 1) { Pages.COUNT }
    var menuOpen by remember { mutableStateOf(false) }
    var fontMenuOpen by remember { mutableStateOf(false) }

    BackHandler { vm.closeSurah() }

    // مراجعة حفظ سورة (تُطلب من قائمة الشاشة الرئيسية)؛ تُلغى عند الخروج من الشاشة
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var review by remember { mutableStateOf<ReviewSession?>(null) }
    var resultClosed by remember(review) { mutableStateOf(false) }
    DisposableEffect(review) {
        val s = review
        onDispose { s?.destroy() }
    }
    BackHandler(enabled = review != null) { review = null }
    var pendingReview by remember { mutableStateOf<Int?>(null) }
    fun startReview(surah: Int) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch { snackbar.showSnackbar("لا تتوفر خدمة التعرف على الكلام على هذا الجهاز") }
            return
        }
        if (vm.isPlaying) vm.togglePlay()
        val textOf = { ayah: Int ->
            vm.pages.values.firstNotNullOfOrNull { page -> page.firstOrNull { it.surah == surah && it.numberInSurah == ayah }?.text }
        }
        review = ReviewSession(context, surah, Surahs.get(surah).ayahCount, textOf).also { it.start() }
        scope.launch { pager.scrollToPage(Pages.startOf(surah) - 1) }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val surah = pendingReview
        pendingReview = null
        if (granted && surah != null) startReview(surah)
        else if (!granted) scope.launch { snackbar.showSnackbar("يلزم إذن الميكروفون لمراجعة الحفظ") }
    }
    LaunchedEffect(vm.reviewRequest) {
        val surah = vm.reviewRequest ?: return@LaunchedEffect
        vm.reviewRequestHandled()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startReview(surah)
        } else {
            pendingReview = surah
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // تقليب الصفحة تلقائياً إلى صفحة الآية المنتظرة (التقليب اليدوي معطل أثناء المراجعة)
    val reviewing = review
    LaunchedEffect(reviewing, reviewing?.current, vm.pages.size) {
        val s = reviewing ?: return@LaunchedEffect
        if (s.finished) return@LaunchedEffect
        val target = vm.pages.entries.firstOrNull { (_, ayahs) ->
            ayahs.any { it.surah == s.surah && it.numberInSurah == s.current + 1 }
        }?.key ?: return@LaunchedEffect
        if (target != pager.currentPage + 1) pager.animateScrollToPage(target - 1)
    }

    // الصفحة المستقرة تحدد السورة المعروضة، ونحمّل الصفحتين المجاورتين مسبقاً
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i ->
            vm.onPageShown(i + 1)
            vm.loadPage(i + 2)
            vm.loadPage(i)
        }
    }
    // الانتقال إلى صفحة الآية الجاري تلاوتها أو المطلوب فتحها
    LaunchedEffect(vm.jump) {
        val j = vm.jump ?: return@LaunchedEffect
        if (j.animate) pager.animateScrollToPage(j.page - 1) else pager.scrollToPage(j.page - 1)
    }

    // وضع ملء الشاشة: الأشرطة مخفية، والضغط على الصفحة يظهرها ويخفيها
    var chrome by rememberSaveable { mutableStateOf(false) }
    val toggleChrome = { chrome = !chrome }
    val view = LocalView.current
    DisposableEffect(chrome) {
        val controller = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // الأشرطة تظهر فوق الصفحة ولا تزاحمها، ومساحة الصفحة ثابتة (لا تتأثر بظهور أشرطة النظام)
        // حتى لا يتغير حجم الخط عند كل ضغطة
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
            beyondViewportPageCount = 1,
            key = { it },
            userScrollEnabled = review == null,
        ) { i ->
            val page = i + 1
            LaunchedEffect(page) { vm.loadPage(page) }
            MushafPage(vm, page, review, toggleChrome)
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.closeSurah() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع إلى الفهرس")
                    }
                },
                title = {
                    Text(
                        vm.openSurah?.let { "سورة ${Surahs.get(it).name}" } ?: "",
                        fontFamily = QuranFont, fontSize = 22.sp,
                    )
                },
                actions = {
                    IconButton(onClick = { vm.changeFontSize(-2f) }) { Icon(Icons.Filled.ZoomOut, "تصغير الخط") }
                    IconButton(onClick = { vm.changeFontSize(2f) }) { Icon(Icons.Filled.ZoomIn, "تكبير الخط") }
                    IconButton(onClick = { fontMenuOpen = true }) { Icon(Icons.Filled.TextFields, "اختيار خط المصحف") }
                    DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
                        Text(
                            "اختر خط المصحف",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        MushafFonts.all.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name) },
                                trailingIcon = { if (f.id == vm.mushafFont.id) Icon(Icons.Filled.Check, null) },
                                onClick = { fontMenuOpen = false; vm.selectMushafFont(f) },
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.RecordVoiceOver, "اختيار القارئ") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            "اختر القارئ",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Reciters.all.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.name) },
                                trailingIcon = { if (r.id == vm.reciter.id) Icon(Icons.Filled.Check, null) },
                                onClick = { menuOpen = false; vm.selectReciter(r) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("الانتقال تلقائياً للسورة التالية") },
                            trailingIcon = { Switch(checked = vm.autoNext, onCheckedChange = null) },
                            onClick = { vm.toggleAutoNext() },
                        )
                    }
                },
                colors = patternBarColors(),
                modifier = Modifier.islamicPattern(),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbar, Modifier.then(if (chrome) Modifier else Modifier.navigationBarsPadding()))
            val s = review
            if (s != null) {
                ReviewBar(s, onClose = { review = null })
            } else AnimatedVisibility(
                visible = chrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                PlayerBar(vm, onTitleClick = null)
            }
        }

        val s = review
        if (s != null && s.finished && !resultClosed) {
            ReviewResultDialog(
                s,
                onRetry = { review = null; startReview(s.surah) },
                onClose = { resultClosed = true },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MushafPage(vm: QuranViewModel, page: Int, review: ReviewSession?, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ayahs = vm.pages[page]

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        when {
            ayahs != null -> Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PageHeader(ayahs)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val segments = remember(ayahs) { pageSegments(ayahs) }
                    val font = vm.mushafFont
                    // حجم الخط يُحسب ليملأ النص الصفحة على أي شاشة، والتكبير/التصغير نسبة منه
                    val fit = rememberFittedSize(segments, font, constraints.maxWidth, constraints.maxHeight)
                    val styles = pageStyles(font.family, fit.size * vm.fontSize / BASE_FONT_SIZE, fit.lineHeight)
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        segments.forEach { seg ->
                            if (seg.first().numberInSurah == 1) {
                                SurahFrame(seg.first().surah, styles)
                                if (Surahs.get(seg.first().surah).hasSeparateBasmala) {
                                    Basmala(
                                        styles,
                                        active = vm.playingSurah == seg.first().surah && vm.playingAyah == 0,
                                        onTap = onTap,
                                        onPlay = { vm.playFrom(seg.first().surah, 1) },
                                    )
                                }
                            }
                            AyahsText(vm, seg, font, styles, review, onTap)
                        }
                    }
                }
                PageFooter(page, ayahs)
            }

            vm.pageErrors[page] == true -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "تعذّر تحميل الصفحة.\nتحقّق من اتصالك بالإنترنت (يلزم الاتصال أول مرة فقط).",
                    textAlign = TextAlign.Center,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { vm.loadPage(page) }) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة المحاولة")
                }
            }

            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/** تقسيم الصفحة إلى مقاطع متتالية، كل مقطع من سورة واحدة */
private fun pageSegments(ayahs: List<PageAyah>): List<List<PageAyah>> {
    val out = mutableListOf<MutableList<PageAyah>>()
    for (a in ayahs) {
        if (out.isEmpty() || out.last().last().surah != a.surah) out += mutableListOf(a) else out.last() += a
    }
    return out
}

/** حجم الخط الافتراضي في الإعدادات؛ يقابل حجم الملاءمة دون تكبير */
private const val BASE_FONT_SIZE = 26f
private const val LINE_HEIGHT = 1.8f
private const val MIN_FIT = 16f
private const val MAX_FIT = 64f
private const val MAX_LINE_HEIGHT = 3f

/** أنماط نص الصفحة كلها مشتقة من حجم واحد حتى تتناسب عند تغيّر الشاشة */
private class PageStyles(base: TextStyle, font: FontFamily, val size: Float, lineHeight: Float = LINE_HEIGHT) {
    val ayahs = base.merge(
        TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = (size * lineHeight).sp, textAlign = TextAlign.Justify)
    )
    val title = base.merge(TextStyle(fontFamily = font, fontSize = (size * 0.92f).sp))
    val ornament = base.merge(TextStyle(fontFamily = QuranFont, fontSize = size.sp))
}

@Composable
private fun pageStyles(font: FontFamily, size: Float, lineHeight: Float): PageStyles {
    val base = LocalTextStyle.current
    return remember(base, font, size, lineHeight) { PageStyles(base, font, size, lineHeight) }
}

// الهوامش الثابتة حول كل عنصر، مطابقة لما في SurahFrame و Basmala و AyahsText
private val FRAME_EXTRA = 20.dp
private val TITLE_EXTRA = 8.dp
private val TEXT_EXTRA = 4.dp

private class Fit(val size: Float, val lineHeight: Float)

/**
 * أكبر حجم خط تتسع به الصفحة كاملة (الإطارات والبسملة والآيات) للمساحة المتاحة،
 * ثم يُمدّ تباعد الأسطر حتى يملأ النص ارتفاع الصفحة كما في المصحف المطبوع.
 */
@Composable
private fun rememberFittedSize(segments: List<List<PageAyah>>, font: MushafFontOption, width: Int, height: Int): Fit {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    return remember(segments, font, width, height, density, base) {
        if (width <= 0 || height <= 0) return@remember Fit(BASE_FONT_SIZE, LINE_HEIGHT)
        val textConstraints = Constraints(maxWidth = width)

        fun heightAt(size: Float, lineHeight: Float = LINE_HEIGHT): Int {
            val s = PageStyles(base, font.family, size, lineHeight)
            var total = 0
            for (seg in segments) {
                val first = seg.first()
                if (first.numberInSurah == 1) {
                    val title = measurer.measure("سُورَةُ ${Surahs.get(first.surah).name}", s.title).size.height
                    val ornament = measurer.measure("۞", s.ornament).size.height
                    total += maxOf(title + with(density) { TITLE_EXTRA.roundToPx() }, ornament) + with(density) { FRAME_EXTRA.roundToPx() }
                    if (Surahs.get(first.surah).hasSeparateBasmala) {
                        total += measurer.measure(Arabic.BASMALA, s.title).size.height + with(density) { TEXT_EXTRA.roundToPx() }
                    }
                }
                val text = buildAnnotatedString { seg.forEach { appendAyah(it, font, SpanStyle()); append(' ') } }
                total += measurer.measure(text, s.ayahs, constraints = textConstraints, placeholders = ayahMarkPlaceholders(text)).size.height + with(density) { TEXT_EXTRA.roundToPx() }
            }
            return total
        }

        var size = MAX_FIT
        if (heightAt(MAX_FIT) > height) {
            var lo = MIN_FIT
            var hi = MAX_FIT
            repeat(8) {
                val mid = (lo + hi) / 2
                if (heightAt(mid) <= height) lo = mid else hi = mid
            }
            size = lo
        }
        // الصفحات القصيرة (كالفاتحة وأول البقرة) لا تُمدّ أكثر من الحد حتى لا تتباعد أسطرها كثيراً
        var lo = LINE_HEIGHT
        var hi = MAX_LINE_HEIGHT
        if (heightAt(size, hi) <= height) {
            lo = hi
        } else repeat(8) {
            val mid = (lo + hi) / 2
            if (heightAt(size, mid) <= height) lo = mid else hi = mid
        }
        Fit(size, lo)
    }
}

/** الترويسة: رقم الجزء يميناً واسم السورة يساراً */
@Composable
private fun PageHeader(ayahs: List<PageAyah>) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text("الجزء ${Arabic.digits(ayahs.first().juz)}", color = c, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(Surahs.get(ayahs.first().surah).name, color = c, fontSize = 15.sp)
    }
}

/** التذييل: الحزب يميناً ورقم الصفحة في إطار يساراً */
@Composable
private fun PageFooter(page: Int, ayahs: List<PageAyah>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Pages.hizbLabel(ayahs.last().hizbQuarter), color = colors.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .border(1.dp, colors.onSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(50))
                .padding(3.dp)
                .border(0.75.dp, colors.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 1.dp),
        ) {
            Text(Arabic.digits(page), color = colors.onBackground, fontSize = 15.sp)
        }
    }
}

/** إطار اسم السورة المزخرف */
@Composable
private fun SurahFrame(surah: Int, styles: PageStyles) {
    val colors = MaterialTheme.colorScheme
    val line = colors.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, line),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier
                .padding(3.dp)
                .clip(RoundedCornerShape(4.dp))
                .islamicPattern(base = colors.surfaceVariant, ink = colors.secondary, strength = 0.7f, cellSize = 26.dp)
                .border(0.75.dp, line.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("۞", style = styles.ornament, color = line)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "سُورَةُ ${Surahs.get(surah).name}",
                    Modifier
                        .padding(vertical = 4.dp)
                        .background(colors.surfaceVariant, RoundedCornerShape(50))
                        .border(0.75.dp, line.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp),
                    style = styles.title,
                    color = colors.onBackground,
                )
            }
            Text("۞", style = styles.ornament, color = line)
        }
    }
}

@Composable
private fun Basmala(styles: PageStyles, active: Boolean, onTap: () -> Unit, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            Arabic.BASMALA,
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) colors.secondaryContainer else colors.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPlay() },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 2.dp),
            style = styles.title,
            color = if (active) colors.onSecondaryContainer else colors.onBackground,
        )
    }
}

private val AYAH_NUMBER = Regex("""\s*([٠-٩]+)$""")

/**
 * نص المجمع ينتهي برقم الآية، ونرسم علامة نهاية الآية بأنفسنا (AyahMark) بدل الاعتماد على الخط:
 * طرق الخطوط في رسم الرقم داخل العلامة تختلف بين الأجهزة (رقم بجوار علامة فارغة، أو أرقام معكوسة ٥٨ ← ٨٥).
 * طول النص المُضاف يساوي دائماً طول نص الآية حتى تصح مواضع الضغط.
 */
private fun AnnotatedString.Builder.appendAyah(
    a: PageAyah,
    font: MushafFontOption,
    style: SpanStyle,
    wordStyle: ((Int) -> SpanStyle)? = null,
) {
    val num = AYAH_NUMBER.find(a.text)
    withStyle(style) {
        val body = if (num == null) a.text else a.text.substring(0, num.range.first)
        // نمط لكل كلمة (للمراجعة: إخفاء الآية أو تلوين الكلمات الفائتة)، والكلمات بترتيب Recitation.displayWords
        if (wordStyle == null) append(body)
        else body.split(' ').forEachIndexed { i, w ->
            if (i > 0) append(' ')
            withStyle(wordStyle(i)) { append(w) }
        }
        if (num == null) return@withStyle
        // المسافة قبل الرقم تبقى نصاً، والرقم نفسه يُستبدل بعلامة نهاية الآية مرسومة (AyahMark)
        val digits = num.groupValues[1]
        append(a.text.substring(num.range.first, num.range.last + 1 - digits.length))
        appendInlineContent(ayahMarkId(digits), digits)
    }
}

/**
 * نص الآيات متصلاً ومضبوطاً على الجانبين. الضغط يظهر الأشرطة ويخفيها،
 * والضغط المطوّل على آية يبدأ التلاوة منها.
 */
@Composable
private fun AyahsText(vm: QuranViewModel, ayahs: List<PageAyah>, font: MushafFontOption, styles: PageStyles, review: ReviewSession?, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // السطر الأخير لا يُضبط على الجانبين في أندرويد، فنوسّع مسافاته بعد القياس الأول حتى يملأ العرض:
    // (بداية السطر الأخير، الزيادة لكل مسافة بالبكسل)
    var lastLine by remember(ayahs, font, styles) { mutableStateOf<Pair<Int, Float>?>(null) }
    // موضع كل آية في النص لمعرفة الآية تحت إصبع المستخدم
    val ranges = remember(ayahs) {
        var start = 0
        ayahs.map { a -> (start until start + a.text.length).also { start += a.text.length + 1 } }
    }
    val text = buildAnnotatedString {
        ayahs.forEach { a ->
            val i = review?.indexOf(a) ?: -1
            if (review != null && i >= 0) {
                // المراجعة: الآيات التي لم تُتلَ بعد مخفية (يبقى رقمها ظاهراً)، والمنتظرة مظللة،
                // والمتلوّة تظهر وكلماتها الفائتة بالأحمر
                val result = review.results.getOrNull(i)
                appendAyah(
                    a, font,
                    if (i == review.current) SpanStyle(background = colors.secondaryContainer.copy(alpha = 0.6f)) else SpanStyle(),
                    if (result == null) { _ -> SpanStyle(color = Color.Transparent) }
                    else { w -> if (w in result.missed) SpanStyle(color = colors.error) else SpanStyle() },
                )
            } else {
                val highlighted = vm.playingSurah == a.surah && vm.playingAyah == a.numberInSurah
                appendAyah(
                    a, font,
                    if (highlighted) SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer)
                    else SpanStyle(),
                )
            }
            append(' ')
        }
        lastLine?.let { (start, extra) ->
            if (extra <= 0f) return@let
            val spacing = with(density) { extra.toSp() }
            val plain = toAnnotatedString().text
            for (i in start until plain.length - 1) {
                if (plain[i] == ' ') addStyle(SpanStyle(letterSpacing = spacing), i, i + 1)
            }
        }
    }
    Text(
        text = text,
        inlineContent = AyahMarkContent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pointerInput(ayahs) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { pos ->
                        if (review != null) return@detectTapGestures
                        val offset = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                        val i = ranges.indexOfFirst { offset in it }.takeIf { it >= 0 }
                            ?: ranges.indexOfFirst { offset < it.first }.takeIf { it >= 0 }
                            ?: ayahs.lastIndex
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.playFrom(ayahs[i].surah, ayahs[i].numberInSurah)
                    },
                )
            },
        onTextLayout = { l ->
            layout = l
            if (lastLine == null) lastLine = lastLineStretch(l)
        },
        style = styles.ayahs,
        color = colors.onBackground,
    )
}

/**
 * الزيادة اللازمة لكل مسافة في السطر الأخير حتى يملأ العرض. لا يُمدّ السطر القصير جداً
 * (أقل من ٧٥٪ من العرض) حتى لا تتباعد كلماته كثيراً.
 */
private fun lastLineStretch(l: TextLayoutResult): Pair<Int, Float> {
    val i = l.lineCount - 1
    val start = l.getLineStart(i)
    if (i < 1) return start to 0f
    val end = l.getLineEnd(i, visibleEnd = true)
    val used = l.getLineRight(i) - l.getLineLeft(i)
    val width = l.size.width.toFloat()
    val spaces = l.layoutInput.text.text.substring(start, end).count { it == ' ' }
    if (spaces == 0 || used < width * 0.75f || used >= width - 1f) return start to 0f
    // هامش صغير حتى لا يلتف السطر بسبب التقريب
    return start to (width - used) * 0.98f / spaces
}

// ---------- علامة نهاية الآية ----------

/** المعرّف حسب عدد الأرقام، فلكل عرض محتوى مضمّن خاص به */
private fun ayahMarkId(digits: String) = "ayah_mark_${digits.length.coerceIn(1, 3)}"

/** فراغ بين العلامة والكلمة قبلها: أواخر الحروف في خط المجمع (كالنون) تمتد خارج عرضها فتغطي العلامة */
private const val AYAH_MARK_GAP = 0.5f

private fun ayahMarkPlaceholder(digitCount: Int) = Placeholder(
    width = (1.25f + 0.25f * (digitCount - 1) + AYAH_MARK_GAP).em,
    height = 1.25.em,
    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
)

private val AyahMarkContent = (1..3).associate { n ->
    ayahMarkId("0".repeat(n)) to InlineTextContent(ayahMarkPlaceholder(n)) { digits -> AyahMark(digits) }
}

/** مواضع العلامات في النص، لقياس ارتفاع الصفحة كما ستُعرض (معرّف وسم المحتوى المضمّن في Compose) */
private fun ayahMarkPlaceholders(text: AnnotatedString) =
    text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, text.length)
        .filter { it.item.startsWith("ayah_mark_") }
        .map { AnnotatedString.Range(ayahMarkPlaceholder(it.end - it.start), it.start, it.end) }

@Composable
private fun AyahMark(digits: String) {
    // نص المصحف من اليمين دائماً، فالكلمة قبل العلامة على يمينها أياً كانت لغة الجهاز
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Box(Modifier.fillMaxSize().absolutePadding(right = height * (AYAH_MARK_GAP / 1.25f)), contentAlignment = Alignment.Center) {
            AyahMarkShape(digits, height)
        }
    }
}

/** دائرتان متداخلتان بزخارف صغيرة على الأطراف، ورقم الآية في وسطها بخط النظام */
@Composable
private fun AyahMarkShape(digits: String, height: Dp) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val numberSize = with(LocalDensity.current) { (height * 0.42f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.height * 0.05f
            val outer = size.height * 0.08f
            val inner = size.height * 0.2f
            drawOval(color, Offset(outer, outer), Size(size.width - 2 * outer, size.height - 2 * outer), style = Stroke(stroke))
            drawOval(
                color.copy(alpha = 0.55f), Offset(inner, inner), Size(size.width - 2 * inner, size.height - 2 * inner),
                style = Stroke(stroke * 0.6f),
            )
            val dot = size.height * 0.055f
            listOf(
                Offset(size.width / 2, outer), Offset(size.width / 2, size.height - outer),
                Offset(outer, size.height / 2), Offset(size.width - outer, size.height / 2),
            ).forEach { drawCircle(color, dot, it) }
        }
        Text(
            digits,
            color = color,
            fontSize = numberSize,
            lineHeight = numberSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
