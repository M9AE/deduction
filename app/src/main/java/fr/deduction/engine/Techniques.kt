package fr.deduction.engine

/**
 * Techniques, de la plus simple à la plus difficile. L'ordre (ordinal) sert de niveau.
 * single = la technique pose un chiffre ; sinon elle élimine des candidats.
 */
enum class Tech(val label: String, val single: Boolean, val chapter: Int) {
    FULL_HOUSE("Dernière case", true, 1),
    HIDDEN_SINGLE_BOX("Singleton caché (bloc)", true, 1),
    HIDDEN_SINGLE_LINE("Singleton caché (ligne/colonne)", true, 1),
    NAKED_SINGLE("Singleton nu", true, 1),
    POINTING("Paire pointante", false, 2),
    CLAIMING("Réduction ligne/bloc", false, 2),
    NAKED_PAIR("Paire nue", false, 3),
    HIDDEN_PAIR("Paire cachée", false, 3),
    NAKED_TRIPLE("Triplet nu", false, 3),
    HIDDEN_TRIPLE("Triplet caché", false, 3),
    X_WING("X-Wing", false, 4),
    SKYSCRAPER("Gratte-ciel", false, 5),
    TWO_STRING_KITE("Cerf-volant", false, 5),
    XY_WING("XY-Wing", false, 5),
    NAKED_QUAD("Quadruplet nu", false, 3),
    HIDDEN_QUAD("Quadruplet caché", false, 3),
    SWORDFISH("Swordfish", false, 4),
    XYZ_WING("XYZ-Wing", false, 5),
    W_WING("W-Wing", false, 5),
    UNIQUE_RECTANGLE("Rectangle unique", false, 5),
    JELLYFISH("Jellyfish", false, 4);

    val bit: Int get() = 1 shl ordinal

    companion object {
        val ALL_MASK: Int = (1 shl values().size) - 1
        val SINGLES = values().filter { it.single }
        val ELIMS = values().filter { !it.single }
        fun maskOf(techs: Collection<Tech>) = techs.fold(0) { m, t -> m or t.bit }
        fun fromMask(mask: Int) = values().filter { mask and it.bit != 0 }
        /** Toutes les techniques jusqu'à t inclus. */
        fun upTo(t: Tech) = (1 shl (t.ordinal + 1)) - 1
    }
}

/** Élimination encodée : case*10 + chiffre. */
fun elim(cell: Int, d: Int) = cell * 10 + d
fun elimCell(e: Int) = e / 10
fun elimDigit(e: Int) = e % 10

/** Une étape de raisonnement : soit une pose (place/digit), soit des éliminations. */
class Step(
    val tech: Tech,
    val place: Int = -1,
    val digit: Int = 0,
    val elims: List<Int> = emptyList(),
    val unit: Int = -1,
    val cells: List<Int> = emptyList(),
    val digits: List<Int> = emptyList(),
    val text: String,
) {
    val isPlacement get() = place >= 0
}

private fun cellsStr(cells: Collection<Int>) = cells.joinToString(", ") { Geo.cellName(it) }
private fun digitsStr(ds: Collection<Int>) = ds.joinToString("/")

private fun <T> combos(items: List<T>, k: Int): Sequence<List<T>> = sequence {
    val n = items.size
    if (k > n || k <= 0) return@sequence
    val idx = IntArray(k) { it }
    while (true) {
        yield(idx.map { items[it] })
        var i = k - 1
        while (i >= 0 && idx[i] == n - k + i) i--
        if (i < 0) break
        idx[i]++
        for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
    }
}

object Techniques {

    // ---------------------------------------------------------------- Singletons

