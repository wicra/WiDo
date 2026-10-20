# Changelog

## 0.7.0

### 🚫 Plus de pub, sur tous les sites
- **Règles d'adresse** EasyList/uBlock : chemins, jokers, `$domain=`, tiers, types, exceptions. Indexées : environ 0,05 ms par requête.
- **Scriptlets uBlock** (18 parmi les plus utilisés) contre les pubs et les « désactivez votre bloqueur », lancés avant les scripts de la page.
- Listes **uBlock Origin** (filtres, correctifs rapides, exceptions) ajoutées aux listes recommandées, avec leurs fichiers inclus et directives `!#if` : les corrections arrivent toutes seules.

### 🎬 Lire dans WiDo
- Les flux vidéo d'une page (HLS, DASH, MP4) s'ouvrent dans le **lecteur natif** : fluidité, vitesse, grands sous-titres, cadrage, reprise exacte.

### ▶️ Reprendre
- Rangée **Reprendre** à l'accueil : les vidéos en cours avec image et progression, reprises à la seconde près.

### 📱 Le téléphone comme télécommande
- **Pavé tactile, touches, texte et liens** depuis le téléphone, appairé une fois par QR code.

## 0.6.1

### Corrigé
- **Redirections souples** : elles acceptent de nouveau toute page d'un domaine non bloqué (la 0.6.0 exigeait un clic et refusait des redirections légitimes).

### Amélioré
- **Formulaire de site** : les champs se sélectionnent sans s'ouvrir, OK entre dans la saisie ; Entrée ne ferme plus le formulaire ; Retour enregistre (un second Retour quitte si le formulaire est invalide).
- **Saisie au téléphone partout** : bouton téléphone dans chaque champ de l'app, et QR code automatique quand un champ d'une page est sélectionné (YouTube, connexions…).
- **Masquer un élément** (menu rapide) : la pop-up ou le bandeau pointé au curseur disparaît et le reste sur ce site.
- Protection **Fermer les calques** dans le formulaire des sites.
- Home Assistant : `--es site last` rouvre le dernier site.

## 0.6.0

