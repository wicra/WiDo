# Scripts intégrés et masquage

## Principe de sécurité

La configuration distante **ne peut pas apporter son propre JavaScript**. Elle choisit seulement parmi les scripts embarqués dans l'APK (`app/src/main/assets/scripts/`) et fournit des sélecteurs CSS. Ainsi, modifier le fichier de configuration (ou compromettre le dépôt de config) ne permet pas d'exécuter du code arbitraire dans tes pages.

Les scripts sont injectés au début du chargement de la page puis à la fin (`onPageStarted` / `onPageFinished`). Un script inconnu ou au nom invalide est ignoré (trace dans les logs).

## Scripts fournis

| Nom | Rôle | Risque |
|---|---|---|
| `block-popups` | Neutralise `window.open` et force les liens `target=_blank` à rester dans la fenêtre (la politique de navigation tranche ensuite). | Faible. |
| `hide-cookie-banners` | Masque les bandeaux de consentement courants (sélecteurs génériques). | Faible. |
| `remove-overlays` | Retire les calques plein écran (`position: fixed`, `z-index ≥ 1000`, couvrant ≥ 80 % de l'écran) sans vidéo ni iframe. | **Heuristique** : peut retirer un calque légitime. À activer site par site. |

## Masquer des éléments : `hideSelectors`

```json
"hideSelectors": [".banniere-promo", "#popup-newsletter", "div[class*='sponsor']"]
```

Chaque sélecteur devient une règle `display:none !important`. Un sélecteur invalide n'affecte pas les autres (une règle par sélecteur). Limites : 200 caractères, pas de `{ } < >`.

## Ajouter un script au projet

1. Crée `app/src/main/assets/scripts/mon-script.js` (nom : `a-z`, `0-9`, tirets).
2. Écris-le de façon **idempotente** (il peut être injecté deux fois par page) : voir `block-popups.js` (drapeau `window.__widoPopups`).
3. Ajoute son nom à l'énumération de [`config/wido.schema.json`](../config/wido.schema.json) et à cette page.
4. Les scripts ne doivent ni envoyer de données ni appeler le réseau.
