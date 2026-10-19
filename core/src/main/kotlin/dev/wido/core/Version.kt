package dev.wido.core

/** Version de type `1.2.3` ou `v1.2.3-beta1`. Une version finale passe devant sa pré-version. */
data class Version(val numbers: List<Int>, val pre: String?) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        val n = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until n) {
            val c = (numbers.getOrElse(i) { 0 }).compareTo(other.numbers.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when {
            pre == other.pre -> 0
            pre == null -> 1
            other.pre == null -> -1
            else -> pre.compareTo(other.pre)
        }
    }

    companion object {
        fun parse(text: String): Version? {
            val s = text.trim().removePrefix("v").removePrefix("V")
            val dash = s.indexOf('-')
            val core = if (dash >= 0) s.substring(0, dash) else s
            val pre = if (dash >= 0) s.substring(dash + 1).ifEmpty { null } else null
            val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
            if (numbers.isEmpty()) return null
            return Version(numbers, pre)
        }
    }
}
