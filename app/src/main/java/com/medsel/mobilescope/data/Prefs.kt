package com.medsel.mobilescope.data

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("user", Context.MODE_PRIVATE)
    private fun settings(ctx: Context) = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): User? {
        val p = sp(ctx)
        if (!p.contains("name")) return null
        return User(p.getString("name", "") ?: "", p.getString("email", "") ?: "", p.getString("photo", null), p.getBoolean("guest", false))
    }

    fun save(ctx: Context, u: User) {
        sp(ctx).edit().putString("name", u.name).putString("email", u.email)
            .putString("photo", u.photo).putBoolean("guest", u.guest).apply()
    }

    fun theme(ctx: Context): String = settings(ctx).getString("theme_mode", "system") ?: "system"

    fun saveTheme(ctx: Context, value: String) {
        settings(ctx).edit().putString("theme_mode", value).apply()
    }

    fun accent(ctx: Context): String = settings(ctx).getString("accent_color", "violet") ?: "violet"

    fun saveAccent(ctx: Context, value: String) {
        settings(ctx).edit().putString("accent_color", value).apply()
    }

    fun compactCards(ctx: Context): Boolean = settings(ctx).getBoolean("compact_cards", false)

    fun saveCompactCards(ctx: Context, value: Boolean) {
        settings(ctx).edit().putBoolean("compact_cards", value).apply()
    }

    fun favoriteModules(ctx: Context): Set<String> =
        settings(ctx).getStringSet("favorite_modules", emptySet())?.toSet() ?: emptySet()

    fun toggleFavoriteModule(ctx: Context, module: String) {
        val favorites = favoriteModules(ctx).toMutableSet()
        if (module in favorites) favorites.remove(module) else favorites.add(module)
        settings(ctx).edit().putStringSet("favorite_modules", favorites).apply()
    }


    // ---------- langue ----------
    /** "fr" (par défaut) ou "en". */
    fun lang(ctx: Context): String = settings(ctx).getString("lang", "fr") ?: "fr"
    fun saveLang(ctx: Context, v: String) { settings(ctx).edit().putString("lang", v).apply() }

    // ---------- préférences avancées ----------
    fun refreshMs(ctx: Context): Int = settings(ctx).getInt("refresh_ms", 2000)
    fun saveRefreshMs(ctx: Context, v: Int) { settings(ctx).edit().putInt("refresh_ms", v).apply() }

    fun decimalUnits(ctx: Context): Boolean = settings(ctx).getBoolean("decimal_units", false)
    fun saveDecimalUnits(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("decimal_units", v).apply() }

    fun historyEnabled(ctx: Context): Boolean = settings(ctx).getBoolean("history_enabled", true)
    fun saveHistoryEnabled(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("history_enabled", v).apply() }

    fun alertsEnabled(ctx: Context): Boolean = settings(ctx).getBoolean("alerts_enabled", false)
    fun saveAlertsEnabled(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("alerts_enabled", v).apply() }

    fun alertTemp(ctx: Context): Int = settings(ctx).getInt("alert_temp", 42)
    fun saveAlertTemp(ctx: Context, v: Int) { settings(ctx).edit().putInt("alert_temp", v).apply() }

    fun alertStorage(ctx: Context): Int = settings(ctx).getInt("alert_storage", 90)
    fun saveAlertStorage(ctx: Context, v: Int) { settings(ctx).edit().putInt("alert_storage", v).apply() }

    fun alertBattery(ctx: Context): Int = settings(ctx).getInt("alert_battery", 15)
    fun saveAlertBattery(ctx: Context, v: Int) { settings(ctx).edit().putInt("alert_battery", v).apply() }

    fun showHealthCard(ctx: Context): Boolean = settings(ctx).getBoolean("show_health_card", true)
    fun saveShowHealthCard(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("show_health_card", v).apply() }

    fun showQuickControls(ctx: Context): Boolean = settings(ctx).getBoolean("show_quick_controls", true)
    fun saveShowQuickControls(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("show_quick_controls", v).apply() }

    fun autoCheckUpdates(ctx: Context): Boolean = settings(ctx).getBoolean("auto_check_updates", true)
    fun saveAutoCheckUpdates(ctx: Context, v: Boolean) { settings(ctx).edit().putBoolean("auto_check_updates", v).apply() }

    /** Réinitialise uniquement les préférences d'affichage ; le compte Google n'est pas touché. */
    fun resetSettings(ctx: Context) { settings(ctx).edit().clear().apply() }

    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
