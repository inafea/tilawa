package app.quran.tilawa

import androidx.compose.foundation.layout.size
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
                    vm.openSurah?.let { SurahName(it, fontSize = 30.sp, color = LocalContentColor.current) }
                },
                actions = {
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
                Column {
                    // في اتجاه اليمين لليسار تكون البداية هي الجهة اليمنى
                    vm.openSurah?.let { surah ->
                        FloatingActionButton(
                            onClick = { vm.requestReview(surah) },
                            modifier = Modifier.align(Alignment.Start).padding(16.dp),
                        ) { Icon(Icons.Filled.Mic, "تسميع السورة") }
                    }
                    PlayerBar(vm, onTitleClick = null)
                }
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
    val font = vm.pageFonts[page]

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        when {
            ayahs != null && font != null -> Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PageHeader(ayahs)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val lines = remember(page, ayahs) { pageLines(page, ayahs) }
                    // ارتفاع السطر ثابت (صفحة من ١٥ سطراً كالمصحف المطبوع)، وحجم الخط يُحسب ليتسع أطول سطر للعرض
                    val rowHeight = maxHeight / MUSHAF_LINES
                    val size = rememberFittedSize(lines, font, constraints.maxWidth, rowHeight)
                    val styles = pageStyles(font, size)
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        lines.forEach { line ->
                            val m = Modifier.fillMaxWidth().height(rowHeight)
                            when (line) {
                                is PageLine.Frame -> SurahFrame(line.surah, styles, m)
                                is PageLine.Basmala -> Basmala(
                                    styles, m,
                                    active = vm.playingSurah == line.surah && vm.playingAyah == 0,
                                    onTap = onTap,
                                    onPlay = { vm.playFrom(line.surah, 1) },
                                )
                                is PageLine.Words -> WordsLine(vm, line, justify = page > 2, styles, review, onTap, m)
                                PageLine.Empty -> Spacer(m)
                            }
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

/** عدد أسطر صفحة مصحف المدينة؛ والصفحتان الأوليان ٨ أسطر في وسط الصفحة */
private const val MUSHAF_LINES = 15

/** كلمة من آية، أو علامة نهايتها (index = -1)، برمزها في خط الصفحة */
private class LineItem(val ayah: PageAyah, val index: Int, val text: String)

private sealed interface PageLine {
    class Words(val items: List<LineItem>) : PageLine
    class Frame(val surah: Int) : PageLine
    class Basmala(val surah: Int) : PageLine
    data object Empty : PageLine
}

/**
 * أسطر الصفحة كما في المصحف المطبوع. الأسطر التي لا كلمات فيها قبل أول سورة تبدأ في الصفحة
 * هي إطار اسم السورة ثم البسملة.
 */
private fun pageLines(page: Int, ayahs: List<PageAyah>): List<PageLine> {
    val byLine = sortedMapOf<Int, MutableList<LineItem>>()
    for (a in ayahs) a.words.forEachIndexed { i, w ->
        byLine.getOrPut(a.lines[i]) { mutableListOf() } += LineItem(a, if (i == a.words.lastIndex) -1 else i, w)
    }
    val count = if (page <= 2) 8 else MUSHAF_LINES
    return (1..count).map { r ->
        byLine[r]?.let { PageLine.Words(it) } ?: run {
            val next = ayahs.firstOrNull { it.lines.isNotEmpty() && it.lines.first() > r }
            when {
                next == null || next.numberInSurah != 1 -> PageLine.Empty
                r == next.lines.first() - 1 && Surahs.get(next.surah).hasSeparateBasmala -> PageLine.Basmala(next.surah)
                else -> PageLine.Frame(next.surah)
            }
        }
    }
}

/** أصغر مسافة بين الكلمات (نسبة من حجم الخط) */
private const val MIN_GAP = 0.06f
/** حجم الخط إلى ارتفاع السطر */
private const val SIZE_TO_ROW = 0.62f

/**
 * أنماط نص الصفحة كلها مشتقة من حجم واحد حتى تتناسب عند تغيّر الشاشة.
 * الكلمات بخط الصفحة، واسم السورة والبسملة بخط المجمع العام (ليسا في خط الصفحة).
 */
private class PageStyles(base: TextStyle, font: FontFamily, val size: Float) {
    val word = base.merge(TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = (size * 1.6f).sp))
    val basmala = base.merge(TextStyle(fontFamily = MushafFont, fontSize = size.sp, lineHeight = (size * 1.6f).sp))
    val title = base.merge(TextStyle(fontFamily = MushafFont, fontSize = (size * 0.85f).sp, lineHeight = (size * 1.4f).sp))
    val ornament = base.merge(TextStyle(fontFamily = QuranFont, fontSize = (size * 0.85f).sp, lineHeight = (size * 1.4f).sp))
}

@Composable
private fun pageStyles(font: FontFamily, size: Float): PageStyles {
    val base = LocalTextStyle.current
    return remember(base, font, size) { PageStyles(base, font, size) }
}

/**
 * أكبر حجم خط يتسع به كل سطر للعرض بأصغر مسافات بين الكلمات، ولا يتجاوز ارتفاع السطر.
 * عرض السطر يتناسب طردياً مع حجم الخط، فيكفي قياسه مرة بحجم مرجعي.
 */
@Composable
private fun rememberFittedSize(lines: List<PageLine>, font: FontFamily, width: Int, rowHeight: Dp): Float {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    return remember(lines, font, width, rowHeight, density, base) {
        val byHeight = with(density) { (rowHeight * SIZE_TO_ROW).toSp().value }
        if (width <= 0) return@remember byHeight
        val ref = 20f
        val style = PageStyles(base, font, ref).word
        val refPx = with(density) { ref.sp.toPx() }
        val widest = lines.filterIsInstance<PageLine.Words>().maxOfOrNull { line ->
            line.items.sumOf { measurer.measure(it.text, style).size.width.toDouble() } + MIN_GAP * refPx * (line.items.size - 1)
        } ?: return@remember byHeight
        minOf(byHeight, ref * width / widest.toFloat())
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

/** إطار اسم السورة المزخرف، بارتفاع سطر واحد من الصفحة */
@Composable
private fun SurahFrame(surah: Int, styles: PageStyles, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val line = colors.onSurfaceVariant.copy(alpha = 0.8f)
    Surface(
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, line),
        modifier = modifier.padding(vertical = 2.dp),
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
                // اسم السورة، وتحته بخط صغير نوعها وعدد آياتها
                val info = Surahs.get(surah)
                Column(
                    Modifier
                        .background(colors.surfaceVariant, RoundedCornerShape(50))
                        .border(0.75.dp, line.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SurahName(surah, fontSize = styles.size.sp, color = colors.onBackground, lineHeight = (styles.size * 1.05f).sp)
                    Text(
                        "${info.revelation} • ${Arabic.ayahCountLabel(info.ayahCount)}",
                        color = colors.onSurfaceVariant,
                        fontSize = (styles.size * 0.3f).sp,
                        lineHeight = (styles.size * 0.36f).sp,
                        maxLines = 1,
                    )
                }
            }
            Text("۞", style = styles.ornament, color = line)
        }
    }
}

@Composable
private fun Basmala(styles: PageStyles, modifier: Modifier, active: Boolean, onTap: () -> Unit, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            Arabic.BASMALA,
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) colors.primaryContainer else colors.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPlay() },
                    )
                }
                .padding(horizontal = 16.dp),
            style = styles.basmala,
            color = if (active) colors.onPrimaryContainer else colors.onBackground,
            maxLines = 1,
        )
    }
}

