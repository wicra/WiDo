# Contribuer

Merci ! Issues et pull requests sont bienvenues, en français ou en anglais.

1. Lis [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) et [docs/SECURITY.md](docs/SECURITY.md).
2. Toute règle de décision va dans `core` **avec un test** : `./gradlew :core:test`.
3. Les fichiers de `config/` doivent rester valides (`ShippedConfigTest` le vérifie).
4. Ne propose pas de sites de streaming dans les exemples : utilise des domaines `example.*`.
5. Pas de secret, pas de JavaScript chargé depuis le réseau, entrées externes validées et bornées.
6. Message de commit clair, une idée par commit.

Pour une nouvelle fonction qui touche l'interface ou la WebView, décris dans la PR comment tu l'as vérifiée sur appareil.
