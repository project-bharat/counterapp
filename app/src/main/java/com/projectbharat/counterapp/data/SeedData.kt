package com.projectbharat.counterapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SeedData {
    fun initialize(context: Context) {
        val dao = CounterDatabase.get(context).dao()
        CoroutineScope(Dispatchers.IO).launch {
            if (dao.allCategories().none { it.kind == "MANTRA" }) {
                // Ensure the Japa category exists; do not seed legacy Baby/Steps categories.
                dao.addCategory(CounterCategory(titleMr = "जप", titleEn = "Chants", kind = "MANTRA"))
            }
        }
    }
}
