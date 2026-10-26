package dev.wido.tv

/**
 * Lien entre la page du site et le lecteur léger qui l'a remplacée à l'écran, sur un appareil à peu de mémoire.
 *
 * La page reste chargée derrière le film (le retour au site est alors instantané, sans rechargement) ; elle
 * n'est vidée que si la mémoire vient à manquer : Android le signale, ou le lecteur voit son image se figer.
 */
object NativeHandoff {
    /** Vide la page laissée derrière le lecteur léger (null : aucune page en attente). Fil principal. */
    var releasePage: ((why: String) -> Unit)? = null

    /** « Épisode suivant » trouvé dans la page au départ du film (son libellé), proposé à la fin ; null : aucun. */
    @Volatile var nextEpisode: String? = null

    fun requestRelease(why: String) {
        releasePage?.invoke(why)
    }
}
