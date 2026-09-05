package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_IS_DARK = "is_dark_theme"

    fun isDarkTheme(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_DARK, true) // Default to dark
    }

    fun setTheme(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIsDark = prefs.getBoolean(KEY_IS_DARK, true)
        
        if (currentIsDark == isDark) return

        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()

        // Switch Activity Alias to change the home screen icon
        val packageManager = context.packageManager
        val darkAlias = ComponentName(context, "com.example.myapplication.MainActivityDark")
        val lightAlias = ComponentName(context, "com.example.myapplication.MainActivityLight")

        if (isDark) {
            packageManager.setComponentEnabledSetting(
                darkAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                lightAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                lightAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                darkAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
