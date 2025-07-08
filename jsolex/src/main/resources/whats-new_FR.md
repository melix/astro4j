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

### 3.3.2

- Correction de la détection de profondeur de pixels des fichiers SER

### 3.3.1

- Correction d'une erreur lors de l'opération de rognage ou d'extraction de trames du fichier SER

### 3.3.0

- Correction d'un bug dans le mode auto contrast qui pouvait provoquer une exception
- Ajout de la fonction `mtf` inspirée de SIRIL qui applique une fonction de transfert des tons moyens (MTF) avec des paramètres configurables pour les ombres, tons moyens et hautes lumières (valeurs 8-bit 0-255)
- Ajout de la fonction `mtf_autostretch` inspirée de SIRIL qui applique un étirement MTF automatique basé sur l'analyse de l'histogramme utilisant un écrêtage des ombres basé sur sigma et un niveau de fond cible
- Rend les variables `angleP`, `b0`, ... disponibles dans la section `[[batch]]` des scripts. Une valeur moyenne est calculée à partir des images de la série (approximation)
- Possiblité de choisir le masque flou en méthode d'amélioration de la netteté
- Ajout d'un script d'exemple pour extraire la couronne E
- Affichage d'une échelle de distance des protubérances dans la fenêtre de mesure
- Ajout d'une option dans le menu pour extraire les trames correspondant à une sélection rectangulaire de l'utilisateur

### 3.2.2

- Ajout de la fonction `unsharp_mask` pour appliquer un masque flou (amélioration de la netteté)
- Optimisation des performances en mode traitement complet
- Correction d'une potentielle exception de pointeur nul au démarrage
- Optimisation de la réactivité de l'interface utilisateur
- Amélioration de la vue de profil : cliquer sur la reconstruction affiche maintenant les profils d'intensité moyenne de la trame et de la raie spectrale, avec l'information de longueur d'onde quand disponible
- Ajout d'un export CSV du profil spectral
- Par défaut, le Sunscan ne devrait pas utiliser le mode AltAz
- Affichage du décalage de pixels dans la carte technique

### 3.2.1

- Modification de l'autostretch pour être moins agressif sur les protubérances
- Correction de la rotation qui était appliquée aux images de spectre dans la détection d'éruption / bombes d'Ellerman si la correction d'angle P était activée
- Traitement de 2 fichiers en parallèle en mode batch
- Correction de la position de l'ellipse lorsque l'image est redimensionnée
- Correction du disque tronqué lors de la réduction de fichiers SER
- Réduction des faux positifs dans la détection de bombes d'Ellerman

### 3.2.0

- Ajout de la détection de [bombes d'Ellerman](#bombe-d-ellerman)
- Amélioration des capacités de la fonction `DRAW_TEXT`
- Ajustement des paramètres par défaut
- Correction de l'image colorisée qui était trop sombre / tronquée
- Ajout de `%PIXEL_SHIFT%` dans les variables de nommage de fichier
- Correction des paramètres qui se superposaient en mode batch

### 3.1.3

- Correction de la fiche technique qui n'était pas orientée correctement lorsque l'angle P était corrigé

### 3.1.2

- Ajout d'un nouveau style pour dessiner le globe, où le label N correspond au nord céleste et P au nord solaire. De plus si l'angle P est corrigé, alors la grille est aussi corrigée.
- Correction d'une erreur dans la création de mosaïque
- Correction d'un bug lors de l'utilisation de fonctions où une image de décalage de pixel inconnu échouerait les scripts

### 3.1.1

- Améliorations de la correction des bords dentelés
- Correction de la correction de bandes qui pouvait causer des artefacts blancs aux extrêmités des pôles
- Correction de la détection d'ellipse lorsque l'image contient des bandes où la valeur des pixels est tronquée à 0

### 3.1.0

- Ajout d'une [correction des bords dentelés](#correction-des-bords-denteles) (expérimental)
- Correction de la fonction `saturate` qui n'utilisait plus la valeur choisie
- Correction de la correction de bandes sur les bords solaires (bandes plus claires)

### 3.0.4

- Correction d'un problème de performance lorsque la mesure des décalages vers le rouge était activée
- Correction de la position des zones actives lorsqu'une rotation est appliquée
- Correction de la détection non déterministe des décalages vers le rouge et des zones actives

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

## Bombes d'Ellerman

Ellerman Bombs are small, transient brightenings that occur in the solar atmosphere, often associated with solar flares or active regions.
Les bombes d'Ellerman sont de petites taches brillantes qui se produisent dans l'atmosphère solaire, souvent associées à des éruptions solaires ou à des régions actives.
Elles s'observent typiquement dans les ailes de la raie H-alpha et peuvent être indicatives d'événements de reconnexion magnétique, avec un maximum autour de 1Å du centre de la raie H-alpha.
De taille très modeste (~300km), elles sont souvent difficiles à détecter, d'autant plus qu'elles sont invisibles au centre de la raie.

JSol'Ex 3.2 introduit une nouvelle fonctionnalité pour détecter automatiquement ces phénomènes.
La détection est activée si vous choisissez le mode complet (ou que vous avez sélectionné la détection dans le mode personnalisé) et que vous travaillez sur la raie H-alpha.

Si une détection est faite, au moins 2 images seront créées :

- une image de spectre par bombe d'Ellerman détectée, où un carré rouge sera dessiné autour de la zone correspondante
- une image du disque solaire dans le continuum, où toutes les bombes détectées seront entourées de carrés rouges

Prenez garde, la détection est très sensible et peut détecter des phénomènes qui ne sont pas des bombes d'Ellerman (par exemple des artefacts de traitement) : la meilleure vérification est donc visuelle.

## Correction des bords dentelés

Cette fonctionnalité est expérimentale et peut ne pas fonctionner parfaitement.
Les images capturées avec un spectrohéliographe montrent souvent des "bords dentelés" au limbe solaire : ceux-ci sont dus à plusieurs causes : turbulence atmosphérique, vent ou suivi imparfait.
JSol'Ex 3.1 introduit une nouvelle fonctionnalité pour corriger ces bords dentelés, qui réduira également le désalignement des caractéristiques sur le disque solaire.
Pour activer la correction, vous devez cocher l'option "correction des bords dentelés" dans les paramètres d'amélioration de l'image.
La valeur sigma peut être utilisée pour ajuster la correction : plus la valeur est élevée, moins les échantillons utilisés pour calculer la correction seront restrictifs.


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
