# Déduction — le sudoku qui t'apprend à raisonner

Application Android (Kotlin + Jetpack Compose), 100 % hors ligne, sans pub.

## Ce qu'il y a dedans

**Carrière** — 21 techniques, de la *Dernière case* au *Jellyfish*, débloquées une à une :
1. une leçon (principe, comment la repérer, exemple illustré sur une vraie position) ;
2. des **exercices ciblés** : une position où *seule* cette technique permet d'avancer. Il faut trouver le chiffre. Un coup juste mais non déductible (« au hasard ») ou faux fait échouer l'exercice → Recommencer / Voir la solution ;
3. une **grille d'application** complète où la technique est nécessaire ;
4. un interrupteur « Je maîtrise déjà cette technique » pour sauter ce que tu connais.

**Entraînement** — grilles qui n'exigent que les singletons + tes techniques maîtrisées (avec, en option, une technique à travailler). Chaque coup est analysé par le moteur :
- ✓ **maîtrisé** (et avec quelle technique) ;
- ↷ **anticipé** (juste, mais il fallait poser d'autres chiffres avant) ;
- ⚠ **au hasard** (juste, mais rien ne permettait encore de le déduire) ;
- ✗ **erreur** ;
- 💡 « Il y avait plus simple » quand tu utilises une technique avancée alors que des singletons attendaient.

**Indice à 3 niveaux** (fenêtre par-dessus la grille, onglets 1 / 2 / 3, croix pour fermer) :
1. combien de coups sont disponibles pour un humain avec tes techniques ;
2. quelle technique, sur quelle ligne / colonne / bloc ;
3. la solution expliquée, avec les candidats, le motif et les éliminations dessinés sur la grille, et un bouton pour placer le chiffre.

**Interface** — palette bleu/gris ; barre : Annuler, Effacer, Crayon, **Saisie rapide** (éclair, activée par défaut : on choisit un chiffre puis on touche les cases), Indice ; pavé 1-9 avec le nombre restant de chaque chiffre.

## Obtenir l'APK

### Option A — GitHub (sans rien installer)
1. Crée un dépôt GitHub (privé si tu veux) et pousse ce dossier.
2. L'action **Build APK** se lance toute seule (onglet *Actions*, ~5 min).
3. L'APK apparaît dans l'onglet **Releases** (`Deduction.apk`) : ouvre la page depuis ton téléphone, télécharge, installe (autoriser l'installation depuis le navigateur).

### Option B — Android Studio
Ouvre le dossier, laisse Gradle se synchroniser, puis *Build → Build APK(s)* ou lance directement sur ton téléphone branché en USB.

Les builds sont signés avec une clé personnelle fixe (`app/perso.keystore`) : les nouvelles versions s'installent par-dessus l'ancienne sans perdre ta progression. Ne publie pas cette clé si tu vises un jour le Play Store (il faudra alors une vraie clé de release).

## Architecture

```
app/src/main/java/fr/deduction/
  engine/            moteur pur Kotlin (aucune dépendance Android)
    Board.kt         géométrie, candidats en masques de bits
    Techniques.kt    les 21 techniques (détection + explication en français)
    Engine.kt        résolution « humaine », jugement d'un coup, disponibilité, indice
    Generator.kt     solveur rapide + générateur de grilles à solution unique
  sudoku/
    game/Game.kt     état d'une partie (saisie, notes, annuler, analyse asynchrone, sauvegarde)
    AppViewModel.kt  navigation, carrière, entraînement
    data/            banque de grilles, leçons, progression (SharedPreferences)
    ui/              écrans Compose (grille dessinée sur Canvas, indices, carrière…)
app/src/main/assets/
  bank.txt           ~4 400 grilles notées (technique la plus dure + techniques utilisées)
  exercises.txt      ~420 positions d'exercice (20 par technique)
tools/               générateur de la banque + vérifications (exécutables sur JVM)
```

### Vérifications réalisées
- 1,2 million de grilles générées et résolues : **0 élimination ou pose fausse** sur l'ensemble des techniques.
- 134 000 indices testés sur des positions intermédiaires : tous justes, et tous jugés « maîtrisés » par le moteur.
- Les 417 exercices joués automatiquement via la logique de jeu : tous réussis avec le bon coup, et un coup « au hasard » fait bien échouer.

## Pistes pour la suite
- Techniques de chaînes (X-Chain, coloriage, AIC) pour les grilles « extrêmes ».
- Statistiques par technique (lesquelles tu rates le plus).
- Mode sombre.
