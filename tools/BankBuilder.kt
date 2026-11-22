import fr.deduction.engine.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Génère hors ligne :
 *  - bank.txt      : grilles notées (technique la plus dure + masque des techniques utilisées)
 *  - exercises.txt : positions d'exercice où une technique précise est nécessaire
 * et vérifie au passage la justesse du moteur (éliminations, indices, jugements).
 */
const val BANK_CAP = 250
const val EX_CAP = 40

fun main(args: Array<String>) {
    val total = args.getOrNull(0)?.toInt() ?: 20000
    val outDir = File(args.getOrNull(1) ?: "out").also { it.mkdirs() }
    val threads = Runtime.getRuntime().availableProcessors()
    val nT = Tech.values().size
    val bank = Array(nT) { ArrayList<String>() }
    val exs = Array(nT) { ArrayList<Pair<Int, String>>() }
    val bad = AtomicInteger(); val done = AtomicInteger(); val solved = AtomicInteger()
    val hintChecks = AtomicInteger(); val hintBad = AtomicInteger()
    val t0 = System.currentTimeMillis()

    val ws = (0 until threads).map { tid ->
        Thread {
            val rng = Random(1000L + tid * 7919L)
            while (true) {
                val k = done.incrementAndGet()
                if (k > total) break
                if (k % 100000 == 0) println("  … $k (${(System.currentTimeMillis() - t0) / 1000}s)")
                val (p, sol) = Generator.makePuzzle(rng)
                val b = Board.fromValues(p)
                val steps = ArrayList<Step>()
                while (!b.isSolved()) {
                    val s = Engine.nextStep(b, Tech.ALL_MASK) ?: break
                    if (s.isPlacement && sol[s.place] != s.digit) bad.incrementAndGet()
                    for (e in s.elims) if (sol[elimCell(e)] == elimDigit(e)) { bad.incrementAndGet(); System.err.println("BAD ${s.tech} ${Board.format(b.values)}") }
                    Engine.apply(b, s); steps += s
                }
                val mask = steps.fold(0) { m, s -> m or s.tech.bit }
                val solStr = Board.format(sol)
                if (b.isSolved()) {
                    solved.incrementAndGet()
                    val h = steps.maxBy { it.tech.ordinal }.tech
                    synchronized(bank) {
                        if (bank[h.ordinal].size < BANK_CAP) bank[h.ordinal] += "${h.ordinal};${Board.format(p)};$solStr;$mask"
                    }
                    // contrôle ponctuel des indices
                    if (k % 50 == 0) {
                        val vals = p.copyOf()
                        for ((i, s) in steps.withIndex()) {
                            if (s.isPlacement) vals[s.place] = s.digit
                            if (i % 9 != 0) continue
                            val hh = Engine.hint(vals, Tech.ALL_MASK) ?: continue
                            hintChecks.incrementAndGet()
                            val j = Engine.judge(vals, sol, hh.placement.place, hh.placement.digit, Tech.ALL_MASK)
                            if (sol[hh.placement.place] != hh.placement.digit || j.verdict != Verdict.MASTERED) hintBad.incrementAndGet()
                        }
                    }
                }
                // Exercices (y compris depuis les grilles trop dures, pour les techniques rares)
                for (t in Tech.values()) {
                    if (mask and t.bit == 0) continue
                    val need = synchronized(exs) { exs[t.ordinal].size < EX_CAP }
                    if (!need) continue
                    val lower = Tech.upTo(t) and t.bit.inv()
                    val vals = p.copyOf()
                    val snaps = ArrayList<IntArray>()
                    for (s in steps) {
                        if (s.tech == t) snaps += vals.copyOf()
                        if (s.isPlacement) vals[s.place] = s.digit
                    }
                    val snap = snaps.shuffled(rng).firstOrNull { v ->
                        v.count { it == 0 } >= 12 &&
                            Engine.availability(v, lower).total == 0 && Engine.availability(v, Tech.upTo(t)).total > 0
                    } ?: continue
                    val tot = Engine.availability(snap, Tech.upTo(t)).total
                    synchronized(exs) { if (exs[t.ordinal].size < EX_CAP) exs[t.ordinal] += tot to "${t.ordinal};${Board.format(snap)};$solStr;$tot" }
                }
            }
        }.also { it.start() }
    }
    ws.forEach { it.join() }
    println("generated=$total solved=${solved.get()} bad=${bad.get()} hintChecks=${hintChecks.get()} hintBad=${hintBad.get()} in ${(System.currentTimeMillis() - t0) / 1000}s")

    File(outDir, "bank.txt").writeText(bank.flatMap { it }.joinToString("\n", postfix = "\n"))
    val exOut = StringBuilder()
    for (t in Tech.values()) {
        val l = exs[t.ordinal].sortedBy { it.first }.take(20)
        for (e in l) exOut.append(e.second).append('\n')
        println("%-34s bank=%4d  exercises=%3d".format(t.label, bank[t.ordinal].size, l.size))
    }
    File(outDir, "exercises.txt").writeText(exOut.toString())
}
