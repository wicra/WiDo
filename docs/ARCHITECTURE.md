# Architecture

```
┌────────────────────────── app (Android) ──────────────────────────┐
│ MainActivity (Compose)      PlayerActivity (Views + WebView)      │
│   MenuScreen / PhoneHome     WiDoWebViewClient ── blocage/navigation│
│   MenuViewModel              ScriptInjector     ── scripts/CSS     │
│   SettingsPane               CursorView, OsdView ── curseur, messages│
│ ConfigRepository  ScreenLink  UpdateManager  Settings  UiSound     │
│ CrashLog  LaunchSnapshot  FaviconRepository  Thumbnails  Playback  │
└───────────────────────────────┬───────────────────────────────────┘
                                │ dépend de
┌───────────────────────────────▼───────────────────────────────────┐
│ core (Kotlin/JVM pur, testé sans Android)                          │
│  Config/ConfigParser   FilterParser/HostBlocker   NavigationPolicy │
│  Version  ReleaseParser  Hosts  LocalSites  Library (Reprendre…)   │
│  LinkProtocol  LinkNodes (LinkHost, LinkGuest)  SyncLedger         │
│  PairingDesk  SiteSharing  MiniHttpServer                          │
└────────────────────────────────────────────────────────────────────┘
        ▲ lit                                  ▲ lit
  GitHub raw (config/wido.json,        GitHub releases (APK + SHA-256)
  listes de blocage)
```

## Écrans liés

La même APK tourne sur TV et téléphone ; `DeviceKind` choisit l'interface (`MenuScreen` ou `PhoneHome`). Sur la TV, `RemoteServer` sert la télécommande du navigateur et les routes du lien (`LinkHost` dans `core` : `/hello`, `/pair`, `/status`, `/play`, `/take`, `/cmd`, `/share`, `/sync`), annoncées en mDNS (`LinkDiscovery`, `_wido._tcp`). Le téléphone n'a pas de serveur : il interroge la TV (`LinkGuest`). `/share` propose des sites (`SiteSharing`) ; la TV n'ajoute rien sans confirmation à l'écran.

La synchro repose sur `SyncLedger` (core) : chaque élément (vidéo de Reprendre, site perso, épingle, masquages d'un site) est une clé datée ; la fusion garde la version la plus récente, les suppressions sont conservées 60 jours. `ConfigRepository` date chaque modification locale au moment où elle a lieu, et applique ce que les autres écrans envoient. Voir [PHONE-AND-SCREENS.md](PHONE-AND-SCREENS.md).

## Fichiers clés de l'app

| Fichier | Rôle |
|---|---|
| `MainActivity` | Accueil (TV : `MenuScreen`, téléphone : `PhoneHome`), ouverture du lecteur, confirmations (liaison, sites proposés). |
| `PlayerActivity` | Le lecteur : WebView, curseur, menu rapide, écran de pause, reprise à la seconde (avec vérification), page d'erreur, moteur de page arrêté, ouverture du vinyle. |
| `ui/MenuScreen` | Accueil TV : onglets Sites (vinyles, vague), Reprendre (grille), Réglages. |
| `ui/SettingsPane` | Réglages par thème (colonne à gauche sur TV), rapport de plantage. |
| `ui/SiteShareUi` | Envoi de sites depuis le téléphone (packs) et confirmation sur la TV. |
| `ScreenLink` | Lien entre écrans côté Android : serveur de la TV, découverte, appairage, synchro, partage. |
| `UiSound` | Sons d'interface synthétisés au premier lancement (aucun fichier audio). |
| `CrashLog` | Journal local des plantages et incidents, affiché dans les réglages. |
| `LaunchSnapshot` | Capture de l'accueil (GPU) affichée pendant l'ouverture d'un site. |
| `Playback` | Notification de la TV pilotée, lecture en arrière-plan (services). |

## Pourquoi deux modules

Toute la logique qui décide (analyser une configuration, bloquer ou non un hôte, autoriser ou non une navigation, comparer des versions, lire une release) vit dans **`core`**, du Kotlin pur. Elle se teste en secondes sur n'importe quel ordinateur, sans émulateur ni SDK Android. L'app n'est qu'une couche fine qui branche ces décisions sur la WebView, l'interface et le réseau.

## Flux d'ouverture d'un site

1. `MainActivity` ou une commande ADB lance `PlayerActivity` avec `site=<id>`.
2. `ConfigRepository.ensureLoaded()` : cache disque, sinon fichier intégré (sans réseau).
3. `blockerFor(site)` : télécharge ou relit en cache les listes, ajoute règles globales + règles du site, exceptions (whitelist, domaines du site).
4. La WebView charge `site.url`. Chaque sous-requête passe par `shouldInterceptRequest` (blocage par hôte) ; chaque navigation par `shouldOverrideUrlLoading` (`NavigationPolicy`).
5. En parallèle, la configuration distante est rafraîchie pour la prochaine ouverture.

## Décisions de conception

| Décision | Raison |
|---|---|
| Configuration **distante et déclarative** | Les sites changent de domaine souvent ; la modification doit coûter une ligne. |
| Pas de JavaScript dans la configuration | Éviter qu'un dépôt de config compromis exécute du code dans les pages. Voir [SECURITY](SECURITY.md). |
| Blocage par **nom d'hôte** uniquement | Simple, rapide, prévisible, équivalent d'un DNS filtrant. Pas de moteur AdBlock complet (voir [BLOCKING](BLOCKING.md)). |
| Une configuration invalide **ne remplace jamais** la valide | Une faute de frappe ne doit pas vider le menu. Écriture du cache atomique. |
| Un site invalide est écarté, pas toute la config | Même raison. |
| Curseur émulé par événements tactiles | La plupart des sites de streaming ne se pilotent pas au focus. |
| `http` seulement par site (`allowInsecure`) | Serveurs locaux, sans ouvrir le reste. |
| Mise à jour : SHA-256 obligatoire | Défense en profondeur par-dessus la signature Android. |
| Serveur local seulement quand WiDo est à l'écran, sur le réseau local | Télécommande et lien entre écrans sans exposer la TV ; chaque appareil a son jeton. Voir [SECURITY](SECURITY.md). |
| Le moteur de page qui s'arrête est géré | Sinon Android ferme toute l'appli ; la vidéo en cause ne se relance pas seule. |

## Dépendances

Volontairement peu nombreuses : Compose (menu), AndroidX activity/lifecycle/core, kotlinx-serialization (`core`), coroutines. Pas de bibliothèque réseau (`HttpURLConnection`), pas d'injection de dépendances.
