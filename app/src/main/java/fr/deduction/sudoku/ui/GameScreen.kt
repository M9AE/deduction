package fr.deduction.sudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import fr.deduction.sudoku.AppViewModel
import fr.deduction.sudoku.data.Progress
import fr.deduction.sudoku.game.ExOutcome
import fr.deduction.sudoku.game.FbKind
import fr.deduction.sudoku.game.Game
import fr.deduction.sudoku.game.Mode
import kotlinx.coroutines.delay

fun formatTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun GameScreen(vm: AppViewModel, game: Game) {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val quick = vm.quickMode

    LaunchedEffect(game) {
        while (true) {
            delay(1000)
            if (!game.finished && game.exOutcome == null && !game.hintOpen &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) game.elapsed++
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            // ---- En-tête
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = C.text) }
                val (title, accent) = when (game.mode) {
                    Mode.TRAINING -> "Entraînement" to ""
                    Mode.EXERCISE -> (game.tech?.label ?: "") to " · exercice"
                    Mode.APPLICATION -> (game.tech?.label ?: "") to " · grille"
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.text)) { append(title) }
                        withStyle(SpanStyle(color = C.blue)) { append(accent) }
                    },
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatTime(game.elapsed), fontSize = 16.sp, color = C.textSoft, modifier = Modifier.padding(end = 8.dp))
            }
            // ---- Compteurs / objectif
            val tech = game.tech
            if (game.mode == Mode.EXERCISE && tech != null) {
                vm.progressVersion
                val done = vm.progress.exercisesDone(tech)
                Text(
                    "Trouve un chiffre que permet de placer : ${tech.label}",
                    color = C.blue, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                )
                Text(
                    "Exercices réussis : ${minOf(done, Progress.EXERCISES_TO_PASS)}/${Progress.EXERCISES_TO_PASS}" +
                        if (!tech.single) "  ·  candidats déjà notés" else "",
                    color = C.textSoft, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Text("Erreurs : ${game.errors}", color = C.textSoft, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("Hasard : ${game.luckyCount}", color = if (game.luckyCount > 0) C.amber else C.textSoft, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Maîtrisés : ${game.masteredCount}", color = C.textSoft, fontSize = 14.sp)
                }
            }

            // ---- Grille
            SudokuGrid(
                values = game.values,
                isGiven = game::isGiven,
                notes = game.notes,
                marks = game.marks,
                selected = game.selectedCell,
                highlightDigit = game.highlightDigit(quick),
                onTap = { c -> game.tapCell(c, quick, scope) },
            )

            // ---- Retour sur le coup
            FeedbackBar(game)

            Spacer(Modifier.weight(1f))

            // ---- Barre d'outils
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.AutoMirrored.Outlined.Undo, "Annuler") { game.undo() }
                ToolButton(Icons.AutoMirrored.Outlined.Backspace, "Effacer") { game.erase() }
                ToolButton(Icons.Outlined.Edit, "Crayon", active = game.pencil) { game.pencil = !game.pencil }
                ToolButton(Icons.Outlined.Bolt, "Saisie rapide", active = quick) { vm.toggleQuick() }
                ToolButton(Icons.Outlined.Lightbulb, "Indice") { game.openHint(scope) }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Pavé numérique
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in 1..9) {
                    val rem = game.remaining(d)
                    val sel = quick && game.selectedDigit == d
                    Box(
                        Modifier.weight(1f).height(74.dp)
                            .then(if (sel) Modifier.shadow(4.dp, RoundedCornerShape(12.dp)) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color.White else C.key)
                            .clickable(enabled = rem > 0 || game.pencil) { game.tapDigit(d, quick, scope) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$d", fontSize = 30.sp,
                                color = when { rem <= 0 -> C.textSoft.copy(alpha = 0.25f); sel -> C.given; else -> C.textSoft }
                            )
                            Text(if (rem > 0) "$rem" else " ", fontSize = 12.sp, color = C.textSoft)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // ---- Résultat d'exercice
        game.exOutcome?.let { ExerciseOutcomePanel(vm, game, it, scope) }

        // ---- Fin de grille
        if (game.finished && game.mode != Mode.EXERCISE) FinishDialog(vm, game)

        // ---- Indices
        if (game.hintOpen) HintSheet(game)
    }
}

