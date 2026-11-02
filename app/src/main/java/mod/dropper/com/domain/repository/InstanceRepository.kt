package mod.dropper.com.domain.repository

import android.net.Uri
import mod.dropper.com.domain.model.LauncherType
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModLoader

interface InstanceRepository {
    suspend fun discoverInstances(): List<MinecraftInstance>
    suspend fun addCustomSafInstance(
        treeUri: Uri,
        customName: String?,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<MinecraftInstance>

    suspend fun addCustomPathInstance(
        path: String,
        name: String,
        launcherType: LauncherType,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<MinecraftInstance>

    suspend fun updateInstanceMetadata(
        instanceId: String,
        gameVersion: String,
        modLoader: ModLoader
    ): Result<Unit>

    suspend fun removeCustomInstance(instanceId: String): Result<Unit>
}
