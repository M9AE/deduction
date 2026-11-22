package fr.deduction.sudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.deduction.engine.Engine
import fr.deduction.engine.Tech
import fr.deduction.sudoku.AppViewModel
import fr.deduction.sudoku.Screen
import fr.deduction.sudoku.data.Content
import fr.deduction.sudoku.data.Progress

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = C.text) }
        else Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
    ) { content() }
}

// ====================================================================== Accueil

@Composable
fun HomeScreen(vm: AppViewModel) {
    vm.progressVersion
    val techs = vm.careerTechs
    val mastered = techs.count { vm.progress.isMastered(it) }
    Column(Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Déduction", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = C.text)
        Text("Le sudoku qui t'apprend à raisonner", fontSize = 15.sp, color = C.textSoft)
        Spacer(Modifier.height(28.dp))

        CardBox(onClick = { vm.navigate(Screen.Career) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Outlined.School)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Carrière", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                        Text("Apprends les techniques une à une", fontSize = 14.sp, color = C.textSoft)
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { if (techs.isEmpty()) 0f else mastered.toFloat() / techs.size },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = C.blue, trackColor = C.key,
                )
                Spacer(Modifier.height(6.dp))
                Text("$mastered / ${techs.size} techniques maîtrisées", fontSize = 13.sp, color = C.textSoft)
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBox {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Outlined.FitnessCenter)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Entraînement", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                        Text("Grilles adaptées à tes techniques, avec analyse de chaque coup", fontSize = 14.sp, color = C.textSoft)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (vm.hasSavedTraining()) {
                    Button(onClick = { vm.resumeTraining() }, Modifier.fillMaxWidth()) { Text("Reprendre la partie") }
                    OutlinedButton(onClick = { vm.navigate(Screen.TrainingSetup) }, Modifier.fillMaxWidth()) { Text("Nouvelle grille") }
                } else {
                    Button(onClick = { vm.navigate(Screen.TrainingSetup) }, Modifier.fillMaxWidth()) { Text("Nouvelle grille") }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBox {
            Column {
                Text("Statistiques", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.text)
                Spacer(Modifier.height(6.dp))
                val p = vm.progress
                val judged = p.stat("mastered") + p.stat("anticipated") + p.stat("lucky")
                StatLine("Grilles terminées", "${p.stat("games")}")
                StatLine("Précision logique", if (judged == 0) "—" else "${100 * (p.stat("mastered") + p.stat("anticipated")) / judged} %")
                StatLine("Coups au hasard", "${p.stat("lucky")}")
                StatLine("Erreurs", "${p.stat("errors")}")
            }
        }
        if (vm.loading) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = C.blue, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Génération d'une grille…", color = C.textSoft)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(C.blueLight), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.blue, modifier = Modifier.size(26.dp))
    }
}

// ====================================================================== Carrière

