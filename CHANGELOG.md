# Changelog

## Non publié (carrousels)

### Ajouté
- **Carrousels à la télécommande** : sur une rangée d'affiches, ◀ ▶ vont à l'affiche suivante même hors de l'écran, et la rangée défile pour la montrer. Ça marche avec les rangées défilantes (y compris « scroll-snap ») et avec les carrousels animés des bibliothèques courantes (Swiper, Slick, Owl, Flickity… par leur API, leurs boutons suivant/précédent, sinon un vrai glisser du doigt). Au bout d'une rangée, le curseur ne saute plus en diagonale sur la rangée du dessous : il reste, ou va au lien voisin (« Tout voir »). Maintenir ◀ ▶ contre le bord fait défiler la rangée sous le curseur.

### Corrigé
- **Curseur sur des éléments cachés** : une affiche coupée par la bordure de son carrousel n'est plus visée (le curseur allait sur un endroit invisible et le clic tombait ailleurs) ; le curseur vise le centre de la partie visible.
- **▲ ▼ amènent la rangée à l'écran** : la carte choisie est montrée en entier (la page défile), au lieu d'un centre hors de l'écran.

## Non publié (ouverture des sites)

### Corrigé
- **Ouverture d'un site plus rapide, surtout à l'allumage du projecteur** : les listes de blocage n'étaient plus à jour après la nuit et étaient retéléchargées pendant l'ouverture, jusqu'à trois fois et sans partage (plus de 10 s si le Wi-Fi n'était pas encore prêt) ; l'ouverture attendait aussi le téléchargement de la configuration. Maintenant : l'ouverture prend les listes en mémoire ou sur disque (même un peu anciennes) et ne télécharge jamais ; une même liste n'est chargée qu'une fois à la fois ; la configuration en mémoire est rendue sans attendre le réseau.
- **Moins de fermetures par Android sur une clé d'1 Go** : au démarrage, la mise à jour des listes attend 15 s (l'accueil, la configuration et les icônes passent d'abord), une liste à la fois ; une liste qui épuise la mémoire en se compilant ne fait plus planter WiDo (le cache prend le relais) ; les icônes des sites sont gardées à 256 px au plus (jusqu'à 4 Mo chacune avant) ; l'accueil lâche ses images en mémoire quand un site s'ouvre ; deux écritures d'une même liste ne se mélangent plus.
- **Passage au lecteur léger sans à-coup** : la décision se prend dès que la page lance sa vidéo et connaît sa durée (avant : au relevé suivant, jusqu'à 10 s plus tard, puis un message et 0,7 s d'attente). Une fois le flux repéré, la page est prévenue : le film qui démarre ne se voit ni ne s'entend dans la page ; un écran noir avec le titre le couvre, le lecteur s'ouvre sans animation sur fond noir et sans barre qui surgit. Une pub courte reprend aussitôt normalement dans la page. Si le lecteur léger ne lit pas le flux, la page reprend d'elle-même à la même seconde (« Lecture sur le site »).
- **Clavier du téléphone (appli liée) : lettres manquantes ou recherche vide** : il n'envoyait que les lettres ajoutées, alors que la TV remplace tout le champ ; il ne restait que la dernière lettre, et Entrée envoyait un texte vide avant de valider. Le texte entier part maintenant à chaque frappe (corrections du clavier et accents compris), Entrée valide le texte tapé, Effacer vide aussi le champ de la TV ; le message « Texte envoyé » ne s'affiche plus à chaque lettre.
- **Un site ne s'ouvrait pas et WiDo revenait à l'accueil (plantage 10 s après l'ouverture)** : à la première ouverture sur une petite clé, les listes de blocage mettent plus de 10 s à se charger ; le relevé de la vidéo, programmé à 10 s, touchait la barre de messages pas encore créée (`lateinit property osd has not been initialized`). Le relevé, les commandes du téléphone et l'aide attendent maintenant que la page soit montée. Le journal détaillé note le temps de chargement des listes.
- **Lecteur léger : « ERROR_CODE_DECODING_FAILED »** : au lieu de relancer à l'identique, il relance dans une qualité plus légère et en H.264 quand le flux en propose.

## Non publié (YouTube en plein écran)

- **Barre de lecture de WiDo pour les vidéos de site en plein écran** (YouTube…) : le site cache sa barre et la télécommande ne pouvait pas la viser. ◀ ▶ déplacent maintenant la seconde visée sur une barre en bas de l'écran (« 12:40  +1:30 », plus vite si la touche est maintenue) ; la vidéo y va dès qu'on lâche (0,9 s) ou avec OK. ▲ ▼ affichent la barre (où en est la vidéo, la durée), Retour l'annule sans quitter le plein écran. Enchaîner les appuis ne fait plus sauter la vidéo à chaque fois.

## Non publié (test de la 1.0.8)

- **« Masquer un élément » ne revient plus en naviguant sur le site** : le masquage est aussi posé sur l'élément lui-même (un site qui force `display:block !important` l'emportait sur la feuille de style), et l'élément est enregistré sous plusieurs signatures pour être retrouvé sur les autres pages : son sélecteur unique, ses propres classes quand elles suffisent à le désigner, et pour une pub en iframe, les iframes du même domaine tiers (jamais une grande iframe, qui peut être le lecteur vidéo).
- **Accueil : ▲ vers les onglets arrive sur l'onglet affiché.** Depuis les réglages, on tombait sur « Sites » (l'onglet le plus proche à l'écran), qui s'ouvrait aussitôt ; puis le focus descendait tout seul sur les disques. Il reste maintenant sur l'onglet, le temps de choisir ; il ne redescend sur les disques qu'après Retour (ou double Retour dans un site).
- **Lecteur léger : montée en qualité plus rapide** (dès 5 s de vidéo d'avance au lieu de 10) : avec les tampons réduits des clés d'1 Go, l'image restait longtemps dans la qualité de départ (512×288 pendant tout le test).
- **Test complet** : la lecture liste les qualités du flux (et celles que les décodeurs refusent), le débit estimé par le lecteur, et signale une image restée en basse qualité alors qu'une meilleure est lisible.

## Non publié (suite au test complet)

### Corrigé
- **Retour du lecteur léger sans rechargement du site** : la page n'est plus vidée au moindre signal « mémoire faible » d'Android (sur une clé d'1 Go il arrive sans cesse, même avec 350 Mo libres). Elle reste en pause derrière le film ; elle n'est rechargée que si l'image du film se fige ou si Android a dû arrêter la page.
- **« Masquer un élément » tient vraiment** : l'élément reste masqué sur les pages suivantes de la même visite (avant : seulement à la prochaine ouverture du site), et il est retrouvé aux visites suivantes : sélecteur le plus court qui ne désigne que lui, sans les noms générés à chaque version du site (`css-1x9f2k`, identifiants à chiffres) ni la position parmi tous les éléments ; style remis si le site le retire ; sélecteurs jusqu'à 300 signes (ils étaient coupés à 200).
- **Mode mixte** : la fenêtre publicitaire se ferme bien 2 s après son chargement (8 s au plus si elle ne charge pas, au lieu de 20 s) ; le message disait encore « 5 s ».
- **Rapport de plantage** : la pile d'une erreur de WiDo n'est plus effacée au redémarrage par la cause donnée par Android (le plantage de l'ouverture d'un site n'avait laissé aucune trace) ; elle figure aussi dans le rapport du test complet.
- **Mémoire de l'appli dans le journal** : mesurée au moment même (Android ne renouvelait la valeur précédente que toutes les 5 minutes environ : « appli 12 Mo » pendant une lecture).

### Amélioré
- **Lecteur léger : bonne qualité dès le début** : il démarre avec le débit mesuré à la lecture précédente au lieu de la qualité la plus basse.

## Non publié

### Reprendre, revu
- **Tenu à jour pendant la lecture** (toutes les 30 s, dans la page comme dans le lecteur léger) : plus d'entrée manquante ou en retard quand Android ferme WiDo pendant un film.
- **Plus d'entrées au hasard** : les vidéos de moins de 3 min (pubs, bandes-annonces) et les aperçus muets qui se lancent seuls sur l'accueil d'un site n'y entrent plus.
- **Une série, une entrée** : dans une même séance sur un site, l'épisode suivant (même dossier dans l'adresse, ou titre qui commence pareil) prend la place du précédent ; un autre film du même site garde sa propre entrée.
- Au retour du lecteur léger, la page (encore à l'ancienne seconde) n'écrase plus la position qu'il vient d'enregistrer.

### Ouverture d'un site
- Si Android ferme WiDo pendant l'ouverture d'un site, l'accueil le dit au redémarrage (au lieu d'un retour muet) et le rapport le note.
- Journal détaillé : chaque étape de l'ouverture (accueil, lecteur créé, page affichée en N ms ou erreur, mémoire) et la cause de l'arrêt précédent, juste avant le redémarrage.

### Ajouté
- **Test complet** (Réglages → Test) : environ une minute pour examiner l'appareil et WiDo de bout en bout (mémoire, réseau et débit, décodeurs vidéo, vraie lecture d'un flux de test dans le lecteur léger avec images perdues et image figée, recherche, réactivité des pages et du curseur, configuration, serveur local). Chaque étape est notée OK / à surveiller / échec et le rapport, propre et complet, se lit à l'écran, s'ouvre sur le téléphone par QR code ou s'envoie au projet.
- **Écran de veille WiDo** (Réglages → Apparence → Écran de veille) : halos de verre bleu (ou de l'ambiance choisie), heure et date sur une plaque de verre qui change de place chaque minute, à la place de l'arc-en-ciel du système.

### Modifié
- **Le lecteur léger devient le chemin normal des films**, sur tous les appareils : « Lecteur léger automatique » est activé d'office, un film de plus de 4 minutes (au lieu de 10) y passe en 0,7 s, et Lecture (OK, ▶, menu, écran de pause) sur un film dont le flux est repéré l'ouvre directement dans le lecteur léger, sans démarrer d'abord dans la page.
- **Mode mixte** : la fenêtre publicitaire se ferme 2 s après son chargement (au lieu de 5).
- **Curseur aimanté plus vif** : avec chaque réponse, la page calcule aussi les cibles suivantes dans les 4 directions (même règle) ; le prochain appui y va tout de suite, sans attendre la page, qui confirme ou corrige aussitôt. Les cibles sont oubliées après un clic, un défilement ou un changement de page.
- Code du lecteur : scripts de page, animation d'ouverture et écran de pause (commun au site et au lecteur léger) sortis de `PlayerActivity` ; le lecteur léger et le test complet partagent le même réglage du lecteur vidéo.

### Corrigé
- **Lecteur léger : image figée alors que le son continue.** Sur les clés d'1 Go, le lecteur gardait jusqu'à 50 s de vidéo d'avance (plus de 100 Mo pour un flux 1080p) : la mémoire manquait au décodeur. Tampons réduits (15 à 30 s, 24 Mo), pas de 4K, décodeur de secours si le décodeur matériel refuse le flux, plus de copie de l'image vidéo toutes les 45 s. Une image qui reste figée plus de 3 s pendant que le son avance est détectée : le décodeur est relancé à la même seconde, la page laissée derrière est libérée, et si cela recommence, la qualité baisse d'un cran. Coupure réseau ou décodeur qui cale : reprise seule à la même seconde (2 fois) au lieu de fermer le lecteur. Roue d'attente pendant le chargement.
- **Retour au site depuis le lecteur léger** : la page n'est plus rechargée à chaque fois. Elle reste en pause derrière le film (scripts arrêtés) et n'est vidée que si Android manque de mémoire ; s'il l'arrête lui-même, il arrête la page et non plus WiDo, et une page neuve s'ouvre au retour, à la bonne seconde.
- **Lecteur léger automatique contourné** : après un retour au site, relancer le film le jouait dans la page (plus de 300 Mo, fermeture par Android). Il repart maintenant dans le lecteur léger, sauf si le flux venait d'échouer.
- **Rapport de plantage : le QR code donne le rapport complet** (il ne contenait que les 1 200 premiers signes). Il ouvre sur le téléphone une page servie par la TV sur le réseau local, avec Copier et Partager, tant que la fenêtre est ouverte ; sans réseau, il garde le début du rapport.
- **Journal détaillé** : « écran de pause demandé » n'est plus répété toutes les 10 s pendant une pause (le journal en était rempli) ; le lecteur léger y note son décodeur, la taille de l'image, les images sautées et les erreurs avec la mémoire du moment.
- **Télécommande** : un appui doublé par la télécommande (deux Retour à 48 ms dans le journal) n'est plus compté deux fois ; les flèches appuyées vite sur un site ne se perdent plus pendant que la page répond (elles sont jouées dans l'ordre).
- Le rappel des touches du lecteur léger ne parle plus du volume aux flèches.

## 0.9.1

### Ajouté
- **Journal détaillé** (Réglages › Général, désactivé par défaut) : enregistre les gestes et décisions de WiDo (touches, retours, lecteur, mémoire toutes les 30 s), jamais les noms ou adresses de sites. Il s'ajoute au rapport de plantage.

### Corrigé
- **Fermetures sur petite TV** : l'appli peut utiliser un tas mémoire plus grand (`largeHeap`), et une mémoire épuisée est notée en une ligne dans le rapport même quand la pile complète ne peut plus être écrite.
- **Réglages** : ▼ depuis l'onglet « Réglages » arrive sur le thème choisi (colonne de gauche), plus sur le premier réglage.
- **Écran de pause** : même fond que WiDo, dans le site et dans le lecteur léger.
- **Rapport de plantage** : le texte à côté du QR code défile avec ▲ ▼ et s'affiche en entier.
- **Liaison : protection contre les demandes en rafale.** Un appareil du réseau ne peut plus faire défiler les fenêtres de confirmation sur la TV : 2 demandes en attente par adresse, 4 par minute au total, 5 minutes de silence après un refus. La fenêtre montre l'adresse de l'appareil et un nom nettoyé, et ne s'affiche pas par-dessus un film qui joue.
- Les décisions de liaison récentes ne sont plus effacées en bloc (seules les plus anciennes partent).
- **Réglages (TV) en deux niveaux** : ▲ ▼ changent de thème, OK ou ▶ entrent dans ses réglages, ◀ ou Retour reviennent à la liste ; aux bords de la liste, le focus ne saute plus vers les onglets du haut.
- **OK maintenu** sur un site ou une vidéo de Reprendre ouvre ses options **sans lancer la première option** (Ouvrir / Reprendre) en relâchant la touche.
- **Reprendre** : les vignettes ont toutes la même hauteur, même avec un titre court.
- **Accueil** : le pied ne montre plus que la version (la mise à jour est à côté de l'heure, les pubs bloquées dans les réglages) ; plus de message sous l'accueil.
- **Quitter** : un premier Retour sur l'accueil prévient, le second quitte WiDo ; dans un site, un Retour qui quitterait le site prévient aussi (le double appui rapide va toujours directement aux sites).
- **Volume** : uniquement aux touches volume de la télécommande (les flèches ▲ ▼ ne le règlent plus, ni dans le plein écran du site ni dans le lecteur léger) ; ◀ ▶ gardent l'avance de 10 s.
- **Lecteur léger** : écran de pause (assombri, titre, temps restant, heure de fin) et touche **Menu** qui ouvre les options sans quitter le film (cadrage, **épingler la page**, veille, début, retour au site).
- **Menu rapide** : fond aux couleurs de WiDo.
- **Curseur sur les sites** : une fenêtre de cookies ou un pop-up attire le curseur sur son bouton principal (puis il revient), les flèches ne parcourent que ses boutons, et ▲ ▼ font défiler la zone sous le curseur (la fenêtre, sinon la page).
- **Navigation à la télécommande, reprise de l'audit :**
  - **Écran de pause** : la touche qui le ferme n'agit plus en plus (Retour ne quitte plus la page) ; il n'apparaît pas pendant qu'on navigue.
  - **Page qui ne s'ouvre pas** : la touche Retour fait ce que dit le bouton « Retour » (plus de page noire).
  - **En pause plein écran**, un message explique comment reprendre (OK clique ; Menu → Lecture).
  - **Lecteur léger** : ◀ ▶ avancent/reculent, ▲ ▼ règlent le volume, un bouton **← Site** ramène à la page, rappel des touches au démarrage et message à la réouverture de la page.
  - **Double Retour** : réglable (Réglages → Général).
  - **Avance rapide** : les flèches accélèrent comme les touches média.
  - **Menu rapide** : toutes les pastilles visibles sur une ou deux lignes (plus de défilement), « Sites » en dernier, l'étiquette de Veille se met à jour, les messages s'affichent au-dessus du menu.
  - **Touche rouge « Menu »** renommée « Retour aux sites » (elle n'ouvre pas le menu rapide).
  - **Déplacer un site** : Retour annule (le site retrouve sa place), OK valide ; rappel des touches.
  - **Confirmation en deux temps** sur TV pour Retirer (Reprendre), Réinitialiser, Oublier un appareil et Effacer le rapport.
  - **Fenêtres** : le focus est placé sur le premier élément à l'ouverture.
  - **Onglets** : la rangée de vinyles et la grille de Reprendre gardent leur position ; revenir sur les sites ramène sur le dernier disque choisi.
  - **Indice** sur l'accueil (OK maintenu ou Menu) ; **OK maintenu** fait défiler les réglages à valeurs multiples (zoom…) ; l'ouverture depuis une vignette de Reprendre agrandit la vignette au lieu d'un disque.
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
- **Tutoriel de la télécommande** (Réglages → Apparence → Aide) : on essaie pour de vrai OK, OK maintenu, Menu et le double Retour, avec un retour à chaque réussite ; on peut passer une étape ou quitter à tout moment. L'aide à l'écran du lecteur est mise à jour (OK maintenu, Menu, double Retour, volume).
- **Bouton « Envoyer le rapport »** (Réglages → Général → Rapport de plantage) : un appui envoie le rapport au projet, sans compte ni configuration, sans les noms de sites. Il passe par un petit relais gratuit (`tools/crash-relay`) qui seul détient la clé GitHub (l'APK n'embarque aucun secret) et dépose le rapport en issue, en regroupant les doublons. Tant que le relais n'est pas déployé, le bouton ouvre la page « Nouvelle issue » de GitHub, pré-remplie.
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
