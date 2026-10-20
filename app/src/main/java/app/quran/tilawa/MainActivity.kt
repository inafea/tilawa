package app.quran.tilawa

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // أشرطة النظام فوق الترويسة الخضراء: أيقونات فاتحة دائماً
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        // إذن الإشعارات لعرض أزرار التحكم أثناء التشغيل في الخلفية (أندرويد 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TilawaTheme {
                // الواجهة بالكامل من اليمين إلى اليسار بغض النظر عن لغة الجهاز
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vm: QuranViewModel = viewModel()
                    val snackbar = remember { SnackbarHostState() }

                    LaunchedEffect(vm.message) {
                        vm.message?.let {
                            snackbar.showSnackbar(it)
                            vm.message = null
                        }
                    }

                    val surah = vm.openSurah
                    if (surah == null) {
                        SurahListScreen(vm, snackbar)
                    } else {
                        key(surah) { ReaderScreen(vm, surah, snackbar) }
                    }
                }
            }
        }
    }
}