@Composable
private fun FeedbackBar(game: Game) {
    val fb = game.feedback
    Box(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(top = 8.dp)) {
        if (fb != null) {
            val (bg, fg) = when (fb.kind) {
                FbKind.GOOD -> C.blueLight to C.blue
                FbKind.NEUTRAL -> C.peer to C.text
                FbKind.WARN -> C.amberSoft to C.amber
                FbKind.BAD -> C.redSoft to C.red
                FbKind.TIP -> C.greenSoft to C.green
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(fb.text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                fb.tip?.let { Text("💡 $it", color = C.green, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(icon, label, tint = if (active) C.blue else C.textSoft, modifier = Modifier.size(32.dp).padding(2.dp))
            if (active) {
                Text(
                    "ON", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(start = 18.dp)
                        .clip(RoundedCornerShape(8.dp)).background(C.blue).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(label, fontSize = 12.sp, color = if (active) C.blue else C.textSoft)
    }
}

@Composable
private fun ExerciseOutcomePanel(vm: AppViewModel, game: Game, o: ExOutcome, scope: kotlinx.coroutines.CoroutineScope) {
    val success = o is ExOutcome.Success
    val msg = when (o) { is ExOutcome.Success -> o.message; is ExOutcome.Fail -> o.message }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (success) C.greenSoft else C.amberSoft),
                contentAlignment = Alignment.Center
            ) { Text(if (success) "✓" else "!", fontSize = 28.sp, color = if (success) C.green else C.amber, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text(if (success) "Réussi !" else "Pas tout à fait…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.text)
            Spacer(Modifier.height(6.dp))
            Text(msg, fontSize = 15.sp, color = C.text, textAlign = TextAlign.Center)
            if (success) {
                val t = game.tech!!
                vm.progressVersion
                val done = vm.progress.exercisesDone(t)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Exercices réussis : ${minOf(done, Progress.EXERCISES_TO_PASS)}/${Progress.EXERCISES_TO_PASS}" +
                        if (done >= Progress.EXERCISES_TO_PASS && !vm.progress.applicationDone(t)) " — passe à la grille d'application !" else "",
                    fontSize = 13.sp, color = C.textSoft, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
            if (success) {
                Button(onClick = { vm.nextExercise() }, Modifier.fillMaxWidth()) { Text("Exercice suivant") }
                TextButton(onClick = { vm.back() }) { Text("Retour à la leçon") }
            } else {
                Button(onClick = { vm.retryExercise() }, Modifier.fillMaxWidth()) { Text("Recommencer") }
                OutlinedButton(onClick = {
                    vm.retryExercise()
                    vm.game?.let { g -> g.openHint(scope); g.hintLevel = 3 }
                }, Modifier.fillMaxWidth()) { Text("Voir la solution") }
            }
        }
    }
}

@Composable
private fun FinishDialog(vm: AppViewModel, game: Game) {
    val judged = game.masteredCount + game.anticipatedCount + game.luckyCount
    val pct = if (judged == 0) 100 else (100 * (game.masteredCount + game.anticipatedCount) / judged)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Grille terminée !") },
        text = {
            Column {
                StatLine("Temps", formatTime(game.elapsed))
                StatLine("Précision logique", "$pct %")
                StatLine("Coups maîtrisés", "${game.masteredCount}")
                StatLine("Coups anticipés", "${game.anticipatedCount}")
                StatLine("Coups au hasard", "${game.luckyCount}")
                StatLine("Erreurs", "${game.errors}")
                StatLine("Indices", "${game.hintCount}")
                if (game.mode == Mode.APPLICATION) {
                    Spacer(Modifier.height(8.dp))
                    Text("Grille d'application validée pour ${game.tech?.label}.", color = C.green)
                }
            }
        },
        confirmButton = {
            if (game.mode == Mode.TRAINING) Button(onClick = { vm.back(); vm.startTraining(null) }) { Text("Nouvelle grille") }
            else Button(onClick = { vm.back() }) { Text("Continuer") }
        },
        dismissButton = { if (game.mode == Mode.TRAINING) TextButton(onClick = { vm.back() }) { Text("Retour") } },
        containerColor = Color.White,
    )
}

@Composable
fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.textSoft, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SmallLabel(text: String) = Text(text, color = C.textSoft, fontSize = 13.sp)

@Composable
fun BlueButtonColors() = ButtonDefaults.buttonColors(containerColor = C.blue)
