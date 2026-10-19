# Publier une version

La CI construit, signe et publie l'APK quand tu pousses un tag `vX.Y.Z`.

## Une seule fois : la clé de signature

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