    /** Un singleton par case (le type le plus simple), limité aux types autorisés. */
    fun singles(b: Board, allowed: Int): List<Step> {
        val found = LinkedHashMap<Int, Step>()
        if (allowed and Tech.FULL_HOUSE.bit != 0) {
            for (u in 0 until 27) {
                var empty = -1
                var n = 0
                for (c in Geo.units[u]) if (b.values[c] == 0) { n++; empty = c }
                if (n == 1 && empty !in found && Bits.count[b.cands[empty]] == 1) {
                    val d = Bits.single(b.cands[empty])
                    found[empty] = Step(
                        Tech.FULL_HOUSE, empty, d, unit = u, cells = listOf(empty), digits = listOf(d),
                        text = "${Geo.the(u).replaceFirstChar { it.uppercase() }} n'a plus qu'une case vide : ${Geo.cellName(empty)} = $d."
                    )
                }
            }
        }
        fun hidden(tech: Tech, unitRange: IntRange) {
            if (allowed and tech.bit == 0) return
            for (u in unitRange) for (d in 1..9) {
                if (b.unitHasValue(u, d)) continue
                val cs = b.cellsWith(u, d)
                if (cs.size == 1 && cs[0] !in found) {
                    val c = cs[0]
                    found[c] = Step(
                        tech, c, d, unit = u, cells = listOf(c), digits = listOf(d),
                        text = "Dans ${Geo.the(u)}, le $d ne peut aller qu'en ${Geo.cellName(c)}."
                    )
                }
            }
        }
        hidden(Tech.HIDDEN_SINGLE_BOX, 18..26)
        hidden(Tech.HIDDEN_SINGLE_LINE, 0..17)
        if (allowed and Tech.NAKED_SINGLE.bit != 0) {
            for (c in 0 until 81) {
                if (b.values[c] != 0 || c in found) continue
                if (Bits.count[b.cands[c]] == 1) {
                    val d = Bits.single(b.cands[c])
                    found[c] = Step(
                        Tech.NAKED_SINGLE, c, d, unit = -1, cells = listOf(c), digits = listOf(d),
                        text = "${Geo.cellName(c)} n'a plus qu'un seul candidat possible : $d."
                    )
                }
            }
        }
        return found.values.toList()
    }

    // ---------------------------------------------------------------- Éliminations

    fun find(t: Tech, b: Board): List<Step> = when (t) {
        Tech.POINTING -> pointing(b)
        Tech.CLAIMING -> claiming(b)
        Tech.NAKED_PAIR -> nakedSubset(b, 2, t)
        Tech.NAKED_TRIPLE -> nakedSubset(b, 3, t)
        Tech.NAKED_QUAD -> nakedSubset(b, 4, t)
        Tech.HIDDEN_PAIR -> hiddenSubset(b, 2, t)
        Tech.HIDDEN_TRIPLE -> hiddenSubset(b, 3, t)
        Tech.HIDDEN_QUAD -> hiddenSubset(b, 4, t)
        Tech.X_WING -> fish(b, 2, t)
        Tech.SWORDFISH -> fish(b, 3, t)
        Tech.JELLYFISH -> fish(b, 4, t)
        Tech.SKYSCRAPER -> skyscraper(b)
        Tech.TWO_STRING_KITE -> kite(b)
        Tech.XY_WING -> xyWing(b)
        Tech.XYZ_WING -> xyzWing(b)
        Tech.W_WING -> wWing(b)
        Tech.UNIQUE_RECTANGLE -> uniqueRectangle(b)
        else -> emptyList()
    }

    private fun pointing(b: Board): List<Step> {
        val out = ArrayList<Step>()
        for (box in 0 until 9) {
            val u = 18 + box
            for (d in 1..9) {
                val cs = b.cellsWith(u, d)
                if (cs.size < 2) continue
                for (line in listOf(Geo.rowOf[cs[0]], 9 + Geo.colOf[cs[0]])) {
                    val inLine = if (line < 9) cs.all { Geo.rowOf[it] == line } else cs.all { Geo.colOf[it] == line - 9 }
                    if (!inLine) continue
                    val el = Geo.units[line].filter { Geo.boxOf[it] != box && Bits.has(b.cands[it], d) }.map { elim(it, d) }
                    if (el.isEmpty()) continue
                    out += Step(
                        Tech.POINTING, elims = el, unit = u, cells = cs.toList(), digits = listOf(d),
                        text = "Dans le ${Geo.unitName(u)}, les $d possibles (${cellsStr(cs.toList())}) sont tous sur la ${Geo.unitName(line)}. " +
                            "Le $d du bloc sera donc sur cette ${if (line < 9) "ligne" else "colonne"} : on l'élimine du reste de la ${Geo.unitName(line)}."
                    )
                }
            }
        }
        return out
    }

