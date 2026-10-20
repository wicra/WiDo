# Sites personnels

Ajoute tes propres sites directement sur la TV, sans toucher à la configuration distante.

## Ajouter un site

1. Dans le menu, choisis la carte **+ Ajouter**.
2. Saisis l'**adresse**. Le champ commence par `https://` ; des boutons t'évitent de taper à la télécommande : `https://`, `www.`, `.com`, `.fr`, `.org`, `.net`, `.tv`, `/` et `⌫` pour effacer.
3. Le **nom se remplit tout seul** à partir de l'adresse (`youtube.com` donne « Youtube »). Tu peux le modifier : il cesse alors de suivre l'adresse.
4. Règle les **protections** (boutons, voir ci-dessous), puis **Enregistrer**.

## Les protections

Chaque bouton s'allume (✓) quand il est actif :

| Bouton | Effet | Par défaut |
|---|---|---|
| **Bloquer les pubs** | Applique les listes de blocage et les règles globales de la configuration. | Activé |
| **Bloquer les pop-ups** | Empêche les pages d'ouvrir des fenêtres. | Activé |
| **Masquer les cookies** | Cache les bandeaux de consentement courants. | Désactivé |
| **Connexions (Google, Microsoft…)** | Autorise les pages de connexion Google, YouTube, Microsoft, Apple et Facebook. | Désactivé |
| **Redirections strictes / souples** | Strictes : le site ne peut aller que sur son propre domaine. Souples : partout, sauf vers les pubs connues des listes. Voir [BLOCKING](BLOCKING.md). | Strictes |

## Domaines autorisés et bloqués : on choisit, on ne saisit pas

Taper un nom de domaine à la télécommande est pénible : WiDo retient donc ce que le site a **réellement essayé de faire** et te le propose en boutons. Ces suggestions apparaissent dans la fenêtre de modification d'un site, **après l'avoir utilisé** (rien ne s'affiche pour un site neuf).

- **Domaines autorisés** : les redirections refusées. Un bouton `+ domaine` l'autorise, un bouton `✓ domaine` le retire.
- **Domaines bloqués** : les domaines tiers que le site contacte (souvent des régies ou des traqueurs). Un bouton `+ domaine` le bloque, `✓ domaine` le débloque.

Les domaines déjà autorisés, déjà bloqués ou propres au site ne sont jamais proposés. Ces informations restent **sur l'appareil**.

## Options d'un site : le bouton « Options »

Quand une carte de **site perso** est sélectionnée, le site s'affiche en grand au-dessus de la rangée avec deux boutons : **Ouvrir** et **Options**. Monte dessus (flèche ▲ depuis la carte) puis appuie sur **OK** : trois boutons s'ouvrent.

- **Ouvrir**
- **Modifier**
- **Supprimer** (un premier appui demande de **Confirmer**)

La touche **Menu** de la télécommande et l'appui long sur OK ouvrent aussi ces options, mais tous les appareils ne les transmettent pas : le bouton **Options** marche partout. Les sites de la configuration distante n'ont pas ce bouton : ils ne sont pas modifiables depuis la TV.

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
