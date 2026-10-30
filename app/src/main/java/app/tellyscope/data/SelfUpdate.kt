// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.tellyscope.adb.Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tellyscope is about 3 MB; anything far past that isn't it. */
internal const val MAX_SELF_APK_BYTES = 64L shl 20

/**
 * Update from inside the app: the release's APK comes from GitHub and goes to Android's installer,
 * which asks the user to confirm and refuses an APK not signed like the installed app.
 */
interface SelfUpdate {
    /** "Install unknown apps" is on for this app. */
    fun allowed(): Boolean

    /** Android's Install unknown apps page for this app. */
    fun openSettings()

    suspend fun fetch(update: Update, onProgress: (Progress) -> Unit): File

    /** Hands [apk] to Android's installer. */
    fun install(apk: File)
}

class AndroidSelfUpdate(context: Context) : SelfUpdate {
    private val ctx = context.applicationContext
    private val dir get() = File(ctx.cacheDir, "update")

    override fun allowed() = ctx.packageManager.canRequestPackageInstalls()

    override fun openSettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override suspend fun fetch(update: Update, onProgress: (Progress) -> Unit): File = withContext(Dispatchers.IO) {
        val url = update.apk ?: throw IOException("no APK in the release")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "tellyscope.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf).takeIf { it >= 0 } ?: break
                        done += n
                        if (done > update.size) throw IOException("larger than the release says")
                        out.write(buf, 0, n)
                        onProgress(Progress(done, update.size))
                    }
                    if (done != update.size) throw IOException("download cut short")
                }
            }
        } finally {
            c.disconnect()
        }
        // The installer checks the signature; this only makes sure it's this app at the offered version.
        @Suppress("DEPRECATION") // the flags overload is API 33+
        val pkg = ctx.packageManager.getPackageArchiveInfo(file.path, 0)
        if (pkg == null || pkg.packageName != ctx.packageName || pkg.versionName != update.version) throw IOException("not Tellyscope ${update.version}")
        file
    }

    override fun install(apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
