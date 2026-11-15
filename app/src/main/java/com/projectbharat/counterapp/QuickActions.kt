package com.projectbharat.counterapp

import android.Manifest
import android.app.PendingIntent
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
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.projectbharat.counterapp.data.CounterDatabase
import com.projectbharat.counterapp.data.CounterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

object CounterQuickActions {
    const val ACTION_START_BABY = "com.projectbharat.counterapp.action.START_BABY"
    const val ACTION_START_WALK = "com.projectbharat.counterapp.action.START_WALK"
    const val ACTION_START_JAPA = "com.projectbharat.counterapp.action.START_JAPA"
    const val EXTRA_COUNTER_KIND = "counter_kind"

    fun pending(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode,
            Intent(context, QuickActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun activityPending(context: Context, kind: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_COUNTER)
                .putExtra(EXTRA_COUNTER_KIND, kind).putExtra("quick_start", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun publishShortcuts(context: Context, categories: List<com.projectbharat.counterapp.data.CounterCategory>) {
        val kinds = listOf("BABY", "MANTRA", "STEPS")
        val shortcuts = kinds.mapNotNull { kind ->
            val category = categories.firstOrNull { it.kind == kind } ?: return@mapNotNull null
            val (short, long) = when (kind) {
                "BABY" -> "Baby" to "Start baby movement session"
                "MANTRA" -> "Japa" to "Start Japa counter"
                else -> "Walk" to "Start walking session"
            }
            ShortcutInfoCompat.Builder(context, "counter_${kind.lowercase()}")
                .setShortLabel(short).setLongLabel(long)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_COUNTER)
                    .putExtra(EXTRA_COUNTER_KIND, kind)
                    .putExtra("quick_start", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}


object QuickActionsNotification {
    private const val CHANNEL = "counter_quick_actions"
    private const val NOTIFICATION_ID = 7100

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Counter quick actions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shortcuts to start a baby movement session or Japa"
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(context, 7100,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Daily Counter")
            .setContentText("Quick start · Baby session · Japa")
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(android.R.drawable.ic_menu_my_calendar, "Start session",
                CounterQuickActions.activityPending(context, "BABY", 7103))
            .addAction(android.R.drawable.ic_media_play, "Start Japa",
                CounterQuickActions.activityPending(context, "MANTRA", 7104))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val app = context.applicationContext
            try {
                val dao = CounterDatabase.get(app).dao()
                val categories = dao.categories().first()
                val baby = categories.firstOrNull { it.kind == "BABY" }
                val mantra = categories.firstOrNull { it.kind == "MANTRA" }
                val steps = categories.firstOrNull { it.kind == "STEPS" }
                when (action) {
                    CounterQuickActions.ACTION_START_BABY -> {
                        baby?.let {
                            CounterRepository(dao).startBabySession(it.id)
                            openCounter(app, "BABY")
                        }
                    }
                    CounterQuickActions.ACTION_START_WALK -> {
                        val ready = hasPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) &&
                            hasPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
                        if (ready && steps != null) {
                            StepTrackingService.start(app, steps.id)
                            openCounter(app, "STEPS")
                        } else openCounter(app, "STEPS")
                    }
                    CounterQuickActions.ACTION_START_JAPA -> {
                        val ready = hasPermission(app, Manifest.permission.RECORD_AUDIO)
                        if (ready && mantra != null) {
                            val prefs = app.getSharedPreferences("chant_settings", Context.MODE_PRIVATE)
                            val target = prefs.getString("target_${mantra.id}", "राम") ?: "राम"
                            val language = if (target.any { it in '\u0900'..'\u097F' }) "hi" else "en"
                            ChantRecognitionService.start(app, mantra.id, target, language)
                        }
                        openCounter(app, "MANTRA")
                    }
                }
                CounterHomeWidget.refresh(app)
            } catch (_: Exception) {
                // Permission and foreground-service failures are handled when the user opens the selected counter.
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun openCounter(context: Context, kind: String) {
        val launch = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_COUNTER)
            .putExtra(CounterQuickActions.EXTRA_COUNTER_KIND, kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(launch)
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
            try {
                val dao = CounterDatabase.get(context).dao()
                val categories = dao.categories().first()
                val today = LocalDate.now().toString()
                val baby = categories.firstOrNull { it.kind == "BABY" }
                val mantra = categories.firstOrNull { it.kind == "MANTRA" }
                val steps = categories.firstOrNull { it.kind == "STEPS" }
                val babyCount = baby?.let { dao.countNow(it.id, today) } ?: 0
                val mantraCount = mantra?.let { dao.countNow(it.id, today) } ?: 0
                val stepCount = steps?.let { dao.countNow(it.id, today) } ?: 0
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.counter_widget)
                    views.setTextViewText(R.id.widget_date, today)
                    views.setTextViewText(R.id.widget_baby_action, "BABY\\n$babyCount moves\\n＋ Session")
                    views.setTextViewText(R.id.widget_japa_action, "JAPA\\n$mantraCount chants\\n＋ Start")
                    views.setTextViewText(R.id.widget_steps_action, "WALK\\n$stepCount steps\\n＋ Start")
                    views.setOnClickPendingIntent(R.id.widget_baby_action, CounterQuickActions.activityPending(context, "BABY", 8101))
                    views.setOnClickPendingIntent(R.id.widget_japa_action, CounterQuickActions.activityPending(context, "MANTRA", 8102))
                    views.setOnClickPendingIntent(R.id.widget_steps_action, CounterQuickActions.activityPending(context, "STEPS", 8103))
                    manager.updateAppWidget(id, views)
                }
            } finally {
                // The widget is refreshed on explicit updates and app launches.
            }
        }
    }
}
