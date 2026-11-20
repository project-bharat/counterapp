package com.projectbharat.counterapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object SeedData {
    fun initialize(context: Context) {
        val dao = CounterDatabase.get(context).dao()
        CoroutineScope(Dispatchers.IO).launch {
            if (dao.categories().first().isEmpty()) {
                dao.addCategory(CounterCategory(titleMr = "जप", titleEn = "Japa", kind = "MANTRA"))
            }
        }
    }
}
