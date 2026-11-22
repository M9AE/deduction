package fr.deduction.engine

/** Géométrie de la grille : cases 0..80, unités 0..26 (0-8 lignes, 9-17 colonnes, 18-26 blocs). */
object Geo {
    val rowOf = IntArray(81) { it / 9 }
    val colOf = IntArray(81) { it % 9 }
    val boxOf = IntArray(81) { (it / 27) * 3 + (it % 9) / 3 }

    val units: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> {
                val b = u - 18
                val r0 = (b / 3) * 3
                val c0 = (b % 3) * 3
                IntArray(9) { (r0 + it / 3) * 9 + c0 + it % 3 }
            }
        }
    }

    /** Pour chaque case : ses 3 unités (ligne, colonne, bloc). */
    val unitsOf: Array<IntArray> = Array(81) { intArrayOf(rowOf[it], 9 + colOf[it], 18 + boxOf[it]) }

    val peers: Array<IntArray> = Array(81) { c ->
        val s = LinkedHashSet<Int>()
        for (u in unitsOf[c]) for (x in units[u]) if (x != c) s.add(x)
        s.toIntArray()
    }

    private val peerSet: Array<BooleanArray> = Array(81) { c ->
        val a = BooleanArray(81)
        for (p in peers[c]) a[p] = true
        a
    }

    fun sees(a: Int, b: Int): Boolean = peerSet[a][b]

    fun cellName(c: Int) = "L${rowOf[c] + 1}C${colOf[c] + 1}"

    fun unitName(u: Int): String = when {
        u < 0 -> ""
        u < 9 -> "ligne ${u + 1}"
        u < 18 -> "colonne ${u - 8}"
        else -> "bloc ${u - 17}"
    }

    /** « la ligne 3 », « le bloc 5 » */
    fun the(u: Int) = (if (u >= 18) "le " else "la ") + unitName(u)
    /** « de la ligne 3 », « du bloc 5 » */
    fun ofThe(u: Int) = if (u >= 18) "du " + unitName(u) else "de la " + unitName(u)

    fun unitNameCap(u: Int) = unitName(u).replaceFirstChar { it.uppercase() }
}

/** Masques de candidats : bit (d-1) pour le chiffre d. */
object Bits {
    val count = IntArray(1 shl 9) { Integer.bitCount(it) }
    fun bit(d: Int) = 1 shl (d - 1)
    fun has(mask: Int, d: Int) = mask and (1 shl (d - 1)) != 0
    fun digits(mask: Int): List<Int> {
        val l = ArrayList<Int>(9)
        for (d in 1..9) if (mask and (1 shl (d - 1)) != 0) l.add(d)
        return l
    }
    fun single(mask: Int): Int = Integer.numberOfTrailingZeros(mask) + 1
    const val ALL = 0x1FF
}

/**
 * État logique : chiffres posés + candidats restants.
 * Les candidats sont ceux d'un joueur « parfait » : calculés à partir des chiffres posés,
 * puis réduits par les éliminations des techniques appliquées.
 */
class Board(val values: IntArray, val cands: IntArray) {

    fun copy() = Board(values.copyOf(), cands.copyOf())

    fun isSolved(): Boolean = values.all { it != 0 }

    fun emptyCount(): Int = values.count { it == 0 }

    fun place(cell: Int, d: Int) {
        values[cell] = d
        cands[cell] = 0
        val m = Bits.bit(d).inv()
        for (p in Geo.peers[cell]) cands[p] = cands[p] and m
    }

    fun eliminate(cell: Int, d: Int): Boolean {
        val b = Bits.bit(d)
        if (cands[cell] and b == 0) return false
        cands[cell] = cands[cell] and b.inv()
        return true
    }

    /** Cases d'une unité où le chiffre d est encore candidat. */
    fun cellsWith(u: Int, d: Int): IntArray {
        val b = Bits.bit(d)
        var n = 0
        val tmp = IntArray(9)
        for (c in Geo.units[u]) if (cands[c] and b != 0) tmp[n++] = c
        return tmp.copyOf(n)
    }

    fun unitHasValue(u: Int, d: Int): Boolean = Geo.units[u].any { values[it] == d }

    companion object {
        fun fromValues(values: IntArray): Board {
            val v = values.copyOf()
            val cands = IntArray(81)
            for (c in 0 until 81) {
                if (v[c] != 0) continue
                var used = 0
                for (p in Geo.peers[c]) if (v[p] != 0) used = used or Bits.bit(v[p])
                cands[c] = Bits.ALL and used.inv()
            }
            return Board(v, cands)
        }

        fun parse(s: String): IntArray = IntArray(81) { i ->
            val ch = s[i]
            if (ch in '1'..'9') ch - '0' else 0
        }

        fun format(v: IntArray): String = buildString { for (x in v) append(if (x == 0) '.' else ('0' + x)) }
    }
}
