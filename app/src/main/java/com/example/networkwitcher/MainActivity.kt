package com.example.networkwitcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.networkwitcher.databinding.ActivityMainBinding
import java.io.DataOutputStream
import java.lang.reflect.Method

class MainActivity : AppCompatActivity() {
    private val REQ_PHONE = 100
    private lateinit var binding: ActivityMainBinding

    @androidx.annotation.RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestAllPermissions()

        binding.btnSettings.setOnClickListener {
            openNetworkSettings()
        }

        binding.btnTesting.setOnClickListener {
            openPhoneInfo()
        }

        binding.btnLteOnly.setOnClickListener  {
            setAndOpenLteOnly()
        }

        binding.btnNrOnly.setOnClickListener {
            setAndOpenNrOnly()
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        // This permission is signature-level, but we'll try anyway
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_PHONE_STATE")
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add("android.permission.MODIFY_PHONE_STATE")
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_PHONE)
        } else {
            updateNetworkStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PHONE) {
            updateNetworkStatus()
        }
    }

    private fun updateNetworkStatus() {
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val networkType = try {
            tm.dataNetworkType
        } catch (e: SecurityException) {
            binding.txtNetwork.text = "Permission error"
            return
        }

        val network = when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "Connected to 5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "Connected to 4G"
            else -> "Other: $networkType"
        }

        binding.txtNetwork.text = network
    }

    private fun openNetworkSettings() {
        startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
    }

    private fun openPhoneInfo() {
        val candidates = listOf(
            Pair("com.android.settings", "com.android.settings.RadioInfo"),
            Pair("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity"),
            Pair("com.android.phone", "com.android.phone.settings.RadioInfo")
        )

        for ((pkg, cls) in candidates) {
            try {
                startActivity(Intent().setClassName(pkg, cls))
                return
            } catch (_: Exception) {}
        }

        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*")))
        } catch (_: Exception) {
            Toast.makeText(this, "Phone Info not accessible", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun setAndOpenLteOnly() {
        // Try multiple methods to set network type
        var success = false

        // Method 1: Broadcast Intent (some devices support this)
        try {
            val intent = Intent("android.intent.action.NETWORK_MODE")
            intent.putExtra("networkMode", 11)
            sendBroadcast(intent)
            success = true
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "Broadcast method failed: ${e.message}")
        }

        // Method 2: ContentProvider approach
        if (!success) {
            success = setNetworkModeViaContentProvider(11)
        }

        // Method 3: Reflection with subscription ID
        if (!success) {
            success = setPreferredNetworkTypeWithSubId(11)
        }

        // Method 4: Old reflection method
        if (!success) {
            success = setPreferredNetworkType(11)
        }

        // Method 5: Root command (if device is rooted)
        if (!success) {
            success = setNetworkModeViaRoot(11)
        }

        // Wait a bit then open phone info
        Handler(Looper.getMainLooper()).postDelayed({
            openPhoneInfo()
        }, 100)
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun setAndOpenNrOnly() {
        var success = false

        try {
            val intent = Intent("android.intent.action.NETWORK_MODE")
            intent.putExtra("networkMode", 20)
            sendBroadcast(intent)
            success = true
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "Broadcast method failed: ${e.message}")
        }

        if (!success) {
            success = setNetworkModeViaContentProvider(20)
        }

        if (!success) {
            success = setPreferredNetworkTypeWithSubId(20)
        }

        if (!success) {
            success = setPreferredNetworkType(20)
        }

        if (!success) {
            success = setNetworkModeViaRoot(20)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            openPhoneInfo()
        }, 1000)
    }

    private fun setNetworkModeViaContentProvider(networkType: Int): Boolean {
        return try {
            val uri = Uri.parse("content://telephony/siminfo")
            val values = ContentValues().apply {
                put("network_mode", networkType)
            }
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Network mode set via provider", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "ContentProvider method failed: ${e.message}")
            false
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun setPreferredNetworkTypeWithSubId(networkType: Int): Boolean {
        return try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val subscriptionManager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val subId = subscriptionManager.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: 0

            val method = telephonyManager.javaClass.getDeclaredMethod(
                "setPreferredNetworkType",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            val result = method.invoke(telephonyManager, subId, networkType) as Boolean

            if (result) {
                Toast.makeText(this, "Network mode set with SubId", Toast.LENGTH_SHORT).show()
            }
            result
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "SubId method failed: ${e.message}")
            false
        }
    }

    private fun setPreferredNetworkType(networkType: Int): Boolean {
        return try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            // Try method without Message parameter
            try {
                val method = telephonyManager.javaClass.getDeclaredMethod(
                    "setPreferredNetworkType",
                    Int::class.javaPrimitiveType
                )
                method.isAccessible = true
                val result = method.invoke(telephonyManager, networkType) as Boolean
                if (result) {
                    Toast.makeText(this, "Network mode set (simple)", Toast.LENGTH_SHORT).show()
                    return true
                }
            } catch (e: Exception) {
                Log.e("NetworkSwitch", "Simple method failed: ${e.message}")
            }

            // Try with Message parameter
            val method = telephonyManager.javaClass.getDeclaredMethod(
                "setPreferredNetworkType",
                Int::class.javaPrimitiveType,
                Message::class.java
            )
            method.isAccessible = true
            method.invoke(telephonyManager, networkType, null)
            Toast.makeText(this, "Network mode set (legacy)", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "Reflection method failed: ${e.message}")
            false
        }
    }

    private fun setNetworkModeViaRoot(networkType: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val writer = DataOutputStream(process.outputStream)

            // Try different ADB/root commands
            writer.writeBytes("settings put global preferred_network_mode $networkType\n")
            writer.writeBytes("settings put global preferred_network_mode1 $networkType\n")
            writer.writeBytes("exit\n")
            writer.flush()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Toast.makeText(this, "Network mode set via root", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("NetworkSwitch", "Root method failed: ${e.message}")
            false
        }
    }
}