package fr.deduction.engine

import kotlin.random.Random

/** Solveur rapide par retour arrière (comptage de solutions) et générateur de grilles. */
object Generator {

    /** Compte les solutions (jusqu'à limit). Si out != null, y écrit la première solution. */
    fun countSolutions(grid: IntArray, limit: Int = 2, out: IntArray? = null): Int {
        val g = grid.copyOf()
        val rows = IntArray(9); val cols = IntArray(9); val boxes = IntArray(9)
        for (c in 0 until 81) {
            val v = g[c]
            if (v != 0) {
                val bit = 1 shl (v - 1)
                val r = Geo.rowOf[c]; val co = Geo.colOf[c]; val bx = Geo.boxOf[c]
                if (rows[r] and bit != 0 || cols[co] and bit != 0 || boxes[bx] and bit != 0) return 0
                rows[r] = rows[r] or bit; cols[co] = cols[co] or bit; boxes[bx] = boxes[bx] or bit
            }
        }
        var count = 0
        fun rec(): Boolean {
            var best = -1
            var bestMask = 0
            var bestN = 10
            for (c in 0 until 81) {
                if (g[c] != 0) continue
                val m = Bits.ALL and (rows[Geo.rowOf[c]] or cols[Geo.colOf[c]] or boxes[Geo.boxOf[c]]).inv()
                val n = Bits.count[m]
                if (n < bestN) { bestN = n; best = c; bestMask = m; if (n <= 1) break }
            }
            if (best < 0) {
                count++
                if (count == 1 && out != null) g.copyInto(out)
                return count >= limit
            }
            if (bestN == 0) return false
            var m = bestMask
            val r = Geo.rowOf[best]; val co = Geo.colOf[best]; val bx = Geo.boxOf[best]
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                g[best] = Integer.numberOfTrailingZeros(bit) + 1
                rows[r] = rows[r] or bit; cols[co] = cols[co] or bit; boxes[bx] = boxes[bx] or bit
                val stop = rec()
                rows[r] = rows[r] xor bit; cols[co] = cols[co] xor bit; boxes[bx] = boxes[bx] xor bit
                g[best] = 0
                if (stop) return true
            }
            return false
        }
        rec()
        return count
    }

    fun solution(grid: IntArray): IntArray? {
        val out = IntArray(81)
        return if (countSolutions(grid, 2, out) == 1) out else null
    }

    /** Grille complète aléatoire. */
    fun randomFull(rng: Random): IntArray {
        val g = IntArray(81)
        // Remplit les 3 blocs diagonaux au hasard (indépendants), puis résout.
        for (bx in listOf(0, 4, 8)) {
            val ds = (1..9).shuffled(rng)
            Geo.units[18 + bx].forEachIndexed { i, c -> g[c] = ds[i] }
        }
        val out = IntArray(81)
        countSolutions(g, 1, out)
        // Mélange des chiffres pour plus de variété
        val perm = (1..9).shuffled(rng)
        return IntArray(81) { perm[out[it] - 1] }
    }

    /** Grille à solution unique, symétrique par rotation, minimale pour cette symétrie. */
    fun makePuzzle(rng: Random): Pair<IntArray, IntArray> {
        val full = randomFull(rng)
        val p = full.copyOf()
        val order = (0..40).shuffled(rng)
        for (c in order) {
            val m = 80 - c
            val a = p[c]; val b = p[m]
            p[c] = 0; p[m] = 0
            if (countSolutions(p, 2) != 1) { p[c] = a; p[m] = b }
        }
        return p to full
    }
}
