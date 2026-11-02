package mod.dropper.com.domain.model

enum class ModLoader(val displayName: String, val modrinthSlug: String) {
    FABRIC("Fabric", "fabric"),
    FORGE("Forge", "forge"),
    NEOFORGE("NeoForge", "neoforge"),
    QUILT("Quilt", "quilt");

    companion object {
        fun fromString(raw: String): ModLoader {
            val lower = raw.lowercase()
            return when {
                "neoforge" in lower -> NEOFORGE
                "quilt" in lower -> QUILT
                "forge" in lower -> FORGE
                else -> FABRIC
            }
        }
    }
}
