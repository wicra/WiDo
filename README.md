# WiDo

Un navigateur de streaming pour **Google TV / Android TV**, pensé pour la télécommande, avec blocage de pubs configurable **à distance**. Open source (MIT).

> Né d'un besoin simple : sur un projecteur Google TV, pas d'Opera ni d'extension. WiDo reprend ce que fait une extension de blocage, mais sur la TV, avec une liste de sites et des règles que tu modifies depuis GitHub, sans réinstaller l'app.

**Statut : MVP (v0.1).** Le module `core` est testé automatiquement. L'app Android n'a pas encore été essayée sur un appareil réel : voir [docs/ROADMAP.md](docs/ROADMAP.md).

## Ce que fait le MVP

- **Menu de sites** à la télécommande (grille, focus visible, état de la configuration).
- **Lecture plein écran** dans une WebView, avec **curseur** piloté aux flèches (OK = clic), clavier de la TV sur les champs de saisie, touches média (lecture/pause, ±10 s).
- **Blocage de pubs et de redirections** : listes de domaines (format AdBlock ou hosts), règles globales ou par site, politique de navigation qui refuse tout domaine non prévu.
- **Configuration distante** : un fichier JSON sur GitHub (sites, listes, règles). Un site change de domaine ? Tu modifies une ligne.
- **Mise à jour depuis les releases GitHub**, sans serveur : l'app télécharge l'APK, vérifie sa somme SHA-256, puis Android demande confirmation.
- **Lancement depuis Home Assistant** : `adb shell am start … --es site <id>`.

Ce qu'il ne fait pas (encore) : API locale pour taper du texte à distance, profils, voir la [feuille de route](docs/ROADMAP.md).

## Démarrage rapide

1. Installer l'APK (voir [docs/INSTALL.md](docs/INSTALL.md)).
2. Éditer [`config/wido.json`](config/wido.json) dans ton dépôt : tes sites, tes règles.
3. Lancer WiDo, choisir un site.

## Documentation

| Document | Contenu |
|---|---|
| [Installation](docs/INSTALL.md) | Installer l'APK sur le projecteur, via ADB. |
| [Configuration](docs/CONFIGURATION.md) | Tous les champs du fichier JSON, avec exemples. |
| [Blocage](docs/BLOCKING.md) | Ce qui est bloqué, comment, et ses limites. |
| [Scripts](docs/SCRIPTS.md) | Les scripts intégrés et les sélecteurs masqués. |
| [Télécommande](docs/REMOTE-CONTROL.md) | Curseur, touches, menu rapide. |
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
