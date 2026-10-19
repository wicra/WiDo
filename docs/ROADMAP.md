# Feuille de route

## État du MVP (v0.1)

| Fonction | État |
|---|---|
| Analyse/validation de la configuration | ✅ codé et testé (`core`) |
| Blocage par hôte, exceptions, formats AdBlock/hosts | ✅ codé et testé |
| Politique de navigation | ✅ codé et testé |
| Lecture des releases GitHub, comparaison de versions | ✅ codé et testé |
| Menu Compose, lecteur WebView, curseur, menu rapide | 🟡 codé, **non compilé ni essayé sur appareil** |
| Téléchargement + SHA-256 + installation | 🟡 codé, **non essayé sur appareil** |
| CI (tests, APK debug, release signée) | 🟡 écrite, **jamais exécutée** |

Le premier travail de la v0.1.x est de faire tourner la CI, corriger ce qu'elle révèle, puis valider sur le projecteur.

## À valider sur un appareil réel (v0.3)

- [ ] Curseur aimanté : saute bien d'un lien à l'autre sur YouTube et sur un site à grille de vignettes.
- [ ] Barre de lecture : pilote une vidéo dans une **iframe** d'un autre domaine (passerelle native).
- [ ] Volume personnalisé : l'indicateur WiDo remplace celui d'Android et le volume change réellement.
- [ ] Touches de couleur : la télécommande du projecteur en a-t-elle ? Que renvoient CH +/- ?
- [ ] Zoom du texte : lisible à 3 mètres, mémorisé par site.
- [ ] Écran de Réglages : navigation et changement de valeurs à la télécommande.


La check-list détaillée, avec les sites de test, est dans [TESTING](TESTING.md).

- [ ] L'APK se compile (CI) et s'installe.
- [ ] Le menu s'affiche, le focus est visible, la première carte est sélectionnée.
- [ ] Widevine L1 effectif (app « DRM Info ») et lecture DRM dans la WebView.
- [ ] Curseur : vitesse, défilement aux bords, clic, clavier TV sur un champ de saisie.
- [ ] Plein écran vidéo, bouton Retour.
- [ ] Dictée vocale du clavier Google dans un champ de la WebView.
- [ ] Lancement par `adb shell am start … --es site <id>`.
- [ ] Écran d'autorisation d'installation sur Google TV, mise à jour complète.
- [ ] Efficacité réelle du blocage sur tes sites, avec tes listes.

## v0.2

- **API locale** (serveur HTTP sur le réseau local, jeton obligatoire) : `/open`, `/type`, `/key`, `/reload`, `/status`, pour piloter depuis Home Assistant ou un téléphone.
- **Page de saisie sur téléphone** : taper dans le champ actif de la TV.
- Cookies tiers **par site** (`thirdPartyCookies`).
- Scripts utilisateur **signés** ou liste blanche de hachages.

## Plus tard

- Réglages avancés (masquage CSS, agent utilisateur) dans le formulaire des sites perso.
- Choix du mode de navigation par défaut quand une régie inconnue des listes passe en `relaxed` (liste de domaines publicitaires à enrichir).
- Export / import des sites perso (aujourd'hui perdus si l'app est désinstallée).
- Saisie des sites perso depuis un téléphone (la saisie à la télécommande est lente).
- Profils (par personne, avec leurs sites et règles).
- Sauvegarde/restauration de la configuration.
- Blocage par chemin pour un sous-ensemble de règles.
- Contrôle parental (horaires, depuis Home Assistant).
- Raccourcis vers des sites depuis l'écran d'accueil Google TV.

## Hors périmètre

- Contournement de DRM, téléchargement de contenus.
- Fournir ou recommander des sites de streaming.
- Moteur AdBlock complet (utiliser un DNS filtrant en complément).
