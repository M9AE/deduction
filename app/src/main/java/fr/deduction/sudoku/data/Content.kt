package fr.deduction.sudoku.data

import android.content.Context
import fr.deduction.engine.Board
import fr.deduction.engine.Tech

class BankEntry(val id: Int, val hardest: Tech, val puzzle: IntArray, val solution: IntArray, val mask: Int)
class Exercise(val index: Int, val tech: Tech, val state: IntArray, val solution: IntArray, val moves: Int)

class Lesson(val principle: String, val spot: String)

/** Contenu embarqué : banque de grilles, exercices et textes des leçons. */
object Content {
    var bank: List<BankEntry> = emptyList(); private set
    var exercises: Map<Tech, List<Exercise>> = emptyMap(); private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        val b = ArrayList<BankEntry>()
        ctx.assets.open("bank.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEachIndexed { i, l ->
                val p = l.split(';')
                b += BankEntry(i, Tech.values()[p[0].toInt()], Board.parse(p[1]), Board.parse(p[2]), p[3].toInt())
            }
        }
        val ex = HashMap<Tech, MutableList<Exercise>>()
        ctx.assets.open("exercises.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { l ->
                val p = l.split(';')
                val t = Tech.values()[p[0].toInt()]
                val list = ex.getOrPut(t) { ArrayList() }
                list += Exercise(list.size, t, Board.parse(p[1]), Board.parse(p[2]), p[3].toInt())
            }
        }
        bank = b
        exercises = ex
        loaded = true
    }

    /** Techniques proposées en carrière (celles pour lesquelles on a des exercices). */
    val careerTechs: List<Tech> get() = Tech.values().filter { (exercises[it]?.size ?: 0) >= 3 }

    fun chapterName(t: Tech): String = when (t.ordinal) {
        in 0..3 -> "Les bases"
        in 4..5 -> "Intersections"
        in 6..9 -> "Paires et triplets"
        in 10..13 -> "Motifs avancés"
        else -> "Expert"
    }

    val lessons: Map<Tech, Lesson> = mapOf(
        Tech.FULL_HOUSE to Lesson(
            "Quand une ligne, une colonne ou un bloc n'a plus qu'une seule case vide, le chiffre manquant y va forcément.",
            "Repère les unités presque pleines : 8 chiffres posés, une seule case libre."
        ),
        Tech.HIDDEN_SINGLE_BOX to Lesson(
            "Dans un bloc, un chiffre doit apparaître une fois. Si toutes les cases du bloc sauf une sont « vues » par ce chiffre (dans leur ligne ou leur colonne), il n'a plus qu'une place.",
            "Choisis un chiffre, puis balaie chaque bloc où il manque : regarde les lignes et colonnes qui le contiennent déjà et élimine les cases qu'elles traversent."
        ),
        Tech.HIDDEN_SINGLE_LINE to Lesson(
            "Même idée que dans un bloc, mais sur une ligne ou une colonne : le chiffre n'a plus qu'une case possible dans cette ligne/colonne.",
            "Pour une ligne où il manque peu de chiffres, demande-toi pour chaque chiffre manquant : où peut-il aller ? Les colonnes et blocs déjà occupés éliminent des cases."
        ),
        Tech.NAKED_SINGLE to Lesson(
            "Une case dont la ligne, la colonne et le bloc contiennent déjà huit chiffres différents n'a plus qu'un candidat possible.",
            "Regarde les cases à l'intersection de lignes, colonnes et blocs bien remplis, et fais la liste de ce qui manque."
        ),
        Tech.POINTING to Lesson(
            "Si, dans un bloc, toutes les places possibles d'un chiffre sont alignées sur une même ligne (ou colonne), alors ce chiffre sera sur cette ligne à l'intérieur du bloc. On peut l'éliminer du reste de la ligne, hors du bloc.",
            "Cherche des blocs où un chiffre n'a que 2 ou 3 places, toutes sur la même ligne ou colonne."
        ),
        Tech.CLAIMING to Lesson(
            "L'inverse de la paire pointante : si, sur une ligne (ou colonne), toutes les places possibles d'un chiffre sont dans un même bloc, ce chiffre est dans cette partie du bloc. On l'élimine des autres cases du bloc.",
            "Cherche une ligne où un chiffre n'a que 2 ou 3 places, toutes dans le même bloc."
        ),
        Tech.NAKED_PAIR to Lesson(
            "Deux cases d'une même unité qui n'ont que les deux mêmes candidats (par ex. 3/7) : ces deux chiffres iront dans ces deux cases. On les élimine de toutes les autres cases de l'unité.",
            "Note les candidats, puis repère deux cases jumelles à deux candidats dans une même ligne, colonne ou bloc."
        ),
        Tech.HIDDEN_PAIR to Lesson(
            "Si deux chiffres n'ont que les deux mêmes places possibles dans une unité, ces deux cases leur sont réservées : on retire tous les autres candidats de ces deux cases.",
            "Dans une unité, compte les places de chaque chiffre manquant : deux chiffres qui partagent exactement les deux mêmes cases forment une paire cachée."
        ),
        Tech.NAKED_TRIPLE to Lesson(
            "Trois cases d'une unité dont les candidats, réunis, ne forment que trois chiffres (par ex. 2/5, 5/8, 2/5/8) : ces trois chiffres y sont forcément. On les élimine du reste de l'unité.",
            "Chaque case n'a pas besoin des trois chiffres : il suffit que l'union des trois cases en compte exactement trois."
        ),
        Tech.HIDDEN_TRIPLE to Lesson(
            "Trois chiffres dont toutes les places possibles, dans une unité, tiennent dans trois cases : ces cases leur sont réservées et on retire leurs autres candidats.",
            "Repère les chiffres qui ont peu de places dans une unité et regarde si trois d'entre eux se partagent trois cases."
        ),
        Tech.X_WING to Lesson(
            "Si un chiffre n'a que deux places sur une ligne A et deux places sur une ligne B, dans les deux mêmes colonnes, il occupera ces colonnes dans les lignes A et B (en diagonale). On l'élimine donc du reste de ces deux colonnes. Même chose en échangeant lignes et colonnes.",
            "Pour un chiffre donné, cherche deux lignes où il n'a que deux places formant un rectangle."
        ),
        Tech.SKYSCRAPER to Lesson(
            "Deux lignes où un chiffre n'a que deux places, avec une extrémité dans la même colonne. Si l'une de ces extrémités alignées n'a pas le chiffre, l'autre l'a ; donc au moins une des deux autres extrémités (le « toit ») contient le chiffre. Toute case qui voit les deux toits peut être éliminée.",
            "C'est un X-Wing « raté » d'une case : cherche deux liens forts parallèles décalés."
        ),
        Tech.TWO_STRING_KITE to Lesson(
            "Un chiffre a deux places sur une ligne et deux places dans une colonne, et une extrémité de chacune est dans le même bloc. Les deux autres extrémités ne peuvent pas être toutes deux sans ce chiffre : on l'élimine de la case qui les voit toutes les deux.",
            "Cherche une ligne et une colonne à deux places pour un même chiffre, reliées par un bloc."
        ),
        Tech.XY_WING to Lesson(
            "Une case pivot à deux candidats X/Y voit deux cases « ailes » X/Z et Y/Z. Si le pivot vaut X, la première aile vaut Z ; s'il vaut Y, la seconde vaut Z. Une des ailes vaut donc Z : on élimine Z des cases qui voient les deux ailes.",
            "Repère les cases à deux candidats et cherche un trio X/Y, X/Z, Y/Z où le pivot voit les deux autres."
        ),
        Tech.NAKED_QUAD to Lesson(
            "Comme la paire et le triplet nus, avec quatre cases dont l'union des candidats ne fait que quatre chiffres.",
            "Rare : utile dans les unités encore très vides."
        ),
        Tech.HIDDEN_QUAD to Lesson(
            "Quatre chiffres dont toutes les places, dans une unité, tiennent dans quatre cases : ces cases leur sont réservées.",
            "Très rare : pense-y quand une unité a beaucoup de candidats et qu'aucun sous-ensemble plus petit n'apparaît."
        ),
        Tech.SWORDFISH to Lesson(
            "L'X-Wing à trois lignes : si un chiffre, sur trois lignes, n'a ses places que dans trois colonnes, ces trois colonnes lui sont réservées sur ces lignes. On l'élimine du reste de ces colonnes.",
            "Chaque ligne n'a pas besoin des trois colonnes : il suffit que l'ensemble tienne dans trois colonnes."
        ),
        Tech.XYZ_WING to Lesson(
            "Comme l'XY-Wing, mais le pivot a trois candidats X/Y/Z. L'une des trois cases vaut forcément Z : on élimine Z des cases qui voient les trois (en pratique, dans le bloc du pivot).",
            "Cherche un pivot à trois candidats et deux ailes à deux candidats qui en sont des sous-ensembles."
        ),
        Tech.W_WING to Lesson(
            "Deux cases X/Y identiques qui ne se voient pas, reliées par un lien fort sur X (une unité où X n'a que deux places, chacune voyant une des cases). Elles ne peuvent pas valoir X toutes les deux, donc l'une vaut Y : on élimine Y des cases qui voient les deux.",
            "Repère deux cases jumelles éloignées, puis cherche un lien fort qui les relie."
        ),
        Tech.UNIQUE_RECTANGLE to Lesson(
            "Une grille valide n'a qu'une solution. Quatre cases en rectangle sur deux blocs ne peuvent pas toutes être réduites à A/B : on pourrait échanger A et B. Si trois coins sont A/B, le quatrième ne vaut ni A ni B.",
            "Cherche trois cases A/B en rectangle (sur exactement deux blocs) et regarde le quatrième coin."
        ),
        Tech.JELLYFISH to Lesson(
            "Le poisson à quatre lignes : si un chiffre, sur quatre lignes, n'a ses places que dans quatre colonnes, on l'élimine du reste de ces colonnes.",
            "Très rare : à chercher quand X-Wing et Swordfish ne donnent rien."
        ),
    )
}
