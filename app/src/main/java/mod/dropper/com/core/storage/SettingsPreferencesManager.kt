package mod.dropper.com.core.storage

import android.content.Context
import mod.dropper.com.domain.model.AppSettings
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.domain.model.ThemeMode

class SettingsPreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("dropper_user_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_COMPACT_CARDS = "compact_mod_cards"
        private const val KEY_AUTO_DEPS = "auto_install_dependencies"
        private const val KEY_VERIFY_SHA512 = "verify_sha512"
        private const val KEY_ENABLE_HAPTICS = "enable_haptics"
        private const val KEY_SORT_INDEX = "mod_sort_index"
    }

    fun loadSettings(): AppSettings {
        val rawTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(rawTheme) }.getOrDefault(ThemeMode.SYSTEM)
        return AppSettings(
            themeMode = theme,
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            compactModCards = prefs.getBoolean(KEY_COMPACT_CARDS, false),
            autoInstallDependencies = prefs.getBoolean(KEY_AUTO_DEPS, true),
            verifySha512 = prefs.getBoolean(KEY_VERIFY_SHA512, true),
            enableHaptics = prefs.getBoolean(KEY_ENABLE_HAPTICS, true)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.useDynamicColor)
            .putBoolean(KEY_COMPACT_CARDS, settings.compactModCards)
            .putBoolean(KEY_AUTO_DEPS, settings.autoInstallDependencies)
            .putBoolean(KEY_VERIFY_SHA512, settings.verifySha512)
            .putBoolean(KEY_ENABLE_HAPTICS, settings.enableHaptics)
            .apply()
    }

    fun loadSortIndex(): ModSortIndex {
        val raw = prefs.getString(KEY_SORT_INDEX, ModSortIndex.RELEVANCE.name) ?: ModSortIndex.RELEVANCE.name
        return runCatching { ModSortIndex.valueOf(raw) }.getOrDefault(ModSortIndex.RELEVANCE)
    }

    fun saveSortIndex(sortIndex: ModSortIndex) {
        prefs.edit().putString(KEY_SORT_INDEX, sortIndex.name).apply()
    }
}
