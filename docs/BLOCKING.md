# Blocage : règles et limites

WiDo applique **quatre filtres indépendants** : requêtes, masquage, navigation, scripts. YouTube a en plus son module dédié.

## Listes légères (appareils à peu de mémoire)

Sur une clé TV d'1 Go, les grosses listes pèsent lourd et Android finit par fermer l'appli pendant un film. Les noms de domaine bloqués sont donc gardés sous forme compacte (8 octets par nom, au prix d'une chance infime de bloquer un domaine de trop, jamais d'en laisser passer un), et **Réglages → Blocage → Taille des listes** propose :

- **Complètes** : EasyList, liste française, filtre DNS d'AdGuard et listes d'uBlock Origin ;
- **Légères** : filtre DNS d'AdGuard et correctifs d'uBlock seulement. Les pubs et pisteurs restent bloqués par leur nom de domaine ; on perd une partie du masquage des bandeaux et des règles d'adresse d'EasyList. C'est le réglage d'office sur les appareils à moins de 1,7 Go de mémoire.

## 1. Blocage de requêtes par nom d'hôte

Chaque requête de la page (script, image, iframe, vidéo) est vérifiée : si son **nom d'hôte** est dans les règles, elle reçoit une réponse vide (HTTP 403). C'est le même principe qu'un DNS filtrant.

Syntaxes comprises (dans `blocklists`, `globalRules`, `rules`) :

| Syntaxe | Effet |
|---|---|
| `\|\|pub.exemple.com^` | Bloque `pub.exemple.com` **et ses sous-domaines**. |
| `@@\|\|cdn.exemple.com^` | Exception : jamais bloqué. L'exception l'emporte toujours. |
| `0.0.0.0 pub.exemple.com` | Format fichier hosts. |
| `pub.exemple.com` | Nom seul, une ligne par domaine. |
| `\|\|site.com/pubs/`, `/banner/*$image`, `-ad-banner.` | Règle d'**adresse** : chemin, jokers `*`, séparateur `^`, ancres `\|`. |
| `$domain=a.com\|~b.com`, `$third-party`, `$script`, `$important` | Options des règles d'adresse : sites concernés, requêtes tierces, type de requête, priorité sur les exceptions. |
| `site.com##+js(set-constant, adBlock, false)` | **Scriptlet** uBlock (voir plus bas). |
| `\|\|pub.exemple.com^$third-party,script` | Options de type ou de tiers acceptées : l'hôte est bloqué entièrement. |
| `##.bandeau`, `site.com##.bandeau` | Masquage cosmétique (voir n° 2). |
| `site.com#@#.bandeau` | Exception de masquage. |
| `@@\|\|site.com^$generichide` / `$elemhide` | Pas de masquage générique / aucun masquage sur ce site. |
| `! commentaire`, `# commentaire` | Ignoré. |

**Non géré**, compté comme « ignoré » : les expressions régulières (`/…/`), les options qui modifient la réponse (`$csp`, `$redirect`, `$removeparam`…) ou visent une page entière (`$document`, `$popup` sur une adresse), les règles cosmétiques procédurales (`:has-text()`…) et les scriptlets non implémentés.

Les règles d'adresse sont indexées par mot : chaque requête ne teste que les quelques règles qui peuvent la concerner (environ 0,05 ms par requête). Elles ne s'appliquent jamais à la page principale elle-même.

### Scriptlets uBlock

Certaines pubs et « désactivez votre bloqueur » ne passent pas par une requête bloquable : ce sont les scripts du site qui les affichent. Les listes d'uBlock Origin y répondent par des **scriptlets**, de petits correctifs lancés avant les scripts de la page. WiDo implémente les plus utilisés : `set-constant`, `abort-on-property-read/write`, `abort-current-script`, `no-setTimeout-if`, `no-setInterval-if`, `nano-setTimeout/Interval-booster`, `json-prune`, `addEventListener-defuser`, `window.open-defuser`, `no-fetch-if`, `no-xhr-if`, `remove-attr`, `remove-class`, `remove-node-text`, `noeval(-if)`, `nowebrtc` (et leurs abréviations). Les directives `!#if`/`!#include` des listes sont prises en charge.

