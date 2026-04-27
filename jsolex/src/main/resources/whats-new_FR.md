# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 5.1.2

- Ajout d'une nouvelle fonction ImageMath `clahe2` qui calcule la moyenne de plusieurs passes CLAHE à des tailles de tuiles adaptées au disque solaire, produisant des artefacts au limbe nettement plus doux qu'une CLAHE classique.
- Correction de la taille de noyau par défaut affichée à la sélection de « Renforcement » ou « Masque flou » qui valait 1 au lieu du minimum documenté de 3, ce qui rendait l'effet sans action tant que l'utilisateur ne modifiait pas la valeur. Le champ contraint désormais la saisie à un entier impair supérieur ou égal à 3.
- La composition de mosaïques utilise désormais un mélange multi-bandes (pyramide laplacienne) avec égalisation locale de l'exposition dans la zone de recouvrement, ce qui dissimule beaucoup mieux les raccords entre tuiles lorsque la luminosité diffère entre les images.
- Assistant d'empilement et de mosaïque : un nouveau choix « Méthode d'empilement » permet de choisir entre « Rapide » (référence unique la plus nette — le comportement précédent) et « Consensus » (dédistorsion multi-images itérative — meilleure qualité mais plusieurs fois plus lente). Le mode Consensus était sélectionné par défaut auparavant mais n'exécutait pas réellement l'algorithme de consensus.
- L'assistant d'empilement et de mosaïque utilise désormais le style partagé de l'application pour une apparence plus cohérente.
- La composition de mosaïques affiche maintenant la progression pendant l'assemblage des paires de panneaux et lors de l'étape finale de fusion.
- Correction d'un plantage lors de l'empilement (« Progress must be between 0.0 and 1.0 ») lorsque la hauteur de l'image n'était pas un multiple du pas de tuile.
- Correction de la lenteur des boutons Suivant/Précédent et de l'ouverture de l'assistant de revue des images du traitement par lot.
- Correction de l'affichage bref du mauvais type d'image dans le panneau de référence de l'assistant de revue des images après navigation.
- Correction de l'ordre incohérent des images dans la liste latérale de l'assistant de revue des images du traitement par lot d'un fichier SER à l'autre.
- Correction du collage créé via Outils | Charger des images qui était enregistré dans le dossier d'installation de JSol'Ex ; il est désormais placé dans le dossier des images chargées.
- Correction de la fenêtre de collage qui s'ouvrait parfois avec une bande d'images vide ou bloquait l'interface pendant le chargement des miniatures.
- Correction de « Ouvrir dans une nouvelle fenêtre » qui produisait une fenêtre vide impossible à fermer lorsqu'elle était utilisée sur une image ouverte via Outils | Charger des images.
- Correction de la fonction ImageMath `sort` qui retournait la liste non triée lorsque le suffixe `asc` était utilisé dans l'argument d'ordre (par exemple `sort(images, "date asc")`).
- Correction des fonctions ImageMath `rotate_deg`, `rotate_left` et `rotate_right` qui échouaient ou tournaient d'un angle erroné lorsqu'elles étaient appliquées à une liste d'images.
- Correction de `dedistort` qui échouait sur des listes d'images de tailles différentes ; les images sont désormais alignées automatiquement.
- Les marqueurs de `draw_text` utilisent désormais les métadonnées propres à chaque image lorsqu'elles sont disponibles.
- Ajout de `copy_metadata(to: ...; from: ...)` pour copier les métadonnées d'observation entre images.

## Nouveautés de la version 5.1.1

