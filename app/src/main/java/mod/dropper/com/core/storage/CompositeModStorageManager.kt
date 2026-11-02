package mod.dropper.com.core.storage

import mod.dropper.com.domain.model.InstalledMod
import java.io.InputStream

class CompositeModStorageManager(
    private val directManager: DirectFileModStorageManager,
    private val safManager: SafModStorageManager
) : ModStorageManager {

    private fun delegateFor(instancePath: String): ModStorageManager {
        return if (instancePath.startsWith("content://")) {
            safManager
        } else {
            directManager
        }
    }

    override suspend fun installMod(
        fileStream: InputStream,
        instancePath: String,
        fileName: String
    ): Result<Unit> = delegateFor(instancePath).installMod(fileStream, instancePath, fileName)

    override suspend fun deleteMod(
        instancePath: String,
        fileName: String
    ): Result<Unit> = delegateFor(instancePath).deleteMod(instancePath, fileName)

    override suspend fun toggleModState(
        instancePath: String,
        fileName: String
    ): Result<InstalledMod> = delegateFor(instancePath).toggleModState(instancePath, fileName)

    override suspend fun listInstalledMods(
        instancePath: String
    ): Result<List<InstalledMod>> = delegateFor(instancePath).listInstalledMods(instancePath)
}
