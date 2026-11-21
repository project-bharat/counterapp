package com.projectbharat.counterapp

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.projectbharat.counterapp.data.CounterDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CounterQuickActions {
    const val EXTRA_COUNTER_KIND = "counter_kind"

    private fun japaIntent(context: Context) =
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_COUNTER)
            .putExtra(EXTRA_COUNTER_KIND, "MANTRA").putExtra("quick_start", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun activityPending(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, japaIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun publishShortcuts(context: Context, categories: List<com.projectbharat.counterapp.data.CounterCategory>) {
        val shortcuts = if (categories.any { it.kind == "MANTRA" }) {
            listOf(ShortcutInfoCompat.Builder(context, "counter_mantra")
                .setShortLabel("Japa").setLongLabel("Start Japa counter")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(japaIntent(context))
                .build())
        } else emptyList()
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        ShortcutManagerCompat.disableShortcuts(context, listOf("counter_baby"), "This shortcut has been retired")
    }
}


object QuickActionsNotification {
    private const val CHANNEL = "counter_quick_actions"
    private const val NOTIFICATION_ID = 7100

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(7105)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Counter quick actions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shortcut to start Japa"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(context, 7100,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Daily Counter")
            .setContentText("Quick start · Japa")
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(android.R.drawable.ic_media_play, "Start Japa",
                CounterQuickActions.activityPending(context, 7104))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}


class CounterHomeWidget : android.appwidget.AppWidgetProvider() {
    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CounterHomeWidget::class.java))
            if (ids.isNotEmpty()) CounterHomeWidget().update(context, manager, ids)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        update(context, manager, ids) { pending?.finish() }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray, onComplete: () -> Unit = {}) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = CounterDatabase.get(context).dao()
                val categories = dao.categories().first()
                val date = LocalDate.now()
                val today = date.toString()
                val todayLabel = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
                val mantra = categories.firstOrNull { it.kind == "MANTRA" }
                val mantraCount = mantra?.let { dao.countNow(it.id, today) } ?: 0
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.counter_widget)
                    views.setTextViewText(R.id.widget_date, todayLabel)
                    views.setTextViewText(R.id.widget_japa_action, "📿  JAPA\n$mantraCount chants\n＋ Start")
                    views.setOnClickPendingIntent(R.id.widget_japa_action, CounterQuickActions.activityPending(context, 8102))
                    manager.updateAppWidget(id, views)
                }
            } finally {
                onComplete()
            }
        }
    }
}
