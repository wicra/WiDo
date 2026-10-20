# Sites personnels

Ajoute tes propres sites directement sur la TV, sans toucher à la configuration distante.

## Ajouter un site

1. Dans le menu, choisis la carte **+** au bout de la rangée.
2. Le **catalogue** s'ouvre : YouTube, Arte, france.tv, TF1+, M6+, Twitch, Dailymotion, Pluto TV, Vimeo, Radio France, Plex… Un clic l'ajoute avec des réglages adaptés (redirections souples, pop-ups bloquées, bandeaux de cookies masqués), modifiables ensuite. Les sites déjà présents n'y figurent pas.
3. Pour un autre site, choisis **Autre adresse…** et saisis l'**adresse**. Le champ commence par `https://` ; des boutons t'évitent de taper à la télécommande : `https://`, `www.`, `.com`, `.fr`, `.org`, `.net`, `.tv`, `/` et `⌫` pour effacer. Le bouton **Téléphone** affiche un QR code : scanne-le avec ton téléphone (même réseau que la TV), tape l'adresse, elle arrive dans le champ.
4. Le **nom se remplit tout seul** à partir de l'adresse (`youtube.com` donne « Youtube »). Tu peux le modifier : il cesse alors de suivre l'adresse.
5. Règle les **protections** (boutons, voir ci-dessous), puis **Enregistrer**.

## Les protections

Chaque bouton s'allume (✓) quand il est actif :

| Bouton | Effet | Par défaut |
|---|---|---|
| **Bloquer les pubs** | Applique les listes de blocage et les règles globales de la configuration. | Activé |
| **Bloquer les pop-ups** | Empêche les pages d'ouvrir des fenêtres. | Activé |
| **Masquer les cookies** | Cache les bandeaux de consentement courants. | Désactivé |
| **Connexions (Google, Microsoft…)** | Autorise les pages de connexion Google, YouTube, Microsoft, Apple et Facebook. | Désactivé |
| **Redirections strictes / souples / mixtes** | Chaque appui passe au mode suivant. Strictes : le site ne peut aller que sur son propre domaine. Souples : ailleurs aussi après un clic, sauf vers les pubs connues des listes. Mixtes : la pub s'ouvre dans une fenêtre fermée seule une fois affichée. Voir [BLOCKING](BLOCKING.md). | Strictes |

## Domaines autorisés et bloqués : on choisit, on ne saisit pas

Taper un nom de domaine à la télécommande est pénible : WiDo retient donc ce que le site a **réellement essayé de faire** et te le propose en boutons. Ces suggestions apparaissent dans la fenêtre de modification d'un site, **après l'avoir utilisé** (rien ne s'affiche pour un site neuf).

- **Domaines autorisés** : les redirections refusées. Un bouton `+ domaine` l'autorise, un bouton `✓ domaine` le retire.
- **Domaines bloqués** : les domaines tiers que le site contacte (souvent des régies ou des traqueurs). Un bouton `+ domaine` le bloque, `✓ domaine` le débloque.

Les domaines déjà autorisés, déjà bloqués ou propres au site ne sont jamais proposés. Ces informations restent **sur l'appareil**.

## Options d'un site : le bouton « Options »

Quand une carte est sélectionnée, le site s'affiche en grand au-dessus de la rangée avec deux boutons : **Ouvrir** et **Options**. Monte dessus (flèche ▲ depuis la carte) puis appuie sur **OK**. Pour tous les sites :

- **Ouvrir**
- **Déplacer** : la carte s'entoure de blanc, ◀ ▶ la déplacent dans la rangée, **OK** ou **Retour** terminent. L'ordre est gardé.
- les **pages épinglées** du site : OK sur l'une la retire.

Pour un site perso, en plus :

- **Ouvrir**
- **Modifier**
- **Supprimer** (un premier appui demande de **Confirmer**)

La touche **Menu** de la télécommande et l'appui long sur OK ouvrent aussi ces options, mais tous les appareils ne les transmettent pas : le bouton **Options** marche partout. Les sites de la configuration distante ne sont pas modifiables depuis la TV (ni Modifier, ni Supprimer).

## Pages épinglées

Pendant la lecture, menu rapide → **Épingler** : la page affichée (une chaîne, une playlist, une émission) apparaît sous les boutons du site, à l'accueil, et s'ouvre directement. 12 pages au plus par site.

## Enregistrer un site depuis la lecture

Pendant la lecture, appui long sur Retour → **Enregistrer** : l'adresse racine de la page affichée est ajoutée au menu.

## Ce qui est conservé, et quand

Les sites perso sont dans un fichier **local** (`local-sites.json`), séparé de la configuration distante.

| Événement | Sites perso |
|---|---|
| Actualiser / nouvelle configuration distante | **Conservés** |
| Mise à jour depuis l'app, ou `adb install -r` | **Conservés** |
| Désinstaller puis réinstaller, ou « Effacer les données » | **Perdus** |

Si la configuration distante contient un site avec le même identifiant qu'un site perso, c'est celui de la configuration qui s'affiche. Les identifiants perso commencent par `perso-`. Limites : 50 sites perso, 40 caractères par nom. Pas encore d'export : voir [ROADMAP](ROADMAP.md).

## Réglages plus fins

Masquage CSS, agent utilisateur et scripts autres que les deux boutons ci-dessus ne sont disponibles que pour les sites de la [configuration distante](CONFIGURATION.md).
