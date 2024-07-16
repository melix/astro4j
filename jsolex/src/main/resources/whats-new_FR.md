# Bienvenue dans JSol'Ex {{version}} !

Voici les nouvelles fonctionnalités de cette version :

- [Message aux utilisateurs français](message-aux-français)
- [Explorateur de spectre](explorateur-de-spectre)
- [Nouvelles fonctions ImageMath](#nouvelles-fonctions-ImageMath)
- [Forcer le polynôme](#forcer-le-polynôme)
- [Corrections de bugs et améliorations](#bugfixes-and-improvements)

## Changements dans la version 2.6.3

- Correction de la précision de la détection des lignes spectrales (issue #360)
- La correction par flat artificiel est maintenant optionnelle (issue #364)
- Amélioration de la correction par flat artificiel

## Changements dans la version 2.6.2

- Correction de la fonction `rescale_rel` qui convertissait les images couleur en mono
- Correction de l'élément de menu de rognage dynamique qui ne faisait rien sur certaines images
- Possibilité d'utiliser le polynôme détecté sur une trame particulière au lieu de le chercher manuellement (issue #353)
- Possibilité de choisir pour quels décalages vers le rouge les animations/panneaux doivent être générés (issue #357)

## Changements dans la version 2.6.1

- Ajout de la possibilité de forcer le polynôme (cf ci-dessous)
- Correction des limites de la plage de décalage de pixels qui pouvaient entraîner le rejet de certaines images (bug #344)
- Amélioration de la correction par flat artificiel
- Réduction des artéfacts de fond de ciel dans les images auto-stretch

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.

## Explorateur de spectre

L'explorateur de spectre est disponible via le menu Outils.
Il affiche une image du spectre telle qu'il serait vu à travers votre logiciel de capture préféré (SharpCap, FireCapture, ...) et vous permet de faire défiler les régions du spectre, ainsi que zoomer.

L'explorateur affichera des lignes spectrales remarquables et vous permettra de rechercher des longueurs d'onde particulières.
Il offre en option une version colorisée du spectre, afin que vous puissiez avoir une idée de "où" vous vous situez dans le spectre visible de la lumière.

Cet explorateur propose également une fonctionnalité expérimentale d'identification, inspirée par [INTI Map](http://valerie.desnoux.free.fr/inti/map.html).
Vous pouvez sélectionner une image du spectre que vous avez capturée et JSol'Ex essaiera de trouver où elle se situe dans le spectre.
Cela peut être particulièrement utile à des fins d'apprentissage, mais aussi pour trouver des lignes plus difficiles à identifier visuellement.

## Nouvelles fonctions ImageMath

- La fonction `draw_earth` a été ajoutée pour dessiner la Terre sur une image, mise à l'échelle en fonction du disque solaire

## Forcer le polynôme

Depuis la version 2.6.1, il est possible de forcer le polynôme utilisé pour détecter la ligne spectrale.
Pour ce faire, dans les paramètres de traitement, cochez la case "Forcer le polynôme", puis cliquez sur les "...".
Ceci ouvrira une fenêtre vous montrant l'image moyenne calculée.
Sur cette image, appuyez sur `Ctrl` + clic gauche pour ajouter un point sur la ligne spectrale, jusqu'à avoir ajouté suffisamment de points.
Cliquez ensuite sur le bouton "Calculer le polynôme" : la valeur sera affichée dans la fenêtre et reportée automatiquement dans les paramètres de traitement.

## Corrections de bugs et améliorations

- Ajout d'un modèle de nom de fichier par défaut pour les batches
- Avertissement ajouté lorsque le décalage de pixels demandé n'est pas disponible
- Remplacement des décalages de pixels invalides par le meilleur ajustement
- Affichage de l'espace disque requis lors de la génération d'animations personnalisées
- Renommage du débogueur de spectre, maintenant appelé "Analyseur de vidéo"
