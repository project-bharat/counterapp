package com.projectbharat.counterapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SeedData {
    fun initialize(context: Context) {
        val dao = CounterDatabase.get(context).dao()
        CoroutineScope(Dispatchers.IO).launch {
            dao.ensureJapaCategory()
        }
    }
}
