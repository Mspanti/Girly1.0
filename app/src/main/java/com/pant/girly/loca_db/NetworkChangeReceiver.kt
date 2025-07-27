package com.pant.girly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val isConnected = isOnline(context)
            Log.d("NetworkChangeReceiver", "Network status changed: Connected = $isConnected")
            if (isConnected) {
                // Network is available, trigger sync
                val syncIntent = Intent(context, SosTriggerActivity::class.java).apply {
                    action = ACTION_SYNC_UNSYNCED_ALERTS
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context?.startActivity(syncIntent)
            }
        }
    }

    private fun isOnline(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    companion object {
        const val ACTION_SYNC_UNSYNCED_ALERTS = "com.pant.girly.ACTION_SYNC_UNSYNCED_ALERTS"
    }
}