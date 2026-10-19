# Installation

Le guide complet est dans **[INSTALL-ANDROID-TV.md](INSTALL-ANDROID-TV.md)** : mode développeur, trois méthodes d'installation (ADB, Downloader, clé USB), mise à jour, dépannage.

## Version courte

```sh
adb connect <ip-de-la-tv>:5555
adb install -r wido-vX.Y.Z.apk
adb shell am start -n dev.wido.tv/.MainActivity
```

## Pointer vers ta configuration

Par défaut, l'app lit `https://raw.githubusercontent.com/wicra/WiDo/main/config/wido.json`. Pour une autre URL (ton propre dépôt, un gist), sans recompiler :

```sh
adb shell am start -n dev.wido.tv/.MainActivity \
  --es config_url "https://raw.githubusercontent.com/<toi>/<repo>/main/config/wido.json"
```

L'URL est mémorisée sur l'appareil. Seul `https` est accepté.

## Vérifier le DRM de l'appareil

Installe « DRM Info » depuis le Play Store et vérifie `Widevine … L1`. Les tests relevés pour le Wanbo Vali 1 Pro annoncent L1, sans fiche officielle : à confirmer sur ton exemplaire.
