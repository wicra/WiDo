package dev.wido.core

/**
 * Ensemble de noms d'hôtes en lecture seule, sous forme d'empreintes de 64 bits triées (8 octets par
 * nom au lieu d'une quinzaine d'octets de chaîne plus l'entrée de table : environ dix fois moins de
 * mémoire pour les listes de blocage de plusieurs centaines de milliers de domaines). Sert aux
 * appareils à peu de mémoire.
 *
 * Contrepartie assumée : on ne peut plus relire les noms (pas d'itération), et deux noms différents
 * ont une chance sur 2^64 d'avoir la même empreinte (de l'ordre de 10^-10 pour une liste entière
 * consultée des milliers de fois) : au pire un domaine de plus bloqué, jamais un blocage manqué.
 */
class CompactHostSet private constructor(private val hashes: LongArray) : AbstractSet<String>() {
    override val size: Int get() = hashes.size

    override fun contains(element: String): Boolean = java.util.Arrays.binarySearch(hashes, hash(element)) >= 0

    override fun iterator(): Iterator<String> =
        throw UnsupportedOperationException("Ensemble compact : les noms ne sont pas conservés")

    override fun equals(other: Any?): Boolean = other is CompactHostSet && hashes.contentEquals(other.hashes)

    override fun hashCode(): Int = hashes.contentHashCode()

    override fun toString(): String = "CompactHostSet(size=$size)"

    /** Rassemble des noms (en doublons si besoin) sans jamais garder les chaînes. */
    class Builder(capacity: Int = 1024) {
        private var data = LongArray(capacity.coerceAtLeast(16))
        private var count = 0

        fun add(host: String) {
            if (count == data.size) data = data.copyOf(data.size * 2)
            data[count++] = hash(host)
        }

        fun build(): CompactHostSet {
            val sorted = data.copyOf(count).also { it.sort() }
            // Doublons retirés (une liste peut répéter un domaine).
            var unique = 0
            for (i in sorted.indices) if (i == 0 || sorted[i] != sorted[i - 1]) sorted[unique++] = sorted[i]
            return CompactHostSet(if (unique == sorted.size) sorted else sorted.copyOf(unique))
        }
    }

    companion object {
        val EMPTY = CompactHostSet(LongArray(0))

        fun of(hosts: Collection<String>): CompactHostSet = Builder(hosts.size).also { b -> hosts.forEach(b::add) }.build()

        /** FNV-1a sur 64 bits, puis mélange final (murmur3) pour répartir les bits. */
        internal fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            h = h xor (h ushr 33)
            h *= -0xae502812aa7333L
            h = h xor (h ushr 33)
            h *= -0x3b314601e57a13adL
            return h xor (h ushr 33)
        }
    }
}