Ordre de décision pour un hôte :
1. dans `whitelist`, ou domaine du site, ou `allowedDomains` ? → **jamais bloqué** ;
2. règle d'exception `@@` ? → jamais bloqué ;
3. règle de blocage (liste, `globalRules`, `rules` du site) ? → bloqué ;
4. sinon → autorisé.

Les listes de `blocklists` sont téléchargées (8 Mo max chacune), analysées une fois puis gardées sur disque **au format compilé** (bien plus rapide à relire), et rafraîchies au plus toutes les 6 heures. Si le téléchargement échoue, la copie en cache est utilisée. Elles sont chargées en arrière-plan dès l'accueil : ouvrir un site est immédiat.

### Listes recommandées

Réglage **Listes recommandées** (activé par défaut) : WiDo ajoute aux listes de la configuration [EasyList](https://easylist.to/), sa variante française [Liste FR](https://easylist-downloads.adblockplus.org/liste_fr.txt), le [filtre DNS d'AdGuard](https://adguardteam.github.io/AdGuardSDNSFilter/) et les listes d'[uBlock Origin](https://github.com/uBlockOrigin/uAssets) (filtres, correctifs rapides, exceptions qui réparent les sites). Ces listes sont mises à jour par leurs communautés plusieurs fois par jour : quand un site trouve une parade, la correction arrive toute seule, sans nouvelle version de WiDo. L'état (nombre de listes, de domaines, de règles de masquage, date de mise à jour) et un bouton **Mettre à jour** sont dans Réglages → Blocage.

## 2. Masquage cosmétique

Les règles `##` des listes masquent les emplacements publicitaires restés vides dans la page (bannières, encarts). WiDo ne garde que les règles génériques (8 000 au plus, pour rester fluide sur une TV) et celles des domaines du site, puis les injecte **avant** les scripts de la page, dans la page principale. Les sélecteurs que le moteur de la TV ne comprend pas sont écartés un par un.

## YouTube sans publicité

Réglage **YouTube sans pub** (activé par défaut), sur youtube.com, m.youtube.com et les lecteurs intégrés :

1. les pubs sont **retirées des données du lecteur** avant qu'il ne les lise : il n'en voit aucune ;
2. si une pub passe quand même, elle est coupée, accélérée jusqu'à sa fin, et « Ignorer » est cliqué ;
3. les encarts publicitaires de l'accueil et de la page vidéo sont masqués, ainsi que le message « bloqueurs de pub interdits ».

Réglage **Sauter les sponsors** : les passages sponsorisés, autopromotions et rappels « abonnez-vous » signalés dans la base collaborative [SponsorBlock](https://sponsor.ajay.app/) sont sautés automatiquement (« Passage sponsorisé sauté » s'affiche). L'identifiant de la vidéo est alors envoyé à SponsorBlock.

YouTube change souvent son lecteur : si des pubs réapparaissent, une mise à jour de WiDo corrigera le module.

## 3. Politique de navigation

La fenêtre ne peut ouvrir que : le domaine du site (sans `www.`), ses sous-domaines, et les `allowedDomains`. Toute autre navigation (redirection, lien publicitaire, `window.open`, `intent:`, `market:`, `javascript:`) est **refusée**. Les pop-ups sont ouverts dans la même fenêtre par la WebView puis refusés ici s'ils vont ailleurs.

C'est le filtre le plus efficace contre les redirections publicitaires, et il ne gêne pas le lecteur vidéo (qui charge ses ressources par des sous-requêtes, jugées par le filtre n° 1).

### Trois modes : `strict`, `relaxed`, `mixed`

| Mode | Navigation permise | Pour qui |
|---|---|---|
| `strict` (défaut) | Le domaine du site, ses sous-domaines, `allowedDomains`. | Sites de streaming : la plupart des pop-ups et redirections de pub sont ainsi coupés. |
| `relaxed` (souple) | En plus, tout domaine web non bloqué par les listes. | Sites qui ont besoin de redirections utiles : connexion, consentement, YouTube. |
| `mixed` (mixte) | Comme `strict`, mais la page refusée s'ouvre dans une **fenêtre publicitaire en arrière-plan** : elle se charge derrière la page (sans son), reste 5 secondes puis se ferme ; l'écran et la télécommande restent sur le site. | Sites qui exigent que la pub soit réellement ouverte ou vue avant de donner accès au contenu. |

En `relaxed`, une redirection vers une régie **présente dans les listes** reste refusée ; une régie inconnue des listes passerait : choisis `relaxed` quand `strict` casse le site, et `mixed` quand le site exige de voir la pub.

En `mixed`, la page du site ne change pas : la redirection ou la pop-up (`window.open`) s'ouvre par-dessus, marquée « Publicité · fermeture dans 5 s », puis se ferme seule (au plus 20 secondes si elle ne finit pas de charger). **Retour** la ferme tout de suite. Cette fenêtre n'est pas filtrée, mais ne peut ouvrir ni application ni autre fenêtre. Le blocage des pop-ups est désactivé pour ces sites, puisque la pop-up doit pouvoir s'ouvrir.

### Pop-ups

Avec la protection **Bloquer les pop-ups** (script `block-popups`), injectée **avant** les scripts de la page et dans toutes ses frames : `window.open` est verrouillé (y compris celui d'une iframe créée exprès), et les liens, formulaires et clics simulés vers une nouvelle fenêtre restent dans la fenêtre, où la politique de navigation tranche.

### Apprendre au fil de l'eau : le menu rapide

Quand une navigation est refusée, un message « Redirection bloquée : … » s'affiche. Ouvre alors le **menu rapide** (appui long sur Retour) :

La redirection refusée s'affiche en tête du menu, sur fond ambre :

- **Toujours autoriser** : le domaine est ajouté aux domaines autorisés de ce site et la page s'ouvre. Pour un site de la configuration distante, le choix est gardé **localement** (fichier `site-overrides.json`) : la config distante n'est pas modifiée, et le choix survit à son actualisation comme aux mises à jour.
- **Ouvrir une fois** : laisse passer cette navigation sans rien retenir.
- **Ignorer** : la retire du menu.

Le bouton **Enregistrer** du menu ajoute la page affichée à ton menu.

## 4. Scripts et sélecteurs

Voir [SCRIPTS](SCRIPTS.md) : retirer des bandeaux, des calques, des pop-ups que les autres filtres ne peuvent pas atteindre. Les scripts du site sont injectés en début de document quand la WebView le permet.

## Limites à connaître

- **Pubs servies par le domaine du site lui-même** : bloquables seulement par masquage CSS (règles `##`, `hideSelectors`) ou script, pas par nom d'hôte.
- **Pubs vidéo insérées dans le flux** (hors YouTube) : hors de portée d'un filtre par domaine.
- **Un domaine partagé** entre pub et contenu (CDN commun) : le bloquer casserait le site ; utilise `whitelist`.
- **Cookies tiers** : désactivés (réglage WebView par défaut). Certains lecteurs peuvent l'exiger ; ce sera une option par site, voir [ROADMAP](ROADMAP.md).
- WiDo n'a pas de moteur AdBlock complet (pas de règles par chemin ni de scriptlets). Pour du blocage plus fin sur tout le réseau, ajoute un DNS filtrant (AdGuard Home, Pi-hole) : il complète WiDo.

## Écrire de bonnes règles

1. Ouvre le site, note les redirections refusées affichées par WiDo : ce sont les domaines à autoriser ou à bloquer.
2. Pour trouver les domaines de pub, un navigateur de bureau avec les outils réseau (F12) montre les requêtes.
3. Commence par `rules` sur le site ; remonte en `globalRules` seulement ce qui est commun à tous.
4. Teste toujours la lecture complète d'un contenu après un changement.
