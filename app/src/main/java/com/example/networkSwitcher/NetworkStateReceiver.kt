package com.example.networkSwitcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

class NetworkStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            "android.intent.action.ANY_DATA_STATE",
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                // Network state changed, update all widgets
                NetworkWidget.updateAllWidgets(context)
            }
        }
    }
}