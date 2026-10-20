# Utiliser la télécommande

## Menu des sites

Les sites sont une **rangée de cartes** en bas de l'écran : ◀ ▶ pour se déplacer, **OK** pour ouvrir. Le site sélectionné s'affiche en grand au-dessus, avec ses boutons **Ouvrir** et, pour un site perso, **Options** (ouvrir, modifier, supprimer, voir [CUSTOM-SITES](CUSTOM-SITES.md)) : flèche ▲ depuis la carte. Le fond prend la couleur de la carte sélectionnée. La dernière carte, **« + »**, ouvre le formulaire d'ajout.

En haut, les onglets **Sites** et **Réglages** ([SETTINGS](SETTINGS.md)) : un onglet s'active dès qu'il reçoit le focus. À droite, l'état de la configuration et l'heure : **OK** dessus actualise la configuration.

## Pendant la lecture

| Touche | Effet |
|---|---|
| Flèches (appui bref) | Le curseur **saute au prochain lien ou bouton** dans cette direction, entouré d'un contour bleu. |
| Flèches (maintenues) | Déplacement **libre** du curseur, de plus en plus rapide. Au bord de l'écran, la page défile. |
| **OK** | Clic à la position du curseur. Sur un champ de saisie, le clavier de la TV s'ouvre. |
| **Retour** | Plein écran vidéo → quitte le plein écran ; sinon page précédente ; sinon retour au menu. |
| **Retour, deux fois vite** | Retour direct aux sites, d'où qu'on soit dans la page. |
| **Retour** (appui long) ou **Menu** | **Menu rapide**. |
| Lecture/pause, ▶, ❚❚ | Agit sur la vidéo de la page (le lecteur du site montre sa propre barre). |
| Avance / recul rapide | Saute de 10 s (réglable) ; plus loin si la touche est maintenue. |
| **Volume + / − / muet** | Règle le volume du système avec l'indicateur de WiDo ([SETTINGS](SETTINGS.md)). |
| **CH + / CH −** (ou Page +/−) | Fait défiler la page d'un écran. |
| Rouge / vert / jaune / bleu | Raccourcis réglables. Par défaut : menu, recharger, texte +, texte −. |

**En plein écran vidéo** (réglage par défaut) : **◀ ▶** reculent/avancent, **▲ ▼** règlent le volume, **OK** lance/met en pause, **Retour** quitte le plein écran.

Le curseur est une flèche de souris (la pointe est le point de clic) ; il devient bleu pendant l'appui sur OK et se masque après quelques secondes d'inactivité.

## Indicateurs

WiDo n'ajoute pas de barre de lecture par-dessus celle du site : quand tu avances ou recules, seul le saut s'affiche (« +30 s »). Le **temps restant et l'heure de fin** sont sur l'écran de pause (ci-dessous). Tous les messages du lecteur (page épinglée, redirection bloquée, envoyé sur la TV…) s'affichent dans la même pastille, avec une icône : ✓ réussi, ⚠ attention, ⛔ bloqué.


Le volume et le zoom ont leur propre indicateur, qui disparaît seul.

**Écran de pause** : une vidéo en pause depuis 5 secondes assombrit la page et affiche son titre, le site, le temps restant et l'heure de fin. **OK** (ou lecture) relance la vidéo ; toute autre touche retire simplement l'écran.

**Page qui ne s'ouvre pas** : WiDo affiche son propre écran (pas de connexion, site introuvable, site qui ne répond pas) avec **Réessayer**, **Retour** et, pour un site perso dont l'adresse ne répond plus, **Modifier l'adresse**.

Le lecteur est piloté **dans toutes les frames de la page**, y compris les iframes d'un autre domaine où se trouve souvent la vidéo. Ce mécanisme dépend de la version de la WebView de l'appareil ; s'il n'est pas disponible, seules les vidéos de la page principale répondent aux touches.

## Écran de chargement

À l'ouverture d'un site, le logo WiDo s'anime pendant que la page se charge. Les chargements suivants affichent une fine barre de progression en haut de l'écran.

## Aide à l'écran

L'aide des commandes s'affiche à la **première ouverture** d'un site, puis à la demande (menu rapide → *Aide : les commandes*). N'importe quelle touche la ferme.

## Menu rapide

Un plateau en bas de l'écran ; la page reste visible au-dessus. Une rangée de **pastilles** (icône et libellé), les plus utiles d'abord ; les flèches pour choisir, **OK** pour valider, **Retour** pour le fermer.

Sur une **vidéo**, le menu commence par ses commandes : Lecture/Pause, **Suivant** (si la page a un bouton « épisode suivant », WiDo le clique pour toi), **Lecteur** (lecteur natif), **Début** (reprendre au début), Veille. Sur une **page**, il commence par Épingler, A− / A+, Masquer, Accueil du site, Recharger.

