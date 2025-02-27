# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 2.11.0

- Ajout d'un outil de revue d'images pour parcourir rapidement les images traitées en mode batch et rejeter les mauvais scans
- Amélioration de la réactivité de l'interface utilisateur

## Nouveautés de la version 2.10.1

- Amélioration de la gestion des fichiers temporaires pour éviter qu'ils ne s'accumulent
- Correction de la fonction `continuum` qui pouvait échouer dans certains cas rares
- Support des opérations binaires sur des listes de même taille : par exemple `min(list1, list2)` applique `min` sur chaque élément des listes
- Ajout de la fonction `concat` pour concaténer des listes
- Ajout de `CENTER_X`, `CENTER_Y` et `SOLAR_R` dans l'en-tête FITS pour la compatibilité avec les outils INTI

## Nouveautés de la version 2.10.0

- Ajout de la possibilité de [réduire les fichiers SER traités](#réduction-des-fichiers-ser)
- Ajout d'un [serveur web embarqué](#serveur-web-embarqué)
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

## Serveur Web embarqué

JSol'Ex est parfois utilisé dans des événements publics, où il peut être intéressant de projeter le soleil en direct avec un vidéoprojecteur par exemple.
Pour cela, JSol'Ex proposait déja une option permettant d'ouvrir les images dans une fenêtre séparée (en faisant un clic droit dessus), ce qui permettrait par exemple de déplacer cette fenêtre sur un écran externe.
Cependant, la limitation est que votre ordinateur doit être connecté à un moniteur externe.

Avec cette nouvelle version, une interface web simplifiée est disponible via le menu "Outils".
Vous pouvez démarrer un serveur Web embarqué qui servira les images en cours de traitement.
L'avantage est que vous pouvez partager l'URL du serveur avec des personnes du même réseau : chacune d'entre elles peut voir les images sur son propre appareil par exemple.
Cela permet également d'utiliser un écran externe depuis un ordinateur distant.

Le serveur Web écoute par défaut sur le port `9122` et peut être activé en allant dans le menu "Outils".

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
