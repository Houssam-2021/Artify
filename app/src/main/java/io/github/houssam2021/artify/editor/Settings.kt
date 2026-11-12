package io.github.houssam2021.artify.editor

import android.content.Context

data class AppSettings(
    /** When a stylus is used, fingers only navigate (palm-friendly). */
    val penOnly: Boolean = false,
    /** Pressure curve exponent: <1 soft, 1 linear, >1 firm. */
    val pressureGamma: Float = 1f,
    val leftHanded: Boolean = false,
    val showGrid: Boolean = true,
    val quickShape: Boolean = true,
    val fingerEyedropper: Boolean = true,
    val exportSize: Int = 2048,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("artify_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        penOnly = prefs.getBoolean("penOnly", false),
        pressureGamma = prefs.getFloat("pressureGamma", 1f).coerceIn(0.3f, 3f),
        leftHanded = prefs.getBoolean("leftHanded", false),
        showGrid = prefs.getBoolean("showGrid", true),
        quickShape = prefs.getBoolean("quickShape", true),
        fingerEyedropper = prefs.getBoolean("fingerEyedropper", true),
        exportSize = prefs.getInt("exportSize", 2048).coerceIn(512, 8192),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("penOnly", s.penOnly)
            .putFloat("pressureGamma", s.pressureGamma)
            .putBoolean("leftHanded", s.leftHanded)
            .putBoolean("showGrid", s.showGrid)
            .putBoolean("quickShape", s.quickShape)
            .putBoolean("fingerEyedropper", s.fingerEyedropper)
            .putInt("exportSize", s.exportSize)
            .apply()
    }

    fun loadPalette(): List<Int> =
        prefs.getString("palette", null)?.split(',')?.mapNotNull { it.toLongOrNull(16)?.toInt() }?.take(60) ?: emptyList()

    fun savePalette(colors: List<Int>) {
        prefs.edit().putString("palette", colors.take(60).joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString(16) }).apply()
    }
}
