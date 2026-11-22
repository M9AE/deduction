package fr.deduction.engine

/** Verdict sur un coup joué. */
enum class Verdict { MASTERED, ANTICIPATED, LUCKY, WRONG }

class Judgement(
    val verdict: Verdict,
    /** Technique la plus avancée nécessaire (pour MASTERED). */
    val tech: Tech? = null,
    /** Pose de type singleton qui conclut le raisonnement. */
    val single: Step? = null,
    /** Une étape plus simple était disponible (conseil « il y avait plus simple »). */
    val simplerTech: Tech? = null,
    val simplerCount: Int = 0,
)

class Availability(
    /** Coups posables immédiatement par singletons, sans élimination. */
    val direct: Int,
    /** Coups posables en utilisant toutes les techniques autorisées (sans poser d'autre chiffre). */
    val total: Int,
    /** Technique la plus simple qui débloque au moins un coup. */
    val simplest: Tech?,
)

class Hint(
    /** Technique clé (celle à trouver). */
    val tech: Tech,
    /** Éliminations préalables plus simples, nécessaires avant l'étape clé. */
    val pre: List<Step>,
    /** Étape d'élimination clé (null si le coup est un simple singleton). */
    val key: Step?,
    /** Le chiffre qu'on peut alors poser. */
    val placement: Step,
) {
    val mainUnit: Int get() = key?.unit?.takeIf { it >= 0 } ?: placement.unit.takeIf { it >= 0 } ?: Geo.rowOf[placement.place]
}

class SolveResult(val steps: List<Step>, val solved: Boolean, val board: Board) {
    val techMask: Int get() = steps.fold(0) { m, s -> m or s.tech.bit }
    val hardest: Tech? get() = steps.maxByOrNull { it.tech.ordinal }?.tech
}

object Engine {

    fun apply(b: Board, s: Step) {
        if (s.isPlacement) b.place(s.place, s.digit)
        for (e in s.elims) b.eliminate(elimCell(e), elimDigit(e))
    }

    private fun singlesMask(allowed: Int) = allowed and Tech.maskOf(Tech.SINGLES)

    /** Applique les techniques d'élimination autorisées jusqu'à stabilité (sans poser de chiffre). */
    fun fixpoint(b: Board, allowed: Int, record: MutableList<Step>? = null) {
        var changed = true
        while (changed) {
            changed = false
            for (t in Tech.ELIMS) {
                if (allowed and t.bit == 0) continue
                val steps = Techniques.find(t, b)
                if (steps.isNotEmpty()) {
                    for (s in steps) {
                        var any = false
                        for (e in s.elims) if (b.eliminate(elimCell(e), elimDigit(e))) any = true
                        if (any) record?.add(s)
                    }
                    changed = true
                    break // on recommence par les techniques les plus simples
                }
            }
        }
    }

    /** Résolution « humaine » pas à pas : toujours l'étape la plus simple disponible. */
    fun solve(values: IntArray, allowed: Int = Tech.ALL_MASK, maxSteps: Int = 2000): SolveResult {
        val b = Board.fromValues(values)
        val steps = ArrayList<Step>()
        while (!b.isSolved() && steps.size < maxSteps) {
            val s = nextStep(b, allowed) ?: break
            apply(b, s)
            steps += s
        }
        return SolveResult(steps, b.isSolved(), b)
    }

    /** L'étape la plus simple sur un état donné. */
    fun nextStep(b: Board, allowed: Int): Step? {
        val singles = Techniques.singles(b, singlesMask(allowed))
        if (singles.isNotEmpty()) return singles.minByOrNull { it.tech.ordinal }
        for (t in Tech.ELIMS) {
            if (allowed and t.bit == 0) continue
            val s = Techniques.find(t, b).firstOrNull()
            if (s != null) return s
        }
        return null
    }

