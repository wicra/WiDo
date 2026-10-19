# Installer WiDo sur Android TV / Google TV

Guide pas à pas, de la TV vierge à l'app qui tourne. Écrit pour le Wanbo Vali 1 Pro (Google TV, Android 14), valable pour la plupart des appareils Android TV et Google TV.

> Les noms exacts des menus varient selon la marque et la version. Là où je ne peux pas le garantir, c'est indiqué **(à confirmer)**.

## 0. Récupérer l'APK

| Situation | Où le trouver |
|---|---|
| Une release existe | [Releases](https://github.com/wicra/WiDo/releases) → fichier `wido-vX.Y.Z.apk` (la somme SHA-256 est dans les notes). |
| Pas encore de release | Onglet **Actions** du dépôt → dernière exécution de *Build* réussie → artefact `wido-debug-apk` (un zip contenant l'APK). Il faut être connecté à GitHub. |

Une APK *debug* n'est **pas signée avec la clé de release** : pour passer plus tard à une release, il faudra d'abord la désinstaller (`adb uninstall dev.wido.tv`).

Vérifie la somme quand elle est fournie :

```sh
sha256sum wido-vX.Y.Z.apk
```

## 1. Activer le mode développeur sur la TV

1. **Paramètres → Système → À propos**.
2. Sélectionne **Build Android TV OS** (ou *Build*) et appuie sur OK **7 fois**. Un message annonce que le mode développeur est activé.
3. Reviens en arrière : **Paramètres → Système → Options pour les développeurs**.
4. Active **Débogage USB**. Sur Android 11 et plus, active aussi **Débogage sans fil** **(à confirmer sur ton modèle)**.

Note l'adresse IP de la TV : **Paramètres → Réseau et Internet → ton Wi-Fi**, ou dans *À propos → État*. Donne-lui une adresse fixe (réservation DHCP dans ta box) : c'est indispensable pour Home Assistant plus tard.

## Méthode A (recommandée) : ADB par le Wi-Fi depuis un ordinateur

### Installer ADB sur l'ordinateur

Télécharge les *platform-tools* Android (outil officiel Google), ou :

```sh
# macOS
brew install android-platform-tools
# Debian/Ubuntu
sudo apt install adb
# Windows : winget install Google.PlatformTools
```

### Se connecter à la TV

**Débogage sans fil (Android 11+)**, la TV affiche une adresse `IP:port` :

```sh
adb pair <ip>:<port-d-appairage>      # saisis le code à 6 chiffres affiché par la TV
adb connect <ip>:<port>
```

**Ancien mode (port 5555)** :

```sh
adb connect <ip>:5555
```

La TV affiche alors **« Autoriser le débogage USB ? »** avec une empreinte : coche *Toujours autoriser* et valide avec la télécommande. Vérifie :

```sh
adb devices        # doit afficher « device », pas « unauthorized » ni « offline »
```

### Installer et lancer

```sh
adb install -r wido-vX.Y.Z.apk
adb shell am start -n dev.wido.tv/.MainActivity
```

`-r` réinstalle en conservant les données : c'est aussi la commande de **mise à jour**.

## Méthode B : sans ordinateur, avec l'app Downloader

1. Installe **Downloader** (par AFTVnews) depuis le Play Store de la TV.
2. Autorise-la à installer des apps : **Paramètres → Applications → Accès spécial des applications → Installer des applications inconnues → Downloader → Autoriser** **(chemin à confirmer, il varie)**. Android le propose aussi au premier téléchargement.
3. Ouvre Downloader, saisis l'URL directe de l'APK :
   ```
   https://github.com/wicra/WiDo/releases/download/vX.Y.Z/wido-vX.Y.Z.apk
   ```
4. Une fois téléchargé, **Installer**, puis **Ouvrir**.

Cette méthode suppose qu'une **release** existe (l'artefact d'Actions n'a pas d'URL directe publique).

## Méthode C : clé USB

1. Copie l'APK sur une clé USB (FAT32 ou exFAT).
2. Branche-la sur le projecteur et ouvre-la avec un gestionnaire de fichiers installé depuis le Play Store (par exemple *File Commander* ou *X-plore*).
3. Sélectionne l'APK, autorise l'installation d'apps inconnues pour ce gestionnaire, puis installe.

## 2. Premier lancement

1. Ouvre WiDo : **Paramètres → Applications → Voir toutes les applications → WiDo** (les apps installées hors Play Store n'apparaissent pas toujours dans la rangée d'accueil **(à confirmer)**).
2. Le menu affiche les sites de test de `config/wido.json` et l'état de la configuration (« Configuration à jour · révision N »).
3. Si l'état affiche « Hors ligne » ou « intégrée », la TV n'a pas pu lire la configuration : vérifie le réseau et l'URL ([INSTALL](INSTALL.md)).
4. Ouvre les sites de test, voir [TESTING](TESTING.md).

Pour retrouver facilement l'app, tu peux la lancer depuis Home Assistant ([HOME-ASSISTANT](HOME-ASSISTANT.md)) ou la déplacer en tête de liste avec un lanceur d'applications.

## 3. Mettre à jour

- **Depuis l'app** : bannière « Mise à jour vX.Y.Z disponible » dans le menu → *Installer*. Android demande de confirmer, et la première fois d'autoriser WiDo à installer des apps ([UPDATES](UPDATES.md)).
- **Depuis l'ordinateur** : `adb install -r wido-vX.Y.Z.apk` (sans confirmation sur la TV).

## 4. Désinstaller

```sh
adb uninstall dev.wido.tv
```

ou **Paramètres → Applications → WiDo → Désinstaller**.

## Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `adb devices` : `unauthorized` | L'autorisation n'a pas été validée sur la TV. | Révoque les autorisations dans *Options pour les développeurs*, reconnecte-toi, valide la fenêtre. |
| `adb devices` : `offline` | Connexion périmée. | `adb disconnect`, puis `adb connect` à nouveau. |
| `failed to connect … Connection refused` | Débogage réseau désactivé, mauvais port, ou TV en veille. | Réveille la TV, vérifie l'IP et le port affichés. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Une version signée avec une autre clé est installée (debug ↔ release). | `adb uninstall dev.wido.tv` puis réinstalle. |
| `INSTALL_FAILED_OLDER_SDK` | Android trop ancien. | WiDo exige Android 8.0 (API 26) ou plus. |
| `INSTALL_PARSE_FAILED_…` | APK corrompue ou incomplète. | Retélécharge, vérifie le SHA-256. |
| L'app ne s'ouvre pas depuis l'accueil | Apps hors Play Store masquées. | Passe par *Paramètres → Applications*, ou `adb shell am start -n dev.wido.tv/.MainActivity`. |
| Menu vide, « Hors ligne » | Config injoignable. | Ouvre l'URL de `config/wido.json` dans un navigateur ; si 404, le dépôt est privé ou le chemin faux. |
| Mise à jour refusée : « pas de somme SHA-256 » | La release ne contient pas `sha256: …`. | Republie via la CI ([RELEASING](RELEASING.md)). |

## Sécurité

- Désactive le **débogage réseau** quand tu n'en as pas besoin : n'importe quel appareil de ton réseau pourrait sinon proposer une connexion (la TV demande validation, mais autant réduire l'exposition).
- N'installe que des APK téléchargées depuis **ce dépôt**, et vérifie leur SHA-256.
