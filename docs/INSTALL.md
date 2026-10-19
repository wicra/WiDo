# Installation

## Prérequis

- Un appareil Google TV / Android TV (appareil visé : Wanbo Vali 1 Pro, Google TV, Android 14).
- Un ordinateur avec `adb` (Android platform-tools) sur le même réseau.
- Le **débogage USB / ADB réseau** activé sur le projecteur : *Paramètres → Système → À propos*, appuyer 7 fois sur « Build », puis *Options pour les développeurs → Débogage USB*.

## Installer l'APK

1. Télécharge `wido-vX.Y.Z.apk` depuis les [releases du dépôt](https://github.com/wicra/WiDo/releases).
2. Vérifie la somme (publiée dans les notes de la release) :
   ```sh
   sha256sum wido-vX.Y.Z.apk
   ```
3. Connecte-toi au projecteur puis installe :
   ```sh
   adb connect <ip-du-projecteur>:5555
   adb install -r wido-vX.Y.Z.apk
   ```
4. Lance WiDo depuis la liste d'apps de la TV.

## Pointer vers ta configuration

Par défaut, l'app lit `https://raw.githubusercontent.com/wicra/WiDo/main/config/wido.json`. Pour utiliser une autre URL (ton propre dépôt, un gist) sans recompiler :

```sh
adb shell am start -n dev.wido.tv/.MainActivity --es config_url "https://raw.githubusercontent.com/<toi>/<repo>/main/config/wido.json"
```

L'URL est mémorisée sur l'appareil. Seul `https` est accepté.

## Autoriser les mises à jour depuis l'app

La première mise à jour demande d'autoriser WiDo à « installer des apps inconnues » (écran Android qui s'ouvre tout seul). Sur certains appareils cet écran est absent : dans ce cas, mets à jour par `adb install -r` (voir [UPDATES](UPDATES.md)).

## À confirmer sur appareil réel

- Présence de l'écran d'autorisation d'installation sur Google TV.
- Niveau Widevine : installe « DRM Info » depuis le Play Store et vérifie `L1`.
