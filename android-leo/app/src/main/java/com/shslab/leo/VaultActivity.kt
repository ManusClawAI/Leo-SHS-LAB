package com.shslab.leo

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.shslab.leo.security.SecurityManager

class VaultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply { text = "Leo Vault\n\nActive Provider: ${SecurityManager.getActiveProvider()}\nModel: ${SecurityManager.getActiveModel()}\nAPI Key: ${if (SecurityManager.getActiveApiKey().isNotBlank()) "✓ Set" else "Not set"}"; setPadding(32, 32, 32, 32) }
        setContentView(tv)
    }
}