    /**
     * Le coup (cell = digit) était-il déductible de la grille actuelle avec les techniques autorisées ?
     * values = chiffres corrects posés (les erreurs doivent être retirées par l'appelant).
     */
    fun judge(values: IntArray, solution: IntArray, cell: Int, digit: Int, allowed: Int): Judgement {
        if (solution[cell] != digit) return Judgement(Verdict.WRONG)
        val b = Board.fromValues(values)
        val avail = availability(values, allowed)
        val sm = singlesMask(allowed)
        var lastElim: Tech? = null
        for (t in Tech.values()) {
            if (allowed and t.bit == 0) continue
            val tierMask = allowed and Tech.upTo(t)
            if (!t.single) { fixpoint(b, tierMask); lastElim = t }
            val singles = Techniques.singles(b, if (t.single) tierMask and sm else sm)
            val hit = singles.firstOrNull { it.place == cell && it.digit == digit }
            if (hit != null) {
                val usedTech = lastElim ?: hit.tech
                // Conseil : un coup par singleton existait alors que celui-ci demandait une élimination
                val simpler = if (lastElim != null && avail.direct > 0) avail.simplest else null
                return Judgement(Verdict.MASTERED, usedTech, hit, simpler, if (simpler != null) avail.direct else 0)
            }
        }
        // Coup anticipé : déductible après 1 ou 2 « vagues » de singletons
        var cur = b
        repeat(2) {
            val singles = Techniques.singles(cur, sm)
            if (singles.isEmpty()) return Judgement(Verdict.LUCKY)
            val next = cur.copy()
            for (s in singles) if (next.values[s.place] == 0 && Bits.has(next.cands[s.place], s.digit)) next.place(s.place, s.digit)
            fixpoint(next, allowed)
            if (Techniques.singles(next, sm).any { it.place == cell && it.digit == digit } || next.values[cell] == digit) {
                return Judgement(Verdict.ANTICIPATED)
            }
            cur = next
        }
        return Judgement(Verdict.LUCKY)
    }

    /** Combien de coups sont disponibles maintenant (indice niveau 1). */
    fun availability(values: IntArray, allowed: Int): Availability {
        val sm = singlesMask(allowed)
        val b = Board.fromValues(values)
        val direct = Techniques.singles(b, sm)
        val simplestSingle = direct.minByOrNull { it.tech.ordinal }?.tech
        var simplest: Tech? = simplestSingle
        if (simplest == null) {
            for (t in Tech.ELIMS) {
                if (allowed and t.bit == 0) continue
                fixpoint(b, allowed and Tech.upTo(t))
                if (Techniques.singles(b, sm).isNotEmpty()) { simplest = t; break }
            }
        }
        fixpoint(b, allowed)
        val total = Techniques.singles(b, sm).size
        return Availability(direct.size, total, simplest)
    }

    /** Indice : prochaine étape la plus simple, avec les éliminations nécessaires. */
    fun hint(values: IntArray, allowed: Int): Hint? {
        val sm = singlesMask(allowed)
        val b = Board.fromValues(values)
        val direct = Techniques.singles(b, sm)
        if (direct.isNotEmpty()) {
            val s = direct.minByOrNull { it.tech.ordinal }!!
            return Hint(s.tech, emptyList(), null, s)
        }
        val pre = ArrayList<Step>()
        for (t in Tech.ELIMS) {
            if (allowed and t.bit == 0) continue
            // Une seule instance de t suffit-elle à débloquer un chiffre ?
            for (s in Techniques.find(t, b)) {
                val c = b.copy()
                apply(c, s)
                val sg = Techniques.singles(c, sm).minByOrNull { it.tech.ordinal }
                if (sg != null) return Hint(t, prune(values, pre, t, sg, allowed), s, sg)
            }
            fixpoint(b, allowed and Tech.upTo(t), pre)
            val sg = Techniques.singles(b, sm).minByOrNull { it.tech.ordinal }
            if (sg != null) {
                // Il a fallu plusieurs instances : la dernière de t est l'étape clé
                val key = pre.lastOrNull { it.tech == t }
                return Hint(t, pre.filter { it !== key }, key, sg)
            }
        }
        return null
    }

    /** Retire les éliminations préalables inutiles. */
    private fun prune(values: IntArray, pre: List<Step>, t: Tech, target: Step, allowed: Int): List<Step> {
        if (pre.isEmpty()) return pre
        val sm = singlesMask(allowed)
        var kept = pre.toMutableList()
        var i = kept.size - 1
        while (i >= 0) {
            val trial = kept.toMutableList().also { it.removeAt(i) }
            val b = Board.fromValues(values)
            for (s in trial) apply(b, s)
            val ok = Techniques.find(t, b).any { s ->
                val c = b.copy(); apply(c, s)
                Techniques.singles(c, sm).any { it.place == target.place && it.digit == target.digit }
            }
            if (ok) kept = trial
            i--
        }
        return kept
    }

    /** Indice quand les techniques autorisées ne suffisent pas : quelle technique manque ? */
    fun missingTech(values: IntArray, allowed: Int): Tech? {
        val h = hint(values, Tech.ALL_MASK) ?: return null
        return if (allowed and h.tech.bit == 0) h.tech else null
    }
}