    private fun claiming(b: Board): List<Step> {
        val out = ArrayList<Step>()
        for (line in 0 until 18) for (d in 1..9) {
            val cs = b.cellsWith(line, d)
            if (cs.size < 2) continue
            val box = Geo.boxOf[cs[0]]
            if (!cs.all { Geo.boxOf[it] == box }) continue
            val inLine: (Int) -> Boolean = if (line < 9) { c -> Geo.rowOf[c] == line } else { c -> Geo.colOf[c] == line - 9 }
            val el = Geo.units[18 + box].filter { !inLine(it) && Bits.has(b.cands[it], d) }.map { elim(it, d) }
            if (el.isEmpty()) continue
            out += Step(
                Tech.CLAIMING, elims = el, unit = line, cells = cs.toList(), digits = listOf(d),
                text = "Sur la ${Geo.unitName(line)}, les $d possibles (${cellsStr(cs.toList())}) sont tous dans le ${Geo.unitName(18 + box)}. " +
                    "On élimine donc le $d des autres cases du ${Geo.unitName(18 + box)}."
            )
        }
        return out
    }

    private fun commonUnits(cells: List<Int>): List<Int> {
        val res = ArrayList<Int>(3)
        val c0 = cells[0]
        if (cells.all { Geo.rowOf[it] == Geo.rowOf[c0] }) res += Geo.rowOf[c0]
        if (cells.all { Geo.colOf[it] == Geo.colOf[c0] }) res += 9 + Geo.colOf[c0]
        if (cells.all { Geo.boxOf[it] == Geo.boxOf[c0] }) res += 18 + Geo.boxOf[c0]
        return res
    }

    private val subsetNames = mapOf(2 to "paire", 3 to "triplet", 4 to "quadruplet")

    private fun nakedSubset(b: Board, k: Int, t: Tech): List<Step> {
        val out = ArrayList<Step>()
        val seen = HashSet<List<Int>>()
        for (u in 0 until 27) {
            val cells = Geo.units[u].filter { b.values[it] == 0 && Bits.count[b.cands[it]] in 2..k }
            if (cells.size < k) continue
            for (combo in combos(cells, k)) {
                var union = 0
                for (c in combo) union = union or b.cands[c]
                if (Bits.count[union] != k) continue
                val key = combo.sorted()
                if (key in seen) continue
                val ds = Bits.digits(union)
                val el = LinkedHashSet<Int>()
                for (cu in commonUnits(combo)) for (x in Geo.units[cu]) {
                    if (x in combo || b.values[x] != 0) continue
                    for (d in ds) if (Bits.has(b.cands[x], d)) el += elim(x, d)
                }
                if (el.isEmpty()) continue
                seen += key
                out += Step(
                    t, elims = el.toList(), unit = u, cells = combo, digits = ds,
                    text = "Les cases ${cellsStr(combo)} ${Geo.ofThe(u)} ne peuvent contenir que ${digitsStr(ds)}. " +
                        "Ces $k chiffres y sont forcément : on les élimine des autres cases ${Geo.ofThe(u)}."
                )
            }
        }
        return out
    }

    private fun hiddenSubset(b: Board, k: Int, t: Tech): List<Step> {
        val out = ArrayList<Step>()
        for (u in 0 until 27) {
            val empty = Geo.units[u].count { b.values[it] == 0 }
            if (empty <= k) continue
            val pos = HashMap<Int, IntArray>()
            val ds = ArrayList<Int>()
            for (d in 1..9) {
                if (b.unitHasValue(u, d)) continue
                val cs = b.cellsWith(u, d)
                if (cs.size in 2..k) { pos[d] = cs; ds += d }
            }
            if (ds.size < k) continue
            for (combo in combos(ds, k)) {
                val cellSet = LinkedHashSet<Int>()
                for (d in combo) for (c in pos[d]!!) cellSet += c
                if (cellSet.size != k) continue
                val keep = combo.fold(0) { m, d -> m or Bits.bit(d) }
                val el = ArrayList<Int>()
                for (c in cellSet) for (d in Bits.digits(b.cands[c] and keep.inv())) el += elim(c, d)
                if (el.isEmpty()) continue
                val cells = cellSet.toList()
                out += Step(
                    t, elims = el, unit = u, cells = cells, digits = combo,
                    text = "Dans ${Geo.the(u)}, les chiffres ${digitsStr(combo)} ne peuvent aller que dans ${cellsStr(cells)}. " +
                        "Ces cases ne peuvent donc contenir que ${digitsStr(combo)} : on retire leurs autres candidats."
                )
            }
        }
        return out
    }

