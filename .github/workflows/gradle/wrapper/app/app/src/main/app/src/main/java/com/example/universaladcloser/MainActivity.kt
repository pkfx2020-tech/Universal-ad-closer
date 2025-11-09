package com.example.universaladcloser

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.view.accessibility.AccessibilityManager
import android.content.Context

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var statsText: TextView
    private lateinit var settingsButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById(R.id.enableSwitch)
        statsText = findViewById(R.id.statsText)
        settingsButton = findViewById(R.id.settingsButton)
        
        updateUI()
        
        enableSwitch.setOnClickListener {
            if (enableSwitch.isChecked) {
                if (!isAccessibilityServiceEnabled()) {
                    showAccessibilityDialog()
                    enableSwitch.isChecked = false
                }
            }
        }
        
        settingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // Atualiza estatísticas
        updateStats()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        updateStats()
    }
    
    private fun updateUI() {
        val isEnabled = isAccessibilityServiceEnabled()
        enableSwitch.isChecked = isEnabled
        
        if (isEnabled) {
            statusText.text = "✓ Proteção Ativa"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusText.text = "✗ Proteção Desativada"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun updateStats() {
        val prefs = getSharedPreferences("AdCloserStats", Context.MODE_PRIVATE)
        val adsBlocked = prefs.getInt("ads_blocked", 0)
        statsText.text = "Anúncios fechados: $adsBlocked"
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(packageName) == true
    }
    
    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissão Necessária")
            .setMessage(
                "Para fechar anúncios automaticamente em todos os apps, " +
                "é necessário ativar o serviço de Acessibilidade.\n\n" +
                "Como ativar:\n" +
                "1. Toque em 'Abrir Configurações'\n" +
                "2. Encontre 'Universal Ad Closer'\n" +
                "3. Ative o serviço\n\n" +
                "⚠️ Este app NÃO coleta seus dados!"
            )
            .setPositiveButton("Abrir Configurações") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}
