package com.example.networkSwitcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.networkSwitcher.databinding.ActivityMainBinding
import java.io.DataOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var activeMode: NetworkMode = NetworkMode.NONE
    private var activeSim: Int = 1

    enum class NetworkMode { LTE, NR, NONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        requestAllPermissions()

        binding.btnSettings.setOnClickListener { openNetworkSettings() }
        binding.btnTesting.setOnClickListener { openPhoneInfo() }
        binding.btnLteOnly.setOnClickListener { applyMode(NetworkMode.LTE) }
        binding.btnNrOnly.setOnClickListener { applyMode(NetworkMode.NR) }
        binding.btnSim1.setOnClickListener { selectSim(1) }
        binding.btnSim2.setOnClickListener { selectSim(2) }
        binding.cardPing.setOnClickListener { runPing() }
    }

    // ── Permissions ─────────────────────────────────────────────

    private fun requestAllPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.READ_PHONE_STATE)

        if (needed.isEmpty()) init()
        else ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PHONE_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHONE_PERMISSION) init()
    }

    private fun init() {
        updateNetworkStatus()
        loadSimInfo()
    }

    // ── Network Status ───────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun updateNetworkStatus() {
        val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val type = try {
            tm.dataNetworkType
        } catch (_: SecurityException) {
            setStatusError("Permission denied"); return
        }

        when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> setStatusNr(tm)
            TelephonyManager.NETWORK_TYPE_LTE -> setStatusLte(tm)
            else -> setStatusOther(type)
        }

        val carrier = tm.networkOperatorName.ifBlank { "Unknown carrier" }
        binding.toolbar.subtitle = "SIM $activeSim · $carrier"
    }

    @SuppressLint("MissingPermission")
    private fun setStatusNr(tm: TelephonyManager) {
        binding.txtNetwork.text = "5G NR"
        binding.txtNetworkDetail.text = "Connected · new radio"
        binding.txtMode.text = "NR"
        binding.chipStatus.text = "Active"

        // Try to get actual band
        val band = try {
            val cells = tm.allCellInfo
            val nrCell = cells?.filterIsInstance<CellInfoNr>()?.firstOrNull()
            val identity = nrCell?.cellIdentity as? android.telephony.CellIdentityNr
            identity?.nrarfcn?.let { arfcn -> nrArfcnToBand(arfcn) } ?: "n78"
        } catch (_: Exception) {
            "n78"
        }
        binding.txtBand.text = band
    }

    private fun setStatusLte(tm: TelephonyManager) {
        binding.txtNetwork.text = "4G LTE"
        binding.txtNetworkDetail.text = "Connected · LTE"
        binding.txtMode.text = "LTE"
        binding.chipStatus.text = "Active"

        val band = try {
            val cells = tm.allCellInfo
            val lteCell = cells?.filterIsInstance<CellInfoLte>()?.firstOrNull()
            val identity = lteCell?.cellIdentity
            identity?.earfcn?.let { earfcn -> lteEarfcnToBand(earfcn) } ?: "B3"
        } catch (_: Exception) {
            "B3"
        }
        binding.txtBand.text = band
    }

    private fun setStatusOther(type: Int) {
        binding.txtNetwork.text = "Connected"
        binding.txtNetworkDetail.text = "Network type: $type"
        binding.txtBand.text = "—"
        binding.txtMode.text = "Auto"
        binding.chipStatus.text = "Active"
    }

    private fun setStatusError(msg: String) {
        binding.txtNetwork.text = "Error"
        binding.txtNetworkDetail.text = msg
        binding.chipStatus.text = "Error"
        binding.txtBand.text = "—"
    }

    // ── Band helpers ─────────────────────────────────────────────

    private fun nrArfcnToBand(arfcn: Int): String = when (arfcn) {
        in 422000..434000 -> "n1"
        in 386000..398000 -> "n3"
        in 173800..178800 -> "n28"
        in 499200..537999 -> "n41"
        in 620000..653333 -> "n78"
        in 620000..680000 -> "n77"
        in 693334..733333 -> "n79"
        else -> "n${arfcn / 10000}"
    }

    private fun lteEarfcnToBand(earfcn: Int): String = when (earfcn) {
        in 0..599 -> "B1"
        in 600..1199 -> "B2"
        in 1200..1949 -> "B3"
        in 1950..2399 -> "B4"
        in 2400..2649 -> "B5"
        in 2750..3449 -> "B7"
        in 3450..3799 -> "B8"
        in 6150..6449 -> "B20"
        in 9870..9919 -> "B28"
        in 36200..36349 -> "B40"
        in 36350..36949 -> "B41"
        else -> "B—"
    }

    // ── Mode switching ───────────────────────────────────────────

    private fun applyMode(mode: NetworkMode) {
        activeMode = mode
        updateModeTileUI()

        val modeCode = if (mode == NetworkMode.LTE) 11 else 20
        var success = false
        if (!success) success = tryBroadcast(modeCode)
        if (!success) success = tryContentProvider(modeCode)
        if (!success) success = tryReflectionWithSubId(modeCode)
        if (!success) success = tryReflection(modeCode)
        if (!success) success = tryRoot(modeCode)

        Handler(Looper.getMainLooper()).postDelayed({ openPhoneInfo() }, 300)
    }

    private fun updateModeTileUI() {
        val isLte = activeMode == NetworkMode.LTE
        binding.btnLteOnly.setCardBackgroundColor(
            ContextCompat.getColorStateList(
                this,
                if (isLte) R.color.tile_active_bg else R.color.tile_inactive_bg
            )
        )
        binding.btnNrOnly.setCardBackgroundColor(
            ContextCompat.getColorStateList(
                this,
                if (!isLte) R.color.tile_active_bg else R.color.tile_inactive_bg
            )
        )
        binding.txtMode.text = if (isLte) "LTE" else "NR"
    }

    // ── SIM ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun loadSimInfo() {
        val sm = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val subs: List<SubscriptionInfo>? = try {
            sm.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        }

        val sim1 = subs?.firstOrNull { it.simSlotIndex == 0 }
        val sim2 = subs?.firstOrNull { it.simSlotIndex == 1 }

        binding.txtSim1Carrier.text = sim1?.carrierName
            ?.toString()?.ifBlank { "Slot empty" } ?: "Slot empty"

        binding.txtSim2Carrier.text = sim2?.carrierName
            ?.toString()?.ifBlank { "Slot empty" } ?: "Not inserted"

        if (sim2 == null) {
            binding.btnSim2.alpha = 0.5f
            binding.btnSim2.isClickable = false
        } else {
            binding.btnSim2.alpha = 1f
            binding.btnSim2.isClickable = true
        }
    }

    private fun selectSim(slot: Int) {
        activeSim = slot
        updateSimTileUI()
        binding.txtSim.text = "SIM $slot"
        updateNetworkStatus()
    }

    private fun updateSimTileUI() {
        val isSim1 = activeSim == 1
        binding.btnSim1.setCardBackgroundColor(
            ContextCompat.getColorStateList(
                this,
                if (isSim1) R.color.tile_active_bg else R.color.tile_inactive_bg
            )
        )
        binding.btnSim2.setCardBackgroundColor(
            ContextCompat.getColorStateList(
                this,
                if (!isSim1) R.color.tile_active_bg else R.color.tile_inactive_bg
            )
        )
    }

    // ── Ping ─────────────────────────────────────────────────────

    private fun runPing() {
        binding.chipPingStatus.text = "Testing…"
        binding.txtPingLabel.text = "Pinging 8.8.8.8…"
        binding.txtPingValue.text = "…"
        binding.txtPingMin.text = "—"
        binding.txtPingAvg.text = "—"
        binding.txtPingMax.text = "—"
        binding.cardPing.isClickable = false

        CoroutineScope(Dispatchers.IO).launch {
            val results = mutableListOf<Long>()
            repeat(4) {
                val ms = pingOnce("8.8.8.8")
                if (ms >= 0) results.add(ms)
                Thread.sleep(300)
            }

            withContext(Dispatchers.Main) {
                binding.cardPing.isClickable = true
                if (results.isEmpty()) {
                    binding.txtPingValue.text = "Failed"
                    binding.txtPingLabel.text = "No response from 8.8.8.8"
                    binding.chipPingStatus.text = "Offline"
                } else {
                    val avg = results.average().toLong()
                    val min = results.min()
                    val max = results.max()
                    binding.txtPingValue.text = "${avg}ms"
                    binding.txtPingLabel.text = "via 8.8.8.8 · ${results.size}/4 replies"
                    binding.chipPingStatus.text = when {
                        avg < 50 -> "Good"
                        avg < 150 -> "Fair"
                        else -> "Poor"
                    }
                    binding.txtPingMin.text = "${min}ms"
                    binding.txtPingAvg.text = "${avg}ms"
                    binding.txtPingMax.text = "${max}ms"
                }
            }
        }
    }

    private fun pingOnce(host: String): Long {
        return try {
            val start = System.currentTimeMillis()
            val proc = Runtime.getRuntime().exec("ping -c 1 -W 2 $host")
            val exit = proc.waitFor()
            if (exit == 0) System.currentTimeMillis() - start else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    // ── Switch methods ───────────────────────────────────────────

    private fun tryBroadcast(mode: Int): Boolean {
        return try {
            sendBroadcast(Intent("android.intent.action.NETWORK_MODE").apply {
                putExtra("networkMode", mode)
            })
            true
        } catch (e: Exception) {
            Log.w(TAG, "broadcast: ${e.message}"); false
        }
    }

    private fun tryContentProvider(mode: Int): Boolean {
        return try {
            contentResolver.update(
                "content://telephony/siminfo".toUri(),
                ContentValues().apply { put("network_mode", mode) },
                null, null
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "provider: ${e.message}"); false
        }
    }

    private fun tryReflectionWithSubId(mode: Int): Boolean {
        return try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val sm = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subId = sm.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: return false
            val m = tm.javaClass.getDeclaredMethod(
                "setPreferredNetworkType",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            m.isAccessible = true
            m.invoke(tm, subId, mode) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "reflection+sub: ${e.message}"); false
        }
    }

    private fun tryReflection(mode: Int): Boolean {
        return try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            try {
                val m = tm.javaClass.getDeclaredMethod(
                    "setPreferredNetworkType", Int::class.javaPrimitiveType
                )
                m.isAccessible = true
                return m.invoke(tm, mode) as? Boolean ?: false
            } catch (_: Exception) {
            }
            val m = tm.javaClass.getDeclaredMethod(
                "setPreferredNetworkType",
                Int::class.javaPrimitiveType, Message::class.java
            )
            m.isAccessible = true
            m.invoke(tm, mode, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "reflection: ${e.message}"); false
        }
    }

    private fun tryRoot(mode: Int): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec("su")
            DataOutputStream(proc.outputStream).use { out ->
                out.writeBytes("settings put global preferred_network_mode $mode\n")
                out.writeBytes("settings put global preferred_network_mode1 $mode\n")
                out.writeBytes("exit\n")
                out.flush()
            }
            proc.waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "root: ${e.message}"); false
        }
    }

    // ── Navigation ───────────────────────────────────────────────

    private fun openNetworkSettings() {
        startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
    }

    private fun openPhoneInfo() {
        val candidates = listOf(
            "com.android.settings" to "com.android.settings.RadioInfo",
            "com.android.settings" to "com.android.settings.Settings\$RadioInfoActivity",
            "com.android.phone" to "com.android.phone.settings.RadioInfo"
        )
        for ((pkg, cls) in candidates) {
            try {
                startActivity(Intent().setClassName(pkg, cls)); return
            } catch (_: Exception) {
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, "tel:*#*#4636#*#*".toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Phone Info not accessible", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "NetworkSwitcher"
        private const val REQUEST_PHONE_PERMISSION = 100
    }
}