    private val fishNames = mapOf(2 to "X-Wing", 3 to "Swordfish", 4 to "Jellyfish")

    private fun fish(b: Board, n: Int, t: Tech): List<Step> {
        val out = ArrayList<Step>()
        for (d in 1..9) for (orient in 0..1) {
            // orient 0 : bases = lignes, couvertures = colonnes ; orient 1 : l'inverse
            val baseUnits = if (orient == 0) 0..8 else 9..17
            val bases = ArrayList<Int>()
            val coverMask = HashMap<Int, Int>()
            for (u in baseUnits) {
                val cs = b.cellsWith(u, d)
                if (cs.size in 2..n) {
                    bases += u
                    coverMask[u] = cs.fold(0) { m, c -> m or (1 shl (if (orient == 0) Geo.colOf[c] else Geo.rowOf[c])) }
                }
            }
            if (bases.size < n) continue
            for (combo in combos(bases, n)) {
                val cover = combo.fold(0) { m, u -> m or coverMask[u]!! }
                if (Integer.bitCount(cover) != n) continue
                val baseIdx = combo.map { if (orient == 0) it else it - 9 }.toSet()
                val coverUnits = (0 until 9).filter { cover and (1 shl it) != 0 }.map { if (orient == 0) 9 + it else it }
                val el = ArrayList<Int>()
                for (cu in coverUnits) for (x in Geo.units[cu]) {
                    val bi = if (orient == 0) Geo.rowOf[x] else Geo.colOf[x]
                    if (bi !in baseIdx && Bits.has(b.cands[x], d)) el += elim(x, d)
                }
                if (el.isEmpty()) continue
                val cells = combo.flatMap { b.cellsWith(it, d).toList() }
                val baseNames = combo.joinToString(", ") { (if (it < 9) it + 1 else it - 8).toString() }
                val coverNames = coverUnits.joinToString(", ") { (if (it < 9) it + 1 else it - 8).toString() }
                val bw = if (orient == 0) "lignes" else "colonnes"
                val cw = if (orient == 0) "colonnes" else "lignes"
                out += Step(
                    t, elims = el, unit = combo[0], cells = cells, digits = listOf(d),
                    text = "${fishNames[n]} sur le $d : dans les $bw $baseNames, le $d ne peut être que dans les $cw $coverNames. " +
                        "Ces $n chiffres $d occuperont donc ces $cw : on élimine le $d du reste des $cw $coverNames."
                )
            }
        }
        return out
    }

    private fun commonPeersWith(b: Board, d: Int, a: Int, c: Int, exclude: Collection<Int>): List<Int> =
        Geo.peers[a].filter { it != c && it !in exclude && Geo.sees(it, c) && Bits.has(b.cands[it], d) }

    private fun skyscraper(b: Board): List<Step> {
        val out = ArrayList<Step>()
        val seen = HashSet<List<Int>>()
        for (d in 1..9) for (orient in 0..1) {
            val lines = (if (orient == 0) 0..8 else 9..17).map { it to b.cellsWith(it, d) }.filter { it.second.size == 2 }
            val cross: (Int) -> Int = if (orient == 0) { c -> Geo.colOf[c] } else { c -> Geo.rowOf[c] }
            for (i in lines.indices) for (j in i + 1 until lines.size) {
                val (l1, p1) = lines[i]
                val (l2, p2) = lines[j]
                val shared = listOf(cross(p1[0]), cross(p1[1])).intersect(setOf(cross(p2[0]), cross(p2[1])))
                if (shared.size != 1) continue
                val s = shared.first()
                val e1 = if (cross(p1[0]) == s) p1[1] else p1[0]
                val e2 = if (cross(p2[0]) == s) p2[1] else p2[0]
                val all = listOf(p1[0], p1[1], p2[0], p2[1])
                val el = commonPeersWith(b, d, e1, e2, all).map { elim(it, d) }
                if (el.isEmpty()) continue
                val key = all.sorted() + d
                if (!seen.add(key)) continue
                out += Step(
                    Tech.SKYSCRAPER, elims = el, unit = l1, cells = all, digits = listOf(d),
                    text = "Sur ${Geo.the(l1)} et ${Geo.the(l2)}, le $d n'a que deux places, dont une sur la même " +
                        "${if (orient == 0) "colonne" else "ligne"}. Les deux autres extrémités, ${Geo.cellName(e1)} et ${Geo.cellName(e2)}, " +
                        "ne peuvent pas être vides de $d toutes les deux : on élimine le $d des cases qui voient les deux."
                )
            }
        }
        return out
    }