/**
 * سطر من الصفحة بكلماته كما في المصحف المطبوع، والفراغ الزائد موزع بالتساوي بين الكلمات
 * (ويُوسّط السطر في الصفحتين الأوليين). الضغط يظهر الأشرطة ويخفيها، والضغط المطوّل على كلمة يبدأ التلاوة من آيتها.
 */
@Composable
private fun WordsLine(
    vm: QuranViewModel,
    line: PageLine.Words,
    justify: Boolean,
    styles: PageStyles,
    review: ReviewSession?,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val items = line.items
    // لون خلفية كل عنصر، ويُرسم بعد التخطيط متصلاً عبر الفراغات بين كلمات الآية
    val backgrounds = items.map { item ->
        val i = review?.indexOf(item.ayah) ?: -1
        when {
            review != null -> if (i >= 0 && i == review.current) colors.primaryContainer.copy(alpha = 0.7f) else null
            vm.playingSurah == item.ayah.surah && vm.playingAyah == item.ayah.numberInSurah -> colors.primaryContainer
            else -> null
        }
    }
    // حدود كل عنصر بعد التخطيط
    val lefts = remember(items) { FloatArray(items.size) }
    val rights = remember(items) { FloatArray(items.size) }

    Layout(
        content = {
            items.forEachIndexed { k, item ->
                // علامة نهاية الآية برقمها يرسمها خط الصفحة كما في المصحف المطبوع
                if (item.index < 0) {
                    Text(item.text, style = styles.word, color = colors.onBackground, maxLines = 1, softWrap = false)
                } else {
                    // المراجعة: الآيات التي لم تُتلَ بعد مخفية (تبقى علامتها)، والمتلوّة تظهر وكلماتها الفائتة بالأحمر
                    val i = review?.indexOf(item.ayah) ?: -1
                    val result = if (i >= 0) review?.results?.getOrNull(i) else null
                    val color = when {
                        review != null && i >= 0 && result == null -> Color.Transparent
                        result != null && item.index in result.missed -> colors.error
                        review == null && backgrounds[k] != null -> colors.onPrimaryContainer
                        else -> colors.onBackground
                    }
                    Text(item.text, style = styles.word, color = color, maxLines = 1, softWrap = false)
                }
            }
        },
        modifier = modifier
            .drawBehind {
                var k = 0
                while (k < items.size) {
                    val bg = backgrounds[k]
                    if (bg == null) { k++; continue }
                    var end = k
                    while (end + 1 < items.size && backgrounds[end + 1] == bg) end++
                    // من اليمين لليسار: أول عنصر في أقصى اليمين
                    // حواف مستديرة، وهامش صغير حول الكلمات حتى لا يلتصق تظليل الأسطر المتتالية
                    val pad = 3.dp.toPx()
                    val inset = size.height * 0.06f
                    val left = (lefts[end] - pad).coerceAtLeast(0f)
                    val right = (rights[k] + pad).coerceAtMost(size.width)
                    drawRoundRect(
                        bg, Offset(left, inset), Size(right - left, size.height - 2 * inset),
                        cornerRadius = CornerRadius(size.height * 0.3f),
                    )
                    k = end + 1
                }
            }
            .pointerInput(items, review) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { pos ->
                        if (review != null) return@detectTapGestures
                        val k = items.indices.firstOrNull { pos.x in lefts[it]..rights[it] }
                            ?: items.indices.minByOrNull { minOf(abs(pos.x - lefts[it]), abs(pos.x - rights[it])) }
                            ?: return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.playFrom(items[k].ayah.surah, items[k].ayah.numberInSurah)
                    },
                )
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val used = placeables.sumOf { it.width }
        val minGap = MIN_GAP * styles.size.sp.toPx()
        val gap = if (justify && placeables.size > 1) maxOf(minGap, (width - used).toFloat() / (placeables.size - 1)) else minGap
        val total = used + gap * (placeables.size - 1)
        // نص المصحف من اليمين دائماً أياً كانت لغة الجهاز
        var x = if (justify) width.toFloat() else (width + total) / 2f
        layout(width, height) {
            placeables.forEachIndexed { k, p ->
                rights[k] = x
                lefts[k] = x - p.width
                p.place(lefts[k].roundToInt(), (height - p.height) / 2)
                x -= p.width + gap
            }
        }
    }
}
