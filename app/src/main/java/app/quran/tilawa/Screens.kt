package app.quran.tilawa

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// الشاشة الرئيسية: فهرس السور
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahListScreen(vm: QuranViewModel, snackbar: SnackbarHostState) {
    val surahs = remember(vm.query) { Surahs.search(vm.query) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("القرآن الكريم", fontFamily = QuranFont, fontSize = 28.sp) },
                actions = {
                    IconButton(onClick = { vm.cycleTheme() }) {
                        when (vm.themeMode) {
                            1 -> Icon(Icons.Filled.LightMode, "الوضع الفاتح (اضغط للتبديل)")
                            2 -> Icon(Icons.Filled.DarkMode, "الوضع الداكن (اضغط للتبديل)")
                            else -> Icon(Icons.Filled.BrightnessAuto, "الوضع حسب النظام (اضغط للتبديل)")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "المزيد")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("حفظ نص المصحف للقراءة دون إنترنت") },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                            onClick = { menuOpen = false; vm.downloadAllText() },
                        )
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
        bottomBar = {
            if (vm.playingSurah != null) {
                PlayerBar(vm, onTitleClick = { vm.playingSurah?.let { vm.openSurah(it, vm.playingAyah) } })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث باسم السورة أو رقمها") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Close, "مسح") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            item {
                Text("القارئ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ReciterChips(vm)
            }

            if (vm.downloading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            if (vm.lastSurah > 0 && vm.query.isBlank()) {
                item { ContinueCard(vm) }
            }

            items(surahs, key = { it.number }) { s ->
                SurahRow(
                    surah = s,
                    playing = vm.playingSurah == s.number,
                    onClick = { vm.openSurah(s.number) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(vm: QuranViewModel) {
    val s = Surahs.get(vm.lastSurah)
    Card(
        onClick = { vm.openSurah(s.number, vm.lastAyah) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("متابعة من حيث توقفت", style = MaterialTheme.typography.labelMedium)
                Text(
                    "سورة ${s.name} • الآية ${Arabic.digits(vm.lastAyah)}",
                    fontFamily = QuranFont, fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            FilledIconButton(onClick = { vm.openSurah(s.number, vm.lastAyah); vm.playFrom(s.number, vm.lastAyah) }) {
                Icon(Icons.Filled.PlayArrow, "استماع")
            }
        }
    }
}

@Composable
private fun SurahRow(surah: Surah, playing: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(surah.number)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("سورة ${surah.name}", fontFamily = QuranFont, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${surah.revelation} • ${Arabic.ayahCountLabel(surah.ayahCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (playing) Icon(Icons.Filled.GraphicEq, "قيد التشغيل", tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun NumberBadge(n: Int) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Arabic.digits(n),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReciterChips(vm: QuranViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Reciters.all.forEach { r ->
            FilterChip(
                selected = r.id == vm.reciter.id,
                onClick = { vm.selectReciter(r) },
                label = { Text(r.name) },
                leadingIcon = if (r.id == vm.reciter.id) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

// شاشة المصحف (صفحات) في MushafScreen.kt

// =====================================================================
// شريط التحكم بالتلاوة
// =====================================================================

@Composable
fun PlayerBar(vm: QuranViewModel, onTitleClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val playing = vm.playingSurah
    val title = when {
        playing != null -> "سورة ${Surahs.get(playing).name} • " +
            if (vm.playingAyah == 0) "البسملة" else "الآية ${Arabic.digits(vm.playingAyah.coerceAtLeast(1))}"
        vm.openSurah != null -> "استمع إلى سورة ${Surahs.get(vm.openSurah!!).name}"
        else -> "اختر سورة للاستماع"
    }

    Surface(color = colors.surfaceVariant, shadowElevation = 12.dp, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).then(
                    if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
                )
            ) {
                Text(title, fontFamily = QuranFont, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.reciter.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }

            IconButton(onClick = { vm.toggleRepeat() }) {
                Icon(
                    if (vm.repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = if (vm.repeatOne) "إيقاف تكرار الآية" else "تكرار الآية",
                    tint = if (vm.repeatOne) colors.secondary else colors.onSurfaceVariant,
                )
            }
            // في الواجهة العربية: "السابق" على اليمين بسهم يشير لليمين، و"التالي" على اليسار
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Filled.SkipNext, "الآية السابقة")
            }
            FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(52.dp)) {
                if (vm.isBuffering && !vm.isPlaying) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = colors.onPrimary)
                } else {
                    Icon(
                        if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (vm.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Filled.SkipPrevious, "الآية التالية")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun greenBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
