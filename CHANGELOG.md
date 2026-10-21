# Changelog

## 0.9.1

### Corrigé
- **Clés TV d'1 Go : WiDo fermé pendant un film, même avec des listes plus légères** (le rapport montrait 238 Mo, quasi inchangé : ce sont la page du site et son lecteur qui pèsent). Pendant la lecture dans « Lire dans WiDo », la page restait chargée derrière : elle est maintenant **vidée pendant le film** sur ces appareils et rechargée à la bonne seconde au retour. Nouveau réglage **Lecteur léger automatique** (Réglages → Lecture, activé d'office sur les appareils à peu de mémoire) : dès qu'un film de plus de 10 minutes est détecté, WiDo passe seul au lecteur intégré (une seule tentative par page, jamais pour une pub).
- **Clés TV à peu de mémoire : moins de mémoire pour les listes de blocage.** Les noms de domaine bloqués sont gardés sous forme compacte (environ dix fois moins de place) et les listes sont relues ligne à ligne au lieu d'être chargées d'un bloc ; les listes inutilisées sont libérées. Nouveau réglage **Taille des listes** (Réglages → Blocage) : **Légères** (filtre DNS d'AdGuard et correctifs d'uBlock, sans EasyList, liste française ni filtres d'uBlock : moins de masquage de bandeaux, mêmes domaines de pubs bloqués) ou **Complètes** ; activé d'office sur les appareils à peu de mémoire.
- **Reprendre restait sur l'épisode précédent.** La position était parfois enregistrée sous la mauvaise page au moment de passer à l'épisode suivant, et un épisode servi par un autre domaine que celui du site n'était pas enregistré du tout. Désormais la position est notée sous la page où la vidéo jouait, juste avant de la quitter, et ces épisodes entrent aussi dans Reprendre.
- **Clés TV à 1 Go de mémoire : WiDo fermé par Android pendant un film.** Sur ces appareils, WiDo ne prépare plus de seconde page en avance, et libère ses images en mémoire et la page préparée dès qu'Android manque de mémoire (au lieu d'attendre le dernier moment). Quand un flux vidéo est repéré, WiDo conseille « Lire dans WiDo », bien plus léger que la page du site.
- **Retour dans WiDo après qu'Android l'a fermé en arrière-plan** (mémoire du téléphone) : le lecteur revient sur la page et à la position du moment, et non plus à celles de l'ouverture ; le message « arrêtée brusquement » ne s'affiche plus que si Android signale un vrai plantage (Android 11 et plus). Le rapport de plantage nomme toutes les raisons de fermeture (mise à jour, mémoire…) et n'affiche plus le processus annexe des pages quand il s'arrête normalement.
- **Reprendre : la vidéo revenait à la bonne minute puis repartait du début.** Beaucoup de lecteurs se réinitialisent juste après le saut (chargement du flux, plein écran, fin d'une pub d'avant-film). Pendant 20 s après la reprise, WiDo vérifie la position et la remet si la vidéo est repartie du début ; il attend aussi la fin d'une pub plus courte que la position avant de sauter. Avancer ou reculer soi-même arrête cette vérification.
- **En pause en plein écran, les flèches montaient le volume** au lieu de permettre d'atteindre les réglages du lecteur du site. En pause, les flèches font maintenant apparaître le curseur (OK clique : sous-titres, qualité, épisodes…) ; tant que le curseur n'est pas sorti, OK relance la vidéo. À la reprise de la lecture, les flèches pilotent à nouveau le lecteur.
- **L'appli se fermait pendant une vidéo, puis Reprendre ramenait en boucle à l'accueil.** Quand le moteur de la page s'arrête (le lecteur du site plante, ou la TV manque de mémoire), Android fermait toute l'appli ; en rouvrant la même vidéo, sa lecture automatique relançait le même plantage. Désormais WiDo reste ouvert et affiche « La page s'est arrêtée » avec Réessayer (même seconde) et Retour. Une vidéo qui a fait tomber la session précédente se rouvre sans lecture automatique : OK pour la lancer. Les captures d'image pour Reprendre sont aussi plus espacées (moins de mémoire utilisée).
- **L'appli téléphone pouvait se fermer** en revenant dessus (notification de la TV ou lecture en arrière-plan arrêtée trop tôt, par exemple après la demande d'autorisation des notifications).
- **Reprendre retombait sur l'accueil du site** quand le site ne couvrait plus l'adresse de la vidéo : la vidéo s'ouvre maintenant avec le site qui la connaît.
- **Retour ne quitte plus WiDo** après une vidéo envoyée depuis le téléphone : il ramène à l'accueil.
- **Accueil TV** : le titre n'est plus coupé sous l'en-tête en descendant vers les sites (la scène et les rangées tiennent dans l'écran).

### Nouveau
- **Envoyer des sites à une TV** (option pour préparer la TV d'un proche) : depuis l'onglet TV du téléphone, coche des sites ou un **pack enregistré** (« Mamie »…) ; la TV demande **toujours** confirmation à l'écran, site par site. Seuls l'adresse et les réglages de blocage voyagent, jamais les comptes. Fonctionne aussi avec une TV liée en Invité.
- **Rapport de plantage** (Réglages → Général) : la dernière erreur de l'appli, **les fermetures décidées par Android** (mémoire insuffisante, plantage natif du moteur vidéo, appli bloquée… lues au démarrage suivant, Android 11 et plus, avec la mémoire utilisée) et le fil des derniers événements du lecteur (ouvert, fermé et pourquoi, page arrêtée, mémoire faible). Gardé sur l'appareil. Sur TV, un QR code permet de le récupérer avec le téléphone ; un message le signale au démarrage après une fermeture sur erreur.
- **Accessibilité** : les boutons qui n'affichent qu'une icône (télécommande, lecture, actualiser…), les disques des sites et les vignettes de Reprendre ont un nom lu par le lecteur d'écran.
- **Onglet Reprendre (TV)** à côté de Sites et Réglages, sur toute la page : grandes vignettes avec le titre en clair, le site et le temps restant, l'image de la vidéo sélectionnée floutée en fond. Plusieurs vidéos d'un même site ont chacune leur vignette. OK maintenu ou Menu : Reprendre, Retirer, Ouvrir le site.
- **Les sites en vinyles** : chaque site est un disque net (sillons fins, anneau à sa couleur, logo sur l'étiquette blanche). Le disque choisi sort du bac, grandit et tourne ; ses voisins s'inclinent en vague, avec des mouvements souples même quand on enchaîne les flèches.
- **Ouverture d'un site en un seul geste**, lente et douce : l'accueil reste à l'écran, le vinyle grandit en tournant et glisse vers le centre, puis se fond dans l'écran d'attente au logo WiDo.
- **Sons de l'interface (TV)**, feutrés et doux : « tock » boisé au passage, goutte ronde à la validation, claquement de doigts adouci avec écho à l'ouverture d'un site, deux notes descendantes au retour. Réglages → Apparence : Désactivé, Doux ou Normal.
- **Retour deux fois vite** dans une page : retour direct aux sites. Retour depuis Reprendre ou Réglages ramène aussi aux sites.
- **Écran de pause (TV)** : après environ 1 s en pause, la page s'assombrit avec le titre, le site, le temps restant et l'heure de fin. OK relance la vidéo, n'importe quelle autre touche retire l'écran.
- **Fond d'ambiance** : à l'accueil TV, la vidéo de Reprendre sélectionnée s'affiche floutée en fond.
- **Page d'erreur WiDo** : à la place de la page grise du navigateur, un écran clair : pas de connexion, site introuvable, site qui ne répond pas… avec **Réessayer**, **Retour** et, pour un site perso introuvable, **Modifier l'adresse**.
- **Ambiances de couleur** : Bleu, Violet, Ambre ou Noir (Réglages → Apparence).
- **Compteur de pubs** : pubs et pisteurs bloqués ce mois-ci, au total (accueil, réglages) et par site (appui long sur la carte).
- **Télécommande par gestes** (téléphone, onglet TV) : glisser = flèches, toucher = OK, appui long = Retour.

### Amélioré
- **Petites TV** : la capture de l'accueil à l'ouverture d'un site est faite par le GPU (plus d'à-coup au clic), l'animation d'ouverture ne crée plus d'objets à chaque image, et les anciens fichiers en cache (icônes floues, sons d'une version précédente) sont supprimés.
- **OK maintenu fonctionne partout à la télécommande** (options d'un site, actions d'une vidéo de Reprendre).
- **Déplacer un site** : le focus reste sur le disque à déplacer.
- **Menu rapide** : Texte + / Texte − et Veille ne ferment plus le menu, on peut ajuster plusieurs fois de suite.
- **Écran de pause** plus rapide (après environ 1 s).
- Le **+ d'Ajouter** est centré dans son cercle.
- **Un site s'ouvre à neuf** sur son accueil (réglage « Ouvrir un site » : Accueil du site par défaut) ; les vidéos commencées se reprennent depuis l'onglet Reprendre.
- **Logos des sites plus nets** : la plus grande icône proposée par le site est choisie, et une petite icône est agrandie sans flou.
- **Plus de barre de lecture de WiDo** par-dessus celle du site : seul le saut s'affiche (« +30 s »). Le temps restant et l'heure de fin sont sur l'écran de pause.
- **Réglages rangés par thème** : sur la TV, une colonne de thèmes à gauche et leurs réglages à droite.
- **Téléphone : après « Sur la TV », l'onglet TV s'ouvre** (depuis le lecteur, Reprendre, un appui long ou le partage) : la vidéo se pilote tout de suite.
- **Sur la TV et Reprendre : lecture et plein écran automatiques.** La vidéo démarre seule ; le plein écran du site est ouvert pour de vrai, sinon la vidéo remplit l'écran (Retour pour revoir la page). Réglage « Plein écran automatique ».
- **Menu rapide** : retour à une rangée de pastilles, plus petites, avec une icône et un libellé clair, toutes visibles (plus de « Plus… »).
- **Ouverture** : le cercle du site s'agrandit en douceur jusqu'à remplir l'écran, puis l'écran d'attente garde le fond de l'appli avec le logo du site.

## 0.9.0

### 📺 TV
- **Les sites en cercles** : le logo dans un cercle de verre qui prend la couleur du site au passage, avec la même arrivée en vague. Le nom et l'adresse restent dans la scène au-dessus.
- **Reprendre en un clic** : à l'ouverture, le focus est sur la dernière vidéo en cours ; la scène montre son titre, son site et le temps restant. OK, et le film repart.
- **Menu rapide en tuiles** : grosses icônes, les commandes de la vidéo d'abord (pause, **épisode suivant** si la page en a un, lecteur natif, reprendre au début, veille), le reste sous « Plus… ».
- **Barre de lecture riche** : image et titre de la vidéo, site, temps restant et **heure de fin**, saut affiché (« +30 s »).
- **Messages dans le style WiDo** : plus de bulles grises d'Android dans le lecteur ; une pastille de verre avec icône (réussi, attention, bloqué).
- **Taille de l'interface** : Normale, Grande, Très grande (projecteur regardé de loin).
- **Présentation au premier lancement** : choisir ses sites, lier son téléphone, les quatre gestes de la télécommande. À revoir depuis les réglages.
- **Ouverture animée** : la page s'agrandit depuis le cercle ou la vignette choisie, sur la couleur et le logo du site ; elle se rétracte au retour.
- **Redirection mixte en arrière-plan** : la pub se charge derrière la page, sans son, 5 secondes, puis se ferme ; l'écran et la télécommande restent sur le site.

### 📱 Téléphone
- **Mini-lecteur** au-dessus des onglets quand la TV lit une vidéo : image, titre, pause, et un toucher pour la télécommande.
- **Notification de la TV** : ⏪ ⏯ ⏩ depuis la notification et l'écran verrouillé, et les touches de volume du téléphone règlent la TV même téléphone verrouillé.
- **Lecture en arrière-plan** : le son continue quand on quitte l'appli ou éteint l'écran, pilotable depuis la notification et les écouteurs.
- **Gestes** : glisser une vidéo pour la retirer (avec « Annuler »), tirer pour actualiser, appui long sur une vidéo ou une épingle (Sur la TV, ouvrir le site, retirer).
- **Icônes dessinées partout** et **mise en page tablette** (onglets sur le côté, la TV à droite de l'accueil).

### 🔧 Sous le capot
- **Synchro incrémentale** : seules les modifications voyagent entre les écrans ; une appli réinstallée reçoit tout à nouveau.
- **Serveur de la TV en parallèle** : une synchro ne retarde plus jamais une touche de la télécommande.
- **Tests de bout en bout du lien** (liaison, statut, Sur la TV, Reprendre sur le téléphone, télécommande, synchro, groupe de TV) sur de vrais serveurs locaux ; **tests Android** du stockage ; **captures d'écran** téléphone et TV publiées par la CI ; **analyse Android (lint)** à chaque envoi.

### Corrigé
- **TV** : les boutons Ouvrir / Options ne perdent plus leur texte quand l'accueil manque de place (il défile au besoin).

## 0.8.1

### 🖼️ Reprendre en images
- Chaque vidéo en cours montre **l'image de la vidéo là où tu l'as quittée** (capturée pendant la lecture, dans la page comme dans le lecteur natif), sinon l'image de la page ; le **logo du site** est en petit badge.
- Sans image (vidéo protégée, page sans illustration) : la **couleur et le logo du site**.

### Corrigé
- **Une seule entrée par vidéo dans Reprendre** : `&t=…`, `?si=…`, `m.youtube.com`, `youtu.be` ou `/shorts/` ne créent plus de doublons (les anciens sont fusionnés).
- **Lire dans WiDo ↔ page** : le lecteur natif démarre à la seconde où en était la page, et la page reprend là où le lecteur natif s'est arrêté.
- **Téléphone** : barre de lecture et messages du lecteur à la largeur de l'écran ; icônes (actualiser, lecture, télécommande, menu ⋯) dessinées et centrées.

## 0.8.0

### 📱 WiDo sur téléphone
- **La même appli, en version tactile** : accueil en grille, Reprendre, épingles, réglages adaptés aux écrans étroits ; dans une page, le bouton ⋯ ouvre le menu rapide.
- **Partager → WiDo** depuis n'importe quelle appli : regarder ici ou sur la TV.

### 📺 Passer d'un écran à l'autre
- **Sur la TV** : la page part sur la TV, à la même seconde. **Reprendre sur le téléphone** : la TV se met en pause, le téléphone reprend.
- **En cours sur la TV** : image, titre, barre de lecture déplaçable, ⏪ ⏯ ⏩.
- **Télécommande intégrée** : touches, pavé tactile, clavier en direct (chaque lettre s'écrit sur la TV), volume par les touches du téléphone.

### 🔗 Écrans liés et synchro
- **Liaison** par QR code ou par la liste des TV du réseau, validée sur la TV : **Invité** (télécommande, envoi) ou **Mon téléphone** (synchro).
- **Synchro de mes écrans** : Reprendre, sites perso, épingles et éléments masqués ; le plus récent gagne, une vidéo finie disparaît partout. Mes TV se synchronisent aussi entre elles.

### Corrigé
- La position des vidéos n'était plus relevée après la sélection d'un champ de la page, ni après le réglage du minuteur de veille.

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

### Divers
- **Domaines autorisés et bloqués** saisissables à la main (clavier ou téléphone), et interrupteur **« Même site, autres extensions »** : un site qui redirige vers lui-même sous un autre domaine n'est plus bloqué.
- **Logos des sites** en petite tuile discrète sur les cartes et à côté du grand titre.

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
