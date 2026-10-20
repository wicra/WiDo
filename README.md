# WiDo

![WiDo](docs/assets/banner.png)

Un navigateur de streaming pour **Google TV / Android TV**, pensé pour la télécommande, avec blocage de pubs configurable **à distance**. La même appli tourne sur **téléphone Android** : interface tactile, télécommande, et une vidéo qui passe d'un écran à l'autre. Open source (MIT).

> Né d'un besoin simple : sur un projecteur Google TV, pas d'Opera ni d'extension. WiDo reprend ce que fait une extension de blocage, mais sur la TV, avec une liste de sites et des règles que tu modifies depuis GitHub, sans réinstaller l'app.

**Statut : MVP (v0.1).** Le module `core` est testé automatiquement. L'app Android n'a pas encore été essayée sur un appareil réel : voir [docs/ROADMAP.md](docs/ROADMAP.md).

## Ce que fait le MVP

- **Menu de sites** à la télécommande (grille, focus visible, état de la configuration).
- **Lecture plein écran** dans une WebView, avec **curseur aimanté** (une flèche saute au prochain lien), clic à OK, clavier de la TV sur les champs de saisie, écran de pause, volume personnalisé, zoom du texte et touches de couleur configurables.
- **Blocage de pubs et de redirections** : listes de domaines (format AdBlock ou hosts), règles globales ou par site, politique de navigation qui refuse tout domaine non prévu.
- **Configuration distante** : un fichier JSON sur GitHub (sites, listes, règles). Un site change de domaine ? Tu modifies une ligne.
- **Mise à jour depuis les releases GitHub**, sans serveur : l'app télécharge l'APK, vérifie sa somme SHA-256, puis Android demande confirmation.
- **Lancement depuis Home Assistant** : `adb shell am start … --es site <id>`.
- **Sur téléphone** : la même appli en version tactile, **Sur la TV** / **Reprendre sur le téléphone** à la même seconde, télécommande (touches, pavé, clavier en direct), « Partager → WiDo », et synchro de « mes écrans » (Reprendre, sites perso, épingles) entre le téléphone et les TV.

Ce qu'il ne fait pas (encore) : API locale pour taper du texte à distance, profils, voir la [feuille de route](docs/ROADMAP.md).

## Démarrage rapide

1. Installer l'APK (voir [docs/INSTALL-ANDROID-TV.md](docs/INSTALL-ANDROID-TV.md)).
2. Éditer [`config/wido.json`](config/wido.json) dans ton dépôt : tes sites, tes règles.
3. Lancer WiDo, choisir un site.

## Documentation

| Document | Contenu |
|---|---|
| [Installer sur Android TV](docs/INSTALL-ANDROID-TV.md) | Guide pas à pas : mode développeur, ADB, Downloader, clé USB, dépannage. |
| [Installation (résumé)](docs/INSTALL.md) | Commandes essentielles et URL de configuration. |
| [Tester](docs/TESTING.md) | Sites de test fournis et check-list sur appareil. |
| [Sites personnels](docs/CUSTOM-SITES.md) | Ajouter ses sites depuis la TV, conservés lors des mises à jour. |
| [Configuration](docs/CONFIGURATION.md) | Tous les champs du fichier JSON, avec exemples. |
| [Blocage](docs/BLOCKING.md) | Ce qui est bloqué, comment, et ses limites. |
| [Scripts](docs/SCRIPTS.md) | Les scripts intégrés et les sélecteurs masqués. |
| [Télécommande](docs/REMOTE-CONTROL.md) | Curseur aimanté, touches, écran de pause, menu rapide. |
| [Téléphone et écrans liés](docs/PHONE-AND-SCREENS.md) | L'appli sur téléphone, lier la TV, passer d'un écran à l'autre, synchro. |
| [Réglages](docs/SETTINGS.md) | Curseur, zoom, volume, touches de couleur, démarrage. |
| [Mises à jour](docs/UPDATES.md) | Fonctionnement via les releases GitHub. |
| [Home Assistant](docs/HOME-ASSISTANT.md) | Scène « soirée cinéma », lancement d'un site. |
| [Architecture](docs/ARCHITECTURE.md) | Modules, flux, décisions de conception. |
| [Sécurité](docs/SECURITY.md) | Modèle de menace et règles à respecter. |
| [Développement](docs/DEVELOPMENT.md) | Construire, tester, contribuer. |
| [Publier une version](docs/RELEASING.md) | Signature, tag, release. |
| [Feuille de route](docs/ROADMAP.md) | Ce qui est fait, ce qui vient, ce qui est incertain. |

## À savoir

- **WiDo ne fournit aucun contenu ni aucune liste de sites.** Tu configures les tiens et tu en es responsable.
- **Aucun contournement de DRM.** La lecture protégée dépend de la certification Widevine de ton appareil.
- Bloquer les pubs au niveau du nom de domaine a des limites (voir [docs/BLOCKING.md](docs/BLOCKING.md)).

[English summary](README.en.md) · [Contribuer](CONTRIBUTING.md) · [Licence MIT](LICENSE)
