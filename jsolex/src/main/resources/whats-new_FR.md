# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 2.8.0

### Support du Sunscan

Le [Sunscan](https://www.sunscan.net/) est le dernier né de l'équipe Staros.
Ce petit frère du Sol'Ex est un appareil compact, entièrement automatisé, qui permet de réaliser des images du soleil à partir de son téléphone, sans avoid besoin d'utliser un ordinateur.
Cependant, certains utilisateurs exportent leurs fichiers SER pour les traiter avec JSol'Ex.
Afin de faciliter ce traitement, JSol'Ex 2.8 apporte quelques nouveautés :

- La possibilité d'inverser le spectre : les images du spectre issu d'un Sunscan ont le rouge en haut et le bleu vers le bas.
- Un nouvel algorithme de stacking, mis au point avec l'aide de Christian Buil, permet d'empiler plus facilement les images issues d'un Sunscan.

Ce nouvel algorithme de stacking remplace l'ancien y compris pour les images non issues de Sunscan.
Des adaptations sur vos scripts peuvent être nécessaires pour prendre en compte ces changements.
Par ailleurs, cet algorithme permet d'implémenter plus facilement de nouvelles fonctionnalités à l'avenir.

### Nouvelles fonctions dans les scripts

De nouvelles fonctions ont été ajoutées pour faciliter le traitement des images :

- `GET_R`, `GET_G`, `GET_B` et `MONO` permettent respectivement d'extraire les canaux rouge, vert et bleu d'une image couleur, et de convertir une image couleur en mono pour la dernière.
- `DEDISTORT`, qui permet de corriger la distorsion d'une image en utilisant une autre image de référence. Cette fonction a été mise au point par Christian Buil et adaptée pour JSol'Ex.
- `STACK_REF`, qui permet de déterminer une image de référence à utiliser pour empilement.

### Corrections de bugs et améliorations

- Correction du bouton "aligner les images" qui ne fonctionnait pas toujours
- Réduction de la pression mémoire lors de la génération d'animations ou de panneaux
- Limitation de la taille des panneaux à 7680x7680 pixels
- Correction de la double création d'animations lorsque FFMPEG était disponible
- Correction du chargement des images JPEG mono 8-bit qui ne prenait pas en compte la correction gamma
- Amélioration de la stabilité de l'empilement
- Correction de l'inversion des champs latitude/longitude
- Ajout d'un module permettant de lancer des scripts depuis la ligne de commande
- Ajout de la possibilité de choisir la couleur des annotations lors de la création d'animations personnalisées
- Changement du rendu de l'image Doppler pour avoir des couleurs plus proches du rouge/bleu
- Utilisation du suréchantillonnage dans la reconstruction pour éviter les artefacts d'aliasing lorsque la ligne spectrale est fine
- Passage à Java 23
- Modifications des structures de données internes pour faciliter de futures évolutions

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
