package com.medsel.mobilescope.data

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("user", Context.MODE_PRIVATE)

    fun load(ctx: Context): User? {
        val p = sp(ctx)
        if (!p.contains("name")) return null
        return User(p.getString("name", "") ?: "", p.getString("email", "") ?: "", p.getString("photo", null), p.getBoolean("guest", false))
    }

    fun save(ctx: Context, u: User) {
        sp(ctx).edit().putString("name", u.name).putString("email", u.email)
            .putString("photo", u.photo).putBoolean("guest", u.guest).apply()
    }

    fun theme(ctx: Context): String = sp(ctx).getString("theme_mode", "system") ?: "system"

    fun saveTheme(ctx: Context, value: String) {
        sp(ctx).edit().putString("theme_mode", value).apply()
    }

    fun compactCards(ctx: Context): Boolean = sp(ctx).getBoolean("compact_cards", false)

    fun saveCompactCards(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("compact_cards", value).apply()
    }

    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
