# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 5.3.5

- Vous pouvez désormais ajouter des fichiers à un lot terminé sans retraiter ceux déjà traités.
- Un lot peut désormais surveiller un dossier et y ajouter automatiquement les nouveaux fichiers SER qui y apparaissent.
- Correction du dernier fichier d'un lot qui affichait tous les autres fichiers après la revue des images.
- Une nouvelle option de renforcement du contraste « Meilleure méthode » choisit automatiquement la meilleure technique selon la raie spectrale détectée : CLAHE pour le calcium et Autostretch pour les autres raies.
- Les images empilées conservent désormais la raie spectrale des images source au lieu de revenir à une longueur d'onde incorrecte.
- Les images produites par l'outil d'empilement peuvent désormais être partagées vers SpectroSolHub.

## Nouveautés de la version 5.3.4

- Un nouveau « mode disque saturé » permet de réutiliser le polynôme de la raie spectrale du scan non saturé le plus proche lorsque le disque solaire est surexposé.
- Une nouvelle fonction de script `deghost` atténue l'image fantôme du disque solaire causée par les reflets optiques.
- L'explorateur de spectre peut désormais être superposé à votre logiciel de capture, avec transparence réglable et correspondance de la courbure du spectre.
- La fenêtre de l'explorateur de spectre peut désormais être affichée sans bordure avec ses contrôles déplacés en bas, pour une superposition plus propre en haut de l'écran.
- Correction d'artefacts en forme de lignes pouvant apparaître lors de l'empilement avec la dédistorsion locale activée.
- Les animations et vidéos sont désormais regroupées dans les mêmes sections par exécution que les images lors d'une nouvelle exécution d'un script.
- Une section de la liste des images peut désormais être fermée d'un coup grâce au bouton de fermeture situé à côté de son titre.
- Le rogneur de fichiers SER affiche à nouveau une barre de progression pendant le rognage, et la mise en page de sa fenêtre a été corrigée.
- Les paramètres des scripts Python sont désormais affichés dans l'ordre où ils sont déclarés dans le script.

## Nouveautés de la version 5.3.3

- Correction des images Hélium qui n'étaient parfois pas générées lorsque la ligne de référence était trop proche du bord de l'image.

## Nouveautés de la version 5.3.2

- Vous pouvez désormais exporter les images en cours dans un fichier de session, puis le rouvrir plus tard pour partager vos résultats sans retraiter la vidéo d'origine.
- Le recadrage dans la visionneuse est désormais interactif, avec les actions courantes accessibles depuis la barre d'outils.
- Une nouvelle correction expérimentale permet de détecter et supprimer les ondulations régulières du limbe solaire causées par des oscillations de la monture.
- Les autres raies spectrales connues présentes dans la fenêtre de capture sont désormais extraites automatiquement.
- Les vues spectrales 3D disposent désormais d'une option « Inverser » et d'un curseur d'opacité pour mieux révéler les zones sombres comme les taches solaires.
- Les images monochromes peuvent désormais être colorisées directement dans la visionneuse, depuis le panneau d'annotations, à l'aide d'un profil spectral ou d'une couleur personnalisée.
- Optimisation de l'accélération GPU
- Le bouton d'interruption arrête désormais le traitement de manière fiable.

## Nouveautés de la version 5.3.1

- Traitement des images plus rapide, avec notamment un premier traitement plus rapide après le lancement de l'application.
- La vue de reconstruction peut désormais être parcourue pixel par pixel avec les touches fléchées (maintenez Maj pour des déplacements plus grands).
- Ajout d'un calque d'annotation des régions actives.
- Ajout d'annotations de texte libre.
- Lors de l'enregistrement d'une image annotée, vous pouvez désormais conserver l'original intact et enregistrer les annotations sur une copie.
- Le bouton d'enregistrement est désormais mis en évidence lorsqu'une image a des modifications non enregistrées.
- Correction d'une vue de reconstruction dupliquée apparaissant parfois pour le même décalage de pixels.

## Nouveautés de la version 5.3.0

- La fiche technique générée séparément est remplacée par des annotations interactives activables image par image : grille d'orientation, détails d'observation, paramètres solaires, échelle des protubérances, référence de taille Terre déplaçable à la souris et signature libre, avec couleurs, épaisseur de trait et modèles personnalisables, le tout enregistrable en tant que préréglage.
- Les images peuvent désormais être dupliquées via le menu contextuel de la barre latérale, pour conserver une version originale à côté d'une version annotée.
- Les mesures de redshift sont plus fiables et leur incertitude est désormais bien plus réaliste.
- Ajout d'une nouvelle fonction ImageMath `average_image` qui renvoie l'image moyenne, éventuellement corrigée de la distorsion.
- Correction des fichiers log qui étaient vides en mode batch
- Correction d'un plantage lors de la génération des animations et panneaux de redshift
- Ajout d'un suivi de progression lors de la génération des animations et panneaux de redshift
- Réorganisation du panneau de droite avec un sélecteur de panneau et un panneau de publication d'images dédié.
- Correction du marqueur de raie spectrale dans la vue de reconstruction qui était mal positionné
- Le profil spectral normalise désormais les intensités de la trame et de la ligne cliquée par rapport à l'image moyenne
- L'onglet du profil spectral peut désormais afficher les intensités absolues (ADU) au lieu des valeurs normalisées à 100
- L'onglet d'image de référence GONG permet désormais de choisir l'observatoire et la taille de l'image, jusqu'à la pleine résolution
- L'onglet d'image de référence GONG peut détecter d'éventuels miroirs de votre image active par rapport à la référence GONG
- Rafraîchissement de la fenêtre principale avec un thème de couleurs cohérent.
- La réexécution d'un script regroupe désormais ses résultats dans des sections de passe numérotées et repliables, pour distinguer et comparer facilement les exécutions successives

