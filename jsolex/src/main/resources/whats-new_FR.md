# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 2.9.0

- [Ajout de la détection de taches solaires](#détection-des-régions-actives)
- [Correction de l'angle parallactique pour les montures Alt-Az](#correction-de-l'angle-parallactique-pour-les-montures-alt-az)
- [Nouvelles fonctions dans les scripts](#nouvelles-fonctions-dans-les-scripts)
- Annotation automatique des taches solaires détectées
- Ajout d'un bouton pour supprimer le fichier SER après traitement
- Ajout de la possibilité de traiter des fichiers SER dans le module de scripting en ligne de commande

Rejoignez-nous sur Discord ! [https://discord.gg/y9NCGaWzve](https://discord.gg/y9NCGaWzve)

### Détection des régions actives

Cette version introduit la détection des régions actives dans les images solaires.
La détection fonctionne en analysant chaque image de votre fichier SER et en identifiant les régions ayant une luminosité inattendue par rapport à un modèle calculé à partir de chaque trame.
La détection des régions actives générera 2 images :

- une de la ligne étudiée avec une superposition des régions actives détectées
- une avec l'image du continuum de la ligne étudiée, avec les régions actives détectées mises en évidence

Le mécanisme de détection est responsable de la détection des "zones" qui couvrent les taches solaires.
Cependant, si vous avez accès à Internet, il pourra également ajouter des étiquettes sur ces zones actives pour vous aider à les identifier.
Notez toutefois que les positions des étiquettes ne seront correctes que si vous utilisez une monture équatoriale, que l'image est orientée correctement (flips horizontaux/verticaux) et que l'heure et la date sont correctement réglées dans votre logiciel d'acquisition.

Vous pouvez également générer plus d'images en utilisant les fonctions de scripts décrites ci-dessous.

### Correction de l'angle parallactique pour les montures Alt-Az

Jusqu'à cette version, JSol'Ex supposait que les images étaient capturées avec une monture équatoriale.
Ainsi, la grille d'orientation et la correction de l'angle P supposaient que vous utilisiez une monture équatoriale.
Avec cette version, vous avez la possibilité de cocher le mode "Alt-Az" dans les paramètres de traitement, ainsi que de déclarer un montage comme utilisant une monture Alt-Az.
C'est par exemple votre cas si vous utilisez un Sunscan, ou un Sol'Ex en mode Sunscan.
Dans ce cas, nous vous recommandons également d'entrer la latitude et la longitude de l'observation, ce qui permettra à JSol'Ex de corriger automatiquement l'angle parallactique.

### Nouvelles fonctions dans les scripts

Les fonctions suivantes ont été ajoutées :

- `GET_AT` permet de récupérer une image dans une liste à un index spécifique.
- `AR_OVERLAY` génère un calque de taches solaires sur une image.
- `CROP_AR` recadre une image sur les taches solaires (génère une image par tache).

### Corrections de bugs et autres améliorations

- Correction d'un bug où un message d'erreur pouvait être affiché lors de la reconstruction, sans effet sur le résultat.
- Correction des fichiers SER qui n'étaient pas fermés, les empêchant d'être supprimés sous Windows.

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamètralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
