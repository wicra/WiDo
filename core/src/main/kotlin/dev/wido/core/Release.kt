package dev.wido.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ReleaseInfo(
    val tag: String,
    val version: Version,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long?,
    /** SHA-256 attendu de l'APK, en hexadécimal minuscule. Sans lui, l'app refuse d'installer. */
    val sha256: String?,
)

/** Lit la réponse de `GET /repos/{owner}/{repo}/releases/latest` de l'API GitHub. */
object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val bodyHash = Regex("sha-?256\\s*[:=]\\s*`?([a-fA-F0-9]{64})`?", RegexOption.IGNORE_CASE)

    fun parse(text: String): ReleaseInfo? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return null
        }
        if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = Version.parse(tag) ?: return null
        val notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val assets = try {
            root["assets"]?.jsonArray?.map { it.jsonObject } ?: return null
        } catch (_: Exception) {
            return null
        }
        val apk = assets.firstOrNull {
            it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
        } ?: return null

        val url = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        if (Hosts.schemeOf(url) != "https" || Hosts.of(url) != "github.com") return null

        // GitHub fournit `digest: "sha256:…"` sur les assets récents ; sinon on cherche dans les notes.
        val digest = apk["digest"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("sha256:")
            ?.takeIf { d -> d.length == 64 && d.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        val sha = (digest ?: bodyHash.find(notes)?.groupValues?.get(1))?.lowercase()

        return ReleaseInfo(
            tag = tag,
            version = version,
            notes = notes,
            apkUrl = url,
            apkSize = apk["size"]?.jsonPrimitive?.longOrNull,
            sha256 = sha,
        )
    }
}