- SpectroSolHub En Direct : des événements de progression sont désormais affichés pendant l'envoi des images
- SpectroSolHub En Direct : les images brutes et de reconstruction ne sont plus envoyées
- SpectroSolHub En Direct : en mode lot, seules les images produites par la section de sortie `[[batch]]` des scripts sont envoyées. Un avertissement est consigné à la fin du lot si aucune image n'a été envoyée.
- Assistants d'orientation (BASS2000, SpectroSolHub) : l'image de référence GONG peut être changée pour celle d'un autre observatoire via un sélecteur superposé à l'image, et l'alignement automatique peut être restreint à la rotation seule quand l'image est déjà correctement retournée.
- Correction de l'alignement des images qui n'était pas annulé lors d'un clic sur un bouton de zoom (+, -, 1:1, ajuster) après avoir activé l'alignement des images.
- Correction du traitement par lot qui lançait bien plus de fichiers en parallèle que la limite configurée, ce qui pouvait épuiser la mémoire sur les gros lots.
- Correction du tableau du traitement par lot qui affichait parfois le contenu d'une autre ligne (barre de progression, redshift ou liens vers les images générées) lors du défilement sur de nombreux fichiers.
- ImageMath : le bloc `meta { ... }` accepte désormais une section optionnelle `requirements { images = [...] }` qui garantit la génération des types d'images listés lorsque le script s'exécute.
- ImageMath : les valeurs par défaut négatives (par exemple `default = -1`) sont désormais acceptées dans le bloc `meta { params { ... } }`.

## Nouveautés de la version 5.1.0

- Ajout du mode SpectroSolHub En Direct : diffusez votre session de traitement en temps réel pour que d'autres puissent la suivre sur spectrosolhub.com/live. Accessible depuis le menu Partage.
- Toutes les images peuvent désormais être téléversées sur SpectroSolHub, et non plus uniquement celles où le disque solaire a été détecté
- Correction du téléchargement des dépôts de scripts dont les noms de fichiers contiennent des espaces
- Amélioration des messages d'erreur lors du chargement des dépôts SpectroSolHub
- Réexécuter un script ImageMath après avoir modifié un paramètre est désormais beaucoup plus rapide : seules les expressions qui dépendent du paramètre modifié sont recalculées.
- Ajout de deux fonctions ImageMath, `scale_to_unit` et `scale_from_unit`, pour convertir les valeurs des pixels entre les plages [0;65535] et [0;1] (avec écrêtage optionnel).
- Amélioration de la qualité de `dedistort` sur les exécutions multi-itérations en mode consensus grâce à une convergence adaptative par tuile.
- Ajout de raccourcis clavier dans l'inspecteur d'images.
- Amélioration de l'ergonomie de l'assistant de publication SpectroSolHub

## Nouveautés de la version 5.0.4

- Correction d'un problème de performance dans l'extraction Hélium si l'accélération GPU était désactivée
- Correction d'une limite de traitement en parallèle qui pouvait conduire à un traitement incomplet
- Ajout d'un alignement automatique dans l'assistant de soumission BASS2000

## Nouveautés de la version 5.0.3

- Ajout d'un nouveau menu « Partage » avec des entrées pour publier sur BASS2000 et partager sur SpectroSolHub
- Ajout de la possibilité de post-traiter les images en externe avant de les téléverser sur SpectroSolHub. Dans l'étape de sélection des images, cochez l'option de post-traitement pour modifier vos images (contraste, netteté, etc.) dans votre éditeur préféré avant le téléversement.
- Correction de résultats de dédistorsion non déterministes
- Amélioration des performances sur les systèmes multi-core
- Refonte de l'extraction des images hélium
- Ajout de la possibilité de déclarer le type d'image généré dans les scripts

## Nouveautés de la version 5.0.2

- Ajout de la possibilité de configurer le niveau de parallélisme utilisé dans le mode traitement par lots
- Correction de l'accélération GPU qui échouait sur les appareils avec une taille de groupe de travail limitée, provoquant un repli inutile sur le CPU
- Lorsqu'un crash OpenGL précédent est détecté, une boîte de dialogue propose de réessayer au lieu de désactiver silencieusement le visualiseur 3D

## Nouveautés de la version 5.0.1

