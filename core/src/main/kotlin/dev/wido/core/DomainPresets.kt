package dev.wido.core

/** Domaines courants proposés en un bouton, pour éviter de les saisir à la télécommande. */
object DomainPresets {
    /** Pages de connexion et de consentement des grands comptes. */
    val LOGINS = listOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "consent.google.com",
        "consent.youtube.com",
        "login.microsoftonline.com",
        "login.live.com",
        "appleid.apple.com",
        "facebook.com",
    )

    fun hasLogins(allowed: List<String>): Boolean = LOGINS.all { it in allowed }

    /** `allowed` avec ou sans les domaines de connexion. */
    fun withLogins(allowed: List<String>, enabled: Boolean): List<String> =
        if (enabled) (allowed + LOGINS).distinct() else allowed.filterNot { it in LOGINS }

    /** Les domaines saisis ou appris, sans ceux du préréglage de connexion. */
    fun custom(allowed: List<String>): List<String> = allowed.filterNot { it in LOGINS }
}
