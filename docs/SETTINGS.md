# Réglages

Menu principal → onglet **Réglages**, en haut de l'écran. Les réglages forment une seule liste qui défile, rangée par thème (Blocage, Curseur, Lecture, Touches de couleur, Général). Chaque choix est un **bouton visible** : déplace le focus dessus et appuie sur **OK** ; la valeur choisie est en verre clair. Les réglages à nombreuses valeurs (zoom, touches de couleur) ont deux boutons **◀ ▶** autour de la valeur. Les changements s'appliquent à l'ouverture du site suivant. **Réinitialiser** remet les réglages de confort à leur valeur d'origine, sans toucher à l'URL de configuration ni aux sites.

| Réglage | Valeurs | Défaut | Effet |
|---|---|---|---|
| Listes recommandées | Activé / Désactivé | Activé | Ajoute EasyList, Liste FR et le filtre DNS d'AdGuard ([BLOCKING](BLOCKING.md)). L'état des listes et **Mettre à jour** sont juste en dessous. |
| YouTube sans pub | Activé / Désactivé | Activé | Pubs retirées du lecteur, sautées si elles passent, encarts masqués. |
| Sauter les sponsors | Activé / Désactivé | Activé | Passages sponsorisés sautés (SponsorBlock). |
| Curseur : vitesse | Lente, Normale, Rapide, Très rapide | Normale | Vitesse du déplacement libre du curseur. |
| Curseur : taille | Petit, Moyen, Grand | Moyen | Taille de la flèche. |
| Curseur : masquage automatique | Jamais, 2, 3, 5, 10 s | 3 s | Délai d'inactivité avant que la flèche disparaisse. |
| Curseur aimanté | Activé / Désactivé | Activé | Un appui bref sur une flèche saute au prochain lien ou bouton dans cette direction. |
| Zoom du texte par défaut | 100 % à 200 % | 100 % | Taille du texte des pages. Peut être ajusté site par site (voir ci-dessous). |
| Saut avance / recul | 5, 10, 15, 30 s | 10 s | Durée des sauts du lecteur. |
| Durée d'affichage de la barre | 2, 4, 6, 10 s | 4 s | Barre de lecture, volume, zoom. |
| Volume | Indicateur WiDo / du système | WiDo | Voir ci-dessous. |
| Plein écran : flèches | Pilotent le lecteur / Déplacent le curseur | Pilotent le lecteur | Comportement des flèches en plein écran vidéo. |
| Touches rouge, verte, jaune, bleue | Voir la liste ci-dessous | Menu, Recharger, Texte +, Texte − | Raccourcis des touches de couleur. |
| Au démarrage | Afficher le menu / Rouvrir le dernier site | Afficher le menu | Au lancement depuis l'accueil de la TV. |
| Ouvrir un site | Dernière page / Accueil du site | Dernière page | Reprend là où tu en étais (position d'une vidéo YouTube comprise). « Accueil du site » est aussi dans le menu rapide. |
| Télécommande téléphone | Activé / Désactivé | Désactivé | Serveur local pour piloter WiDo depuis le téléphone ; QR code d'appairage et **Nouveau code** juste en dessous ([REMOTE-CONTROL](REMOTE-CONTROL.md)). |
| Nouveaux sites : redirections | Strictes / Souples / Mixtes | Strictes | Valeur proposée dans le formulaire d'un nouveau site perso ([BLOCKING](BLOCKING.md)). |

## Actions des touches de couleur

Aucune action, Retour au menu, Recharger la page, Activer/désactiver le curseur, Texte plus grand, Texte plus petit, Menu rapide, Enregistrer la page, Haut de la page.

Si ta télécommande n'a pas de touches de couleur, ces réglages restent sans effet : toutes les actions sont aussi dans le **menu rapide** (appui long sur Retour).

## Zoom du texte

- **Texte plus grand / plus petit** : touches de couleur par défaut, ou menu rapide. Pas de 10 %, de 70 % à 250 %.
- Le réglage est **mémorisé par site** : agrandir un site ne change pas les autres. Le zoom par défaut s'applique aux sites jamais ajustés.
- Il agrandit le **texte** des pages (réglage `textZoom` de la WebView). Les images et les vidéos gardent leur taille.

## Volume

Avec **Indicateur WiDo**, les touches de volume règlent le volume du système (flux média) sans afficher le panneau d'Android, et WiDo affiche sa propre barre de volume.

Limites :

- Le panneau de volume d'Android lui-même **ne peut pas être personnalisé** par une application.
- Si l'appareil a un volume fixe (sortie HDMI qui impose son propre volume, par exemple), WiDo laisse le système gérer les touches.
- En **plein écran vidéo**, les flèches haut/bas règlent le volume de la même façon.

## Ce qui n'est pas réglable (encore)

Couleur d'accent fixe ou automatique, densité des cartes, ordre des sites, langue (suit celle de la TV). Voir la [feuille de route](ROADMAP.md).
