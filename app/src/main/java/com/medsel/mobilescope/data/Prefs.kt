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

    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
