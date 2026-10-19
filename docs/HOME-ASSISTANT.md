# Intégration Home Assistant

WiDo ne dépend pas de Home Assistant : celui-ci se contente de **lancer** l'app, avec l'identifiant du site à ouvrir.

## Prérequis

- Le projecteur est ajouté à Home Assistant via l'intégration **Android TV** (ADB). Il faut activer le débogage ADB réseau sur le projecteur ([INSTALL](INSTALL.md)).
- Les noms d'entités ci-dessous (`media_player.projecteur`…) sont des exemples à adapter.

## Ouvrir un site

```yaml
action: androidtv.adb_command
target:
  entity_id: media_player.projecteur
data:
  command: "am start -n dev.wido.tv/.PlayerActivity --es site site-a"
```

`site-a` est l'`id` du site dans la configuration. Un identifiant inconnu ou désactivé affiche un message puis ferme l'écran.

## Scène « Soirée cinéma »

```yaml
script:
  soiree_cinema:
    alias: Soirée cinéma
    sequence:
      - action: media_player.turn_on
        target: { entity_id: media_player.projecteur }
      - delay: "00:00:15"                      # le projecteur démarre
      - action: media_player.volume_set
        target: { entity_id: media_player.projecteur }
        data: { volume_level: 0.4 }
      - action: remote.send_command            # mise au point, si commandable en infrarouge
        target: { entity_id: remote.emetteur_ir }
        data: { device: projecteur, command: focus_plus, num_repeats: 5 }
      - action: androidtv.adb_command
        target: { entity_id: media_player.projecteur }
        data:
          command: "am start -n dev.wido.tv/.MainActivity"
```

Ce que HA peut ou ne peut pas faire dépend du modèle : la mise au point est souvent réservée à la télécommande infrarouge, et l'entrée HDMI dépend de ce que le projecteur expose. **À vérifier sur ton appareil.** Pour l'app WiDo, la partie utile est la dernière action.

## Commande vocale

Expose le script `soiree_cinema` à Assist (ou à ton assistant vocal) : « Lance la soirée cinéma ». Pour ouvrir directement un site, crée un script par site avec la commande `am start … --es site <id>`.

## Changer l'URL de configuration à distance

```yaml
command: >-
  am start -n dev.wido.tv/.MainActivity
  --es config_url "https://raw.githubusercontent.com/<toi>/<repo>/main/config/wido.json"
```

## Mettre à jour sans toucher la TV

```sh
adb connect <ip>:5555
adb install -r wido-vX.Y.Z.apk
```

(Installation silencieuse, sans confirmation sur l'écran.)