## Nouveautés de la version 5.2.2

- L'assistant de soumission BASS2000 liste désormais les soumissions en attente d'autres observateurs pour la même date et la même longueur d'onde, avec une frise visuelle indiquant leurs heures de capture.
- Les vitesses de redshift sont désormais calculées au subpixel et accompagnées d'une incertitude.
- Correction du bouton d'exécution des scripts ImageMath qui perdait son menu de paramètres avancés au lancement d'un traitement par lots.
- Correction d'une fuite mémoire native dans les transferts d'images vers le GPU qui pouvait épuiser la mémoire après plusieurs traitements par lots.
- Correction de la vue de reconstruction qui affichait la croix du spectre du mauvais côté de la ligne de référence lors d'un clic sur l'image solaire.
- Correction d'une erreur pouvant survenir lors de l'enregistrement de certaines images de débogage.

## Nouveautés de la version 5.2.1

- Correction d'une erreur qui pouvait empêcher la génération de l'image inversée lorsque le disque solaire était petit dans l'image.
- L'image inversée n'affiche plus les informations d'observation.
- L'import SunScan ignore désormais les scans déjà présents dans le dossier de téléchargement au lieu de les télécharger à nouveau.
- Réduction de l'utilisation mémoire lors de plusieurs traitements par lot successifs, qui pouvait auparavant provoquer des erreurs de mémoire insuffisante.

## Nouveautés de la version 5.2.0

- Refonte de l'écran de traitement par lots avec un tableau de bord de progression, des lignes compactes et un panneau latéral de détails.
- Les fichiers d'un lot sont désormais traités dans l'ordre de soumission.
- L'étirement automatique produit désormais une luminosité plus douce et plus naturelle, préservant les détails dans les régions actives sans écraser les protubérances ni le dégradé du limbe.
- L'image inversée n'inverse désormais que le disque solaire, avec un contraste accentué sur ses détails, tout en conservant les protubérances dans leur orientation naturelle.
- Les images colorisées bénéficient désormais d'une plage tonale plus équilibrée grâce à une chaîne d'étirement revue.
- Les images dans la barre latérale peuvent désormais être renommées via le menu contextuel (clic droit) pour les distinguer dans les collages.
- La fonction ImageMath `dedistort` accepte désormais un paramètre `drizzle` (toute valeur entre 1 et 4, par ex. 1.5, 2, 3) qui produit une image super-résolue pour récupérer des détails sous d'excellentes conditions.
- Les scans acquis sur un appareil SunScan peuvent être détectés et importés directement depuis le menu Fichier.
- Correction d'images générées parfois absentes des résultats du traitement par lots et de la fenêtre de revue.
- Le double-clic sur une image pour zoomer à 1:1 centre désormais la vue sur le point cliqué.
- Ajout d'un paramètre avancé pour choisir le dossier où sont écrits les fichiers temporaires.
- Correction de l'aperçu de reconstruction parfois non ajusté en contraste pendant le traitement.
- Ajout d'un indicateur d'activité des journaux pour rendre visible le travail en arrière-plan depuis n'importe quel onglet.

## Nouveautés de la version 5.1.3

- Traitement des fichiers SER plus rapide grâce à la réduction des copies mémoire lors de la lecture des images.
- Correction des courbes de couleur personnalisées silencieusement perdues lors d'une mise à jour depuis une version antérieure.
- Ajout d'une option « Faire confiance à la profondeur de bits déclarée » dans les paramètres de traitement avancés pour contourner la détection automatique lorsqu'elle donne un résultat incorrect.
- Correction des vues 3D (image 3D et tomographie sphérique, modes coquilles et lissé) qui ne s'affichaient pas sur macOS.
- Analyseur de vidéo : ajout d'un bouton de lecture avec un réglage de vitesse (jusqu'à 300 i/s) pour lire les images comme une vidéo.
- Ajout d'une entrée « Aller aux coordonnées... » dans le menu contextuel de la vue de reconstruction pour atteindre des coordonnées (X, Y) précises sans avoir à cliquer sur l'image.

## Nouveautés de la version 5.1.2

- Ajout d'une nouvelle fonction ImageMath `clahe2` qui calcule la moyenne de plusieurs passes CLAHE à des tailles de tuiles adaptées au disque solaire, produisant des artefacts au limbe nettement plus doux qu'une CLAHE classique.
- Correction de la taille de noyau par défaut affichée à la sélection de « Renforcement » ou « Masque flou ».
- La composition de mosaïques utilise désormais un mélange multi-bandes (pyramide laplacienne) avec égalisation locale de l'exposition dans la zone de recouvrement.
- Assistant d'empilement et de mosaïque : un nouveau choix « Méthode d'empilement » permet de choisir entre « Rapide » et « Consensus ».
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
