# Bienvenue dans JSol'Ex {{version}} !

- [Version 4.2.0](#nouveautes-de-la-version-4-2-0) - Java 25, support GIF, réglages de format de fichier
- [Version 4.1.4](#nouveautes-de-la-version-4-1-4) - Corrections de bugs et améliorations
- [Version 4.1.3](#nouveautes-de-la-version-4-1-3) - Amélioration de la correction des bandes
- [Version 4.1.2](#nouveautes-de-la-version-4-1-2) - Corrections de bugs
- [Version 4.1.1](#nouveautes-de-la-version-4-1-1) - Corrections de bugs
- [Version 4.1.0](#nouveautes-de-la-version-4-1-0) - Préréglages utilisateur, création de collages
- [Version 4.0.1](#nouveautes-de-la-version-4-0-1) - Sélection de langue, corrections
- [Version 4.0.0](#nouveautes-de-la-version-4-0-0) - Interface améliorée, intégration BASS2000

## Nouveautés de la version 4.2.0

- Passage à Java 25
- Les formats de fichier font désormais partie des réglages avancés (en dehors des paramètres de traitement)
- Ajout de la possibilité de générer des fichiers GIF en plus du MP4

## Nouveautés de la version 4.1.4

- Correction d'une fuite de mémoire
- Clarification de l'affichage de la conversion des pixels en Angströms dans la boîte de dialogue des paramètres de traitement
- Correction de `COLORIZE` qui n'acceptait pas un paramètre de longueur d'onde comme annoncé
- Correction d'un cas limite où la colorisation pouvait échouer
- Correction de l'affichage du décalage de pixels dans les détails d'observation
- Correction de la correction excessive des bandes

## Nouveautés de la version 4.1.3

- Amélioration de la correction des bandes
- Amélioration de l'algorithme d'alignement/empilement
- Ajout de la fonction `STACK_DEDIS` pour empiler en prenant en compte la distorsion comme poids

## Nouveautés de la version 4.1.2

- Ajout d'un champ nom de fichier dans la boîte de dialogue de création de collage pour personnaliser le nom des fichiers
- Correction de l'orientation des images ailes de raies qui n'était pas appliquée (BASS2000)
- Réduction de l'utilisation mémoire lors de la génération de collages

## Nouveautés de la version 4.1.1

- Correction de la sélection de couleur d'arrière-plan des collages qui n'était pas appliquée correctement
- Correction du paramètre d'espacement des collages qui n'était pas appliqué correctement
- Correction des scripts qui échouaient s'ils contenaient un bloc `[params]`

## Nouveautés de la version 4.1.0

- **Préréglages utilisateur** : Créez, sauvegardez et gérez vos propres préréglages personnalisés pour la sélection d'images et les scripts, en complément des modes Rapide et Complet
- Les scripts peuvent maintenant déclarer leurs paramètres qui seront automatiquement configurables dans l'interface utilisateur
- Ajout du mode fondu pour l'alignement d'images BASS2000 avec opacité réglable
- Avertissement lors de la soumission à BASS2000 si un fichier a déjà été envoyé le même jour pour la même longueur d'onde
- Correction du bouton d'upload BASS2000 activé avant la sauvegarde des fichiers sur disque
- [Création de collages d'images](#creation-de-collages-d-images) : Nouvelle fonctionnalité de collage permettant de combiner plusieurs images traitées en une seule image composite avec mise en page et espacement personnalisables

## Nouveautés de la version 4.0.1

- [ui] Possibilité de choisir la langue de l'interface
- [bugfix] Correction des popups de complétion intrusives
- [bugfix] Correction du champ INSTRUME dans la soumission BASS2000

## Nouveautés de la version 4.0.0

- [Interface utilisateur améliorée](#interface-utilisateur-améliorée)
- [Intégration BASS2000](#intégration-bass2000)
- [Détection manuelle d'ellipse](#détection-manuelle-dellipse)
- [Scripts automatiques](#scripts-automatiques)
- [Nouvelles fonctions ImageMath](#nouvelles-fonctions-imagemath)
- [Corrections et améliorations](#corrections-et-améliorations)

## Interface utilisateur améliorée

JSol'Ex 4.0 présente une interface des paramètres de traitement entièrement repensée.  
L'interface a été modernisée pour une meilleure organisation des contrôles et une interface plus intuitive.

![Nouvelle interface utilisateur](/docs/new-ui-fr.png)

## Intégration BASS2000

Cette version introduit l'intégration avec la base de données solaire BASS2000.  
Cette fonctionnalité vous permet de soumettre vos observations à une base de données professionnelle et de contribuer à la recherche scientifique.
Vous devrez compléter un court processus d'inscription pour être approuvé en tant que contributeur.
JSol'Ex vous propose un assistant de soumission qui vous guidera dans ce processus.

![BASS2000 Submission Wizard](/docs/bass2000-fr.png)

BASS2000 accepte les observations d'instruments approuvés (variantes Sol'Ex et MLAstro SHG 700) qui respectent des exigences spécifiques :
- Uniquement des images de disque solaire complet (pas de disques partiels, stacks ou mosaïques)
- Les images doivent provenir de scans uniques sans transformations supplémentaires ou amélioration du contraste
- La correction de l'angle P doit être appliquée avec un tilt inférieur à 1°
- La mise en station doit être précise
- Longueurs d'onde supportées : H-alpha, H-alpha continuum bleu, CaII K, CaII aile bleue, CaII H centre

L'assistant valide automatiquement vos images traitées par rapport à ces exigences et gère le processus de soumission.

## Détection manuelle d'ellipse

JSol'Ex 4.0 introduit la détection manuelle d'ellipse pour les cas où la détection automatique du disque solaire échoue.  
Quand la détection automatique d'ellipse ne peut pas identifier correctement les contours du disque solaire, vous pouvez maintenant dessiner manuellement une ellipse autour du disque solaire pour assurer une correction géométrique précise.

![Détection manuelle d'ellipse](/docs/assisted-fit-fr.png)

Cette fonctionnalité est particulièrement utile pour :
- Les images avec un contraste faible ou des conditions d'éclairage inhabituelles
- Les observations de disque solaire partiel
- Les cas où la détection automatique est perturbée par des artefacts ou des protubérances
- L'ajustement fin de la correction géométrique pour des résultats optimaux

Pour utiliser la détection manuelle d'ellipse, sélectionnez "Assistée par l'utilisateur" dans le menu déroulant du mode de détection d'ellipse dans la section Paramètres de traitement avancés.

## Scripts automatiques

JSol'Ex 4.0 introduit la capacité d'exécuter automatiquement des scripts pour des longueurs d'onde spécifiques.  
Cette fonctionnalité vous permet de configurer des flux de traitement standardisés qui s'exécutent de manière cohérente à travers vos observations.

L'exécution des scripts a été améliorée pour s'assurer que les sections batch fonctionnent correctement en mode de traitement de fichier unique et en mode lot.  
Cela corrige les problèmes précédents où les scripts batch pouvaient ne pas s'exécuter comme attendu.

## Nouvelles fonctions ImageMath

Deux nouvelles fonctions statistiques ont été ajoutées au module ImageMath :

- `avg2` : calcule la moyenne de plusieurs images avec écrêtage sigma pour rejeter automatiquement les valeurs aberrantes
- `median2` : calcule la médiane de plusieurs images avec détection configurable des valeurs aberrantes basée sur sigma

Ces fonctions sont particulièrement utiles lors de la combinaison de plusieurs observations, car elles aident à réduire le bruit et éliminent les artefacts qui pourraient apparaître dans les images individuelles.

## Création de collages d'images

JSol'Ex 4.1 introduit une nouvelle fonctionnalité pour créer des collages d'images à partir de plusieurs images traitées.
Cela vous permet de combiner plusieurs images en une seule image composite avec mise en page et espacement personnalisables.

![Création de collages d'images](/docs/collage-interface-fr.jpg)

Vous pouvez sélectionner plusieurs images traitées et les organiser dans une grille.

## Corrections et améliorations

Cette version corrige plusieurs bugs et inclut diverses améliorations :

- Correction d'un bug où les sections batch des scripts automatisés n'étaient pas exécutées en mode lot
- Correction de la lecture des fichiers FITS legacy écrits par les versions précédentes de JSol'Ex
- Correction des faux positifs dans la détection de bombes d'Ellerman se produisant près du limbe solaire
- Correction du schéma de couleurs incorrect appliqué quand le mode auto-détection est activé
- Correction des erreurs non propagées correctement en mode de traitement par lots
- Correction de la touche ESC qui continuait à démarrer le traitement au lieu d'annuler
- Correction des info-bulles manquantes et de la validation des formulaires dans diverses boîtes de dialogue
- Les images générées par script ne sont plus automatiquement étirées par défaut (corrige l'issue #660)
- Ajout de la possibilité d'ajouter plus de fichiers à un lot existant pour traitement
- Ajout de l'option de télécharger des images GONG à une date et heure arbitraires
- Ajout de l'option de taille fixe au menu de recadrage automatique pour maintenir des dimensions d'image spécifiques
- Le bouton d'exécution est maintenant désactivé pendant le traitement pour éviter les démarrages multiples accidentels

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamétralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
