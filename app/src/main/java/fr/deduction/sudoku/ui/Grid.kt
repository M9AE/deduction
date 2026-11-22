package fr.deduction.sudoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import fr.deduction.engine.Bits
import fr.deduction.engine.Geo
import fr.deduction.engine.elimCell
import fr.deduction.engine.elimDigit
import fr.deduction.sudoku.game.Mark

/** Surimpression pour illustrer un indice ou une leçon. */
class GridOverlay(
    val tint: Set<Int> = emptySet(),
    val pattern: Set<Int> = emptySet(),
    val patternDigits: Set<Int> = emptySet(),
    val elims: Set<Int> = emptySet(),
    val target: Int = -1,
    val targetDigit: Int = 0,
)

@Composable
fun SudokuGrid(
    values: List<Int>,
    isGiven: (Int) -> Boolean,
    notes: List<Int>,
    modifier: Modifier = Modifier,
    marks: List<Mark>? = null,
    selected: Int = -1,
    highlightDigit: Int = 0,
    overlay: GridOverlay? = null,
    onTap: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val density = LocalDensity.current
        val sizePx = with(density) { maxWidth.toPx() }
        val cell = sizePx / 9f
        val big = remember(cell) {
            val st = TextStyle(fontSize = with(density) { (cell * 0.62f).toSp() }, fontWeight = FontWeight.Normal)
            (1..9).map { measurer.measure(it.toString(), st) }
        }
        val small = remember(cell) {
            val st = TextStyle(fontSize = with(density) { (cell * 0.24f).toSp() }, fontWeight = FontWeight.Medium)
            (1..9).map { measurer.measure(it.toString(), st) }
        }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).then(
                if (onTap != null) Modifier.pointerInput(cell) {
                    detectTapGestures { o ->
                        val c = (o.x / cell).toInt().coerceIn(0, 8)
                        val r = (o.y / cell).toInt().coerceIn(0, 8)
                        tap?.invoke(r * 9 + c)
                    }
                } else Modifier
            )
        ) {
            drawRect(Color.White)
            // Fonds
            for (c in 0 until 81) {
                val r = c / 9; val col = c % 9
                val tl = Offset(col * cell, r * cell)
                val v = values[c]
                val wrong = marks?.get(c) == Mark.WRONG
                val bg: Color? = when {
                    overlay != null && c == overlay.target -> C.greenSoft
                    overlay != null && c in overlay.pattern -> C.blueLight
                    overlay != null && c in overlay.tint -> Color(0xFFEAF0FD)
                    c == selected -> Color(0xFFC3D4F7)
                    wrong -> C.redSoft
                    v != 0 && v == highlightDigit -> C.blueSoft
                    selected >= 0 && (Geo.rowOf[c] == Geo.rowOf[selected] || Geo.colOf[c] == Geo.colOf[selected] ||
                        Geo.boxOf[c] == Geo.boxOf[selected]) -> C.peer
                    else -> null
                }
                if (bg != null) drawRect(bg, tl, Size(cell, cell))
            }
            // Chiffres et notes
            for (c in 0 until 81) {
                val r = c / 9; val col = c % 9
                val x0 = col * cell; val y0 = r * cell
                val v = values[c]
                if (v != 0) {
                    val color = when {
                        isGiven(c) -> C.given
                        marks?.get(c) == Mark.WRONG -> C.red
                        marks?.get(c) == Mark.LUCKY -> C.amber
                        marks?.get(c) == Mark.HINT -> C.green
                        else -> C.blue
                    }
                    drawCentered(big[v - 1], color, x0, y0, cell, cell)
                } else if (overlay != null && c == overlay.target) {
                    drawCentered(big[overlay.targetDigit - 1], C.green, x0, y0, cell, cell)
                } else {
                    val n = notes[c]
                    if (n != 0) {
                        val sc = cell / 3f
                        for (d in 1..9) {
                            if (!Bits.has(n, d)) continue
                            val nx = x0 + ((d - 1) % 3) * sc
                            val ny = y0 + ((d - 1) / 3) * sc
                            val elimHere = overlay != null && (c * 10 + d) in overlay.elims
                            val pat = overlay != null && c in overlay.pattern && d in overlay.patternDigits
                            val hl = overlay == null && d == highlightDigit
                            val nb = when { elimHere -> C.red.copy(alpha = 0.85f); pat -> C.blue; hl -> C.blueSoft; else -> null }
                            if (nb != null) drawRoundRect(nb, Offset(nx + sc * 0.08f, ny + sc * 0.08f), Size(sc * 0.84f, sc * 0.84f), CornerRadius(sc * 0.18f))
                            val tc = if (elimHere || pat) Color.White else C.note
                            drawCentered(small[d - 1], tc, nx, ny, sc, sc)
                        }
                    }
                }
            }
            // Lignes
            for (i in 0..9) {
                val thick = i % 3 == 0
                val w = if (thick) cell * 0.045f else cell * 0.015f
                val col = if (thick) C.lineThick else C.lineThin
                if (!thick) {
                    drawLine(col, Offset(i * cell, 0f), Offset(i * cell, size.height), w)
                    drawLine(col, Offset(0f, i * cell), Offset(size.width, i * cell), w)
                }
            }
            for (i in 0..9 step 3) {
                val w = cell * 0.045f
                val p = (i * cell).coerceIn(w / 2, size.width - w / 2)
                drawLine(C.lineThick, Offset(p, 0f), Offset(p, size.height), w)
                drawLine(C.lineThick, Offset(0f, p), Offset(size.width, p), w)
            }
        }
    }
}

private fun DrawScope.drawCentered(t: TextLayoutResult, color: Color, x: Float, y: Float, w: Float, h: Float) {
    drawText(t, color, Offset(x + (w - t.size.width) / 2f, y + (h - t.size.height) / 2f))
}

/** Liste des éliminations à afficher (helper pour les textes). */
fun elimsText(elims: List<Int>): String =
    elims.groupBy { elimDigit(it) }.entries.joinToString(" ; ") { (d, es) ->
        "$d retiré de " + es.joinToString(", ") { Geo.cellName(elimCell(it)) }
    }
