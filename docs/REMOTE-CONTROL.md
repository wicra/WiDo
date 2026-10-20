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
| **Retour** (appui long) ou **Menu** | **Menu rapide**. |
| Lecture/pause, ▶, ❚❚ | Agit sur la vidéo de la page et affiche la **barre de lecture**. |
| Avance / recul rapide | Saute de 10 s (réglable) ; plus loin si la touche est maintenue. |
| **Volume + / − / muet** | Règle le volume du système avec l'indicateur de WiDo ([SETTINGS](SETTINGS.md)). |
| **CH + / CH −** (ou Page +/−) | Fait défiler la page d'un écran. |
| Rouge / vert / jaune / bleu | Raccourcis réglables. Par défaut : menu, recharger, texte +, texte −. |

**En plein écran vidéo** (réglage par défaut) : **◀ ▶** reculent/avancent, **▲ ▼** règlent le volume, **OK** lance/met en pause, **Retour** quitte le plein écran.

Le curseur est une flèche de souris (la pointe est le point de clic) ; il devient bleu pendant l'appui sur OK et se masque après quelques secondes d'inactivité.

## Barre de lecture et indicateurs

En bas de l'écran : état (lecture ou pause), temps écoulé et durée, volume de la vidéo et barre de progression. Elle s'affiche à chaque touche média et disparaît seule. Le volume et le zoom ont leur propre indicateur.

Le lecteur est piloté **dans toutes les frames de la page**, y compris les iframes d'un autre domaine où se trouve souvent la vidéo. Ce mécanisme dépend de la version de la WebView de l'appareil ; s'il n'est pas disponible, seules les vidéos de la page principale répondent aux touches.

## Écran de chargement

À l'ouverture d'un site, le logo WiDo s'anime pendant que la page se charge. Les chargements suivants affichent une fine barre de progression en haut de l'écran.

## Aide à l'écran

L'aide des commandes s'affiche à la **première ouverture** d'un site, puis à la demande (menu rapide → *Aide : les commandes*). N'importe quelle touche la ferme.

## Menu rapide

Un plateau en bas de l'écran ; la page reste visible au-dessus. ◀ ▶ pour choisir, **OK** pour valider, **Retour** pour le fermer.

- La barre de lecture, si une vidéo a été pilotée sur la page
- Reprendre, recharger la page, activer/désactiver le curseur
- **A− / A+** : texte plus petit / plus grand (mémorisé par site)
- **Enregistrer** ce site, Aide, retour aux **Sites**
- Compteurs : requêtes bloquées et navigations refusées pendant cette session
- Après une redirection refusée, elle s'affiche en tête du plateau avec **Ouvrir une fois**, **Toujours autoriser** et **Ignorer** (voir [BLOCKING](BLOCKING.md))

## Saisie de texte et voix

- Le **clavier de la TV** s'ouvre sur un champ de saisie touché avec le curseur. La dictée du clavier Google peut s'y trouver : **à confirmer sur appareil réel**.
- Le micro de la télécommande dialogue avec l'assistant Google, pas avec la page.
- WiDo refuse que les sites accèdent au micro ou à la caméra.
- Saisie depuis un téléphone ou Home Assistant : voir la [feuille de route](ROADMAP.md).

## Limites connues

- Le curseur aimanté ne voit que la page principale : un élément dans une iframe d'un autre domaine se atteint au curseur libre (flèches maintenues).
- Les sites qui réagissent au survol (`hover`) ne voient pas le curseur : seul le clic est émulé.
- Le zoom agrandit le texte, pas les images.
