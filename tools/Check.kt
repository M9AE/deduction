import fr.deduction.engine.*
import kotlin.random.Random

/** Vérifie la justesse des techniques : aucune élimination ne doit retirer le chiffre de la solution. */
fun main(args: Array<String>) {
    val n = args.getOrNull(0)?.toInt() ?: 300
    val rng = Random(args.getOrNull(1)?.toLong() ?: 42L)
    val hist = IntArray(Tech.values().size)
    var stuck = 0
    var bad = 0
    val t0 = System.currentTimeMillis()
    var genMs = 0L; var solveMs = 0L
    repeat(n) {
        val g0 = System.nanoTime()
        val (p, sol) = Generator.makePuzzle(rng)
        val g1 = System.nanoTime()
        val b = Board.fromValues(p)
        var steps = 0
        while (!b.isSolved()) {
            val s = Engine.nextStep(b, Tech.ALL_MASK) ?: break
            if (s.isPlacement && sol[s.place] != s.digit) { bad++; println("BAD placement ${s.tech} ${Board.format(p)}") }
            for (e in s.elims) if (sol[elimCell(e)] == elimDigit(e)) { bad++; println("BAD elim ${s.tech}: ${s.text}\n${Board.format(b.values)}") }
            Engine.apply(b, s)
            steps++
        }
        solveMs += (System.nanoTime() - g1) / 1_000_000
        genMs += (g1 - g0) / 1_000_000
        if (!b.isSolved()) stuck++ else {
            val r = Engine.solve(p)
            hist[r.hardest!!.ordinal]++
        }
    }
    println("puzzles=$n stuck=$stuck bad=$bad time=${System.currentTimeMillis() - t0}ms gen=${genMs}ms solve=${solveMs}ms")
    for (t in Tech.values()) println("%-32s %d".format(t.label, hist[t.ordinal]))
}