- Correction du téléversement SpectroSolHub qui n'appliquait pas les transformations (comme la correction de l'angle P) aux images non consultées individuellement avant le téléversement
- Correction de l'étape de comparaison d'orientation SpectroSolHub qui n'appliquait pas la correction de l'angle P à l'image utilisateur lors de la comparaison avec la référence GONG

## Nouveautés de la version 5.0.0

- [Intégration SpectroSolHub](#intégration-spectrosolhub)
- [Scripts Python](#scripts-python)
- [Correction de rotation Doppler](#correction-de-rotation-doppler)
- [Améliorations de l'explorateur de spectre](#améliorations-de-lexplorateur-de-spectre)
- [Nouvelles fonctions ImageMath](#nouvelles-fonctions-imagemath)
- [Corrections et améliorations](#corrections-et-améliorations)

## Intégration SpectroSolHub

[SpectroSolHub](https://spectrosolhub.com) est un nouveau service compagnon de JSol'Ex. Actuellement en bêta, il vous permet de partager vos images spectrohéliographiques avec la communauté. Il est développé par l'auteur de JSol'Ex.

![SpectroSolHub](/docs/spectrosolhub.png)

Vous pouvez désormais publier vos images traitées directement sur SpectroSolHub depuis JSol'Ex. Après le traitement d'un fichier SER, cliquez sur le bouton « SpectroSolHub » dans la barre d'état pour ouvrir l'assistant de publication. L'assistant vous guide à travers l'authentification, la sélection d'images, les métadonnées de session et le téléversement.

Vous pouvez également parcourir et ajouter des dépôts de scripts depuis SpectroSolHub directement depuis le gestionnaire de dépôts de scripts. Cliquez sur « Parcourir SpectroSolHub » pour découvrir les dépôts disponibles et les ajouter en un clic.

**Note :** En téléversant des images sur SpectroSolHub, vous accordez une licence non exclusive, mondiale et libre de redevance pour l'utilisation de votre contenu à des fins de recherche scientifique.

## Scripts Python

JSol'Ex 5.0 introduit le scripting Python, une avancée majeure en matière d'extensibilité.
Vous pouvez désormais écrire des scripts de traitement d'image en Python, en complément du langage ImageMath existant.
Les scripts Python ont accès à l'ensemble du pipeline de traitement et peuvent exploiter le riche écosystème Python pour des analyses avancées.

Consultez la documentation pour les détails sur l'écriture et l'utilisation des scripts Python.

## Correction de rotation Doppler

JSol'Ex 5.0 introduit un nouveau type d'image « Doppler (correction de rotation) ».
Cette image Doppler est générée en soustrayant le gradient lisse de rotation solaire par ajustement polynomial 2D, ce qui rend les structures de vitesse chromosphériques beaucoup plus faciles à identifier.

Deux nouvelles fonctions ImageMath accompagnent cette fonctionnalité : `SIGNED_DIFF(a, b)` calcule la différence entre deux images en préservant le signe (sans normalisation), tandis que `POLY_FIT_2D(image, degree)` ajuste une surface polynomiale 2D dans le disque solaire.

## Améliorations de l'explorateur de spectre

L'explorateur de spectre a été considérablement amélioré dans cette version :

- Les graduations de longueur d'onde s'adaptent automatiquement au niveau de zoom, avec des graduations mineures et des lignes de grille pour une meilleure lisibilité
- Ajout de niveaux de zoom prédéfinis (25%, 50%, 75%, 100%, 150%, 200%, 400%) relatifs à la dispersion de l'instrument

## Nouvelles fonctions ImageMath

- `COLLAGE` : crée des mises en page via un motif textuel (ex : `collage(".X. / X.X", images)` pour une pyramide)
- `SIGNED_DIFF(a, b)` : calcule la différence entre deux images en préservant le signe
- `POLY_FIT_2D(image, degree)` : ajuste une surface polynomiale 2D dans le disque solaire

## Corrections et améliorations

- Correction d'une fuite mémoire lors de la reconstruction, visible sur les très gros fichiers SER

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamétralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
