# Développement

## Prérequis

- JDK 17 ou plus.
- Pour l'app : Android SDK (plateforme 35). Android Studio le fournit.
- Le module `core` n'a **besoin d'aucun SDK Android**.

## Commandes

```sh
./gradlew :core:test            # tests de la logique (rapide, sans Android)
./gradlew :app:assembleDebug    # APK de développement (SDK Android requis)
```

La propriété `org.gradle.configureondemand=true` permet de construire et tester `:core` sans que le plugin Android soit même résolu.

## Configurer le dépôt visé

`gradle.properties` :

```properties
wido.githubRepo=wicra/WiDo
wido.configUrl=https://raw.githubusercontent.com/wicra/WiDo/main/config/wido.json
```

Ces valeurs sont injectées dans `BuildConfig` : un fork change ces deux lignes pour viser ses propres releases et sa propre configuration.

## Structure

```
core/   logique pure (Kotlin/JVM) + tests
app/    application Android (Compose pour le menu, Views pour le lecteur)
config/ configuration livrée, exemple, schéma JSON
docs/   documentation
.github/workflows/  build.yml (tests + APK debug), release.yml (APK signé)
```

## Tests

Les tests de `core` couvrent : règles de blocage (AdBlock, hosts, exceptions), validation de la configuration, politique de navigation, lecture des releases GitHub, ordre des versions, et validité des fichiers de config livrés (`ShippedConfigTest`).

Le lien entre écrans (`LinkHost`, `LinkGuest`, `MiniHttpServer`) vit aussi dans `core` : `LinkEndToEndTest` lance de vraies TV sur des ports locaux et un téléphone, et vérifie liaison, statut, Sur la TV, Reprendre, télécommande et synchro.

Côté app (`app/src/test`, Robolectric) : `RepositorySyncTest` (stockage et synchro réels) et `ScreensTest`, qui dessine les principaux écrans (téléphone portrait et paysage, TV) avec Roborazzi. La CI publie les images dans l'artefact **wido-captures-ecran** de chaque build, et le rapport **lint** dans **wido-lint**.

```
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
```

Ce qui **n'est pas testé automatiquement** : la WebView et les vrais sites, le curseur, la découverte mDNS sur un vrai réseau, l'installation. Ça se vérifie à la main sur un appareil, voir la liste dans [ROADMAP](ROADMAP.md).

## Style

Kotlin standard (`kotlin.code.style=official`), commentaires en français expliquant le *pourquoi*. Toute règle de décision va dans `core` avec un test, pas dans l'app.

## Environnements restreints

Si Maven Central est limité ou lent, un miroir peut être injecté sans modifier le projet, via un script d'initialisation Gradle (`-I miroir.init.gradle`) qui remplace les dépôts. Le projet lui-même reste sur `google()` et `mavenCentral()`.
