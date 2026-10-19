# Blocage : règles et limites

WiDo applique **trois filtres indépendants**.

## 1. Blocage de requêtes par nom d'hôte

Chaque requête de la page (script, image, iframe, vidéo) est vérifiée : si son **nom d'hôte** est dans les règles, elle reçoit une réponse vide (HTTP 403). C'est le même principe qu'un DNS filtrant.

Syntaxes comprises (dans `blocklists`, `globalRules`, `rules`) :

| Syntaxe | Effet |
|---|---|
| `\|\|pub.exemple.com^` | Bloque `pub.exemple.com` **et ses sous-domaines**. |
| `@@\|\|cdn.exemple.com^` | Exception : jamais bloqué. L'exception l'emporte toujours. |
| `0.0.0.0 pub.exemple.com` | Format fichier hosts. |
| `pub.exemple.com` | Nom seul, une ligne par domaine. |
| `! commentaire`, `# commentaire` | Ignoré. |

**Non géré**, compté comme « ignoré » : les règles avec chemin (`\|\|a.com/pub/*`), joker, option (`$third-party`, `$script`…) et les règles cosmétiques (`site.com##.bandeau`). Ce sont celles que les extensions de navigateur savent appliquer et pas WiDo, voir plus bas.

Ordre de décision pour un hôte :
1. dans `whitelist`, ou domaine du site, ou `allowedDomains` ? → **jamais bloqué** ;
2. règle d'exception `@@` ? → jamais bloqué ;
3. règle de blocage (liste, `globalRules`, `rules` du site) ? → bloqué ;
4. sinon → autorisé.

Les listes de `blocklists` sont téléchargées (8 Mo max chacune), mises en cache et rafraîchies au plus toutes les 6 heures. Si le téléchargement échoue, la copie en cache est utilisée.

## 2. Politique de navigation

La fenêtre ne peut ouvrir que : le domaine du site (sans `www.`), ses sous-domaines, et les `allowedDomains`. Toute autre navigation (redirection, lien publicitaire, `window.open`, `intent:`, `market:`, `javascript:`) est **refusée**. Les pop-ups sont ouverts dans la même fenêtre par la WebView puis refusés ici s'ils vont ailleurs.

C'est le filtre le plus efficace contre les redirections publicitaires, et il ne gêne pas le lecteur vidéo (qui charge ses ressources par des sous-requêtes, jugées par le filtre n° 1).

## 3. Scripts et sélecteurs

Voir [SCRIPTS](SCRIPTS.md) : retirer des bandeaux, des calques, des pop-ups que les filtres 1 et 2 ne peuvent pas atteindre.

## Limites à connaître

- **Pubs servies par le domaine du site lui-même** : bloquables seulement par masquage CSS (`hideSelectors`) ou script, pas par nom d'hôte.
- **Pubs vidéo insérées dans le flux** : hors de portée d'un filtre par domaine.
- **Un domaine partagé** entre pub et contenu (CDN commun) : le bloquer casserait le site ; utilise `whitelist`.
- **Cookies tiers** : désactivés (réglage WebView par défaut). Certains lecteurs peuvent l'exiger ; ce sera une option par site, voir [ROADMAP](ROADMAP.md).
- WiDo n'a pas de moteur AdBlock complet. Pour du blocage plus fin sur tout le réseau, ajoute un DNS filtrant (AdGuard Home, Pi-hole) : il complète WiDo.

## Écrire de bonnes règles

1. Ouvre le site, note les redirections refusées affichées par WiDo : ce sont les domaines à autoriser ou à bloquer.
2. Pour trouver les domaines de pub, un navigateur de bureau avec les outils réseau (F12) montre les requêtes.
3. Commence par `rules` sur le site ; remonte en `globalRules` seulement ce qui est commun à tous.
4. Teste toujours la lecture complète d'un contenu après un changement.
