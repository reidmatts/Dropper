package mod.dropper.com.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mod.dropper.com.domain.model.InstalledMod
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class DirectFileModStorageManager : ModStorageManager {

    private fun resolveModsDir(instancePath: String): Path {
        val base = Paths.get(instancePath).toAbsolutePath().normalize()
        if (base.fileName?.toString()?.equals("mods", ignoreCase = true) == true) {
            return base
        }
        val subMods = base.resolve("mods").normalize()
        if (Files.exists(subMods) && Files.isDirectory(subMods)) {
            return subMods
        }
        // If the base folder already contains mod files directly, use it
        if (Files.exists(base) && Files.isDirectory(base)) {
            val hasDirectJars = base.toFile().listFiles()?.any {
                it.isFile && (it.name.endsWith(".jar", ignoreCase = true) || it.name.endsWith(".jar.disabled", ignoreCase = true))
            } == true
            if (hasDirectJars) {
                return base
            }
        }
        return subMods
    }

    /**
     * Validates a mod filename and resolves it inside [modsDir], strictly preventing
     * Path Traversal (CWE-22) and enforcing .jar / .jar.disabled extensions.
     */
    internal fun validateAndResolveModPath(modsDir: Path, rawFileName: String): Path {
        require(!rawFileName.contains("..")) {
            "Path traversal detected in filename: $rawFileName"
        }
        val normalizedModsDir = modsDir.toAbsolutePath().normalize()
        val baseName = rawFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()

        require(baseName.isNotEmpty() && baseName != "." && baseName != "..") {
            "Invalid mod filename: $rawFileName"
        }
        require(!baseName.contains("..") && !baseName.contains('/') && !baseName.contains('\\')) {
            "Path traversal detected in filename: $rawFileName"
        }
        require(
            baseName.endsWith(".jar", ignoreCase = true) ||
                baseName.endsWith(".jar.disabled", ignoreCase = true)
        ) {
            "Only .jar and .jar.disabled files are permitted: $baseName"
        }

        val resolved = normalizedModsDir.resolve(baseName).normalize()
        require(resolved.parent == normalizedModsDir && resolved.startsWith(normalizedModsDir)) {
            "Resolved path escapes target mods directory"
        }
        return resolved
    }

    override suspend fun installMod(
        fileStream: InputStream,
        instancePath: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = resolveModsDir(instancePath)
            if (!Files.exists(modsDir)) {
                Files.createDirectories(modsDir)
            }
            val targetPath = validateAndResolveModPath(modsDir, fileName)
            fileStream.use { input ->
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
            Unit
        }
    }

    override suspend fun deleteMod(
        instancePath: String,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = resolveModsDir(instancePath)
            val targetPath = validateAndResolveModPath(modsDir, fileName)
            Files.deleteIfExists(targetPath)
            Unit
        }
    }

    override suspend fun toggleModState(
        instancePath: String,
        fileName: String
    ): Result<InstalledMod> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = resolveModsDir(instancePath)
            val sourcePath = validateAndResolveModPath(modsDir, fileName)
            val sourceFile = sourcePath.toFile()
            if (!sourceFile.exists()) {
                throw IllegalArgumentException("Mod file not found: $fileName")
            }

            val cleanCurrentName = sourcePath.fileName.toString()
            val isCurrentlyDisabled = cleanCurrentName.endsWith(".disabled", ignoreCase = true)
            val newFileName = if (isCurrentlyDisabled) {
                cleanCurrentName.removeSuffix(".disabled").removeSuffix(".DISABLED")
            } else {
                "$cleanCurrentName.disabled"
            }

            val targetPath = validateAndResolveModPath(modsDir, newFileName)
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            val updatedFile = targetPath.toFile()
            updatedFile.toInstalledMod()
        }
    }

    override suspend fun listInstalledMods(
        instancePath: String
    ): Result<List<InstalledMod>> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = resolveModsDir(instancePath).toFile()
            if (!modsDir.exists() || !modsDir.isDirectory) {
                return@runCatching emptyList()
            }
            modsDir.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        !Files.isSymbolicLink(file.toPath()) && (
                            file.name.endsWith(".jar", ignoreCase = true) ||
                                file.name.endsWith(".jar.disabled", ignoreCase = true)
                            )
                }
                ?.map { it.toInstalledMod() }
                ?.sortedBy { it.displayName.lowercase() }
                ?: emptyList()
        }
    }

    private fun File.toInstalledMod(): InstalledMod {
        val enabled = !name.endsWith(".disabled", ignoreCase = true)
        val cleanName = name
            .removeSuffix(".disabled")
            .removeSuffix(".DISABLED")
            .removeSuffix(".jar")
            .removeSuffix(".JAR")
        return InstalledMod(
            fileName = name,
            displayName = cleanName,
            sizeBytes = length(),
            lastModified = lastModified(),
            isEnabled = enabled
        )
    }
}
