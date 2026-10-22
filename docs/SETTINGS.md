# Réglages

Menu principal → onglet **Réglages**, en haut de l'écran. Navigation à deux niveaux sur la TV : **▲ ▼** passent d'un thème à l'autre, **OK** ou **▶** entrent dans les réglages du thème, **◀** (au bord gauche) ou **Retour** reviennent à la liste des thèmes. Les réglages sont rangés par thème : **Blocage, Lecture, Apparence, Touches de couleur, Écrans liés, Général**. Sur la TV, les thèmes forment une colonne à gauche : le thème qui reçoit le focus s'affiche aussitôt à droite (flèche droite pour entrer dans ses réglages). Sur le téléphone, tout tient dans une seule liste. Le thème Blocage affiche aussi le nombre de **pubs et pisteurs bloqués ce mois-ci** (le détail par site est dans l'appui long sur sa carte). Chaque choix est un **bouton visible** : déplace le focus dessus et appuie sur **OK** ; la valeur choisie est en verre clair. Les réglages à nombreuses valeurs (zoom, touches de couleur) ont deux boutons **◀ ▶** autour de la valeur. Les changements s'appliquent à l'ouverture du site suivant. **Réinitialiser** remet les réglages de confort à leur valeur d'origine, sans toucher à l'URL de configuration ni aux sites.

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
| Durée d'affichage des messages | 2, 4, 6, 10 s | 4 s | Saut (« +30 s »), volume, zoom, messages du lecteur. |
| Volume | Indicateur WiDo / du système | WiDo | Voir ci-dessous. |
| Plein écran : flèches | Pilotent le lecteur / Déplacent le curseur | Pilotent le lecteur | Comportement des flèches en plein écran vidéo. |
| Touches rouge, verte, jaune, bleue | Voir la liste ci-dessous | Menu, Recharger, Texte +, Texte − | Raccourcis des touches de couleur. |
| Ambiance | Bleu / Violet / Ambre / Noir | Bleu | Couleur des halos et de la lumière de l'appli. « Noir » : fond noir pur et lumière très discrète, pour un projecteur dans une pièce sombre. Les sites gardent leur couleur. |
| Sons de l'interface | Désactivé / Doux / Normal | Normal | TV : un tic au passage du focus, un « toc » à la validation, un claquement de doigts (avec son écho) à l'ouverture d'un site, deux notes au retour. Sons système : ils ne coupent jamais la vidéo. |
| Double Retour : retour aux sites | Activé / Désactivé | Activé | Deux appuis rapides sur Retour dans le lecteur ramènent aux sites. Désactivé : Retour recule de page en page. |
| Lecteur léger automatique | Activé / Désactivé | Activé sur les appareils à peu de mémoire | Un film de plus de 10 minutes détecté dans la page passe seul dans le lecteur intégré (« Lire dans WiDo »), bien moins gourmand ; la page reste en pause derrière le film (retour instantané) et n'est vidée, puis rechargée au retour, que si la mémoire vient à manquer. Revenir au site puis relancer le film le renvoie dans le lecteur léger. Voir [REMOTE-CONTROL](REMOTE-CONTROL.md). |
| Taille de l'interface | Normale / Grande / Très grande | Normale | Tout l'accueil, les menus et les messages du lecteur grandissent (projecteur regardé de loin). Le zoom du texte des pages reste à part. |
| Aide | Tutoriel de la télécommande / Présentation | — | Le **tutoriel** fait essayer pour de vrai OK, OK maintenu, Menu et le double Retour, puis rappelle le lecteur, le volume et les fenêtres de cookies ; on peut passer une étape ou le quitter à tout moment. La **présentation** rejoue les trois écrans du premier lancement (sites, téléphone, gestes). |
| Au démarrage | Afficher le menu / Rouvrir le dernier site | Afficher le menu | Au lancement depuis l'accueil de la TV. |
| Ouvrir un site | Dernière page / Accueil du site | Accueil du site | Un site s'ouvre à neuf, sur son accueil : les vidéos commencées se reprennent depuis l'onglet **Reprendre**. « Dernière page » rouvre la page où tu étais. |
| Télécommande téléphone | Activé / Désactivé | Désactivé | Serveur local pour piloter WiDo depuis le téléphone ; QR code d'appairage et **Nouveau code** juste en dessous ([REMOTE-CONTROL](REMOTE-CONTROL.md)). |
| Appli téléphone et autres TV | Activé / Désactivé | Activé | Section **Écrans liés** : l'appli WiDo du téléphone et tes autres TV peuvent trouver cette TV et s'y lier. Nom de la TV, QR code d'appairage, adresse, appareils liés (Mon téléphone / Invité, Oublier) juste en dessous ([PHONE-AND-SCREENS](PHONE-AND-SCREENS.md)). |
| Rapport de plantage | Voir le rapport / Effacer | — | Dernière erreur de l'appli, fermetures décidées par Android (mémoire, plantage natif, appli bloquée ; Android 11 et plus) et fil des derniers événements du lecteur, gardés sur l'appareil (rien n'est envoyé). Sur TV, le QR code ouvre le rapport **complet** sur le téléphone (même Wi-Fi : page servie par la TV tant que la fenêtre est ouverte, avec Copier et Partager ; sans réseau, il ne contient que le début) ; sur téléphone, Copier ou Partager. Après une fermeture sur erreur, un message le signale au démarrage suivant. **Envoyer le rapport** l'envoie au projet en un appui, sans les noms de sites ([tools/crash-relay](../tools/crash-relay/README.md)). |
| Journal détaillé | Activé / Désactivé | Désactivé | Garde sur l'appareil les derniers gestes et décisions de WiDo (touches, retours, lecteur, mémoire), sans noms ni adresses de sites ; ajouté au rapport de plantage (voir le rapport : ▲ ▼ le font défiler). |
| Nouveaux sites : redirections | Strictes / Souples / Mixtes | Strictes | Valeur proposée dans le formulaire d'un nouveau site perso ([BLOCKING](BLOCKING.md)). |

Sur **téléphone**, la liste ne montre que ce qui y sert (blocage, lecture, reprise, nom du téléphone) : pas de curseur ni de touches de couleur, les choix vont à la ligne sur un écran étroit. Deux réglages propres au téléphone : **Notification de la TV** (piloter la TV depuis la notification et l'écran verrouillé, volume du téléphone → TV) et **Lecture en arrière-plan** (le son continue quand on quitte l'appli), activés par défaut.

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
