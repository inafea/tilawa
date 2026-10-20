package app.quran.tilawa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// شاشة المصحف: صفحات مصحف المدينة (٦٠٤) يُقلَّب بينها بالسحب
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
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
        },
        bottomBar = { PlayerBar(vm, onTitleClick = null) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // في الواجهة العربية ينتقل السحب لليمين إلى الصفحة التالية كما في المصحف الورقي
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount = 1,
            key = { it },
        ) { i ->
            val page = i + 1
            LaunchedEffect(page) { vm.loadPage(page) }
            MushafPage(vm, page)
        }
    }
}

@Composable
private fun MushafPage(vm: QuranViewModel, page: Int) {
    val colors = MaterialTheme.colorScheme
    val ayahs = vm.pages[page]

    Box(Modifier.fillMaxSize()) {
        when {
            ayahs != null -> Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PageHeader(ayahs)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    pageSegments(ayahs).forEach { seg ->
                        if (seg.first().numberInSurah == 1) {
                            SurahFrame(seg.first().surah)
                            if (Surahs.get(seg.first().surah).hasSeparateBasmala) {
                                Basmala(
                                    active = vm.playingSurah == seg.first().surah && vm.playingAyah == 0,
                                    onClick = { vm.playFrom(seg.first().surah, 1) },
                                )
                            }
                        }
                        AyahsText(vm, seg)
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
private fun SurahFrame(surah: Int) {
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
            Text("۞", fontFamily = QuranFont, fontSize = 26.sp, color = line)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "سُورَةُ ${Surahs.get(surah).name}",
                    Modifier
                        .padding(vertical = 4.dp)
                        .border(0.75.dp, line.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp),
                    fontFamily = QuranFont,
                    fontSize = 24.sp,
                    color = colors.onBackground,
                )
            }
            Text("۞", fontFamily = QuranFont, fontSize = 26.sp, color = line)
        }
    }
}

@Composable
private fun Basmala(active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            Arabic.BASMALA,
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) colors.secondaryContainer else colors.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 2.dp),
            fontFamily = QuranFont,
            fontSize = 24.sp,
            color = if (active) colors.onSecondaryContainer else colors.onBackground,
        )
    }
}

/** نص الآيات متصلاً ومضبوطاً على الجانبين، وكل آية قابلة للضغط لبدء التلاوة منها */
@Composable
private fun AyahsText(vm: QuranViewModel, ayahs: List<PageAyah>) {
    val colors = MaterialTheme.colorScheme
    val text = buildAnnotatedString {
        ayahs.forEach { a ->
            val highlighted = vm.playingSurah == a.surah && vm.playingAyah == a.numberInSurah
            withLink(
                LinkAnnotation.Clickable(
                    tag = "${a.surah}:${a.numberInSurah}",
                    styles = TextLinkStyles(style = SpanStyle()),
                    linkInteractionListener = { vm.playFrom(a.surah, a.numberInSurah) },
                )
            ) {
                withStyle(
                    if (highlighted) SpanStyle(background = colors.secondaryContainer, color = colors.onSecondaryContainer)
                    else SpanStyle()
                ) {
                    // U+06DD علامة نهاية الآية؛ خط أميري قرآن يرسم الرقم داخل الزخرفة
                    append(a.text)
                    append(" ۝")
                    append(Arabic.digits(a.numberInSurah))
                }
            }
            append(' ')
        }
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        fontFamily = QuranFont,
        fontSize = vm.fontSize.sp,
        lineHeight = (vm.fontSize * 1.9f).sp,
        textAlign = TextAlign.Justify,
        color = colors.onBackground,
    )
}
