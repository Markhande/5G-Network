package com.example.networkSwitcher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri

class OpenSettings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_open_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        openPhoneInfo()
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
            startActivity(Intent(Intent.ACTION_DIAL, "tel:*#*#4636#*#*".toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Phone Info not accessible", Toast.LENGTH_LONG).show()
        }
    }
}