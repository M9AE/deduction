package fr.deduction.sudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.deduction.engine.Engine
import fr.deduction.engine.Generator
import fr.deduction.engine.Tech
import fr.deduction.sudoku.data.Content
import fr.deduction.sudoku.data.Progress
import fr.deduction.sudoku.game.ExOutcome
import fr.deduction.sudoku.game.Game
import fr.deduction.sudoku.game.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed class Screen {
    data object Home : Screen()
    data object Career : Screen()
    data class Lesson(val tech: Tech) : Screen()
    data object TrainingSetup : Screen()
    data object Play : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val progress = Progress(app)

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val backStack = ArrayList<Screen>()

    var game by mutableStateOf<Game?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Incrémenté à chaque changement de progression (pour rafraîchir l'UI). */
    var progressVersion by mutableIntStateOf(0)
        private set

    var quickMode by mutableStateOf(progress.quickMode)
        private set

    private var exerciseCursor = HashMap<Tech, Int>()

    init {
        Content.load(app)
    }

    fun toggleQuick() {
        quickMode = !quickMode
        progress.quickMode = quickMode
    }

    fun navigate(s: Screen) {
        backStack += screen
        screen = s
    }

    fun back(): Boolean {
        if (screen == Screen.Play) saveNow()
        val prev = backStack.removeLastOrNull() ?: return false
        screen = prev
        return true
    }

    private fun bumpProgress() { progressVersion++ }

    fun hasSavedTraining() = progress.savedGame != null

    fun saveNow() {
        val g = game ?: return
        if (g.mode == Mode.TRAINING && !g.finished) progress.savedGame = g.toJson()
    }

    // ------------------------------------------------------------------ Carrière

    val careerTechs: List<Tech> get() = Content.careerTechs

    fun markKnown(t: Tech, known: Boolean) {
        progress.setMastered(t, known)
        bumpProgress()
    }

    fun startExercise(t: Tech, next: Boolean = false) {
        val list = Content.exercises[t] ?: return
        val cur = exerciseCursor[t] ?: (progress.exercisesDone(t) % list.size)
        val idx = if (next) (cur + 1) % list.size else cur
        exerciseCursor[t] = idx
        val ex = list[idx]
        val g = Game(
            Mode.EXERCISE, t, Tech.upTo(t), ex.state, ex.solution,
            exerciseIndex = idx, prefillNotes = !t.single
        )
        g.onChanged = {
            val o = g.exOutcome
            if (o is ExOutcome.Success && !successCounted) {
                successCounted = true
                progress.setExercisesDone(t, progress.exercisesDone(t) + 1)
                checkMastery(t)
                bumpProgress()
            }
        }
        successCounted = false
        openGame(g)
    }

    private var successCounted = false

    fun retryExercise() {
        val g = game ?: return
        val t = g.tech ?: return
        startExerciseReplace(t, next = false)
    }

    fun nextExercise() {
        val g = game ?: return
        val t = g.tech ?: return
        startExerciseReplace(t, next = true)
    }

    private fun startExerciseReplace(t: Tech, next: Boolean) {
        // remplace l'écran de jeu courant sans empiler
        if (screen == Screen.Play) backStack.removeLastOrNull()?.let { screen = it }
        startExercise(t, next)
    }

    /** Grilles complètes où t est la technique la plus difficile requise. */
    fun applicationPool(t: Tech) = Content.bank.filter { it.hardest == t }

    fun startApplication(t: Tech) {
        val played = progress.played
        val pool = applicationPool(t)
        if (pool.isEmpty()) return
        val e = pool.filter { it.id !in played }.ifEmpty { pool }.random()
        val g = Game(Mode.APPLICATION, t, Tech.upTo(t), e.puzzle, e.solution, bankId = e.id)
        g.onFinished = {
            progress.setApplicationDone(t)
            progress.played = progress.played + e.id
            recordStats(g)
            checkMastery(t)
            bumpProgress()
        }
        openGame(g)
    }

    private fun checkMastery(t: Tech) {
        val appOk = progress.applicationDone(t) || applicationPool(t).isEmpty()
        if (progress.exercisesDone(t) >= Progress.EXERCISES_TO_PASS && appOk) progress.setMastered(t, true)
    }

    // ------------------------------------------------------------------ Entraînement

    /** Techniques autorisées en entraînement : les singletons + les techniques maîtrisées. */
    fun trainingAllowed(): Int = progress.masteredMask or Tech.maskOf(Tech.SINGLES)

    fun trainingFocusChoices(): List<Tech> = Tech.fromMask(trainingAllowed()).filter { t ->
        Content.bank.any { it.hardest == t && it.mask and trainingAllowed().inv() == 0 }
    }

    fun resumeTraining() {
        val g = progress.savedGame?.let { Game.fromJson(it) } ?: return
        wireTraining(g)
        openGame(g)
    }

    fun startTraining(focus: Tech?) {
        progress.savedGame = null
        val allowed = trainingAllowed()
        val played = progress.played
        val ok = Content.bank.filter { it.mask and allowed.inv() == 0 }
        val target = focus ?: run {
            // Sans focus : on privilégie les techniques les plus avancées maîtrisées
            val hs = ok.map { it.hardest }.distinct().sortedBy { it.ordinal }
            if (hs.isEmpty()) null else {
                val w = hs.mapIndexed { i, _ -> i + 1 }
                var r = Random.nextInt(w.sum())
                var pick = hs.last()
                for ((i, t) in hs.withIndex()) { r -= w[i]; if (r < 0) { pick = t; break } }
                pick
            }
        }
        val pool = ok.filter { target == null || it.hardest == target }
        val fresh = pool.filter { it.id !in played }
        if (fresh.isNotEmpty()) {
            val e = fresh.random()
            val g = Game(Mode.TRAINING, null, allowed, e.puzzle, e.solution, bankId = e.id)
            wireTraining(g)
            openGame(g)
            return
        }
        // Banque épuisée pour ce critère : on génère une grille sur l'appareil
        loading = true
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                val t0 = System.currentTimeMillis()
                var best: Pair<IntArray, IntArray>? = null
                while (System.currentTimeMillis() - t0 < 4000) {
                    val (p, s) = Generator.makePuzzle(Random)
                    val r = Engine.solve(p, allowed)
                    if (!r.solved) continue
                    if (target == null || r.hardest == target) { best = p to s; break }
                    if (best == null) best = p to s
                }
                best
            }
            loading = false
            val (p, s) = res ?: pool.randomOrNull()?.let { it.puzzle to it.solution } ?: return@launch
            val g = Game(Mode.TRAINING, null, allowed, p, s)
            wireTraining(g)
            openGame(g)
        }
    }

    private fun wireTraining(g: Game) {
        g.onChanged = { if (!g.finished) progress.savedGame = g.toJson() }
        g.onFinished = {
            progress.savedGame = null
            if (g.bankId >= 0) progress.played = progress.played + g.bankId
            recordStats(g)
            bumpProgress()
        }
    }

    private fun recordStats(g: Game) {
        progress.addStat("games")
        progress.addStat("mastered", g.masteredCount)
        progress.addStat("lucky", g.luckyCount)
        progress.addStat("anticipated", g.anticipatedCount)
        progress.addStat("errors", g.errors)
        progress.addStat("hints", g.hintCount)
    }

    private fun openGame(g: Game) {
        g.pencil = false
        game = g
        if (screen == Screen.Play) screen = Screen.Play else navigate(Screen.Play)
    }
}
