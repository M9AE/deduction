package fr.deduction.sudoku.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette bleu / gris inspirée de l'app de référence. */
object C {
    val bg = Color(0xFFF4F6FA)
    val surface = Color(0xFFFFFFFF)
    val blue = Color(0xFF2F67D8)
    val blueSoft = Color(0xFF8BADF2)      // cases du chiffre sélectionné
    val blueLight = Color(0xFFDCE6FB)     // motif d'indice
    val peer = Color(0xFFE7EAF0)          // ligne/colonne/bloc de la case sélectionnée
    val given = Color(0xFF2A2D34)
    val text = Color(0xFF3B404B)
    val textSoft = Color(0xFF6B7280)
    val note = Color(0xFF545B68)
    val lineThin = Color(0xFFC9CED8)
    val lineThick = Color(0xFF5B6270)
    val key = Color(0xFFE9ECF2)           // touches du pavé
    val red = Color(0xFFDC3F35)
    val redSoft = Color(0xFFF7D3D0)
    val amber = Color(0xFFC77A00)
    val amberSoft = Color(0xFFFBE9C8)
    val green = Color(0xFF1E9E5A)
    val greenSoft = Color(0xFFD2F0DF)
}

@Composable
fun DeductionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.blue,
            onPrimary = Color.White,
            background = C.bg,
            surface = C.surface,
            onSurface = C.text,
            onBackground = C.text,
            secondaryContainer = C.blueLight,
            surfaceVariant = C.key,
        ),
        content = content
    )
}
