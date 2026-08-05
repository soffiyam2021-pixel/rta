package com.yami.autoreply

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.yami.autoreply.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apiKeyInput.setText(SecurePrefs.getApiKey(this))
        binding.promptInput.setText(SecurePrefs.getPrompt(this))
        binding.activeSwitch.isChecked = SecurePrefs.isActive(this)
        updateStatusText()

        binding.saveButton.setOnClickListener {
            SecurePrefs.saveApiKey(this, binding.apiKeyInput.text.toString().trim())
            SecurePrefs.savePrompt(this, binding.promptInput.text.toString().trim())
        }

        binding.permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.overlayPermissionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                startActivity(intent)
            }
        }

        binding.chooseAppsButton.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.activeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && binding.apiKeyInput.text.toString().isBlank()) {
                AlertDialog.Builder(this).setTitle("Falta la API key").setMessage("Ingresa tu API key de Gemini y guarda la configuracion antes de activar.").setPositiveButton("Entendido", null).show()
                binding.activeSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            SecurePrefs.setActive(this, isChecked)
            if (isChecked) {
                startFloatingService()
            } else {
                stopService(Intent(this, FloatingBubbleService::class.java))
            }
            updateStatusText()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateStatusText() {
        binding.statusText.text = if (SecurePrefs.isActive(this)) "Estado: activo" else "Estado: inactivo"
    }
}
