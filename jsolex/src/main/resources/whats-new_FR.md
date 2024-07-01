# Bienvenue dans JSol'Ex {{version}} !

Voici les nouvelles fonctionnalités de cette version :

- [Animations personnalisées et recadrage](#animations-personnalisees-et-recadrage)
- [Corrections de bugs et améliorations](#corrections-de-bugs-et-ameliorations)

## Changes in 2.5.4

- Ajout d'un avertissement lorsque le décalage de pixel demandé n'est pas disponible
- Remplacement des décalages de pixel invalides par le meilleur ajustement possible

## Modifications dans 2.5.3

- Correction de la position des décalages vers le rouge qui pouvait être incorrecte en case de fort tilt ou d'inversion horizontale/verticale
- Correction du double-clic qui ne changeait plus le zoom
- Amélioration des performances de création des animations de décalages vers le rouge

## Modifications dans 2.5.2

- Amélioration du calculateur de temps d'exposition
- Possibilité d'annoter les animations de décalages vers le rouge
- Ajout de champs supplémentaires à l'éditeur de spectrohéliographes personnalisés
- Correction de la lecture des métadonnées de Firecapture
- Réduction de l'utilisation mémoire lors de l'analyse d'images
- Remplacement du curseur de zoom par des boutons
- Correction des images manquantes dans les scripts en cas de composition avec le résultat de la fonction `find_shift`

## Modifications dans 2.5.1

- Ajout d'une catégorie d'affichage séparée pour les décalages vers le rouge et promotion des images de débogage des décalages vers le rouge à cette catégorie
- Affichage du nombre de FPS dans la calculatrice d'exposition optimale
- Correction de la stratégie d'autostretch produisant des images lumineuses lorsque le fichier SER original a un offset trop grand
- Correction de l'ajustement du contraste ne s'étendant pas sur toute la plage disponible
- Correction du décalage de pixel min/max dans les animations personnalisées limité au minimum des deux

## Animations personnalisées et recadrage

Il est désormais possible de recadrer une image manuellement ou de créer des animations d'une région d'un disque solaire, ou un panneau de décalages vers le rouge, en sélectionnant une région d'intérêt.

Pour ce faire, dans la vue, appuyez sur "CTRL" puis cliquez et faites glisser pour sélectionner la région d'intérêt.
Un menu apparaîtra où vous pourrez choisir de rogner l'image à la sélection ou de créer une animation/un panneau de la région sélectionnée.

Pour que les annotations soient précises, assurez-vous que :

- vous ayez défini la taille de pixel de votre caméra dans les détails de l'observation
- vous ayez soit sélectionné "autodétection" dans la sélection de la longueur d'onde, soit spécifié explicitement la bonne longueur d'onde

## Corrections de bugs et améliorations

- Ajout de la possibilité d'afficher les coordonnées GPS dans les détails de l'observation
- Correction des artefacts de sonnerie dans l'image autostretchée
- Correction d'un bug empêchant les coordonnées de longitude ou de latitude négatives
