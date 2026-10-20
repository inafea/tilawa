package app.quran.tilawa

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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

    BackHandler { vm.closeSurah() }

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
        ) { i ->
            val page = i + 1
            LaunchedEffect(page) { vm.loadPage(page) }
            MushafPage(vm, page, toggleChrome)
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
                colors = greenBarColors(),
            )
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbar, Modifier.then(if (chrome) Modifier else Modifier.navigationBarsPadding()))
            AnimatedVisibility(
                visible = chrome,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                PlayerBar(vm, onTitleClick = null)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MushafPage(vm: QuranViewModel, page: Int, onTap: () -> Unit) {
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
                    // حجم الخط يُحسب ليملأ النص الصفحة على أي شاشة، والتكبير/التصغير نسبة منه
                    val fit = rememberFittedSize(segments, constraints.maxWidth, constraints.maxHeight)
                    val styles = pageStyles(fit * vm.fontSize / BASE_FONT_SIZE)
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
                            AyahsText(vm, seg, styles, onTap)
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
private const val MAX_FIT = 36f

/** أنماط نص الصفحة كلها مشتقة من حجم واحد حتى تتناسب عند تغيّر الشاشة */
private class PageStyles(base: TextStyle, val size: Float) {
    val ayahs = base.merge(
        TextStyle(fontFamily = MushafFont, fontSize = size.sp, lineHeight = (size * LINE_HEIGHT).sp, textAlign = TextAlign.Justify)
    )
    val title = base.merge(TextStyle(fontFamily = MushafFont, fontSize = (size * 0.92f).sp))
    val ornament = base.merge(TextStyle(fontFamily = QuranFont, fontSize = size.sp))
}

@Composable
private fun pageStyles(size: Float): PageStyles {
    val base = LocalTextStyle.current
    return remember(base, size) { PageStyles(base, size) }
}

// الهوامش الثابتة حول كل عنصر، مطابقة لما في SurahFrame و Basmala و AyahsText
private val FRAME_EXTRA = 20.dp
private val TITLE_EXTRA = 8.dp
private val TEXT_EXTRA = 4.dp

/** أكبر حجم خط تتسع به الصفحة كاملة (الإطارات والبسملة والآيات) للمساحة المتاحة */
@Composable
private fun rememberFittedSize(segments: List<List<PageAyah>>, width: Int, height: Int): Float {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    return remember(segments, width, height, density, base) {
        if (width <= 0 || height <= 0) return@remember BASE_FONT_SIZE
        val textConstraints = Constraints(maxWidth = width)

        fun heightAt(size: Float): Int {
            val s = PageStyles(base, size)
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
                val text = seg.joinToString(" ", postfix = " ") { it.text }
                total += measurer.measure(text, s.ayahs, constraints = textConstraints).size.height + with(density) { TEXT_EXTRA.roundToPx() }
            }
            return total
        }

        if (heightAt(MAX_FIT) <= height) return@remember MAX_FIT
        var lo = MIN_FIT
        var hi = MAX_FIT
        repeat(8) {
            val mid = (lo + hi) / 2
            if (heightAt(mid) <= height) lo = mid else hi = mid
        }
        lo
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

/**
 * نص الآيات متصلاً ومضبوطاً على الجانبين. الضغط يظهر الأشرطة ويخفيها،
 * والضغط المطوّل على آية يبدأ التلاوة منها.
 */
@Composable
private fun AyahsText(vm: QuranViewModel, ayahs: List<PageAyah>, styles: PageStyles, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // موضع كل آية في النص لمعرفة الآية تحت إصبع المستخدم
    val ranges = remember(ayahs) {
        var start = 0
        ayahs.map { a -> (start until start + a.text.length).also { start += a.text.length + 1 } }
    }
    val text = buildAnnotatedString {
        ayahs.forEach { a ->
            val highlighted = vm.playingSurah == a.surah && vm.playingAyah == a.numberInSurah
            withStyle(
                if (highlighted) SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer)
                else SpanStyle()
            ) {
                // نص المجمع ينتهي برقم الآية، وخط المصحف يرسمه داخل علامة نهاية الآية
                append(a.text)
            }
            append(' ')
        }
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pointerInput(ayahs) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { pos ->
                        val offset = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                        val i = ranges.indexOfFirst { offset in it }.takeIf { it >= 0 }
                            ?: ranges.indexOfFirst { offset < it.first }.takeIf { it >= 0 }
                            ?: ayahs.lastIndex
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.playFrom(ayahs[i].surah, ayahs[i].numberInSurah)
                    },
                )
            },
        onTextLayout = { layout = it },
        style = styles.ayahs,
        color = colors.onBackground,
    )
}
