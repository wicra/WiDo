# Publier une version

La CI construit, signe et publie l'APK quand tu pousses un tag `vX.Y.Z`.

## Une seule fois : la clé de signature

**Raccourci** : `scripts/setup-signing.sh` fait tout ce qui suit (clé + 4 secrets) en une commande, sur ton ordinateur. Les étapes manuelles restent décrites ci-dessous.

```sh
gh auth login                    # une fois, si besoin
scripts/setup-signing.sh wicra/WiDo
```

La clé doit rester **la même pour toutes les versions** : Android refuse une mise à jour signée par une autre clé.

```sh
keytool -genkeypair -v -keystore wido.jks -alias wido \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 wido.jks   # à coller dans le secret ci-dessous
```

Dans *Settings → Secrets and variables → Actions* du dépôt, crée :

| Secret | Contenu |
|---|---|
| `WIDO_KEYSTORE_BASE64` | le keystore encodé en base64 |
| `WIDO_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `WIDO_KEY_ALIAS` | `wido` |
| `WIDO_KEY_PASSWORD` | mot de passe de la clé |

**Sauvegarde `wido.jks` hors du dépôt** (gestionnaire de mots de passe). Si tu la perds, tes utilisateurs devront désinstaller puis réinstaller l'app.

## Pourquoi ces secrets ?

Android **refuse d'installer une APK non signée**, et refuse de mettre à jour une app si la nouvelle version n'est pas signée avec **la même clé** que l'ancienne. Cette signature, c'est ce qui prouve que la mise à jour vient bien de toi et pas de quelqu'un d'autre qui aurait imité ton app.

| Élément | À quoi il sert |
|---|---|
| **Keystore** (`wido.jks`) | Le fichier qui contient ta clé privée de signature. C'est ton identité d'éditeur. |
| **Mot de passe du keystore** | Ouvre ce fichier. |
| **Alias** | Le nom de la clé dans le fichier (ici `wido`). |
| **Mot de passe de la clé** | Protège la clé elle-même. |

Pourquoi des *secrets GitHub* plutôt que des fichiers dans le dépôt : le dépôt est public. Si la clé y était, n'importe qui pourrait signer une fausse version de WiDo qui s'installerait comme une mise à jour légitime. Les secrets ne sont lisibles que par la CI, jamais affichés dans les journaux, jamais visibles dans le code.

À retenir :

- **Tu les crées toi-même** sur ton ordinateur ; je ne les vois jamais et je ne les ai pas demandés pour travailler : ils ne servent qu'à la CI de release.
- **Ils ne sont pas nécessaires pour tester.** La CI `build.yml` produit une APK *debug* sans aucun secret (signée avec la clé de debug automatique d'Android). Les secrets ne servent qu'au moment de publier une vraie release.
- **Perdre la clé** veut dire que plus personne ne pourra mettre à jour l'app existante : il faudrait désinstaller puis réinstaller. Sauvegarde le keystore hors du dépôt.

## Pré-release de test (sans secrets)

Pour un tag contenant `-test` (par exemple `v0.1.0-test1`) et sans secrets, le workflow ne s'arrête pas : il publie une **pré-release**. Pour tout autre tag, l'absence de secrets fait échouer la publication, afin de ne jamais diffuser par erreur une APK signée avec la clé de debug. La pré-release dont l'APK est signée avec la clé de debug d'Android. C'est pratique pour tester sur la TV (par exemple avec Downloader), mais :

- elle est marquée *pre-release* sur GitHub, donc **l'app ne la propose pas comme mise à jour** (`releases/latest` l'ignore) ;
- une release signée avec ta vraie clé ne pourra pas l'écraser : il faudra d'abord `adb uninstall dev.wido.tv` ou la désinstaller depuis les réglages de la TV.

## Pré-release de test (sans secrets)

Pour un tag contenant `-test` (par exemple `v0.1.0-test1`) et sans secrets, le workflow ne s'arrête pas : il publie une **pré-release**. Pour tout autre tag, l'absence de secrets fait échouer la publication, afin de ne jamais diffuser par erreur une APK signée avec la clé de debug. La pré-release dont l'APK est signée avec la clé de debug d'Android. C'est pratique pour tester sur la TV (par exemple avec Downloader), mais :

- elle est marquée *pre-release* sur GitHub, donc **l'app ne la propose pas comme mise à jour** (`releases/latest` l'ignore) ;
- une release signée avec ta vraie clé ne pourra pas l'écraser : il faudra d'abord `adb uninstall dev.wido.tv` ou la désinstaller depuis les réglages de la TV.

## À chaque version

1. Mets à jour [`CHANGELOG.md`](../CHANGELOG.md).
2. Pousse le tag :
   ```sh
   git tag v0.2.0 && git push origin v0.2.0
   ```
3. La CI lance les tests, construit l'APK signé (`versionName` = tag sans `v`, `versionCode` = numéro d'exécution de la CI), calcule le SHA-256 et crée la release avec les notes `sha256: …`.

## Vérifier

- La release contient `wido-vX.Y.Z.apk` et la ligne `sha256:`.
- `sha256sum wido-vX.Y.Z.apk` donne la même valeur.
- Sur un appareil avec la version précédente : la bannière de mise à jour apparaît au prochain lancement du menu.

## Numérotation

`MAJEUR.MINEUR.CORRECTIF`. Un tag avec suffixe (`v0.2.0-beta1`) est considéré plus ancien que `v0.2.0`. Marque les versions de test comme *pre-release* sur GitHub : l'endpoint `releases/latest` ne les renvoie pas.
