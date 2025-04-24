# Bienvenue dans JSol'Ex {{version}} !

## Nouveautés de la version 3

- [Changements depuis la 3.0.0](#changements-depuis-la-3.0.0)
- [Améliorations des images](#améliorations-des-images)
- [Nouveaux modes de stretching d'image](#nouveaux-modes-de-stretching-d'image)
- [Correction par flat physique](#correction-par-flat-physique)
- [Mesure de distances](#mesure-de-distances)
- [Améliorations de ImageMath](#améliorations-de-imagemath)
- [Corrections de bugs](#corrections-de-bugs)

## Changements depuis la 3.0.0
### 3.0.4

- Correction d'un problème de performance lorsque la mesure des décalages vers le rouge était activée

### 3.0.3

- Correction d'un bug lors du redimensionnement d'une image, causant des désalignements, ce qui pouvait entraîner de mauvais résultats de stacking ou un masquage incorrect du disque
- Possibilité d'afficher le globe ou non dans la fenêtre de mesure
- Ajout d'un historique dans la fenêtre de mesure
- Correction des retournements et rotations non appliqués à la fenêtre de mesure

### 3.0.2

- Correction de l'image incorrecte enregistrée : l'image affichée avait un étirement différent de celui montré dans l'aperçu

### 3.0.1

- Désactivation de l'amplification des protubérances par défaut, il y a maintenant un paramètre pour l'activer
- Correction de fautes de frappe dans les traductions
- Détection de la ligne la plus sombre plus robuste

## Améliorations des images

La qualité des images a été améliorée grâce à plusieurs modifications :

- ajout d'une neutralisation du fond, qui permet notamment de retirer les gradients liés aux réflections de lumière
- amélioration de la mise en valeur des protubérances
- luminosité des images plus stable
- amélioration de l'algorithme d'extraction automatique de la raie Hélium

L'algorithme de calcul de fond de ciel est aussi disponble dans les scripts avec la fonction `bg_model`.

## Nouveaux modes de stretching d'image

Lorsqu'une image est affichée, il est désormais possible de choisir entre plusieurs modes d'étirement :

- le mode linéaire, par défaut, est celui qui était utilisé avant cette version
- le mode courbe, nouveauté de cette version, permet d'appliquer une transformation non linéaire : en fournissant 2 valeurs d'entrée et sortie, l'algorithme calcule un polynôme de second degré qui passe par les points (0,0), (in,out) et (255, 255), puis applique ce polynôme à chaque pixel
- aucune transformation, ce qui permet d'afficher l'image sans aucun étirement

## Correction par flat physique

Cette fonctionnalité est particulièrement intéressante pour les spéctrohéliographes utilisant des longues fentes (7mm ou plus) ou de longues focales (500mm et plus).
Dans ce cas, il n'est pas rare de constater un assombrissement des pôles du soleil, ce qui est dommageable.
JSol'Ex offrait jusqu'ici une solution mathématique, qui montre parfois ses limites.
Cette version introduit une nouvelle possiblité : celle de faire des flats physiques.
Il s'agit concrètement de prendre une série d'images du soleil, avec un diffuseur de lumière devant l'instrument (par exemple un papier calque).
Le logiciel calculera alors automatiquement un modèle d'illumination du soleil, qui pourra être appliqué pour corriger vos images.

Notez que cette solution peut aussi être intéressante pour le Sol'Ex, dans certaines longueurs d'ondes où la diffusion est particulièrement visible, telles que le sodium ou le H-beta.

## Mesure de distances

Cette version intègre un nouvel outil de mesure de distances.
Il permet d'évaluer, par exemple, la taille de protubérances ou de filaments.
Pour ce faire, il suffit de cliquer sur le bouton de mesure, ou de faire un clic droit sur l'image.
Une nouvelle fenêtre s'ouvrira, avec la grille solaire dessinée. Vous aurez alors la possibilité de cliquer sur des points de l'image, pour créer par exemple un chemin suivant un filament.
Le logiciel s'occupe de calculer la distance, en prenant en compte la courbure du soleil (pour les points dans le disque solaire).
Pour les points en dehors du disque, une simple mesure linéaire est effectuée.

Notez que les distances sont approximatives : il n'est en effet pas possible de connaître précisément la hauteur des sujets observés, les mesures sont donc effectuées en utilisant une valeur moyenne du rayon solaire.

## Améliorations de ImageMath

Cette nouvelle version a considérablement amélioré le module ImageMath ainsi que sa documentation.
En effet, vous pouvez désormais :

- créer vos propres fonctions
- importer des scripts dans un autre script
- appeler un service web externe pour générer un script ou des images
- écrire vos appels de fonctions sur plusieurs lignes
- utiliser des paramètres nommés

De nouvelles fonctions ont aussi été ajoutées :

- `bg_model` : modélisation du fond de ciel
- `a2px` et `px2a` : conversion entre pixels et Angströms
- `wavelen` : retourne la longueur d'onde d'une image, basée sur son décalage de pixel, sa dispersion et la longueur d'onde de référence
- `remote_scriptgen` : permet d'appeler un service web externe pour générer un script ou des images
- `transition` : permet de créer une transition entre deux ou plusieurs images
- `curve_transform` : permet d'appliquer une transformation à l'image basée sur une courbe
- `equalize` : égalise les histogrammes d'une série d'images pour qu'elles se ressemblent en luminosité et contraste

Et d'autres ont été améliorées :

- `find_shift` : ajout d'un paramètre optionnel correspondant à la longueur d'onde de référence
- `continuum` : amélioration de la fiabilité de la fonction et par conséquent de l'extraction de la raie Hélium

Attention, il y a certains changements incompatibles avec les versions précédentes :

- les variables définies dans la partie "simple" d'un script ne sont plus disponibles dans la partie `[[batch]]` : il est nécessaire de les redéfinir.
- la fonction `dedistort` inverse la position des paramètres de référence et de liste d'images dans le cas où on réutilise des distorsions déja calculées, pour être cohérente avec le cas simple :

## Corrections de bugs

Cette version corrige plusieurs bugs :

- Correction d'un bug avec lequel l'ellipse détectée n'était pas réutilisée lors du calcul d'images à des décalages différents, ce qui pouvait causer des disques ou des images de tailles différentes
- Correction de l'histogramme ne s'ouvrant pas correctement dans une nouvelle fenêtre

## Message aux utilisateurs français

**Si vous votez Rassemblement National ou tout autre parti proche de l'extrême droite, je vous demande de ne pas utiliser ce logiciel.**

Mes convictions sont diamétralement opposées à celles de ces partis et je ne souhaite pas que mon travail développé soirs et week-ends et malgré une licence libre, serve à des personnes qui soutiennent ces idées nauséabondes.

La solidarité, le partage, l'écologie, l'ouverture aux autres, la lutte contre les discriminations et les inégalités, le respect de toutes les religions, de tous les genres et orientations sexuelles sont les valeurs qui m'animent.
Elles sont à l'opposé de celles prônées par ces partis.
