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

    fun saveThemePreference(context: Context, isDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
    }

    fun applyIconChange(context: Context) {
        val isDark = isDarkTheme(context)
        val packageManager = context.packageManager

        val darkAlias = ComponentName(context, "com.example.myapplication.MainActivityDark")
        val lightAlias = ComponentName(context, "com.example.myapplication.MainActivityLight")

        // FIX: Handle COMPONENT_ENABLED_STATE_DEFAULT correctly
        val darkSetting = packageManager.getComponentEnabledSetting(darkAlias)
        val lightSetting = packageManager.getComponentEnabledSetting(lightAlias)

        if (isDark) {
            // We want Dark enabled, Light disabled
            if (darkSetting != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(
                    darkAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            if (lightSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                packageManager.setComponentEnabledSetting(
                    lightAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } else {
            // We want Light enabled, Dark disabled
            if (lightSetting != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(
                    lightAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            if (darkSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                packageManager.setComponentEnabledSetting(
                    darkAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
