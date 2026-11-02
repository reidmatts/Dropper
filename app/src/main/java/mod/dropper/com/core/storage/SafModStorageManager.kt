package mod.dropper.com.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mod.dropper.com.domain.model.InstalledMod
import java.io.InputStream

class SafModStorageManager(
    private val context: Context
) : ModStorageManager {

    private fun sanitizeAndValidateModName(rawFileName: String): String {
        require(!rawFileName.contains("..")) {
            "Path traversal detected in SAF mod filename: $rawFileName"
        }
        val baseName = rawFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        require(baseName.isNotEmpty() && !baseName.contains("..")) {
            "Invalid SAF mod filename: $rawFileName"
        }
        require(
            baseName.endsWith(".jar", ignoreCase = true) ||
                baseName.endsWith(".jar.disabled", ignoreCase = true)
        ) {
            "Only .jar and .jar.disabled files are permitted: $baseName"
        }
        return baseName
    }

    private fun getOrCreateModsDir(instanceUriString: String): DocumentFile {
        val treeUri = Uri.parse(instanceUriString)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Cannot resolve SAF tree URI: $instanceUriString")

        if (rootDoc.name.equals("mods", ignoreCase = true)) {
            return rootDoc
        }

        // If the selected folder directly contains mod files (.jar or .jar.disabled), use it directly
        val containsDirectJars = rootDoc.listFiles().any { doc ->
            val docName = doc.name.orEmpty()
            doc.isFile && (docName.endsWith(".jar", ignoreCase = true) || docName.endsWith(".jar.disabled", ignoreCase = true))
        }
        if (containsDirectJars) {
            return rootDoc
        }

        val existingMods = rootDoc.findFile("mods")
        if (existingMods != null && existingMods.isDirectory) {
            return existingMods
        }

        return rootDoc.createDirectory("mods")
            ?: throw IllegalStateException("Unable to create 'mods' directory in SAF tree")
    }

    override suspend fun installMod(
        fileStream: InputStream,
        instancePath: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = getOrCreateModsDir(instancePath)
            val sanitizedName = sanitizeAndValidateModName(fileName)
            modsDir.findFile(sanitizedName)?.delete()

            val newDoc = modsDir.createFile("application/java-archive", sanitizedName)
                ?: throw IllegalStateException("Failed to create SAF document for $sanitizedName")

            context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use { output ->
                fileStream.use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Failed to open output stream for ${newDoc.uri}")
            Unit
        }
    }

    override suspend fun deleteMod(
        instancePath: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = getOrCreateModsDir(instancePath)
            val sanitizedName = sanitizeAndValidateModName(fileName)
            val target = modsDir.findFile(sanitizedName)
            if (target != null && !target.delete()) {
                throw IllegalStateException("Could not delete $sanitizedName from SAF tree")
            }
        }
    }

    override suspend fun toggleModState(
        instancePath: String,
        fileName: String
    ): Result<InstalledMod> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = getOrCreateModsDir(instancePath)
            val sanitizedName = sanitizeAndValidateModName(fileName)
            val target = modsDir.findFile(sanitizedName)
                ?: throw IllegalArgumentException("Mod not found in SAF directory: $sanitizedName")

            val isCurrentlyDisabled = sanitizedName.endsWith(".disabled", ignoreCase = true)
            val newFileName = if (isCurrentlyDisabled) {
                sanitizedName.removeSuffix(".disabled").removeSuffix(".DISABLED")
            } else {
                "$sanitizedName.disabled"
            }

            var renameSucceeded = target.renameTo(newFileName)
            if (!renameSucceeded) {
                // Fallback for SAF providers (like ExternalStorageProvider on Android 11+)
                // where renameTo across extension boundaries is not supported:
                // Copy data stream to a new document and remove the old file.
                val mimeType = if (newFileName.endsWith(".jar", ignoreCase = true)) {
                    "application/java-archive"
                } else {
                    "application/octet-stream"
                }
                val newDoc = modsDir.createFile(mimeType, newFileName)
                    ?: throw IllegalStateException("Failed to create SAF document for $newFileName during fallback toggle")

                try {
                    context.contentResolver.openInputStream(target.uri)?.use { input ->
                        context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use { output ->
                            input.copyTo(output)
                        } ?: throw IllegalStateException("Failed to open output stream for ${newDoc.uri}")
                    } ?: throw IllegalStateException("Failed to open input stream for ${target.uri}")
                    
                    if (!target.delete()) {
                        // Attempt to clean up new file if deleting old fails to avoid duplicates
                        newDoc.delete()
                        throw IllegalStateException("Failed to delete original mod file after copying: $sanitizedName")
                    }
                    renameSucceeded = true
                } catch (e: Exception) {
                    newDoc.delete()
                    throw e
                }
            }

            val updatedDoc = modsDir.findFile(newFileName) ?: target
            updatedDoc.toInstalledMod(overrideName = newFileName)
        }
    }

    override suspend fun listInstalledMods(
        instancePath: String
    ): Result<List<InstalledMod>> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(instancePath)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@runCatching emptyList()

            val modsDir = if (rootDoc.name.equals("mods", ignoreCase = true)) {
                rootDoc
            } else {
                val hasDirectJars = rootDoc.listFiles().any { doc ->
                    val docName = doc.name.orEmpty()
                    doc.isFile && (docName.endsWith(".jar", ignoreCase = true) || docName.endsWith(".jar.disabled", ignoreCase = true))
                }
                if (hasDirectJars) {
                    rootDoc
                } else {
                    rootDoc.findFile("mods") ?: return@runCatching emptyList()
                }
            }

            modsDir.listFiles()
                .filter { doc ->
                    val docName = doc.name.orEmpty()
                    doc.isFile && (
                        docName.endsWith(".jar", ignoreCase = true) ||
                            docName.endsWith(".jar.disabled", ignoreCase = true)
                        )
                }
                .map { it.toInstalledMod() }
                .sortedBy { it.displayName.lowercase() }
        }
    }

    private fun DocumentFile.toInstalledMod(overrideName: String? = null): InstalledMod {
        val actualName = overrideName ?: name ?: "unknown.jar"
        val enabled = !actualName.endsWith(".disabled", ignoreCase = true)
        val cleanName = actualName
            .removeSuffix(".disabled")
            .removeSuffix(".DISABLED")
            .removeSuffix(".jar")
            .removeSuffix(".JAR")
        return InstalledMod(
            fileName = actualName,
            displayName = cleanName,
            sizeBytes = length(),
            lastModified = lastModified(),
            isEnabled = enabled
        )
    }
}
