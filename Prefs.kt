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

    fun clear(ctx: Context) { sp(ctx).edit().clear().apply() }
}
