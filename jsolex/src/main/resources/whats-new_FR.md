# Bienvenue dans JSol'Ex {{version}} !

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
