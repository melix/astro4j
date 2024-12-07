# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 2.8.0

- Correction du bouton "aligner les images" qui ne fonctionnait pas toujours
- Réduction de la pression mémoire lors de la génération d'animations ou de panneaux
- Limitation de la taille des panneaux à 7680x7680 pixels
- Correction de la double création d'animations lorsque FFMPEG était disponible
- Ajout d'une option pour effectuer un retournement vertical du spectre, utile par exemple avec Sunscan où le rouge apparaît en haut au lieu d'en bas
- Ajout des fonctions `GET_R`, `GET_G`, `GET_B` et `MONO` qui extraient respectivement les canaux rouge, vert et bleu d'une image couleur, et convertissent une image couleur en mono pour la dernière
- Correction du chargement des images JPEG mono 8-bit qui ne prenait pas en compte la correction gamma
- Amélioration de la stabilité de l'empilement
- Modifications des structures de données internes pour faciliter de futures évolutions
- Correction de l'inversion des champs latitude/longitude
- Ajout d'un module permettant de lancer des scripts depuis la ligne de commande
- Ajout de la possibilité de choisir la couleur des annotations lors de la création d'animations personnalisées
- Changement du rendu de l'image Doppler pour avoir des couleurs plus proches du rouge/bleu
- Passage à Java 23

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
