package mod.dropper.com.domain.model

enum class LauncherType(
    val displayName: String,
    val defaultRelativePaths: List<String>
) {
    POJAV(
        displayName = "PojavLauncher",
        defaultRelativePaths = listOf(
            "PojavLauncher/.minecraft",
            "Android/data/net.kdt.pojavlaunch/files/.minecraft",
            ".minecraft"
        )
    ),
    MOJO(
        displayName = "MojoLauncher",
        defaultRelativePaths = listOf(
            "Games/Mojo/.minecraft",
            "Games/Mojo",
            "MojoLauncher/.minecraft"
        )
    ),
    AMETHYST(
        displayName = "Amethyst",
        defaultRelativePaths = listOf(
            "Amethyst/.minecraft",
            "Amethyst",
            "Games/Amethyst/.minecraft"
        )
    ),
    CUSTOM(
        displayName = "Custom Folder",
        defaultRelativePaths = emptyList()
    )
}
