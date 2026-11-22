package fr.deduction.sudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.deduction.engine.Geo
import fr.deduction.engine.Hint
import fr.deduction.engine.Tech
import fr.deduction.sudoku.game.Game
import fr.deduction.sudoku.game.HintData

/** Unité principale à montrer au niveau 2. */
fun hintUnit(h: Hint): Int {
    val k = h.key
    if (k != null) return if (k.unit >= 0) k.unit else Geo.rowOf[k.cells.first()]
    return if (h.placement.unit >= 0) h.placement.unit else 18 + Geo.boxOf[h.placement.place]
}

fun hintOverlayLevel3(h: Hint): GridOverlay {
    val k = h.key
    val pattern = (k?.cells ?: h.placement.cells).toSet()
    val elims = (h.pre.flatMap { it.elims } + (k?.elims ?: emptyList())).toSet()
    val tint = if (k == null && h.placement.unit >= 0) Geo.units[h.placement.unit].toSet() else emptySet()
    return GridOverlay(
        tint = tint, pattern = pattern, patternDigits = (k?.digits ?: emptyList()).toSet(), elims = elims,
        target = h.placement.place, targetDigit = h.placement.digit,
    )
}

@Composable
fun HintSheet(game: Game) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, null) { game.hintOpen = false }
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(22.dp)).background(C.bg)
                .clickable(remember { MutableInteractionSource() }, null) {} // absorbe les clics
        ) {
            // Barre du haut : 1 2 3 + croix
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Indice", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(C.key).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (lvl in 1..3) {
                        val sel = game.hintLevel == lvl
                        Box(
                            Modifier.size(width = 52.dp, height = 36.dp).clip(RoundedCornerShape(11.dp))
                                .background(if (sel) Color.White else Color.Transparent)
                                .clickable { game.hintLevel = lvl },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$lvl", fontSize = 18.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) C.blue else C.textSoft)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { game.hintOpen = false }) { Icon(Icons.Filled.Close, "Fermer", tint = C.text) }
            }
            val data = game.hintData
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (data == null) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.blue) }
                } else when (game.hintLevel) {
                    1 -> Level1(data)
                    2 -> Level2(data)
                    else -> Level3(game, data)
                }
            }
        }
    }
}

@Composable
private fun Level1(d: HintData) {
    val a = d.avail
    Text("Niveau 1 · Combien de coups ?", color = C.textSoft, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))
    Text("${a.total}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = C.blue)
    Text(
        if (a.total <= 1) "coup disponible" else "coups disponibles",
        fontSize = 18.sp, color = C.text
    )
    Spacer(Modifier.height(6.dp))
    if (a.total > 0) Text(
        "dont ${a.direct} par simple singleton (sans élimination préalable)",
        fontSize = 14.sp, color = C.textSoft
    )
    Spacer(Modifier.height(20.dp))
    val msg = when {
        a.total == 0 && d.missing != null -> "Aucun coup avec tes techniques actuelles. Ce blocage demande : ${d.missing.label}."
        a.total == 0 -> "Aucun coup trouvé avec les techniques connues du moteur."
        a.total >= 5 -> "Pas besoin d'aide : ouvre l'œil, il y en a plein !"
        a.total >= 2 -> "Quelques coups t'attendent. Balaie la grille chiffre par chiffre."
        else -> "Un seul coup possible. Si tu bloques, passe au niveau 2."
    }
    Text(msg, fontSize = 16.sp, color = C.text)
    a.simplest?.let {
        Spacer(Modifier.height(10.dp))
        Text("Technique la plus simple qui fonctionne : ${it.label}", fontSize = 14.sp, color = C.textSoft)
    }
}

@Composable
private fun Level2(d: HintData) {
    Text("Niveau 2 · Quelle technique, où ?", color = C.textSoft, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    val h = d.hint
    if (h == null) {
        Text(d.missing?.let { "Il faut une technique que tu n'as pas encore apprise : ${it.label}." } ?: "Pas d'indice disponible.",
            fontSize = 16.sp, color = C.text)
        return
    }
    val u = hintUnit(h)
    Text(h.tech.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.blue)
    Spacer(Modifier.height(4.dp))
    Text("Regarde ${Geo.the(u)}.", fontSize = 17.sp, color = C.text)
    if (h.pre.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text("(après quelques éliminations plus simples : ${h.pre.map { it.tech.label }.distinct().joinToString(", ")})",
            fontSize = 13.sp, color = C.textSoft)
    }
    Spacer(Modifier.height(14.dp))
    SudokuGrid(
        values = d.values.toList(), isGiven = { d.values[it] != 0 }, notes = List(81) { 0 },
        overlay = GridOverlay(tint = Geo.units[u].toSet()),
    )
}

@Composable
private fun Level3(game: Game, d: HintData) {
    Text("Niveau 3 · Solution expliquée", color = C.textSoft, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    val h = d.hint
    if (h == null) {
        Text(d.missing?.let { "Il faut une technique que tu n'as pas encore apprise : ${it.label}." } ?: "Pas d'indice disponible.",
            fontSize = 16.sp, color = C.text)
        return
    }
    Text(h.tech.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.blue)
    Spacer(Modifier.height(8.dp))
    for (p in h.pre) {
        Text("D'abord (${p.tech.label}) : ${p.text}", fontSize = 14.sp, color = C.textSoft)
        Text("→ ${elimsText(p.elims)}", fontSize = 13.sp, color = C.red)
        Spacer(Modifier.height(6.dp))
    }
    h.key?.let { k ->
        Text(k.text, fontSize = 15.sp, color = C.text)
        Text("→ ${elimsText(k.elims)}", fontSize = 14.sp, color = C.red)
        Spacer(Modifier.height(6.dp))
    }
    val pl = h.placement
    Text(
        if (h.key == null) pl.text
        else "On peut alors placer ${pl.digit} en ${Geo.cellName(pl.place)} (${pl.tech.label.lowercase()}) : ${pl.text}",
        fontSize = 15.sp, color = C.green, fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(12.dp))
    val showCands = h.key != null || h.pre.isNotEmpty() || h.placement.tech == Tech.NAKED_SINGLE
    SudokuGrid(
        values = d.values.toList(), isGiven = { d.values[it] != 0 },
        notes = if (showCands) d.cands.toList() else List(81) { 0 },
        overlay = hintOverlayLevel3(h),
    )
    Spacer(Modifier.height(14.dp))
    Button(onClick = { game.applyHint() }, Modifier.fillMaxWidth()) { Text("Placer le ${pl.digit} en ${Geo.cellName(pl.place)}") }
}
