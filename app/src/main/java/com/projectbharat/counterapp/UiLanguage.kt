package com.projectbharat.counterapp

import android.content.Context
import java.util.Locale

enum class UiLanguage(val tag: String, val label: String) {
    MARATHI("mr", "मराठी"),
    HINDI("hi", "हिन्दी"),
    ENGLISH("en", "English");

    val isEnglish: Boolean get() = this == ENGLISH
    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun indic(marathi: String, hindi: String): String = if (this == HINDI) hindi else marathi

    companion object {
        fun load(context: Context): UiLanguage {
            val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            return entries.firstOrNull { it.tag == prefs.getString("ui_language", null) }
                ?: if (prefs.getBoolean("english_language", false)) ENGLISH else MARATHI
        }
    }
}
