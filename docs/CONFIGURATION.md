# Configuration

Un seul fichier JSON décrit tout ce qui change souvent : sites, listes de blocage, règles. L'app le télécharge à chaque ouverture (et via le bouton *Actualiser*) et garde la dernière version valide en cache. Hors ligne, elle utilise le cache, puis un fichier minimal intégré.

Fichiers fournis :

- [`config/wido.json`](../config/wido.json) : configuration lue par défaut (à adapter).
- [`config/wido.example.json`](../config/wido.example.json) : exemple complet, avec toutes les options.
- [`config/wido.schema.json`](../config/wido.schema.json) : schéma JSON pour l'autocomplétion dans ton éditeur.

## Racine

| Champ | Type | Défaut | Rôle |
|---|---|---|---|
| `schema` | entier | `1` | Version du **format**. Si le fichier est plus récent que l'app, il est refusé avec un message : mets l'app à jour. |
| `revision` | entier | `0` | Numéro libre, affiché dans le menu, pour vérifier que la TV a bien ta dernière version. |
| `blocklists` | liste d'URL `https` | `[]` | Listes de domaines à bloquer (format AdBlock `\|\|domaine^` ou fichier hosts). |
| `globalRules` | liste de règles | `[]` | Règles écrites ici, pour tous les sites. Max 5000. |
| `whitelist` | liste de domaines | `[]` | Domaines **jamais** bloqués, pour tous les sites. |
| `sites` | liste | `[]` | Les sites du menu. Max 100. |

## Un site

| Champ | Type | Défaut | Rôle |
|---|---|---|---|
| `id` | texte | obligatoire | Identifiant stable : `a-z`, `0-9`, tirets, 32 max, unique. C'est lui que Home Assistant utilise. |
| `name` | texte | obligatoire | Nom affiché sur la carte. |
| `url` | texte | obligatoire | Page d'ouverture. `https` obligatoire (sauf `allowInsecure`). |
| `allowedDomains` | liste | `[]` | Domaines **supplémentaires** où la fenêtre peut naviguer (lecteur, authentification). Le domaine de `url` l'est déjà, `www.` ignoré. |
| `rules` | liste | `[]` | Règles de blocage propres à ce site. |
| `scripts` | liste | `[]` | Scripts intégrés à injecter, voir [SCRIPTS](SCRIPTS.md). |
| `hideSelectors` | liste | `[]` | Sélecteurs CSS à masquer (200 caractères max chacun, sans `{ } < >`). |
| `enabled` | booléen | `true` | `false` : le site disparaît du menu sans perdre sa configuration. |
| `userAgent` | texte | `default` | `default` (WebView), `desktop` (Chrome Linux) ou une chaîne complète. |
| `color` | texte | **automatique** | Optionnel. Sans lui, la couleur de la carte et de l'ambiance du menu est tirée de l'icône du site. Renseigne `#RRGGBB` seulement pour l'imposer. |
| `icon` | texte | détecté | URL `https` d'une icône. Sans elle, WiDo cherche le favicon du site (voir plus bas). |
| `navigation` | texte | `strict` | `strict` : la fenêtre ne navigue que sur le domaine du site et `allowedDomains` (le plus sûr contre les pubs). `relaxed` : navigation libre, **sauf** vers les domaines bloqués par les listes. À réserver aux sites qui ont besoin de redirections utiles (YouTube, connexions). Voir [BLOCKING](BLOCKING.md). |
| `allowInsecure` | booléen | `false` | Autorise `http` pour **ce site seulement** (serveur local type `http://192.168.1.20:8096`). |

## Quand le site change de domaine

1. Modifie `url` (et `allowedDomains` si le lecteur est ailleurs).
2. Augmente `revision`.
3. Commit sur GitHub. Sur la TV : *Actualiser* (ou relance l'app).

Si un site cesse de fonctionner après un changement, la redirection refusée s'affiche brièvement à l'écran (« Redirection bloquée : … ») : ajoute ce domaine à `allowedDomains`.

## Validation et tolérance aux erreurs

- JSON illisible ou format trop récent → fichier **refusé**, l'app garde la dernière config valide.
- Un site invalide (identifiant mal formé, doublon, `http` sans `allowInsecure`…) est **ignoré avec un avertissement**, les autres restent. Le nombre d'avertissements est affiché dans le menu.
- Une liste de blocage non-`https` est ignorée.
- Les clés inconnues sont ignorées : un fichier écrit pour une version future reste lisible.

## Héberger la configuration

- **Dans ce dépôt** (`config/wido.json`) : le plus simple, versionné.
- **Dans un gist** ou un autre dépôt : change l'URL avec `--es config_url`.
- Aucun secret ne doit y figurer : le fichier est public si le dépôt l'est.

## Icônes des cartes

Chaque carte affiche l'icône du site. WiDo la cherche ainsi, dans cet ordre :

1. le champ `icon` s'il est renseigné ;
2. la page d'accueil du site : balises `apple-touch-icon`, puis `icon` (la plus grande) ;
3. `/favicon.ico` à la racine du site.

L'icône est téléchargée **directement depuis le site** (aucun service tiers n'est contacté), en `https` seulement, limitée à 512 Ko, puis mise en cache 14 jours. Si rien n'est trouvé, la carte affiche la première lettre du nom.

## Couleur des cartes

Par défaut, chaque carte prend **la couleur de l'icône de son site** : WiDo moyenne les pixels colorés de l'icône (gris, blancs et noirs ignorés), puis ramène le résultat à une teinte vive et lisible. Le fond du menu prend cette couleur quand la carte est sélectionnée. Une icône sans couleur (logo noir et blanc) donne la couleur d'accent violette. Le champ `color` ne sert qu'à imposer une teinte.
