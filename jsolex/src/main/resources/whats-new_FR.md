# Bienvenue dans JSol'Ex {{version}} !

Voici les nouvelles fonctionnalités de cette version :

- [Changements depuis la 2.4.0](#changements-depuis-la-2.4.0)
- [Traitement automatique de l'hélium D3](#traitement-automatique-de-lhélium-d3)
- [Gestion du matériel](#gestion-du-matériel)
- [Support d'autres spectrohéliographes](#support-d'autres-spectrohéliographes)

## Changements depuis la 2.4.0

- L'autostretch ignorera désormais la correction gamma si l'image est trop sombre
- Ajout d'une image "Eclipse en mode Doppler"
- Correction de l'image Hélium mono écrasée par sa version couleur
- Amélioration de la résolution des lignes spectrales de référence (1/100e d'Angström)
- Ajout de la possibilité de sauvegarder l'image GONG en faisant un clic droit sur l'image
- Amélioration de la reconnaissance de contours
- Correction d'une consommation excessive de mémoire pendant la reconstruction
- Correction des liens d'ouverture des fichiers générés en mode batch
- Correction d'une erreur lorsqu'un script essaie d'enregistrer une animation par dessus une autre sous Windows
- Ajout de la possibilité de continuer le traitement d'un script en mode batch même si certains fichiers sont en erreur
- Correction de la position incorrecte des vitesses détectées sur les images/animations lorsque l'angle de tilt n'est pas de 0
- Amélioration de la précision de la détection de la vitesse
- Ajout des fonctions ImageMath `SORT` et `VIDEO_DATETIME`
- Correction d'un bug de neutralisation du fond de ciel sur certaines vidéos
- Le décalage du continuum est maintenant configurable
- Ajout d'un bouton pour réinitialiser les paramètres de traitement en mode surveillance

## Traitement automatique de l'hélium D3

Avant cette version, le traitement d'une ligne d'hélium D3 nécessitait une intervention manuelle : en particulier, il fallait analyser le fichier SER en utilisant le débogueur de spectre pour déterminer le décalage de pixel entre une ligne de référence (généralement la ligne de sodium D2 ou la ligne de fer Fe 1) et la ligne d'hélium D3.

À partir de cette version, JSol'Ex peut générer automatiquement des images de la ligne d'hélium sans aucune intervention manuelle, en un seul clic !

Pour ce faire, la ligne de référence doit soit être correctement détectée (lors de l'utilisation du mode "Autodétection"), soit être définie manuellement dans la fenêtre des "Paramètres de traitement".

Il est important de noter que le binning et la taille des pixels de votre caméra doivent être correctement définis dans la section "Détails de l'observation" pour que le calcul du décalage de pixel soit précis.

Les nouvelles images seront générées automatiquement dès que vous sélectionnez les images "Corrigées en géométrie (traitées)" ou les images "Colorisées" dans l'onglet de sélection d'images (cela est automatique en mode rapide et en mode complet respectivement).

Si l'image d'hélium générée est incorrecte, vous pouvez toujours générer manuellement des images de la ligne d'hélium en utilisant les [scripts ImageMath](https://melix.github.io/astro4j/latest/en/jsolex.html#_imagemath_scripts).

## Gestion du matériel

Dans les versions précédentes, si vous aviez plusieurs équipements (par exemple différents télescopes, différentes configurations avec et sans réducteur de focale, etc.), vous deviez changer manuellement les paramètres de l'équipement chaque fois que vous vouliez passer de l'un à l'autre.
À partir de JSol'Ex 2.4, un nouveau menu "Matériel" a été ajouté, ce qui vous permet de déclarer plusieurs équipements et de passer facilement de l'un à l'autre.

## Support d'autres spectrohéliographes

JSol'Ex a toujours été capable de traiter d'autres fichiers SER que ceux produits par l'instrument Sol'Ex, mais le calcul des longueurs d'onde du profil n'était pas précis pour ceux-ci.
Dans cette version, nous avons ajouté la prise en charge de nouveaux spectrohéliographes, avec leur propre réseau, longueur focale, etc.
