package dev.wido.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import dev.wido.core.ReleaseInfo
import dev.wido.core.ReleaseParser
import dev.wido.core.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class UpdateException(@StringRes val messageRes: Int, val detail: String? = null) : Exception(detail)

enum class InstallResult { Started, NeedsPermission }

/**
 * Met à jour l'app depuis les releases GitHub du dépôt, sans serveur :
 * lit `releases/latest`, télécharge l'APK, vérifie sa somme SHA-256 publiée, puis passe la main
 * à l'installateur Android, qui demande confirmation et refuse toute APK signée par une autre clé.
 */
class UpdateManager(private val context: Context, private val repo: String) {

    /** La release la plus récente si elle est plus récente que l'app installée, sinon null. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val body = Http.get(
            "https://api.github.com/repos/$repo/releases/latest",
            maxBytes = 512_000,
            headers = mapOf("Accept" to "application/vnd.github+json"),
        )
        val info = ReleaseParser.parse(String(body, Charsets.UTF_8)) ?: return@withContext null
        // L'APK doit venir des releases de CE dépôt, pas d'une autre origine.
        if (!info.apkUrl.startsWith("https://github.com/$repo/releases/download/")) return@withContext null
        val current = Version.parse(BuildConfig.VERSION_NAME) ?: return@withContext null
        info.takeIf { it.version > current }
    }

    suspend fun download(info: ReleaseInfo, onProgress: (percent: Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val expected = info.sha256 ?: throw UpdateException(R.string.update_no_hash)
            val dir = File(context.cacheDir, "updates").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val file = File(dir, "wido-${info.tag}.apk")
            try {
                Http.download(info.apkUrl, file, MAX_APK_BYTES) { done, total ->
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            } catch (e: Exception) {
                throw UpdateException(R.string.update_failed, e.message)
            }
            if (!sha256(file).equals(expected, ignoreCase = true)) {
                file.delete()
                throw UpdateException(R.string.update_bad_hash)
            }
            file
        }

    fun install(apk: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallResult.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallResult.Started
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_APK_BYTES = 100L * 1024 * 1024
    }
}
