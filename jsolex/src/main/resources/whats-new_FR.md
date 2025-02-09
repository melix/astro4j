# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 2.10.0

- Ajout de la possibilité de [réduire les fichiers SER traités](#réduction-des-fichiers-ser)
- Ajout de la taille du noyau de convolution pour les fonctions `SHARPEN` et `BLUR`
- Correction d'un bug rare où la régression d'ellipse échouait malgré sa détection
- Traitement des fichiers en séquence dans le mode batch, pour réduire la pression sur les machines moins puissantes

### Réduction des fichiers SER

De temps en temps, pour des raisons pratiques, les fichiers SER que vous traitez peuvent contenir beaucoup de trames vides au début ou à la fin du fichier, par exemple si vous utilisez une monture qui met quelques secondes à se stabiliser avant de commencer l'acquisition.
D'autres fois, vous avez peut-être utilisé une fenêtre de recadrage large, qui contient significativement plus de lignes spectrales que nécessaire pour le traitement.

Dans ces situations, il est possible de réduire les fichiers SER traités pour supprimer ces trames vides et réduire la taille des fichiers.
A la fin du traitement, le bouton "Réduire le fichier SER" sera activé, et proposera une plage de trames à conserver, ainsi qu'une portion de chaque trame à recadrer.
Ces valeurs sont basées sur la détection du disque solaire dans les trames, avec une marge de 10%.
De plus, le fichier SER réduit aura une correction du "sourire" appliquée, ce qui signifie que toutes les lignes seront parfaitement horizontales.
Vous disposez de la possibilité de choisir combien de pixels vous souhaitez conserver autour de la ligne centrale.

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
