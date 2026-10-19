# Sécurité

## Modèle de menace

| Menace | Mesure |
|---|---|
| Le dépôt de configuration est compromis | La config ne peut pas apporter de JavaScript (scripts embarqués seulement) ; sélecteurs CSS filtrés ; au pire, elle change la liste des sites et des règles. |
| Config distante altérée en transit | `https` obligatoire (config, listes, mises à jour). |
| APK de mise à jour falsifiée | Origine limitée aux releases de ce dépôt ; SHA-256 obligatoire ; signature Android identique exigée ; confirmation à l'écran. |
| Site malveillant ou publicité | Politique de navigation, blocage par hôte, pas de nouvelles fenêtres, pas de micro/caméra/géolocalisation, accès fichiers désactivé, contenu mixte interdit. |
| Redirection vers une appli ou un store | Les schémas `intent:`, `market:`, `javascript:` et tout non-web sont refusés. |
| Lancement d'un site par une autre app | `PlayerActivity` est exportée pour Home Assistant, mais n'ouvre que des sites **déjà présents dans ta configuration**. |
| Fichier ou liste énorme | Tailles bornées (config 1 Mo, liste 8 Mo, APK 100 Mo, 100 sites, 5000 règles). |

## Ce que WiDo ne protège pas

- Le contenu des sites que tu configures : leur fiabilité reste ta responsabilité.
- Un appareil dont ADB est exposé sur un réseau non fiable : désactive le débogage réseau quand tu n'en as pas besoin.
- `allowInsecure` : le trafic `http` d'un site local est lisible sur le réseau.

## Règles pour contribuer

1. Aucun secret dans le dépôt (jetons, mots de passe, clés de signature).
2. Aucun JavaScript chargé depuis la configuration ou le réseau.
3. Toute nouvelle entrée externe (fichier, URL, intent) est **validée et bornée**.
4. Toute URL de téléchargement est en `https`.
5. Pas de nouvelle permission Android sans la justifier dans cette page.

Permissions actuelles : `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` (mise à jour). `usesCleartextTraffic=true` est nécessaire pour `allowInsecure` ; la politique de navigation refuse `http` partout ailleurs.

## Signaler une vulnérabilité

Ouvre un [signalement privé](https://github.com/wicra/WiDo/security/advisories/new) sur le dépôt plutôt qu'une issue publique.
