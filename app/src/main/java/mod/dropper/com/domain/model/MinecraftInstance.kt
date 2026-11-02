package mod.dropper.com.domain.model

data class MinecraftInstance(
    val id: String,
    val name: String,
    val path: String,
    val launcherType: LauncherType,
    val gameVersion: String,
    val modLoader: ModLoader,
    val installedModsCount: Int = 0,
    val isSafUri: Boolean = path.startsWith("content://")
)
