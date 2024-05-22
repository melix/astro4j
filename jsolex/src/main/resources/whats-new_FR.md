# Bienvenue dans JSol'Ex {{version}} !

Voici les nouveautés de cette version :

- [Détection automatique des raies spectrales](#détection-automatique-de-la-raie-étudiée)
- [Calcul automatique de vitesse (décalage vers le rouge)](#calcul-automatique-de-vitesse-décalage-vers-le-rouge)
- [Colorisation automatique basée sur la longueur d'onde](#colorisaton-automatique-basée-sur-la-longueur-d-onde)
- [Lecture des métadonnées Sharpcap et Firecapture](#lecture-des-métadonnées-sharpcap-et-firecapture)

## Détection automatique de la raie étudiée

JSol'Ex propose maintenant la détection automatique de la raie étudiée, ainsi que du binning.
Afin que la détection se fasse correctement, il est important que vous **entriez correctement la taille des pixels de votre caméra** dans la section "détails d'observation".
It's worth noting that detection will work better with a larger cropping window and will probably fail if the spectrum is too saturated.
La détection fonctionnera mieux si vous utilisez une fenêtre de cropping assez large et qu'elle est susceptible de confondre des raies spectrales si le spectre est trop saturé.

Enfin, si le mode "automatique" n'apparaît pas dans la liste des raies disponibles, c'est que vous aviez ajouté ou modifié des raies dans l'éditeur, auquel cas il vous faudra [procéder à une réinitialisation](#réinitialiser-les-raies-disponibles) pour que le mode "automatique" apparaisse.
Lorsqu'une raie est sélectionnée, l'onglet profil affiche désormais le profil mesuré, mais comparé à un profil de référence.

## Calcul automatique de vitesse (décalage vers le rouge)

Si la raie étudiée est l'hydrogène alpha, JSol'Ex analysera automatiquement les images pour détecter les décalages Doppler.
Le logiciel calculera automatiquement la vitesse associée au décalage et l'affichera dans la fenêtre de log.
De plus, il générera une image qui montre où les vitesses les plus élevées ont été détectées.
Si vous activez les images de débogage, il extraira également des images du fichier SER où les événements de vitesse maximale ont été détectés afin que vous puissiez vérifier si les mesures sont correctes.
Enfin, une fois le traitement terminé, un nouvel onglet nommé "Redshit" sera activé.
Si vous cliquez dessus, il vous permettra de générer des animations ou des panneaux d'images des régions où les vitesses ont été détectées !

## Lecture des métadonnées Sharpcap et Firecapture

Si un fichier de métadonnées Sharpcap ou Firecapture est trouvé à côté du fichier SER, il sera alors lu ce qui permettra à JSol'Ex de renseigner automatiquement les champs "Caméra" et "Binning".
Vous devrez cependant entrer manuellement la taille des pixels de la caméra dans la section "détails d'observation".
Les fichiers Sharpcap seront détectés s'ils ont le nom du fichier SER mais avec l'extension `.CameraSettings.txt`.
Les fichiers Firecapture seront détectés s'ils ont le nom du fichier SER mais avec l'extension `.txt`.

## Colorisation automatique basée sur la longueur d'onde

Dans les précédentes versions, la colorisation automatique n'était disponible que si vous définissiez une courbe de colorisation.
Désormais, la colorisation automatique estimera la couleur automatiquement.
Vous pouvez cependant remplacer la couleur automatique par une courbe manuelle si le résultat ne vous convient pas.

## Divers
### Correctifs et améliorations mineures

- Ajout de nouveaux boutons de zoom et centrage automatique
- Amélioration de l'agorithme de colorisation
- Amélioration de la détection de contours en particulier sur les images calcium

### Réinitialiser les raies disponibles

JSol'Ex vous propose d'ajouter manuellement des raies à la liste prédéfinie, ou de personaliser les courbes de colorisation.
Si vous avez procédé à de telles modifications par le passé, le mode "automatique" ne sera pas automatiquement disponible.
Vous avez 2 possibilités pour l'ajouter. Dans les 2 cas, ouvrez l'éditeur de raies spectrales en passant par le menu "Outils" puis :

- Ajouter manuellement une entrée nommée `Autodetect` avec une longueur d'onde de 0 nm dans la liste des raies disponibles.
- Ou cliquez sur "réinitialiser par défaut"

Cette dernière option étant la plus simple, elle vous permettra de retrouver les raies prédéfinies et le mode "automatique", tout en profitant des nouvelles raies ajoutées dans cette version.
