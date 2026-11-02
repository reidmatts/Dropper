package mod.dropper.com.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mod.dropper.com.DropperApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

@Serializable
data class DownloadItemPayload(
    val title: String,
    val url: String,
    val fileName: String,
    val expectedSha512: String?,
    val expectedSize: Long
)

class ModDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_INSTANCE_PATH = "instance_path"
        const val KEY_PAYLOAD_JSON = "payload_json"

        const val PROGRESS_CURRENT_FILE = "progress_current_file"
        const val PROGRESS_BYTES_READ = "progress_bytes_read"
        const val PROGRESS_TOTAL_BYTES = "progress_total_bytes"
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_ITEM_INDEX = "progress_item_index"
        const val PROGRESS_ITEM_COUNT = "progress_item_count"

        const val OUTPUT_INSTALLED_FILES = "output_installed_files"
        const val OUTPUT_ERROR_MESSAGE = "output_error_message"

        // Hard 250 MB cap per mod .jar to prevent disk exhaustion / zip-bomb stream DoS
        private const val MAX_MOD_SIZE_BYTES = 250L * 1024L * 1024L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val instancePath = inputData.getString(KEY_INSTANCE_PATH)
            ?: return@withContext Result.failure(workDataOf(OUTPUT_ERROR_MESSAGE to "Missing target instance path"))
        val payloadJson = inputData.getString(KEY_PAYLOAD_JSON)
            ?: return@withContext Result.failure(workDataOf(OUTPUT_ERROR_MESSAGE to "Missing download payload"))

        val app = applicationContext as? DropperApp
            ?: return@withContext Result.failure(workDataOf(OUTPUT_ERROR_MESSAGE to "Application context not initialized"))
        val storageManager = app.container.storageManager
        val json: Json = app.container.json

        val items = runCatching {
            json.decodeFromString<List<DownloadItemPayload>>(payloadJson)
        }.getOrElse { e ->
            return@withContext Result.failure(
                workDataOf(OUTPUT_ERROR_MESSAGE to "Invalid download payload: ${e.message}")
            )
        }

        val installedNames = mutableListOf<String>()

        for ((index, item) in items.withIndex()) {
            val rawBaseName = item.fileName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim()
            val safeFileName = if (rawBaseName.endsWith(".jar", ignoreCase = true)) {
                rawBaseName
            } else {
                "$rawBaseName.jar"
            }

            if (safeFileName.contains("..")) {
                return@withContext Result.failure(
                    workDataOf(OUTPUT_ERROR_MESSAGE to "Rejected unsafe filename: $safeFileName")
                )
            }

            val tempFile = File(applicationContext.cacheDir, "dropper_dl_${System.currentTimeMillis()}_$safeFileName")
            try {
                val digest = MessageDigest.getInstance("SHA-512")
                val connection = openSecureHttpsConnectionWithRedirects(item.url)

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(
                        workDataOf(OUTPUT_ERROR_MESSAGE to "HTTP $responseCode downloading $safeFileName")
                    )
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: item.expectedSize
                if (totalBytes > MAX_MOD_SIZE_BYTES) {
                    return@withContext Result.failure(
                        workDataOf(OUTPUT_ERROR_MESSAGE to "File $safeFileName exceeds 250 MB safety limit")
                    )
                }

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesReadTotal = 0L
                        var lastReportedPercent = -1
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            bytesReadTotal += read
                            if (bytesReadTotal > MAX_MOD_SIZE_BYTES) {
                                throw IllegalStateException("Download stream exceeded 250 MB safety limit")
                            }

                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)

                            val percent = if (totalBytes > 0) {
                                ((bytesReadTotal * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }

                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                setProgress(
                                    workDataOf(
                                        PROGRESS_CURRENT_FILE to safeFileName,
                                        PROGRESS_BYTES_READ to bytesReadTotal,
                                        PROGRESS_TOTAL_BYTES to totalBytes,
                                        PROGRESS_PERCENT to percent,
                                        PROGRESS_ITEM_INDEX to (index + 1),
                                        PROGRESS_ITEM_COUNT to items.size
                                    )
                                )
                            }
                        }
                    }
                }

                // Verify JAR/ZIP header magic bytes (PK\x03\x04)
                if (!hasValidJarZipMagicBytes(tempFile)) {
                    return@withContext Result.failure(
                        workDataOf(
                            OUTPUT_ERROR_MESSAGE to "Security check failed: $safeFileName is not a valid JAR/ZIP archive"
                        )
                    )
                }

                // Verify SHA-512 hash if present
                if (!item.expectedSha512.isNullOrBlank()) {
                    val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computedHash.equals(item.expectedSha512, ignoreCase = true)) {
                        return@withContext Result.failure(
                            workDataOf(
                                OUTPUT_ERROR_MESSAGE to "SHA-512 mismatch for $safeFileName. Expected ${item.expectedSha512.take(12)}..., got ${computedHash.take(12)}..."
                            )
                        )
                    }
                }

                // Install verified jar into active instance's mods/ folder
                FileInputStream(tempFile).use { verifiedStream ->
                    storageManager.installMod(
                        fileStream = verifiedStream,
                        instancePath = instancePath,
                        fileName = safeFileName
                    ).getOrThrow()
                }

                installedNames.add(safeFileName)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    workDataOf(
                        OUTPUT_ERROR_MESSAGE to "Failed installing $safeFileName: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    )
                )
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        Result.success(
            workDataOf(
                OUTPUT_INSTALLED_FILES to installedNames.toTypedArray()
            )
        )
    }

    /**
     * Opens an HTTPS-only connection and validates each redirect hop to prevent
     * protocol downgrade (HTTPS -> HTTP) and SSRF to local/private network addresses.
     */
    private fun openSecureHttpsConnectionWithRedirects(initialUrl: String): HttpsURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 5) {
            val parsedUrl = URL(currentUrl)
            require(parsedUrl.protocol.equals("https", ignoreCase = true)) {
                "Blocked non-HTTPS download URL: ${parsedUrl.protocol}"
            }
            val host = parsedUrl.host
            require(host.isNotBlank()) { "Invalid download URL host" }
            val address = InetAddress.getByName(host)
            require(
                !address.isLoopbackAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isAnyLocalAddress
            ) {
                "Blocked connection to private/local network address"
            }

            val conn = (parsedUrl.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Dropper-Android/1.0.0 (mod.dropper.com)")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    return conn
                }
                currentUrl = URL(parsedUrl, location).toString()
                redirects++
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects while downloading $initialUrl")
    }

    private fun hasValidJarZipMagicBytes(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val header = ByteArray(4)
        val bytesRead = FileInputStream(file).use { it.read(header) }
        if (bytesRead < 4) return false
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }
}
