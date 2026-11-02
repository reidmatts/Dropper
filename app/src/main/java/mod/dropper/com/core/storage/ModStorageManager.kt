package mod.dropper.com.core.storage

import mod.dropper.com.domain.model.InstalledMod
import java.io.InputStream

interface ModStorageManager {
    suspend fun installMod(
        fileStream: InputStream,
        instancePath: String,
        fileName: String
    ): Result<Unit>

    suspend fun deleteMod(
        instancePath: String,
        fileName: String
    ): Result<Unit>

    suspend fun toggleModState(
        instancePath: String,
        fileName: String
    ): Result<InstalledMod>

    suspend fun listInstalledMods(
        instancePath: String
    ): Result<List<InstalledMod>>
}
