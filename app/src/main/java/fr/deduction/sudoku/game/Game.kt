package fr.deduction.sudoku.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.deduction.engine.Availability
import fr.deduction.engine.Bits
import fr.deduction.engine.Board
import fr.deduction.engine.Engine
import fr.deduction.engine.Geo
import fr.deduction.engine.Hint
import fr.deduction.engine.Tech
import fr.deduction.engine.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Mode { EXERCISE, APPLICATION, TRAINING }

enum class Mark { NONE, MASTERED, ANTICIPATED, LUCKY, WRONG, HINT }

enum class FbKind { GOOD, NEUTRAL, WARN, BAD, TIP }

class Feedback(val text: String, val kind: FbKind, val tip: String? = null)

sealed class ExOutcome {
    class Success(val message: String) : ExOutcome()
    class Fail(val message: String) : ExOutcome()
}

/** Données d'indice calculées pour la position courante. */
class HintData(
    val avail: Availability,
    val hint: Hint?,
    val missing: Tech?,
    val values: IntArray,
    /** Candidats après les éliminations préalables (pour l'affichage du niveau 3). */
    val cands: IntArray,
)

class Game(
    val mode: Mode,
    val tech: Tech?,
    val allowed: Int,
    val start: IntArray,
    val solution: IntArray,
    val bankId: Int = -1,
    val exerciseIndex: Int = -1,
    prefillNotes: Boolean = false,
) {
    val values = mutableStateListOf<Int>().apply { addAll(start.toList()) }
    val notes = mutableStateListOf<Int>().apply {
        val b = Board.fromValues(start)
        addAll(List(81) { if (prefillNotes) b.cands[it] else 0 })
    }
    val marks = mutableStateListOf<Mark>().apply { addAll(List(81) { Mark.NONE }) }

    var selectedCell by mutableIntStateOf(-1)
    var selectedDigit by mutableIntStateOf(1)
    var pencil by mutableStateOf(false)
    var feedback by mutableStateOf<Feedback?>(null)
    var finished by mutableStateOf(false)
    var exOutcome by mutableStateOf<ExOutcome?>(null)
    var elapsed by mutableIntStateOf(0)

    var errors by mutableIntStateOf(0)
    var luckyCount by mutableIntStateOf(0)
    var masteredCount by mutableIntStateOf(0)
    var anticipatedCount by mutableIntStateOf(0)
    var hintCount by mutableIntStateOf(0)

    // Indices
    var hintOpen by mutableStateOf(false)
    var hintLevel by mutableIntStateOf(1)
    var hintData by mutableStateOf<HintData?>(null)

    var onFinished: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private class Snap(val v: IntArray, val n: IntArray, val m: Array<Mark>)
    private val undoStack = ArrayList<Snap>()

    fun isGiven(c: Int) = start[c] != 0

    /** Nombre de fois où le chiffre d est encore à placer. */
    fun remaining(d: Int): Int = 9 - (0 until 81).count { values[it] == d && marks[it] != Mark.WRONG }

    fun highlightDigit(quick: Boolean): Int =
        if (quick) selectedDigit else selectedCell.takeIf { it >= 0 }?.let { values[it] } ?: 0

    private fun correctValues(): IntArray = IntArray(81) { if (values[it] != 0 && marks[it] != Mark.WRONG) values[it] else 0 }

    private fun pushUndo() {
        undoStack += Snap(values.toIntArray(), notes.toIntArray(), marks.toTypedArray())
        if (undoStack.size > 200) undoStack.removeAt(0)
    }

    fun undo() {
        if (exOutcome != null || finished) return
        val s = undoStack.removeLastOrNull() ?: return
        for (i in 0 until 81) { values[i] = s.v[i]; notes[i] = s.n[i]; marks[i] = s.m[i] }
        feedback = null
        onChanged?.invoke()
    }

    fun tapCell(c: Int, quick: Boolean, scope: CoroutineScope) {
        if (finished || exOutcome != null) return
        selectedCell = c
        if (quick) {
            if (values[c] != 0 && marks[c] != Mark.WRONG) { selectedDigit = values[c]; return }
            input(c, selectedDigit, scope)
        }
    }

    fun tapDigit(d: Int, quick: Boolean, scope: CoroutineScope) {
        if (finished || exOutcome != null) return
        if (quick) { selectedDigit = d; return }
        if (selectedCell >= 0) input(selectedCell, d, scope)
    }

    private fun input(c: Int, d: Int, scope: CoroutineScope) {
        if (isGiven(c)) return
        if (values[c] != 0 && marks[c] != Mark.WRONG) return // chiffre juste : verrouillé
        if (pencil) {
            if (values[c] != 0) return
            pushUndo()
            notes[c] = notes[c] xor Bits.bit(d)
            onChanged?.invoke()
            return
        }
        if (values[c] == d) return
        place(c, d, scope)
    }

    private fun place(c: Int, d: Int, scope: CoroutineScope) {
        pushUndo()
        val before = correctValues()
        values[c] = d
        notes[c] = 0
        if (solution[c] != d) {
            marks[c] = Mark.WRONG
            errors++
            feedback = Feedback("✗ Erreur : ce n'est pas un $d.", FbKind.BAD)
            if (mode == Mode.EXERCISE) exOutcome = ExOutcome.Fail("Ce chiffre est faux. Observe à nouveau la position.")
            onChanged?.invoke()
            return
        }
        marks[c] = Mark.NONE
        for (p in Geo.peers[c]) notes[p] = notes[p] and Bits.bit(d).inv()
        checkFinished()
        onChanged?.invoke()
        scope.launch {
            val j = withContext(Dispatchers.Default) { Engine.judge(before, solution, c, d, allowed) }
            if (values[c] != d) return@launch // annulé entre-temps
            when (j.verdict) {
                Verdict.MASTERED -> {
                    marks[c] = Mark.MASTERED
                    masteredCount++
                    val tip = j.simplerTech?.let {
                        val n = j.simplerCount
                        "Il y avait plus simple : $n coup${if (n > 1) "s" else ""} par ${it.label.lowercase()}. Commence par la cascade !"
                    }
                    feedback = Feedback("✓ ${j.tech!!.label}", FbKind.GOOD, tip)
                    if (mode == Mode.EXERCISE && exOutcome == null) {
                        exOutcome = if (j.tech == tech) ExOutcome.Success("Bravo ! Tu as trouvé le coup grâce à : ${j.tech.label}.")
                        else ExOutcome.Fail("Juste, mais trouvé avec ${j.tech.label} et non ${tech?.label}.")
                    }
                }
                Verdict.ANTICIPATED -> {
                    marks[c] = Mark.ANTICIPATED
                    anticipatedCount++
                    feedback = Feedback("↷ Coup anticipé : juste, mais il fallait d'abord poser d'autres chiffres.", FbKind.NEUTRAL)
                    if (mode == Mode.EXERCISE && exOutcome == null)
                        exOutcome = ExOutcome.Fail("Ce chiffre est juste, mais il n'était pas encore déductible ici. Cherche le coup que permet la technique.")
                }
                Verdict.LUCKY -> {
                    marks[c] = Mark.LUCKY
                    luckyCount++
                    feedback = Feedback("⚠ Coup au hasard : juste, mais rien ne permettait encore de le déduire.", FbKind.WARN)
                    if (mode == Mode.EXERCISE && exOutcome == null)
                        exOutcome = ExOutcome.Fail("Ce chiffre est juste… mais par hasard : il n'était pas déductible. Réessaie.")
                }
                Verdict.WRONG -> {}
            }
            onChanged?.invoke()
        }
    }

    fun erase() {
        if (finished || exOutcome != null) return
        val c = selectedCell
        if (c < 0 || isGiven(c)) return
        if (marks[c] == Mark.WRONG) {
            pushUndo(); values[c] = 0; marks[c] = Mark.NONE
        } else if (values[c] == 0 && notes[c] != 0) {
            pushUndo(); notes[c] = 0
        }
        onChanged?.invoke()
    }

    private fun checkFinished() {
        if ((0 until 81).all { values[it] == solution[it] }) {
            finished = true
            onFinished?.invoke()
        }
    }

    // ------------------------------------------------------------------ Indices

    fun openHint(scope: CoroutineScope) {
        if (finished) return
        hintOpen = true
        hintLevel = 1
        hintData = null
        hintCount++
        val vals = correctValues()
        scope.launch {
            val data = withContext(Dispatchers.Default) {
                val avail = Engine.availability(vals, allowed)
                val h = Engine.hint(vals, allowed)
                val missing = if (h == null) Engine.missingTech(vals, allowed) else null
                val b = Board.fromValues(vals)
                h?.pre?.forEach { Engine.apply(b, it) }
                HintData(avail, h, missing, vals, b.cands)
            }
            hintData = data
        }
    }

    /** Applique la solution de l'indice (niveau 3). */
    fun applyHint() {
        val h = hintData?.hint ?: return
        val c = h.placement.place
        val d = h.placement.digit
        hintOpen = false
        if (values[c] == d) return
        pushUndo()
        values[c] = d
        notes[c] = 0
        marks[c] = Mark.HINT
        for (p in Geo.peers[c]) notes[p] = notes[p] and Bits.bit(d).inv()
        selectedCell = c
        selectedDigit = d
        feedback = Feedback("Indice appliqué : ${h.tech.label}", FbKind.NEUTRAL)
        if (mode == Mode.EXERCISE) exOutcome = ExOutcome.Fail("Solution affichée. Recommence l'exercice pour le valider toi-même.")
        checkFinished()
        onChanged?.invoke()
    }

    // ------------------------------------------------------------------ Sauvegarde

    fun toJson(): String = JSONObject().apply {
        put("mode", mode.name)
        put("tech", tech?.name ?: "")
        put("allowed", allowed)
        put("start", Board.format(start))
        put("solution", Board.format(solution))
        put("bankId", bankId)
        put("values", Board.format(values.toIntArray()))
        put("notes", JSONArray(notes.toList()))
        put("marks", JSONArray(marks.map { it.name }))
        put("elapsed", elapsed)
        put("errors", errors); put("lucky", luckyCount); put("mastered", masteredCount)
        put("anticipated", anticipatedCount); put("hints", hintCount)
    }.toString()

    companion object {
        fun fromJson(s: String): Game? = try {
            val o = JSONObject(s)
            val tn = o.getString("tech")
            val g = Game(
                Mode.valueOf(o.getString("mode")),
                if (tn.isEmpty()) null else Tech.valueOf(tn),
                o.getInt("allowed"),
                Board.parse(o.getString("start")),
                Board.parse(o.getString("solution")),
                o.getInt("bankId"),
            )
            val v = Board.parse(o.getString("values"))
            val n = o.getJSONArray("notes")
            val m = o.getJSONArray("marks")
            for (i in 0 until 81) {
                g.values[i] = v[i]; g.notes[i] = n.getInt(i); g.marks[i] = Mark.valueOf(m.getString(i))
            }
            g.elapsed = o.getInt("elapsed")
            g.errors = o.getInt("errors"); g.luckyCount = o.getInt("lucky"); g.masteredCount = o.getInt("mastered")
            g.anticipatedCount = o.getInt("anticipated"); g.hintCount = o.getInt("hints")
            g
        } catch (e: Exception) { null }
    }
}
