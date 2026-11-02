package mod.dropper.com.data.instances

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mod.dropper.com.core.storage.ModStorageManager
import mod.dropper.com.data.modrinth.ModRepositoryImpl
import mod.dropper.com.domain.model.LauncherType
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.repository.InstanceRepository
import java.io.File
import java.util.UUID

@Serializable
data class SavedCustomInstance(
    val id: String,
    val name: String,
    val path: String,
    val launcherTypeName: String,
    val gameVersion: String,
    val modLoaderName: String
)

@Serializable
data class SavedMetadataOverride(
    val instanceId: String,
    val gameVersion: String,
    val modLoaderName: String
)

class InstanceDiscoveryService(
    private val context: Context,
    private val storageManager: ModStorageManager,
    private val json: Json
) : InstanceRepository {

    private val prefs = context.getSharedPreferences("dropper_instances_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_INSTANCES = "custom_instances_json"
        private const val KEY_METADATA_OVERRIDES = "instance_metadata_overrides_v2_json"
        private val MC_VERSION_REGEX = Regex("""((?:1|\d+)\.\d+(?:\.\d+)?)""")
        val DEFAULT_LATEST_VERSION: String
            get() = ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
    }

    /**
     * Prevents untrusted launcher_profiles.json or user path inputs from pointing
     * inside Dropper's private internal app sandbox or restricted OS system roots.
     */
    private fun isSafeExternalInstancePath(rawPath: String): Boolean {
        if (rawPath.startsWith("content://")) return true
        val canonical = runCatching { File(rawPath).canonicalPath }.getOrNull() ?: return false
        val appDataDir = runCatching { File(context.applicationInfo.dataDir).canonicalPath }.getOrNull()
        if (appDataDir != null && canonical.startsWith(appDataDir)) {
            return false
        }
        val forbiddenPrefixes = listOf(
            "/data/data",
            "/data/user",
            "/system",
            "/proc",
            "/dev",
            "/etc",
            "/apex"
        )
        return forbiddenPrefixes.none { prefix -> canonical.startsWith(prefix) }
    }

    override suspend fun discoverInstances(): List<MinecraftInstance> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<MinecraftInstance>()
        val overrides = loadMetadataOverrides()

        val rootsToScan = getCandidateRoots()

        for ((launcherType, baseDir) in rootsToScan) {
            if (baseDir.exists() && baseDir.isDirectory) {
                val parsedFromProfiles = parseLauncherProfiles(baseDir, launcherType)
                if (parsedFromProfiles.isNotEmpty()) {
                    discovered.addAll(parsedFromProfiles)
                } else {
                    val inferred = inferInstanceFromDirectory(baseDir, launcherType)
                    discovered.add(inferred)
                }

                // Scan sub-instances inside "instances/", "profiles/", or ".minecraft/instances"
                listOf("instances", "profiles", ".minecraft/instances").forEach { subFolder ->
                    val instancesDir = File(baseDir, subFolder)
                    if (instancesDir.exists() && instancesDir.isDirectory) {
                        instancesDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                            discovered.add(inferInstanceFromDirectory(subDir, launcherType))
                        }
                    }
                }
            }
        }

        // Load user-added custom / SAF instances
        val savedCustom = loadSavedCustomInstances()
        for (custom in savedCustom) {
            if (!isSafeExternalInstancePath(custom.path)) continue
            val launcher = runCatching { LauncherType.valueOf(custom.launcherTypeName) }
                .getOrDefault(LauncherType.CUSTOM)
            val loader = runCatching { ModLoader.valueOf(custom.modLoaderName) }
                .getOrDefault(ModLoader.FABRIC)
            val modsCount = storageManager.listInstalledMods(custom.path).getOrNull()?.size ?: 0
            discovered.add(
                MinecraftInstance(
                    id = custom.id,
                    name = custom.name,
                    path = custom.path,
                    launcherType = launcher,
                    gameVersion = custom.gameVersion,
                    modLoader = loader,
                    installedModsCount = modsCount,
                    isSafUri = custom.path.startsWith("content://")
                )
            )
        }

        // Ensure each standard launcher has at least a default target profile so the user can
        // immediately select PojavLauncher, MojoLauncher, or Amethyst and install mods into it.
        val sdcard = Environment.getExternalStorageDirectory()
        val defaults = listOf(
            Triple(LauncherType.POJAV, File(sdcard, "PojavLauncher/.minecraft"), "PojavLauncher Default"),
            Triple(LauncherType.MOJO, File(sdcard, "Games/Mojo/.minecraft"), "MojoLauncher Default"),
            Triple(LauncherType.AMETHYST, File(sdcard, "Amethyst/.minecraft"), "Amethyst Default")
        )
        for ((type, dir, label) in defaults) {
            if (discovered.none { it.launcherType == type }) {
                val id = "${type.name}_${dir.absolutePath.hashCode()}"
                val modsCount = storageManager.listInstalledMods(dir.absolutePath).getOrNull()?.size ?: 0
                discovered.add(
                    MinecraftInstance(
                        id = id,
                        name = label,
                        path = dir.absolutePath,
                        launcherType = type,
                        gameVersion = DEFAULT_LATEST_VERSION,
                        modLoader = ModLoader.FABRIC,
                        installedModsCount = modsCount
                    )
                )
            }
        }

        // Deduplicate by canonical path + name and apply user metadata overrides
        discovered
            .distinctBy { instance ->
                val normPath = if (instance.isSafUri) {
                    instance.path
                } else {
                    runCatching { File(instance.path).canonicalPath }.getOrDefault(instance.path)
                }
                "$normPath::${instance.name}"
            }
            .map { instance ->
                val override = overrides[instance.id]
                val modsCount = storageManager.listInstalledMods(instance.path).getOrNull()?.size ?: 0
                if (override != null) {
                    instance.copy(
                        gameVersion = override.first,
                        modLoader = override.second,
                        installedModsCount = modsCount
                    )
                } else {
                    instance.copy(installedModsCount = modsCount)
                }
            }
    }

    private fun getCandidateRoots(): List<Pair<LauncherType, File>> {
        val externalRoot = Environment.getExternalStorageDirectory()
        val sdcardRoot = File("/sdcard")
        val baseRoots = listOf(externalRoot, sdcardRoot)
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        val candidates = mutableListOf<Pair<LauncherType, File>>()
        for (root in baseRoots) {
            // PojavLauncher paths
            candidates.add(LauncherType.POJAV to File(root, "PojavLauncher/.minecraft"))
            candidates.add(LauncherType.POJAV to File(root, "games/PojavLauncher/.minecraft"))
            candidates.add(LauncherType.POJAV to File(root, "Games/PojavLauncher/.minecraft"))
            candidates.add(LauncherType.POJAV to File(root, ".minecraft"))
            candidates.add(LauncherType.POJAV to File(root, "Android/data/net.kdt.pojavlaunch/files/.minecraft"))
            candidates.add(LauncherType.POJAV to File(root, "Android/data/net.kdt.pojavlaunch.debug/files/.minecraft"))

            // MojoLauncher paths
            candidates.add(LauncherType.MOJO to File(root, "Games/Mojo/.minecraft"))
            candidates.add(LauncherType.MOJO to File(root, "games/Mojo/.minecraft"))
            candidates.add(LauncherType.MOJO to File(root, "Games/Mojo"))
            candidates.add(LauncherType.MOJO to File(root, "MojoLauncher/.minecraft"))
            candidates.add(LauncherType.MOJO to File(root, "Android/data/git.artdeell.mojo/files/.minecraft"))

            // Amethyst paths
            candidates.add(LauncherType.AMETHYST to File(root, "Amethyst/.minecraft"))
            candidates.add(LauncherType.AMETHYST to File(root, "Games/Amethyst/.minecraft"))
            candidates.add(LauncherType.AMETHYST to File(root, "games/Amethyst/.minecraft"))
            candidates.add(LauncherType.AMETHYST to File(root, "Amethyst"))
        }
        return candidates.distinctBy { (_, file) ->
            runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        }
    }

    private suspend fun parseLauncherProfiles(
        baseDir: File,
        launcherType: LauncherType
    ): List<MinecraftInstance> {
        val profilesFile = File(baseDir, "launcher_profiles.json").takeIf { it.exists() && it.length() < 2_000_000L }
            ?: File(baseDir, ".minecraft/launcher_profiles.json").takeIf { it.exists() && it.length() < 2_000_000L }
            ?: return emptyList()

        return runCatching {
            val content = profilesFile.readText()
            val rootObj = json.parseToJsonElement(content).jsonObject
            val profilesObj = rootObj["profiles"]?.jsonObject ?: return emptyList()

            val results = mutableListOf<MinecraftInstance>()
            for ((profileKey, element) in profilesObj.entries) {
                val profile = element as? JsonObject ?: continue
                val rawName = profile["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: profileKey
                val lastVersionId = profile["lastVersionId"]?.jsonPrimitive?.content.orEmpty()
                val customGameDir = profile["gameDir"]?.jsonPrimitive?.content

                val defaultTargetPath = profilesFile.parentFile?.absolutePath ?: baseDir.absolutePath
                val targetPath = if (!customGameDir.isNullOrBlank() && isSafeExternalInstancePath(customGameDir)) {
                    File(customGameDir).canonicalPath
                } else {
                    defaultTargetPath
                }

                val gameVersion = extractMcVersion(lastVersionId)
                    ?: inferVersionFromVersionsDir(File(targetPath))
                    ?: DEFAULT_LATEST_VERSION
                val modLoader = inferModLoader(lastVersionId, File(targetPath))
                val modsCount = storageManager.listInstalledMods(targetPath).getOrNull()?.size ?: 0

                results.add(
                    MinecraftInstance(
                        id = "${launcherType.name}_${profileKey}_${targetPath.hashCode()}",
                        name = rawName,
                        path = targetPath,
                        launcherType = launcherType,
                        gameVersion = gameVersion,
                        modLoader = modLoader,
                        installedModsCount = modsCount
                    )
                )
            }
            results
        }.getOrDefault(emptyList())
    }

    private suspend fun inferInstanceFromDirectory(
        dir: File,
        launcherType: LauncherType
    ): MinecraftInstance {
        val effectiveDir = if (File(dir, ".minecraft").exists() && !File(dir, "mods").exists()) {
            File(dir, ".minecraft")
        } else {
            dir
        }
        val version = inferVersionFromVersionsDir(effectiveDir) ?: DEFAULT_LATEST_VERSION
        val loader = inferModLoader(effectiveDir.name, effectiveDir)
        val modsCount = storageManager.listInstalledMods(effectiveDir.absolutePath).getOrNull()?.size ?: 0
        val displayName = when {
            effectiveDir.name == ".minecraft" -> {
                val parentName = effectiveDir.parentFile?.name
                if (parentName == null || parentName.equals(launcherType.displayName, ignoreCase = true) || parentName.equals(launcherType.name, ignoreCase = true)) {
                    "${launcherType.displayName} Default"
                } else {
                    "${launcherType.displayName} ($parentName)"
                }
            }
            effectiveDir.name.equals(launcherType.displayName, ignoreCase = true) || effectiveDir.name.equals(launcherType.name, ignoreCase = true) -> {
                "${launcherType.displayName} Default"
            }
            else -> "${launcherType.displayName} - ${effectiveDir.name}"
        }

        return MinecraftInstance(
            id = "${launcherType.name}_${effectiveDir.absolutePath.hashCode()}",
            name = displayName,
            path = effectiveDir.absolutePath,
            launcherType = launcherType,
            gameVersion = version,
            modLoader = loader,
            installedModsCount = modsCount
        )
    }

    private fun extractMcVersion(raw: String): String? {
        return MC_VERSION_REGEX.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun inferVersionFromVersionsDir(instanceDir: File): String? {
        val versionsDir = File(instanceDir, "versions")
        if (!versionsDir.exists() || !versionsDir.isDirectory) return null
        val subDirs = versionsDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (sub in subDirs) {
            val match = extractMcVersion(sub.name)
            if (match != null) return match
        }
        return null
    }

    private fun inferModLoader(versionHint: String, instanceDir: File): ModLoader {
        val lowerHint = versionHint.lowercase()
        when {
            "neoforge" in lowerHint -> return ModLoader.NEOFORGE
            "quilt" in lowerHint -> return ModLoader.QUILT
            "forge" in lowerHint -> return ModLoader.FORGE
            "fabric" in lowerHint -> return ModLoader.FABRIC
        }

        val versionsDir = File(instanceDir, "versions")
        val versionNames = versionsDir.listFiles()?.map { it.name.lowercase() }.orEmpty()
        return when {
            versionNames.any { "neoforge" in it } -> ModLoader.NEOFORGE
            versionNames.any { "quilt" in it } -> ModLoader.QUILT
            versionNames.any { "forge" in it } -> ModLoader.FORGE
            else -> ModLoader.FABRIC
        }
    }

    override suspend fun addCustomSafInstance(
        treeUri: Uri,
        customName: String?,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<MinecraftInstance> = withContext(Dispatchers.IO) {
        runCatching {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            }

            val docDir = DocumentFile.fromTreeUri(context, treeUri)
            val resolvedName = customName?.takeIf { it.isNotBlank() }
                ?: docDir?.name?.takeIf { it.isNotBlank() }
                ?: "SAF Instance"

            // Check if launcher_profiles.json exists inside the SAF tree to infer version
            val detectedVersion = docDir?.findFile("launcher_profiles.json")?.let { profileDoc ->
                context.contentResolver.openInputStream(profileDoc.uri)?.bufferedReader()?.use { reader ->
                    extractMcVersion(reader.readText())
                }
            } ?: gameVersion

            val id = "SAF_${UUID.randomUUID()}"
            val uriString = treeUri.toString()
            val saved = SavedCustomInstance(
                id = id,
                name = resolvedName,
                path = uriString,
                launcherTypeName = LauncherType.CUSTOM.name,
                gameVersion = detectedVersion,
                modLoaderName = modLoader.name
            )
            saveCustomInstance(saved)

            val modsCount = storageManager.listInstalledMods(uriString).getOrNull()?.size ?: 0
            MinecraftInstance(
                id = id,
                name = resolvedName,
                path = uriString,
                launcherType = LauncherType.CUSTOM,
                gameVersion = detectedVersion,
                modLoader = modLoader,
                installedModsCount = modsCount,
                isSafUri = true
            )
        }
    }

    override suspend fun addCustomPathInstance(
        path: String,
        name: String,
        launcherType: LauncherType,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<MinecraftInstance> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedPath = path.trim()
            require(isSafeExternalInstancePath(trimmedPath)) {
                "Path is restricted or inside system/app-private storage"
            }
            val targetDir = File(trimmedPath).canonicalFile
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val id = "CUSTOM_${targetDir.absolutePath.hashCode()}"
            val saved = SavedCustomInstance(
                id = id,
                name = name.ifBlank { targetDir.name.ifBlank { "Custom Instance" } },
                path = targetDir.absolutePath,
                launcherTypeName = launcherType.name,
                gameVersion = gameVersion.ifBlank { DEFAULT_LATEST_VERSION },
                modLoaderName = modLoader.name
            )
            saveCustomInstance(saved)

            val modsCount = storageManager.listInstalledMods(targetDir.absolutePath).getOrNull()?.size ?: 0
            MinecraftInstance(
                id = saved.id,
                name = saved.name,
                path = saved.path,
                launcherType = launcherType,
                gameVersion = saved.gameVersion,
                modLoader = modLoader,
                installedModsCount = modsCount,
                isSafUri = false
            )
        }
    }

    override suspend fun updateInstanceMetadata(
        instanceId: String,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = loadMetadataOverrides().toMutableMap()
            current[instanceId] = gameVersion to modLoader
            val list = current.entries.map { (id, pair) ->
                SavedMetadataOverride(
                    instanceId = id,
                    gameVersion = pair.first,
                    modLoaderName = pair.second.name
                )
            }
            prefs.edit().putString(KEY_METADATA_OVERRIDES, json.encodeToString(list)).apply()
        }
    }

    override suspend fun removeCustomInstance(
        instanceId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = loadSavedCustomInstances().filterNot { it.id == instanceId }
            prefs.edit().putString(KEY_CUSTOM_INSTANCES, json.encodeToString(current)).apply()
        }
    }

    private fun loadMetadataOverrides(): Map<String, Pair<String, ModLoader>> {
        val raw = prefs.getString(KEY_METADATA_OVERRIDES, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<SavedMetadataOverride>>(raw).associate { item ->
                val loader = runCatching { ModLoader.valueOf(item.modLoaderName) }
                    .getOrDefault(ModLoader.FABRIC)
                item.instanceId to (item.gameVersion to loader)
            }
        }.getOrDefault(emptyMap())
    }

    private fun loadSavedCustomInstances(): List<SavedCustomInstance> {
        val raw = prefs.getString(KEY_CUSTOM_INSTANCES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<SavedCustomInstance>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun saveCustomInstance(instance: SavedCustomInstance) {
        val current = loadSavedCustomInstances().filterNot { it.path == instance.path }.toMutableList()
        current.add(instance)
        prefs.edit().putString(KEY_CUSTOM_INSTANCES, json.encodeToString(current)).apply()
    }
}