- Recharger la page, **Accueil du site**, activer/désactiver le curseur
- **A− / A+** : texte plus petit / plus grand (mémorisé par site)
- **Téléphone** : un QR code s'affiche ; scanne-le avec ton téléphone (même réseau), tape ton texte, il arrive dans le champ sélectionné de la page (ou le premier champ de recherche). « Envoyer + Entrée » valide aussi la recherche.
- **Épingler** la page à l'accueil ([CUSTOM-SITES](CUSTOM-SITES.md))
- **Masquer un élément** : pour une pop-up ou un bandeau qui résiste. Pointe-le avec le curseur (les flèches le déplacent librement), puis **OK** : il disparaît, et restera masqué aux prochaines visites. **Retour** annule. Options du site → « Réafficher les éléments masqués » annule tout.
- **Enregistrer** ce site
- **Veille** : chaque appui passe à 30, 60, 90, 120 min, puis désactivé. À la fin, la vidéo se met en pause, WiDo revient à l'accueil et la TV peut se mettre en veille. Un avertissement s'affiche une minute avant.
- Aide, retour aux **Sites**
- Compteurs : requêtes bloquées et navigations refusées pendant cette session
- Après une redirection refusée, elle s'affiche en tête du plateau avec **Ouvrir une fois**, **Toujours autoriser** et **Ignorer** (voir [BLOCKING](BLOCKING.md))

## Lire dans WiDo (lecteur natif)

Quand WiDo repère dans la page un flux vidéo qu'il sait lire (HLS `.m3u8`, DASH `.mpd`, fichier `.mp4`/`.webm`), il l'annonce (« Vidéo détectée »). Menu rapide → **▶ Lire dans WiDo** (ou une touche de couleur réglée sur « Lire dans WiDo ») : la vidéo s'ouvre dans le lecteur natif d'Android.

- Décodage matériel, avance instantanée, **vitesse** et **sous-titres** dans les réglages du lecteur (roue dentée), sous-titres grands sur fond noir.
- **Menu** : cadrage de l'image (adaptée, zoomée pour remplir l'écran, étirée).
- Reprise à la seconde près, et la vidéo apparaît dans « Reprendre ».
- **Retour** revient à la page.

Limites : les vidéos protégées (DRM) et les flux assemblés par script (YouTube, Netflix…) ne sont pas lisibles ainsi ; WiDo ne les propose pas, ou revient à la page avec un message.

## Reprendre

À l'accueil, la rangée **Reprendre** montre les dernières vidéos regardées, tous sites confondus, avec leur image et leur progression. **OK** rouvre la page et reprend la vidéo là où elle en était ; **appui long** retire la carte. Une vidéo à peine commencée (moins de 10 s) n'y entre pas, une vidéo terminée en sort.

## Télécommande sur téléphone

Réglages → **Téléphone** → active **Télécommande téléphone**, puis scanne le QR code avec ton téléphone (même réseau) et garde la page ouverte (ou ajoute-la à l'écran d'accueil du téléphone).

- **Pavé** : glisse pour déplacer le curseur, touche pour cliquer, bande de droite pour faire défiler. Sur l'accueil de WiDo, le pavé se comporte comme les flèches.
- **Touches** : flèches, OK, retour, menu, lecture/pause, avance/recul, volume.
- **Texte** : écrit dans le champ sélectionné de la page.
- **Lien** : colle une adresse (vidéo, page) : elle s'ouvre sur la TV, dans le site du menu qui la connaît ou dans une fenêtre à part.

Le serveur de la TV n'est ouvert que lorsque WiDo est à l'écran et que le réglage est actif. L'adresse contient un code secret : **Nouveau code** l'oublie, et les téléphones doivent rescanner.

Avec **l'appli WiDo sur le téléphone**, tout cela est intégré (touches, pavé, clavier en direct, volume par les touches du téléphone), plus « Partager → WiDo », **Sur la TV** et **Reprendre sur le téléphone** : voir [PHONE-AND-SCREENS.md](PHONE-AND-SCREENS.md). La page dans le navigateur reste utile pour un téléphone sans l'appli (iPhone).

## Saisie de texte et voix

- Le **clavier de la TV** s'ouvre sur un champ de saisie touché avec le curseur. La dictée du clavier Google peut s'y trouver : **à confirmer sur appareil réel**.
- Le micro de la télécommande dialogue avec l'assistant Google, pas avec la page.
- WiDo refuse que les sites accèdent au micro ou à la caméra.
- Saisie depuis un téléphone : quand un champ de la page est sélectionné (recherche YouTube, identifiant…), un **QR code** s'affiche en haut à droite ; scanne-le, tape, le texte arrive dans le champ. Réglage « QR code sur les champs ». Aussi : menu rapide → **Téléphone**. Le petit serveur de la TV n'est ouvert que pendant l'affichage du QR code, et refuse toute requête sans le jeton aléatoire de ce QR code.
- Saisie depuis Home Assistant : voir la [feuille de route](ROADMAP.md).

## Limites connues

- Le curseur aimanté ne voit que la page principale : un élément dans une iframe d'un autre domaine se atteint au curseur libre (flèches maintenues).
- Les sites qui réagissent au survol (`hover`) ne voient pas le curseur : seul le clic est émulé.
- Le zoom agrandit le texte, pas les images.
