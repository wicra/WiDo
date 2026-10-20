# Architecture

```
┌────────────────────────── app (Android) ──────────────────────────┐
│ MainActivity (Compose)      PlayerActivity (Views + WebView)      │
│   MenuScreen                 WiDoWebViewClient ── blocage/navigation│
│   MenuViewModel              ScriptInjector     ── scripts/CSS     │
│                              CursorView         ── curseur         │
│ ConfigRepository  UpdateManager  Http  Settings                    │
└───────────────────────────────┬───────────────────────────────────┘
                                │ dépend de
┌───────────────────────────────▼───────────────────────────────────┐
│ core (Kotlin/JVM pur, testé sans Android)                          │
│  Config/ConfigParser   FilterParser/HostBlocker   NavigationPolicy │
│  Version               ReleaseParser              Hosts            │
└────────────────────────────────────────────────────────────────────┘
        ▲ lit                                  ▲ lit
  GitHub raw (config/wido.json,        GitHub releases (APK + SHA-256)
  listes de blocage)
```

## Écrans liés

La même APK tourne sur TV et téléphone ; `DeviceKind` choisit l'interface (`MenuScreen` ou `PhoneHome`). Sur la TV, `RemoteServer` sert la télécommande du navigateur et les routes de `ScreenLink` (`/hello`, `/pair`, `/status`, `/play`, `/take`, `/cmd`, `/sync`), annoncées en mDNS (`LinkDiscovery`, `_wido._tcp`). Le téléphone n'a pas de serveur : il interroge la TV (`LinkClient`).

La synchro repose sur `SyncLedger` (core) : chaque élément (vidéo de Reprendre, site perso, épingle, masquages d'un site) est une clé datée ; la fusion garde la version la plus récente, les suppressions sont conservées 60 jours. `ConfigRepository` date chaque modification locale au moment où elle a lieu, et applique ce que les autres écrans envoient. Voir [PHONE-AND-SCREENS.md](PHONE-AND-SCREENS.md).

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
| Pas de serveur HTTP local dans le MVP | Surface d'attaque réduite ; voir [ROADMAP](ROADMAP.md). |

## Dépendances

Volontairement peu nombreuses : Compose (menu), AndroidX activity/lifecycle/core, kotlinx-serialization (`core`), coroutines. Pas de bibliothèque réseau (`HttpURLConnection`), pas d'injection de dépendances.
