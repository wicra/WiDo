# Sécurité

## Modèle de menace

| Menace | Mesure |
|---|---|
| Le dépôt de configuration est compromis | La config ne peut pas apporter de JavaScript (scripts embarqués seulement) ; sélecteurs CSS filtrés ; au pire, elle change la liste des sites et des règles. |
| Config distante altérée en transit | `https` obligatoire (config, listes, mises à jour). |
| APK de mise à jour falsifiée | Origine limitée aux releases de ce dépôt ; SHA-256 obligatoire ; signature Android identique exigée ; confirmation à l'écran. |
| Site malveillant ou publicité | Politique de navigation, blocage par hôte, pas de nouvelles fenêtres, pas de micro/caméra/géolocalisation, accès fichiers désactivé, contenu mixte interdit. |
| Redirection vers une appli ou un store | Les schémas `intent:`, `market:`, `javascript:` et tout non-web sont refusés. |
| Lancement d'un site par une autre app | `PlayerActivity` est exportée pour Home Assistant, mais n'ouvre que des sites **déjà présents dans ta configuration**. |
| Fichier ou liste énorme | Tailles bornées (config 1 Mo, liste 8 Mo, APK 100 Mo, 100 sites, 5000 règles). |

## Scripts internes et passerelle native

WiDo injecte dans les pages (et leurs iframes) un **script interne** embarqué dans l'APK (`assets/internal/`) pour le curseur aimanté et le lecteur vidéo. Règles :

- il ne vient **jamais** de la configuration ni du réseau ;
- il ne lit ni ne transmet le contenu de la page : il répond seulement à des commandes d'app (`toggle`, `seek`, `vol`, `state`) et renvoie l'état de la vidéo (pause, temps, durée, volume) ;
- la passerelle native (`WiDoBridge`) n'expose **aucune** action sensible : un site malveillant qui l'appelle ne peut qu'envoyer un message à l'app, qui n'en traite que le type `media` ;
- elle est installée avec des règles d'origine `*` pour atteindre les lecteurs dans des iframes d'autres domaines : c'est un choix assumé, limité à ce canal.

## Écrans liés (téléphone, autres TV)

- Le serveur local de la TV n'est ouvert que lorsque WiDo est à l'écran ; il est désactivable (Réglages → Écrans liés).
- Sans jeton, il ne répond qu'à `/hello` (nom et identifiant de la TV) et `/pair` (demande de liaison). Une demande n'est acceptée que **validée à l'écran de la TV**, ou si elle porte la clé du QR code d'appairage, qui change après chaque usage.
- Chaque appareil lié reçoit un jeton aléatoire de 128 bits, comparé en temps constant ; « Oublier » le révoque.
- `/share` (sites proposés par un appareil lié, même Invité) n'ajoute **rien** sans confirmation à l'écran de la TV ; les adresses sont filtrées (http/https valides, 30 sites au plus, listes bornées) et le nom affiché est celui que la TV connaît pour cet appareil, pas celui qu'il annonce. Aucun compte ni cookie n'est transmis.
- Le rapport de plantage reste sur l'appareil ; il ne quitte la TV que si l'utilisateur scanne son QR code.
- La synchronisation n'accepte que des clés connues (Reprendre, sites perso, épingles, masquages) et exige le niveau « Mon téléphone » côté TV. Cookies, connexions et réglages ne sont jamais transmis.
- Les TV d'un même groupe publient une empreinte (SHA-256 tronqué) de la clé du groupe, jamais la clé ; elles s'authentifient avec un jeton dérivé.
- Le trafic local est en `http` : sur un réseau partagé non fiable, désactive les écrans liés.

## Ce que WiDo ne protège pas

- Le contenu des sites que tu configures : leur fiabilité reste ta responsabilité.
- Un appareil dont ADB est exposé sur un réseau non fiable : désactive le débogage réseau quand tu n'en as pas besoin.
- `allowInsecure` : le trafic `http` d'un site local est lisible sur le réseau.

## Règles pour contribuer

1. Aucun secret dans le dépôt (jetons, mots de passe, clés de signature).
2. Aucun JavaScript chargé depuis la configuration ou le réseau.
3. Toute nouvelle entrée externe (fichier, URL, intent) est **validée et bornée**.
4. Toute URL de téléchargement est en `https`.
5. Pas de nouvelle permission Android sans la justifier dans cette page.

Permissions actuelles : `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` (mise à jour). `usesCleartextTraffic=true` est nécessaire pour `allowInsecure` ; la politique de navigation refuse `http` partout ailleurs.

## Signaler une vulnérabilité

Ouvre un [signalement privé](https://github.com/wicra/WiDo/security/advisories/new) sur le dépôt plutôt qu'une issue publique.