    private fun kite(b: Board): List<Step> {
        val out = ArrayList<Step>()
        for (d in 1..9) {
            val rows = (0..8).map { it to b.cellsWith(it, d) }.filter { it.second.size == 2 }
            val cols = (9..17).map { it to b.cellsWith(it, d) }.filter { it.second.size == 2 }
            for ((r, rp) in rows) for ((c, cp) in cols) {
                val all = listOf(rp[0], rp[1], cp[0], cp[1])
                if (all.toSet().size != 4) continue
                var found = false
                for (ri in 0..1) for (ci in 0..1) {
                    if (found) continue
                    if (Geo.boxOf[rp[ri]] == Geo.boxOf[cp[ci]]) {
                        val er = rp[1 - ri]
                        val ec = cp[1 - ci]
                        val el = commonPeersWith(b, d, er, ec, all).map { elim(it, d) }
                        if (el.isNotEmpty()) {
                            found = true
                            out += Step(
                                Tech.TWO_STRING_KITE, elims = el, unit = r, cells = all, digits = listOf(d),
                                text = "Le $d n'a que deux places sur la ${Geo.unitName(r)} et deux dans la ${Geo.unitName(c)}, " +
                                    "reliées dans le ${Geo.unitName(18 + Geo.boxOf[rp[ri]])}. Donc ${Geo.cellName(er)} ou ${Geo.cellName(ec)} vaut $d : " +
                                    "on élimine le $d des cases qui voient les deux."
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun xyWing(b: Board): List<Step> {
        val out = ArrayList<Step>()
        val seen = HashSet<List<Int>>()
        for (p in 0 until 81) {
            if (b.values[p] != 0 || Bits.count[b.cands[p]] != 2) continue
            val (x, y) = Bits.digits(b.cands[p])
            val wings = Geo.peers[p].filter { b.values[it] == 0 && Bits.count[b.cands[it]] == 2 }
            for (a in wings) {
                if (!Bits.has(b.cands[a], x) || Bits.has(b.cands[a], y)) continue
                val z = Bits.single(b.cands[a] and Bits.bit(x).inv())
                for (c in wings) {
                    if (c == a || b.cands[c] != (Bits.bit(y) or Bits.bit(z))) continue
                    val el = commonPeersWith(b, z, a, c, listOf(p)).map { elim(it, z) }
                    if (el.isEmpty()) continue
                    if (!seen.add(listOf(p, minOf(a, c), maxOf(a, c)))) continue
                    out += Step(
                        Tech.XY_WING, elims = el, unit = -1, cells = listOf(p, a, c), digits = listOf(x, y, z),
                        text = "Pivot ${Geo.cellName(p)} ($x/$y), ailes ${Geo.cellName(a)} ($x/$z) et ${Geo.cellName(c)} ($y/$z). " +
                            "Que le pivot vaille $x ou $y, une des ailes vaudra $z : on élimine le $z des cases qui voient les deux ailes."
                    )
                }
            }
        }
        return out
    }

    private fun xyzWing(b: Board): List<Step> {
        val out = ArrayList<Step>()
        for (p in 0 until 81) {
            if (b.values[p] != 0 || Bits.count[b.cands[p]] != 3) continue
            val pm = b.cands[p]
            val wings = Geo.peers[p].filter { b.values[it] == 0 && Bits.count[b.cands[it]] == 2 && (b.cands[it] and pm.inv()) == 0 }
            for (i in wings.indices) for (j in i + 1 until wings.size) {
                val a = wings[i]; val c = wings[j]
                if ((b.cands[a] or b.cands[c]) != pm) continue
                val zm = b.cands[a] and b.cands[c]
                if (Bits.count[zm] != 1) continue
                val z = Bits.single(zm)
                val el = Geo.peers[p].filter { it != a && it != c && Geo.sees(it, a) && Geo.sees(it, c) && Bits.has(b.cands[it], z) }
                    .map { elim(it, z) }
                if (el.isEmpty()) continue
                val pd = Bits.digits(pm)
                out += Step(
                    Tech.XYZ_WING, elims = el, unit = -1, cells = listOf(p, a, c), digits = pd,
                    text = "Pivot ${Geo.cellName(p)} (${digitsStr(pd)}), ailes ${Geo.cellName(a)} (${digitsStr(Bits.digits(b.cands[a]))}) et " +
                        "${Geo.cellName(c)} (${digitsStr(Bits.digits(b.cands[c]))}). Dans tous les cas, l'une des trois cases vaut $z : " +
                        "on élimine le $z des cases qui voient les trois."
                )
            }
        }
        return out
    }

    private fun wWing(b: Board): List<Step> {
        val out = ArrayList<Step>()
        val bi = (0 until 81).filter { b.values[it] == 0 && Bits.count[b.cands[it]] == 2 }
        for (i in bi.indices) for (j in i + 1 until bi.size) {
            val a = bi[i]; val c = bi[j]
            if (b.cands[a] != b.cands[c] || Geo.sees(a, c)) continue
            val ds = Bits.digits(b.cands[a])
            var done = false
            for (x in ds) {
                if (done) break
                val y = if (ds[0] == x) ds[1] else ds[0]
                for (u in 0 until 27) {
                    val cs = b.cellsWith(u, x)
                    if (cs.size != 2 || a in cs || c in cs) continue
                    val (e1, e2) = cs
                    val linked = (Geo.sees(e1, a) && Geo.sees(e2, c)) || (Geo.sees(e1, c) && Geo.sees(e2, a))
                    if (!linked) continue
                    val el = commonPeersWith(b, y, a, c, emptyList()).map { elim(it, y) }
                    if (el.isEmpty()) continue
                    out += Step(
                        Tech.W_WING, elims = el, unit = u, cells = listOf(a, c, e1, e2), digits = listOf(x, y),
                        text = "${Geo.cellName(a)} et ${Geo.cellName(c)} contiennent tous deux $x/$y. Dans ${Geo.the(u)}, le $x n'a que deux places " +
                            "(${Geo.cellName(e1)}, ${Geo.cellName(e2)}), qui voient chacune une de ces cases. L'une d'elles vaut donc $y : " +
                            "on élimine le $y des cases qui voient les deux."
                    )
                    done = true
                    break
                }
            }
        }
        return out
    }

    private fun uniqueRectangle(b: Board): List<Step> {
        val out = ArrayList<Step>()
        for (r1 in 0 until 9) for (r2 in r1 + 1 until 9) for (c1 in 0 until 9) for (c2 in c1 + 1 until 9) {
            val sameBand = r1 / 3 == r2 / 3
            val sameStack = c1 / 3 == c2 / 3
            if (sameBand == sameStack) continue // le rectangle doit couvrir exactement 2 blocs
            val cells = listOf(r1 * 9 + c1, r1 * 9 + c2, r2 * 9 + c1, r2 * 9 + c2)
            if (cells.any { b.values[it] != 0 }) continue
            val bivals = cells.filter { Bits.count[b.cands[it]] == 2 }
            if (bivals.size != 3) continue
            val m = b.cands[bivals[0]]
            if (bivals.any { b.cands[it] != m }) continue
            val other = cells.first { it !in bivals }
            if ((b.cands[other] and m) != m) continue
            val ds = Bits.digits(m)
            val el = ds.map { elim(other, it) }
            out += Step(
                Tech.UNIQUE_RECTANGLE, elims = el, unit = -1, cells = cells, digits = ds,
                text = "Les cases ${cellsStr(cells)} formeraient un « rectangle mortel » ${digitsStr(ds)} : la grille aurait deux solutions. " +
                    "Comme elle n'en a qu'une, ${Geo.cellName(other)} ne peut valoir ni ${ds[0]} ni ${ds[1]}."
            )
        }
        return out
    }
}