@Composable
fun CareerScreen(vm: AppViewModel) {
    vm.progressVersion
    val techs = vm.careerTechs
    Column(Modifier.fillMaxSize().background(C.bg)) {
        TopBar("Carrière") { vm.back() }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            var lastChapter = ""
            val rows = ArrayList<Pair<String?, Tech>>()
            for (t in techs) {
                val ch = Content.chapterName(t)
                rows += (if (ch != lastChapter) ch else null) to t
                lastChapter = ch
            }
            items(rows) { (header, t) ->
                if (header != null) {
                    Text(header.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.textSoft,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
                }
                val unlocked = vm.progress.isUnlocked(t, techs)
                val mastered = vm.progress.isMastered(t)
                val ex = minOf(vm.progress.exercisesDone(t), Progress.EXERCISES_TO_PASS)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .clickable(enabled = unlocked) { vm.navigate(Screen.Lesson(t)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(
                            when { mastered -> C.blue; unlocked -> C.blueLight; else -> C.key }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            mastered -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            unlocked -> Text("${techs.indexOf(t) + 1}", color = C.blue, fontWeight = FontWeight.Bold)
                            else -> Icon(Icons.Filled.Lock, null, tint = C.textSoft, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (unlocked) C.text else C.textSoft)
                        Text(
                            when {
                                mastered -> "Maîtrisée"
                                !unlocked -> "Verrouillée"
                                vm.applicationPool(t).isEmpty() -> "Exercices $ex/${Progress.EXERCISES_TO_PASS}"
                                else -> "Exercices $ex/${Progress.EXERCISES_TO_PASS} · grille ${if (vm.progress.applicationDone(t)) "✓" else "à faire"}"
                            },
                            fontSize = 13.sp, color = if (mastered) C.blue else C.textSoft
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ====================================================================== Leçon

@Composable
fun LessonScreen(vm: AppViewModel, t: Tech) {
    vm.progressVersion
    val lesson = Content.lessons[t]
    val example = remember(t) {
        Content.exercises[t]?.firstOrNull()?.let { ex -> Engine.hint(ex.state, Tech.upTo(t))?.let { ex to it } }
    }
    val exampleCands = remember(example) {
        example?.let { (ex, h) ->
            val b = fr.deduction.engine.Board.fromValues(ex.state)
            h.pre.forEach { Engine.apply(b, it) }
            b.cands
        }
    }
    val p = vm.progress
    val exDone = minOf(p.exercisesDone(t), Progress.EXERCISES_TO_PASS)
    Column(Modifier.fillMaxSize().background(C.bg)) {
        TopBar(t.label) { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(Content.chapterName(t), color = C.blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            if (lesson != null) {
                CardBox {
                    Column {
                        Text("Le principe", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(lesson.principle, color = C.text, fontSize = 15.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Comment la repérer", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(lesson.spot, color = C.text, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
            if (example != null && exampleCands != null) {
                val (ex, h) = example
                Spacer(Modifier.height(14.dp))
                CardBox {
                    Column {
                        Text("Exemple", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        SudokuGrid(
                            values = ex.state.toList(), isGiven = { ex.state[it] != 0 },
                            notes = if (t.single && t != Tech.NAKED_SINGLE) List(81) { 0 } else exampleCands.toList(),
                            overlay = hintOverlayLevel3(h),
                        )
                        Spacer(Modifier.height(8.dp))
                        h.key?.let {
                            Text(it.text, fontSize = 14.sp, color = C.text, lineHeight = 20.sp)
                            Text("→ ${elimsText(it.elims)}", fontSize = 13.sp, color = C.red)
                        }
                        val pl = h.placement
                        Text(
                            if (h.key == null) pl.text else "On peut alors placer ${pl.digit} en ${fr.deduction.engine.Geo.cellName(pl.place)}.",
                            fontSize = 14.sp, color = C.green, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            CardBox {
                Column {
                    Text("Ta progression", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("1. Exercices ciblés : $exDone/${Progress.EXERCISES_TO_PASS}", color = C.text)
                    SmallLabel("Une position où seule cette technique permet d'avancer. Trouve le chiffre !")
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { vm.startExercise(t) }, Modifier.fillMaxWidth()) {
                        Text(if (exDone >= Progress.EXERCISES_TO_PASS) "Refaire un exercice" else "Commencer les exercices")
                    }
                    if (vm.applicationPool(t).isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("2. Grille d'application : ${if (p.applicationDone(t)) "validée ✓" else "à faire"}", color = C.text)
                    SmallLabel("Une grille complète où la technique est nécessaire, avec analyse de tes coups.")
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { vm.startApplication(t) }, Modifier.fillMaxWidth()) { Text("Jouer la grille") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Je maîtrise déjà cette technique", color = C.text, fontSize = 15.sp)
                            SmallLabel("La débloque en entraînement et ouvre la suivante.")
                        }
                        Switch(
                            checked = p.isMastered(t), onCheckedChange = { vm.markKnown(t, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = C.blue)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ====================================================================== Préparation entraînement

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainingSetupScreen(vm: AppViewModel) {
    vm.progressVersion
    val choices = remember { vm.trainingFocusChoices() }
    var focus by remember { mutableStateOf<Tech?>(null) }
    Column(Modifier.fillMaxSize().background(C.bg)) {
        TopBar("Entraînement") { vm.back() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CardBox {
                Column {
                    Text("Techniques actives", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    SmallLabel("Les singletons + les techniques que tu maîtrises. Les grilles n'exigent rien d'autre, et l'ordinateur juge tes coups avec ces techniques.")
                    Spacer(Modifier.height(8.dp))
                    Text(Tech.fromMask(vm.trainingAllowed()).joinToString(" · ") { it.label }, color = C.text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            CardBox {
                Column {
                    Text("Technique à travailler", fontWeight = FontWeight.SemiBold, color = C.text, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    SmallLabel("La grille demandera au moins une fois cette technique (la plus difficile requise).")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Mélange", focus == null) { focus = null }
                        for (t in choices) Chip(t.label, focus == t) { focus = t }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.back(); vm.startTraining(focus) }, Modifier.fillMaxWidth().height(52.dp)) {
                Text("Commencer", fontSize = 17.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) C.blue else C.key)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (selected) Color.White else C.text, fontSize = 14.sp) }
}
