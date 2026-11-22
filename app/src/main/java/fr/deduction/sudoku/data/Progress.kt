package fr.deduction.sudoku.data

import android.content.Context
import android.content.SharedPreferences
import fr.deduction.engine.Tech

/** Progression du joueur, stockée localement. */
class Progress(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("deduction", Context.MODE_PRIVATE)

    companion object {
        const val EXERCISES_TO_PASS = 3
    }

    var masteredMask: Int
        get() = p.getInt("mastered", 0)
        set(v) = p.edit().putInt("mastered", v).apply()

    fun isMastered(t: Tech) = masteredMask and t.bit != 0
    fun setMastered(t: Tech, on: Boolean) {
        masteredMask = if (on) masteredMask or t.bit else masteredMask and t.bit.inv()
    }

    fun exercisesDone(t: Tech) = p.getInt("ex_${t.name}", 0)
    fun setExercisesDone(t: Tech, n: Int) = p.edit().putInt("ex_${t.name}", n).apply()

    fun applicationDone(t: Tech) = p.getBoolean("app_${t.name}", false)
    fun setApplicationDone(t: Tech) = p.edit().putBoolean("app_${t.name}", true).apply()

    /** Une technique est ouverte si c'est la première, ou si la précédente est maîtrisée. */
    fun isUnlocked(t: Tech, order: List<Tech>): Boolean {
        val i = order.indexOf(t)
        return i <= 0 || isMastered(order[i - 1]) || isMastered(t)
    }

    var quickMode: Boolean
        get() = p.getBoolean("quick", true)
        set(v) = p.edit().putBoolean("quick", v).apply()

    var showMoveFeedback: Boolean
        get() = p.getBoolean("feedback", true)
        set(v) = p.edit().putBoolean("feedback", v).apply()

    var played: Set<Int>
        get() = (p.getString("played", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = p.edit().putString("played", v.joinToString(",")).apply()

    var savedGame: String?
        get() = p.getString("saved_game", null)
        set(v) = p.edit().putString("saved_game", v).apply()

    // Statistiques cumulées
    fun stat(k: String) = p.getInt("stat_$k", 0)
    fun addStat(k: String, n: Int = 1) = p.edit().putInt("stat_$k", stat(k) + n).apply()
}
