# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 5.0.5

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
