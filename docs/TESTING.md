# Tester WiDo sur un appareil

La configuration livrée (`config/wido.json`) contient **trois sites de test légaux** et une liste de blocage publique. Ils servent à vérifier chaque fonction de l'app avant d'ajouter tes propres sites.

> Ces sites sont des services tiers : leur adresse ou leur contenu peuvent changer. S'ils ne répondent plus, remplace-les dans `config/wido.json`. Je n'ai pas pu les ouvrir depuis l'environnement de développement : leur disponibilité est **à confirmer**.

## Les sites de test

| `id` | Site | Ce qu'il permet de vérifier |
|---|---|---|
| `test-pubs` | Page de test de blocage de pubs (d3ward) | Le blocage par domaine : la page tente de joindre des régies publicitaires et affiche le nombre de domaines bloqués. |
| `test-drm` | Démo du lecteur Shaka (Google) | Un lecteur vidéo réel, avec des contenus de test, dont certains chiffrés Widevine : lecture, plein écran, DRM. |
| `archive` | Internet Archive, films libres | Un vrai site de vidéo sans DRM : navigation au curseur, lecture, plein écran, clavier TV sur la recherche. |

## La liste de blocage

`config/wido.json` référence la liste publique **StevenBlack/hosts** (format hosts). Mesurée avec le vrai analyseur du projet : environ 71 000 domaines chargés en 1,3 seconde, 13 lignes ignorées. Elle bloque des régies et traqueurs connus ; elle ne se prétend pas exhaustive.

## Check-list manuelle

Coche au fur et à mesure (et corrige `docs/ROADMAP.md` quand c'est validé).

**Menu**
- [ ] Les trois cartes s'affichent, la première a le focus.
- [ ] L'état de la configuration affiche « Configuration à jour · révision 2 ».
- [ ] *Actualiser* recharge sans erreur.

**Blocage (`test-pubs`)**
- [ ] La page s'affiche entièrement.
- [ ] Une grande part des tests indique « bloqué ».
- [ ] Menu rapide (appui long sur Retour) : le compteur de requêtes bloquées est supérieur à 0.

**Lecteur (`test-drm`)**
- [ ] Une vidéo non chiffrée se lit.
- [ ] Un contenu **Widevine** se lit (sinon : appareil en L3, voir [INSTALL](INSTALL.md)).
- [ ] Le plein écran fonctionne ; **Retour** en sort.
- [ ] Lecture/pause et ±10 s à la télécommande.

**Navigation (`archive`)**
- [ ] Le curseur se déplace, accélère si on maintient, fait défiler la page aux bords.
- [ ] **OK** ouvre un lien ; **Retour** revient à la page précédente, puis au menu.
- [ ] Sur le champ de recherche, **OK** ouvre le clavier de la TV ; la saisie arrive dans la page.
- [ ] Dictée vocale du clavier Google dans ce champ (peut ne pas marcher).

**Redirections**
- [ ] Cliquer un lien vers un autre domaine affiche « Redirection bloquée : … » et ne quitte pas la page.

**Mise à jour et lancement**
- [ ] `adb shell am start -n dev.wido.tv/.PlayerActivity --es site archive` ouvre le site directement.
- [ ] Un identifiant inconnu affiche un message puis ferme l'écran.

## Si quelque chose ne marche pas

Récupère les journaux de l'app :

```sh
adb logcat -s WiDoConfig WiDoScripts chromium AndroidRuntime
```

et ouvre une issue avec la sortie, le modèle de la TV et ce que tu attendais.

## Passer à tes propres sites

1. Garde `test-pubs` tant que tu règles tes filtres : c'est ton baromètre.
2. Ajoute tes sites dans `config/wido.json` ([CONFIGURATION](CONFIGURATION.md)).
3. Augmente `revision`, commit, *Actualiser* sur la TV.
