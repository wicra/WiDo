#!/usr/bin/env bash
# Crée la clé de signature de WiDo et l'enregistre comme secrets GitHub Actions.
# À lancer sur TON ordinateur (Linux, macOS, ou Git Bash sous Windows).
# Prérequis : un JDK 17+ (keytool) ; la CLI `gh` connectée (`gh auth login`) pour l'envoi automatique.
#
# Usage : scripts/setup-signing.sh [propriétaire/dépôt] [dossier-de-sortie]
set -euo pipefail

REPO="${1:-wicra/WiDo}"
OUT="${2:-$HOME/wido-signing}"
KS="$OUT/wido.jks"

command -v keytool >/dev/null || { echo "keytool introuvable : installe un JDK 17 ou plus." >&2; exit 1; }
[ -e "$KS" ] && { echo "$KS existe déjà : je refuse de l'écraser (perdre la clé empêche toute mise à jour)." >&2; exit 1; }

mkdir -p "$OUT"
chmod 700 "$OUT"

read -r -s -p "Choisis un mot de passe pour la clé (12 caractères minimum) : " PASS
echo
[ "${#PASS}" -ge 12 ] || { echo "Mot de passe trop court." >&2; exit 1; }

keytool -genkeypair -keystore "$KS" -alias wido \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=WiDo, O=WiDo"
chmod 600 "$KS"

B64="$(base64 < "$KS" | tr -d '\n')"

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  echo "Envoi des 4 secrets vers $REPO…"
  printf %s "$B64"  | gh secret set WIDO_KEYSTORE_BASE64   --repo "$REPO"
  printf %s "$PASS" | gh secret set WIDO_KEYSTORE_PASSWORD --repo "$REPO"
  printf %s "wido"  | gh secret set WIDO_KEY_ALIAS         --repo "$REPO"
  printf %s "$PASS" | gh secret set WIDO_KEY_PASSWORD      --repo "$REPO"
  echo "Secrets enregistrés."
else
  printf %s "$B64" > "$OUT/wido.jks.base64"
  chmod 600 "$OUT/wido.jks.base64"
  cat <<MSG

La CLI gh n'est pas connectée : crée les secrets à la main
(GitHub → $REPO → Settings → Secrets and variables → Actions → New repository secret) :

  WIDO_KEYSTORE_BASE64    contenu du fichier $OUT/wido.jks.base64
  WIDO_KEYSTORE_PASSWORD  le mot de passe que tu viens de saisir
  WIDO_KEY_ALIAS          wido
  WIDO_KEY_PASSWORD       le même mot de passe

Puis supprime $OUT/wido.jks.base64.
MSG
fi

echo
echo "IMPORTANT : sauvegarde $KS et ton mot de passe dans un gestionnaire de mots de passe."
echo "Sans eux, impossible de publier une mise à jour installable par-dessus l'existante."
