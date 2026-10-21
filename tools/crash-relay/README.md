# Relais des rapports de plantage

Le bouton **Envoyer le rapport** (Réglages → Général → Rapport de plantage) envoie le rapport au dépôt
sans rien demander à l'utilisateur : ni compte, ni configuration. Pour cela, l'appli parle à ce petit
relais, qui est **le seul à connaître la clé GitHub**. L'APK, public, n'embarque que l'adresse du relais :
on n'y trouve aucun secret.

```
appli (bouton)  ──POST rapport──▶  relais (Cloudflare Worker, gratuit)  ──clé GitHub──▶  issue du dépôt
```

## Ce que fait le relais

- accepte uniquement un rapport de forme WiDo (en-tête `WiDo x.y.z · appareil`, 8 000 caractères au plus) ;
- limite à 3 rapports par minute et par adresse IP ;
- **regroupe** : même version et même raison de fermeture → un commentaire sur l'issue existante, pas une
  nouvelle issue (étiquette `crash-report`) ;
- dépose le texte dans un bloc de code : pas de lien, de @mention ni de HTML interprétés.

L'appli retire des rapports les **noms et adresses des sites** avant l'envoi, et ne l'envoie qu'à l'appui
sur le bouton. Aucun identifiant de l'appareil n'est envoyé.

## Mise en place (une fois, environ 5 minutes)

1. **Clé GitHub limitée** : GitHub → Settings → Developer settings → *Fine-grained tokens* → nouvelle clé,
   dépôt **`wicra/WiDo` seulement**, permission **Issues : Read and write**, rien d'autre. Même si elle
   fuitait, elle ne pourrait que créer des issues.
2. **Étiquette** : crée l'étiquette `crash-report` dans le dépôt (Issues → Labels).
3. **Déployer** (compte Cloudflare gratuit) :
   ```
   cd tools/crash-relay
   npx wrangler deploy
   npx wrangler secret put GITHUB_TOKEN     # colle la clé de l'étape 1
   ```
   `wrangler` affiche l'adresse du relais (`https://wido-crash-relay.<ton-compte>.workers.dev`).
4. **Brancher l'appli** : dans `gradle.properties`, mets cette adresse :
   ```
   wido.crashUrl=https://wido-crash-relay.<ton-compte>.workers.dev
   ```
   puis publie une nouvelle version. Tant que cette ligne est vide, le bouton ouvre à la place la page
   « Nouvelle issue » de GitHub, pré-remplie (sur téléphone, compte GitHub requis).

## Tester

```
node tools/crash-relay/relay.test.mjs
```

Les tests simulent GitHub : rapports valides, doublons, taille, JSON invalide, limite de débit, panne de GitHub.

## Si le relais est abusé

La limite par IP et la forme imposée du rapport arrêtent le spam simple. En cas de besoin : réduire la
limite dans `wrangler.toml`, ajouter une règle de pare-feu Cloudflare, ou révoquer la clé (l'appli retombe
alors sur « Envoyer a échoué » sans rien casser).
