# Mises à jour via GitHub

Aucun serveur à héberger : les **releases GitHub** du dépôt font office de canal de distribution.

## Déroulé

1. Au lancement du menu, l'app lit `https://api.github.com/repos/wicra/WiDo/releases/latest`.
2. Si la version (`tag_name`, ex. `v0.2.0`) est plus récente que celle installée, une bannière apparaît.
3. Sur *Installer*, l'app télécharge l'APK de la release dans son cache.
4. Elle vérifie la **somme SHA-256** publiée. Sans somme, ou si elle diffère : **refus**.
5. Elle passe la main à l'installateur Android, qui **demande confirmation**.

## Ce qui protège la mise à jour

| Contrôle | Qui |
|---|---|
| L'APK doit venir de `https://github.com/<ce dépôt>/releases/download/…` | WiDo |
| Somme SHA-256 obligatoire et vérifiée | WiDo |
| Seul le téléchargement en `https` est accepté | WiDo |
| Même **clé de signature** que la version installée, sinon refus | Android |
| Confirmation de l'utilisateur à l'écran | Android |

La somme est lue dans le champ `digest` de l'asset (fourni par GitHub) ou, à défaut, dans les notes de release sous la forme `sha256: <64 caractères hexadécimaux>`. La CI de ce dépôt l'écrit automatiquement ([RELEASING](RELEASING.md)).

## Ce qui n'est pas possible

- **Installation 100 % silencieuse** : réservée aux apps système ou à ADB. Pour un parc maîtrisé, depuis un ordinateur ou Home Assistant : `adb install -r wido-vX.Y.Z.apk`.
- **Mise à jour d'une version signée avec une autre clé** : Android la refuse. Une APK de développement (clé debug) doit être désinstallée avant d'installer une release.

## Dépôt privé

Déconseillé : il faudrait mettre un jeton GitHub sur la TV. Garde le dépôt public et n'y mets aucun secret.

## Limites de l'API GitHub

Sans authentification : environ 60 requêtes par heure et par adresse IP. L'app ne vérifie qu'à l'ouverture du menu.
