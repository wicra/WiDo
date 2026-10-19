# Utiliser la télécommande

## Menu des sites

Flèches pour se déplacer, **OK** pour ouvrir. La première carte a le focus à l'arrivée. *Actualiser* recharge la configuration distante.

Dernière carte **« + Ajouter un site »** : voir [CUSTOM-SITES](CUSTOM-SITES.md). Sur une carte PERSO, un appui long sur OK (ou la touche Menu) ouvre la modification.

Le fond du menu prend la couleur de la carte sélectionnée. Chaque carte affiche l'icône de son site.

## Pendant la lecture

| Touche | Effet |
|---|---|
| Flèches | Déplacent le **curseur** (vitesse croissante si maintenu). Au bord de l'écran, la page défile. |
| **OK** | Clic à la position du curseur. Sur un champ de saisie, le clavier de la TV s'ouvre. |
| **Retour** | Plein écran vidéo → quitte le plein écran ; sinon page précédente ; sinon retour au menu. |
| **Retour** (appui long) ou **Menu** | Ouvre le **menu rapide**. |
| Lecture/pause, ▶, ❚❚ | Agit sur la plus grande vidéo de la page. |
| Avance / recul rapide | ±10 secondes sur cette vidéo. |

Le curseur se masque après 3 secondes d'inactivité et réapparaît au moindre mouvement.

## Menu rapide

- **Reprendre**
- **Recharger la page**
- **Curseur activé/désactivé** : désactivé, les flèches font naviguer au focus (utile pour les sites conçus pour la TV).
- **Retour au menu des sites**
- Compteurs : requêtes bloquées et navigations refusées pendant cette session.

## Saisie de texte et voix

- Le **clavier de la TV** s'ouvre automatiquement sur un champ de saisie touché avec le curseur. Le micro du clavier Google (dictée) peut s'y trouver : **à confirmer sur appareil réel**.
- Le micro de la télécommande dialogue avec l'assistant Google, pas avec la page.
- WiDo refuse volontairement que les sites accèdent au micro ou à la caméra.
- Saisie depuis un téléphone ou Home Assistant : prévu dans la [feuille de route](ROADMAP.md).

## Limites connues

- La touche *lecture/pause* n'atteint pas une vidéo dans une iframe d'un autre domaine (restriction du navigateur).
- Les sites qui réagissent au survol (`hover`) ne voient pas le curseur : seul le clic est émulé.
