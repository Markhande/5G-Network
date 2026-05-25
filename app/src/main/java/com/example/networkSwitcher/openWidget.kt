package com.example.networkSwitcher

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.net.toUri

class NetworkWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

//    override fun onEnabled(context: Context) {
//        super.onEnabled(context)
//        // Widget is added to home screen - register receiver if needed
//    }
//
//    override fun onDisabled(context: Context) {
//        super.onDisabled(context)
//        // Last widget removed - unregister receiver if needed
//    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_LTE -> {
                openPhoneInfo(context, "Tap 'LTE only'")
                scheduleWidgetUpdate(context)
            }
            ACTION_NR -> {
                openPhoneInfo(context, "Tap 'NR only'")
                scheduleWidgetUpdate(context)
            }
            // Listen for connectivity changes
            ConnectivityManager.CONNECTIVITY_ACTION,
            "android.intent.action.ANY_DATA_STATE" -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {

        const val ACTION_LTE = "com.example.networkwitcher.ACTION_LTE"
        const val ACTION_NR = "com.example.networkwitcher.ACTION_NR"

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_network_switcher)

           // views.setTextViewText(R.id.widgetNetwork, getNetworkTextSafe(context))

            views.setOnClickPendingIntent(
                R.id.widgetLte,
                getBroadcastIntent(context, ACTION_LTE)
            )

            views.setOnClickPendingIntent(
                R.id.widgetNr,
                getBroadcastIntent(context, ACTION_NR)
            )

            manager.updateAppWidget(id, views)
        }

        private fun getBroadcastIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, NetworkWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        @SuppressLint("MissingPermission")
        private fun getNetworkTextSafe(context: Context): String {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork ?: return "No Internet"
                val caps = cm.getNetworkCapabilities(network) ?: return "No Internet"

                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                        when (tm.dataNetworkType) {
                            TelephonyManager.NETWORK_TYPE_NR -> "5G Active"
                            TelephonyManager.NETWORK_TYPE_LTE -> "4G Active"
                            TelephonyManager.NETWORK_TYPE_HSPAP,
                            TelephonyManager.NETWORK_TYPE_UMTS -> "3G Active"
                            TelephonyManager.NETWORK_TYPE_EDGE,
                            TelephonyManager.NETWORK_TYPE_GPRS -> "2G Active"
                            else -> "Mobile"
                        }
                    }
                    else -> "Unknown"
                }
            } catch (_: SecurityException) {
                "Permission needed"
            }
        }

        /**
         * Update all widget instances
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, NetworkWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }

            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = widgetManager.getAppWidgetIds(
                ComponentName(context, NetworkWidget::class.java)
            )

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
        }

        /**
         * Schedule widget updates with delay to catch network state changes
         */
        private fun scheduleWidgetUpdate(context: Context) {
            val handler = Handler(Looper.getMainLooper())

            // Update immediately
            updateAllWidgets(context)

            // Update after 1 second (network might take time to switch)
            handler.postDelayed({ updateAllWidgets(context) }, 1000)

            // Update after 3 seconds (to catch delayed state changes)
            handler.postDelayed({ updateAllWidgets(context) }, 3000)

            // Final update after 5 seconds
            handler.postDelayed({ updateAllWidgets(context) }, 5000)
        }
    }

    private fun openPhoneInfo(context: Context, message: String) {
        val candidates = listOf(
            Pair("com.android.settings", "com.android.settings.RadioInfo"),
            Pair("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity"),
            Pair("com.android.phone", "com.android.phone.settings.RadioInfo")
        )

        for ((pkg, cls) in candidates) {
            try {
                val intent = Intent().setClassName(pkg, cls).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {}
        }

        try {
            val dial = Intent(Intent.ACTION_DIAL, "tel:*#*#4636#*#*".toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dial)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Phone Info not accessible", Toast.LENGTH_LONG).show()
        }
    }
}