### Anti-pub
- **YouTube sans pub** : pubs retirées des données du lecteur, coupées et sautées si elles passent, encarts masqués. **SponsorBlock** saute les passages sponsorisés.
- **Listes recommandées** (EasyList, Liste FR, filtre DNS d'AdGuard), en plus de celles de la configuration ; compilées sur disque et chargées dès l'accueil.
- **Masquage cosmétique** : les règles `##` des listes cachent les emplacements publicitaires.
- Règles à options (`$third-party`, `$script`…) désormais comprises.
- **Pop-ups mieux bloquées** : script injecté avant ceux de la page et dans toutes les frames, `window.open` verrouillé, formulaires et clics simulés couverts.
- Redirections **souples** : un autre domaine n'est accepté qu'après un clic ou une redirection du serveur.
- Nouveau mode de redirection **mixte** : la pub s'ouvre dans une fenêtre affichée 5 s puis fermée, pour les sites qui exigent de la voir.

### Simplicité
- **Catalogue** de sites prêts à ajouter en un clic.
- **Saisie depuis le téléphone** par QR code (adresse d'un site, recherche dans une page).
- **Reprise** de la dernière page de chaque site, position YouTube comprise.
- **Pages épinglées** à l'accueil ; **ordre des cartes** modifiable (Options → Déplacer).
- **Minuteur de veille** dans le menu rapide.

### Fluidité
- Release optimisée par **R8**, profils de démarrage installés (**profileinstaller**).
- **WebView préparée** pendant l'accueil : les sites s'ouvrent plus vite.
- Couleurs des sites gardées sur disque : l'accueil s'affiche directement en couleur.

### Divers
- **Nouvelle icône et bannière** : le logo « WiDo » du style Verre.

## 0.5.0

### Nouveau style « Verre »
- Police **Plus Jakarta Sans** partout, logo en texte « WiDo » extra-gras (accueil et écran de chargement).
- Fond presque noir éclairé par des **halos de la couleur du site** sélectionné ; panneaux en verre translucide.
- Focus franc : la pastille ou la ligne focalisée devient **blanche pleine**, la carte focalisée passe à la **couleur du site** et se soulève.
- **Accueil** : le site sélectionné en grand titre avec **Ouvrir** et **Options**, au-dessus d'une rangée de cartes. Le bouton **Options** remplace la bande « ••• ».
- **Onglets Sites / Réglages** en haut ; les réglages deviennent une liste défilante dans l'onglet, avec la couleur de chaque touche.
- **Formulaire de site** plein écran, champs en verre.
- **Menu rapide** en plateau au bas de l'écran (barre de lecture, pastilles) ; une redirection refusée y prend la tête avec Ouvrir une fois / Toujours autoriser / Ignorer.
- Barre de lecture, aide et écran de chargement au même style.
- L'état de la configuration (en haut à droite) remplace le bouton **Actualiser** : OK dessus actualise.

## 0.4.1

### Corrigé
- Les options d'un site perso ne dépendent plus de l'appui long (que certaines télécommandes ne transmettent pas) : une bande **« ••• »** visible en bas de la carte les ouvre. Elle est séparée du corps de la carte, donc atteignable au focus et sans superposition.

### Amélioré
- **Écran de Réglages refait** : réglages rangés par thème (Curseur, Lecture, Touches de couleur, Général), choix toujours visibles sous forme de boutons, plus de valeur cachée derrière une flèche.

## 0.4.0

### Refonte de l'interface
- Menu repensé et épuré : barre du haut courte, grille de cartes aérée, ligne d'état discrète en bas. Chaque zone a sa place : plus rien ne se superpose.
- Cartes simplifiées (icône, nom, adresse), sans décor superflu. Textes raccourcis partout.
- Fenêtres au cadre commun : titre en haut, contenu qui défile, **boutons toujours visibles** en bas.

### Sites perso plus simples à gérer
- **Suppression** visible : appui long sur une carte perso → Ouvrir / Modifier / Supprimer.
- Formulaire guidé : on commence par l'**adresse**, aidée de boutons (`https://`, `www.`, `.com`, `.fr`…), le **nom s'en déduit** et reste modifiable.
- Protections en **boutons** : bloquer les pubs, bloquer les pop-ups, masquer les cookies, autoriser les connexions, redirections strictes ou souples.
- **Domaines autorisés et bloqués proposés en boutons** d'après ce que le site a réellement tenté de faire, au lieu de les saisir.
- Champ `blockAds` (par site) dans la configuration.

## 0.3.1

### Corrigé
- Mise à jour : l'état de la vérification est maintenant **visible** dans le pied de page (« À jour », « Recherche… », ou le motif de l'échec). Avant, tout échec était silencieux.
- La vérification est relancée par **Actualiser** et au retour dans l'app après plus de 5 minutes, et non plus seulement au tout premier affichage du menu.

## 0.3.0

### Ajouté
- **Curseur aimanté** : un appui bref sur une flèche saute au lien ou bouton le plus naturel dans cette direction (contour bleu) ; maintenue, la flèche déplace librement. Désactivable.
- **Barre de lecture** à l'écran (état, temps, durée, volume, progression), qui pilote la vidéo même dans une iframe d'un autre domaine quand la WebView le permet.
- **Volume personnalisé** : les touches de volume règlent le volume du système avec un indicateur WiDo.
- **Zoom du texte**, mémorisé par site (touches de couleur ou menu rapide).
- **Touches de couleur** configurables, CH +/− pour faire défiler, flèches du plein écran vidéo (avance/recul, volume, lecture/pause).
- **Aide à l'écran** des commandes (première ouverture, puis menu rapide).
- **Écran de Réglages** : vitesse, taille et masquage du curseur, zoom par défaut, saut de lecture, durée des indicateurs, volume, touches, démarrage sur le dernier site, redirections des nouveaux sites.

## 0.2.0

### Ajouté
- **Redirections** : mode `strict` ou `relaxed` par site (champ `navigation`). `relaxed` laisse passer connexions et redirections utiles (YouTube) tout en refusant les domaines des listes de blocage.
- Sites perso : domaines autorisés, domaines à bloquer et mode de navigation réglables dans le formulaire.
- Menu rapide : **Autoriser le domaine refusé**, **Ouvrir une seule fois**, **Enregistrer cette page comme site perso**. Les domaines autorisés sur un site de la config distante sont gardés localement.
- **Écran de chargement** animé (logo) à l'ouverture d'un site, et barre de progression pour les chargements suivants, à la place de l'écran noir.
- **Curseur de souris** en forme de flèche, qui devient bleu pendant le clic.
- **Nouvelle identité visuelle** tirée de l'icône : icône du lanceur, bannière Android TV, logo dans le menu, palette bleu nuit / bleu électrique.

### Amélioré
- Blocage plus rapide : les listes ne sont plus recopiées à chaque ouverture de site, et les décisions sont mises en cache par hôte.

## 0.1.1

### Ajouté
- Sites personnels ajoutés depuis la TV (carte « + Ajouter un site »), conservés lors de l'actualisation et des mises à jour.
- Icône (favicon) du site sur chaque carte, téléchargée directement depuis le site puis mise en cache. Champ `icon` pour la forcer.
- Couleur des cartes tirée automatiquement de l'icône du site (le champ `color` devient optionnel).
- Menu retravaillé façon salle de cinéma : ambiance colorée suivant la carte sélectionnée, halo de focus, perforations de pellicule, apparition progressive des cartes.

## 0.1.0

### Ajouté
- Module `core` : configuration, blocage par hôte (AdBlock/hosts), politique de navigation, versions, releases GitHub, avec tests.
- App Android TV : menu Compose, lecteur WebView avec curseur et menu rapide, scripts intégrés, configuration distante, mise à jour depuis les releases GitHub (SHA-256).
- CI : tests + APK debug, release signée.
- Documentation complète dans `docs/`, dont le guide d'installation Android TV et la check-list de test.
- Configuration livrée avec trois sites de test légaux et la liste de blocage publique StevenBlack/hosts.
