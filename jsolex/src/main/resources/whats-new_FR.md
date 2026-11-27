# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 6.0.0

- [Une prise en main simplifiée](#une-prise-en-main-simplifiée)
- [Retouches dans la visionneuse](#retouches-dans-la-visionneuse)
- [Bombes d'Ellerman et flares](#bombes-d-ellerman-et-flares)
- [Scripts Python](#scripts-python)
- [Corrections et améliorations](#corrections-et-améliorations)

## Une prise en main simplifiée

JSol'Ex est plus facile à découvrir. Au premier lancement, un assistant vous permet de traiter votre premier fichier sans explorer les options. Il lit la caméra dans le fichier de réglages de votre logiciel d'acquisition et vous fait choisir votre spectrohéliographe à partir de photos. La lunette, la monture et l'observateur sont facultatifs.

![Assistant de configuration](/docs/setup-assistant-fr.png)

L'écran d'accueil affiche maintenant les réglages qui seront utilisés pour le traitement, et permet de revenir aux réglages automatiques. Avant le traitement, JSol'Ex vous prévient si le fichier a été enregistré avec une autre caméra que celle de votre matériel.

Après le premier traitement, une visite guidée vous présente les différentes zones de l'écran. Elle reste disponible dans le menu « Aide ».

![Visite guidée](/docs/guided-tour-fr.jpg)

## Retouches dans la visionneuse

La visionneuse laisse plus de place à l'image : les boutons tiennent sur deux lignes, avec des icônes en couleur, et les retouches s'ouvrent dans un panneau à côté de l'image. Elle peut maintenant adoucir les images ou les rendre plus nettes, y compris avec un masque flou. L'étirement des images est aussi plus rapide, en particulier avec CLAHE, CLAHE2 et AutoStretch.

![Visionneuse d'images](/docs/image-viewer-fr.jpg)

## Bombes d'Ellerman et flares

La détection des bombes d'Ellerman est plus fiable. Elle s'inspire de l'approche décrite par Stephan Schmelzing. Chaque bombe d'Ellerman ou flare détecté est accompagné d'un graphique comparant le profil de sa raie à celui de son entourage.

## Scripts Python

**Les environnements virtuels Python doivent maintenant être créés avec GraalPy 25.6.** Si vous en utilisez un, recréez-le avec cette version.

- Conversion plus rapide des images vers et depuis NumPy.
- L'outil de script autonome peut maintenant utiliser un environnement virtuel GraalPy.
- NumPy peut maintenant être utilisé sous Windows.
- Les scripts peuvent enregistrer leurs propres fichiers, par exemple des fichiers CSV, avec les autres sorties.

## Corrections et améliorations

- Consommation mémoire réduite pendant le traitement.
- Le réglage « Ajustement de la consommation mémoire » a été supprimé, car il n'est plus nécessaire.
- Correction des coins blancs sur les images pivotées pour corriger l'angle P.
- Correction de l'outil de script autonome qui échouait sur les ordinateurs équipés d'une carte graphique.
- Correction des scripts Python dans la version native de l'outil de script autonome.
- Correction d'erreurs qui pouvaient interrompre le traitement à l'ouverture d'un nouveau fichier ou à l'affichage d'une image.
- Correction d'une erreur lors de la création d'une animation ou d'un panneau avant la fin du traitement.

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamétralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
