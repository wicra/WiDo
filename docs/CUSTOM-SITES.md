# Sites personnels (ajoutés depuis l'app)

Tu peux ajouter tes propres sites directement sur la TV, sans modifier la configuration distante.

## Ajouter

1. Dans le menu, va sur la dernière carte **« + Ajouter un site »** et appuie sur OK.
2. Saisis un **nom** et une **adresse** avec le clavier de la TV. `exemple.org` suffit : `https://` est ajouté tout seul.
3. **Enregistrer**. La carte apparaît avec le badge **PERSO**.

## Modifier ou supprimer

Sur une carte PERSO : **appui long sur OK** (ou la touche **Menu** de la télécommande). Pour supprimer, un premier appui sur *Supprimer* demande une confirmation.

## Ce qui est appliqué à un site perso

| Élément | Valeur |
|---|---|
| Listes de blocage et règles globales de la configuration distante | **Oui** |
| Politique de navigation | Oui : seul le domaine du site est autorisé |
| Script `block-popups` | Oui, par défaut |
| `allowedDomains`, `rules`, `hideSelectors`, `userAgent` | Non (réglages disponibles seulement pour les sites de la configuration) |
| Icône et couleur de la carte | Détectées automatiquement, comme pour les autres cartes |
| `http://` | Accepté (serveur du réseau local) : le site est alors marqué `allowInsecure` |

Si un site perso a besoin de réglages fins (domaines supplémentaires, règles propres), ajoute-le plutôt à la configuration distante ([CONFIGURATION](CONFIGURATION.md)).

## Ce qui est conservé, et quand

Les sites perso sont enregistrés dans un fichier **local** de l'app (`local-sites.json`), séparé de la configuration distante.

| Événement | Sites perso |
|---|---|
| *Actualiser* / nouvelle configuration distante | **Conservés** |
| Mise à jour depuis l'app (bannière) | **Conservés** |
| `adb install -r` (mise à jour par ordinateur) | **Conservés** |
| Désinstaller puis réinstaller | **Perdus** |
| « Effacer les données » dans les réglages Android | **Perdus** |

Si la configuration distante contient plus tard un site avec le **même identifiant** qu'un site perso, c'est celui de la configuration qui s'affiche. Les identifiants perso commencent toujours par `perso-` : il n'y a normalement pas de collision.

Limites : 50 sites perso, 40 caractères pour un nom. Il n'y a pas encore de sauvegarde ni d'export : voir [ROADMAP](ROADMAP.md).
