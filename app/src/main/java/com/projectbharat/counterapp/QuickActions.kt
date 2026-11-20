package com.projectbharat.counterapp

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    const val ACTION_START_JAPA = "com.projectbharat.counterapp.action.START_JAPA"
    const val EXTRA_COUNTER_KIND = "counter_kind"

    fun activityPending(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_COUNTER)
                .putExtra(EXTRA_COUNTER_KIND, "MANTRA").putExtra("quick_start", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun publishShortcuts(context: Context, categories: List<com.projectbharat.counterapp.data.CounterCategory>) {
        val category = categories.firstOrNull { it.kind == "MANTRA" } ?: return
        val shortcut = ShortcutInfoCompat.Builder(context, "counter_japa")
            .setShortLabel("Japa").setLongLabel("Open Japa counter")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_COUNTER)
                .putExtra(EXTRA_COUNTER_KIND, "MANTRA")
                .putExtra("quick_start", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
    }
}

object QuickActionsNotification {
    private const val CHANNEL = "counter_quick_actions"
    private const val NOTIFICATION_ID = 7100
    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Japa quick action", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shortcut to open the Japa counter"; setShowBadge(false)
        })
        val open = CounterQuickActions.activityPending(context, 7101)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Japa Counter").setContentText("Tap to open your Japa counter")
            .setContentIntent(open).setAutoCancel(false).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(android.R.drawable.ic_media_play, "Open Japa", open).build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { CounterHomeWidget.refresh(context.applicationContext) }
            finally { pending.finish() }
        }
    }
}

class CounterHomeWidget : android.appwidget.AppWidgetProvider() {
    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CounterHomeWidget::class.java))
            if (ids.isNotEmpty()) CounterHomeWidget().onUpdate(context, manager, ids)
        }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val dao = CounterDatabase.get(context).dao()
            val japa = dao.categories().first().firstOrNull { it.kind == "MANTRA" }
            val total = japa?.let { dao.countNow(it.id, LocalDate.now().toString()) } ?: 0
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.counter_widget)
                views.setTextViewText(R.id.widget_date, date)
                views.setTextViewText(R.id.widget_japa_action, "JAPA\n$total chants\n＋ Open")
                views.setOnClickPendingIntent(R.id.widget_japa_action, CounterQuickActions.activityPending(context, 8102))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
