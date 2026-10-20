package app.quran.tilawa

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** ذهبي الزخارف فوق اللازوردي */
val PatternGold = Color(0xFFD4AF5F)

/**
 * خلفية زخرفة إسلامية هندسية: نجوم ثمانية (مربعان متداخلان) في شبكة متكررة،
 * بينها معينات صغيرة، بخطوط ذهبية فوق لون الشريط. [strength] يخففها للخلفيات الفاتحة.
 */
@Composable
fun Modifier.islamicPattern(
    base: Color = MaterialTheme.colorScheme.primary,
    ink: Color = PatternGold,
    strength: Float = 1f,
    cellSize: Dp = 44.dp,
): Modifier {
    return clipToBounds().background(base).drawWithCache {
        val cell = cellSize.toPx()
        val r = cell * 0.42f
        val stroke = Stroke(width = 1.1.dp.toPx())
        val thin = Stroke(width = 0.8.dp.toPx())

        // نجمة ثمانية: ١٦ رأساً تتناوب بين نصف القطر الخارجي والداخلي
        fun star(c: Offset, outer: Float, inner: Float) = Path().apply {
            for (i in 0 until 16) {
                val a = Math.PI / 8 * i - Math.PI / 2
                val rr = if (i % 2 == 0) outer else inner
                val p = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        fun diamond(c: Offset, s: Float) = Path().apply {
            moveTo(c.x, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s, c.y); close()
        }

        val stars = mutableListOf<Path>()
        val inners = mutableListOf<Path>()
        val diamonds = mutableListOf<Path>()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (row in -1..rows) for (col in -1..cols) {
            val c = Offset(col * cell + cell / 2, row * cell + cell / 2)
            stars += star(c, r, r * 0.72f)
            inners += star(c, r * 0.48f, r * 0.34f)
            diamonds += diamond(Offset(c.x + cell / 2, c.y + cell / 2), cell * 0.12f)
        }
        val gold = ink.copy(alpha = 0.4f * strength)
        val faint = ink.copy(alpha = 0.22f * strength)
        onDrawBehind {
            stars.forEach { drawPath(it, gold, style = stroke) }
            inners.forEach { drawPath(it, faint, style = thin) }
            diamonds.forEach { drawPath(it, gold) }
        }
    }
}
