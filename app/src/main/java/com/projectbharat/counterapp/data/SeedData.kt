package com.projectbharat.counterapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SeedData {
    fun initialize(context: Context) {
        val dao = CounterDatabase.get(context).dao()
        CoroutineScope(Dispatchers.IO).launch {
            if (dao.categories().first().isEmpty()) {
                dao.addCategory(CounterCategory(titleMr = "बाळाची हालचाल", titleEn = "Baby Movements", kind = "BABY"))
                dao.addCategory(CounterCategory(titleMr = "जप / मंत्र", titleEn = "Chants / Mantra", kind = "MANTRA"))
                dao.addCategory(CounterCategory(titleMr = "पावले", titleEn = "Walking Steps", kind = "STEPS", goal = 6000))
            }
        }
    }
}
