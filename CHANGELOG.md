# Changelog

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
