package com.example.inputmonitor.platform.ime

import android.content.Context
import android.content.res.Configuration

/** Stores appearance only, never input or media selections. */
internal object KeyboardAppearance {
    fun isDark(context: Context): Boolean {
        val preferences = context.getSharedPreferences("keyboard_appearance", Context.MODE_PRIVATE)
        return if (preferences.contains("dark")) preferences.getBoolean("dark", false)
        else context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun toggle(context: Context) {
        context.getSharedPreferences("keyboard_appearance", Context.MODE_PRIVATE)
            .edit().putBoolean("dark", !isDark(context)).apply()
    }

    fun followSystem(context: Context) {
        context.getSharedPreferences("keyboard_appearance", Context.MODE_PRIVATE)
            .edit().remove("dark").apply()
    }
}
