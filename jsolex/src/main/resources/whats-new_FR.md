# Bienvenue dans JSol'Ex {{version}} !

Voici les nouvelles fonctionnalités de cette version :

- [Message aux utilisateurs français](message-aux-français)
- [Explorateur de spectre](explorateur-de-spectre)
- [Nouvelles fonctions ImageMath](#nouvelles-fonctions-ImageMath)
- [Corrections de bugs et améliorations](#bugfixes-and-improvements)

## Changements dans la version 2.6.1

- Correction des limites de la plage de décalage de pixels qui pouvaient entraîner le rejet de certaines images (bug #344)

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

## Corrections de bugs et améliorations

- Ajout d'un modèle de nom de fichier par défaut pour les batches
- Avertissement ajouté lorsque le décalage de pixels demandé n'est pas disponible
- Remplacement des décalages de pixels invalides par le meilleur ajustement
- Affichage de l'espace disque requis lors de la génération d'animations personnalisées
- Renommage du débogueur de spectre, maintenant appelé "Analyseur de vidéo"
