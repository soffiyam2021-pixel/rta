package com.yami.autoreply

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class TimoAccessibilityService : AccessibilityService() {

      companion object {
            var instance: TimoAccessibilityService? = null
            private const val SCAN_CHANNEL_ID = "auto_reply_scan"
            private var scanNotifId = 8000
            private const val TAG = "AutoReplyDebug"
      }

      override fun onServiceConnected() {
            super.onServiceConnected()
            Log.e(TAG, "TimoAccessibilityService conectado")
            instance = this
      }

      override fun onDestroy() {
            super.onDestroy()
            instance = null
      }

      override fun onAccessibilityEvent(event: AccessibilityEvent?) {
      }

      override fun onInterrupt() {}

      fun scanAndNotify() {
            try {
                  Log.e(TAG, "scanAndNotify entro al try")
                  val root = rootInActiveWindow
                  if (root == null) {
                        Log.e(TAG, "rootInActiveWindow es null")
                        showScanNotification("Escaneo fallo", "No se pudo leer la pantalla actual.")
                        return
                  }
                  Log.e(TAG, "root encontrado, escaneando")

                  val found = mutableListOf<String>()
                  collectNodes(root, found, depth = 0)

                  Log.e(TAG, "elementos encontrados: " + found.size)

                  if (found.isEmpty()) {
                        showScanNotification("Escaneo vacio", "No se encontraron elementos con texto o clickeables en esta pantalla.")
                        return
                  }

                  val chunks = mutableListOf<StringBuilder>()
                  var current = StringBuilder()
                  for (line in found) {
                        if (current.length + line.length > 700) {
                              chunks.add(current)
                              current = StringBuilder()
                        }
                        current.append(line).append("\n")
                  }
                  if (current.isNotEmpty()) chunks.add(current)

                  Log.e(TAG, "voy a mostrar " + chunks.size + " notificaciones")
                  chunks.forEachIndexed { index, chunk ->
                        showScanNotification("Escaneo (${index + 1}/${chunks.size})", chunk.toString())
                  }
                  Log.e(TAG, "termine de mostrar notificaciones")
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en scanAndNotify: " + e.toString())
            }
      }

      private fun collectNodes(node: AccessibilityNodeInfo, found: MutableList<String>, depth: Int) {
            if (depth > 25) return

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val id = node.viewIdResourceName
            val className = node.className?.toString()?.substringAfterLast('.')

            val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank()
            if (node.isClickable || hasContent) {
                  val parts = mutableListOf<String>()
                  if (!text.isNullOrBlank()) parts.add("texto=\"$text\"")
                  if (!desc.isNullOrBlank()) parts.add("desc=\"$desc\"")
                  if (!id.isNullOrBlank()) parts.add("id=$id")
                  parts.add("clase=$className")
                  if (node.isClickable) parts.add("[CLICKEABLE]")
                  if (parts.isNotEmpty()) {
                        found.add(parts.joinToString(" | "))
                  }
            }

            for (i in 0 until node.childCount) {
                  val child = node.getChild(i) ?: continue
                  collectNodes(child, found, depth + 1)
                  child.recycle()
            }
      }

      private fun showScanNotification(title: String, text: String) {
            Log.e(TAG, "showScanNotification: " + title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  val channel = NotificationChannel(
                        SCAN_CHANNEL_ID,
                        "Auto Reply - Escaneo de pantalla",
                        NotificationManager.IMPORTANCE_HIGH
                        )
                  val manager = getSystemService(NotificationManager::class.java)
                  manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(applicationContext, SCAN_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setAutoCancel(true)
            .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(scanNotifId++, notification)
            Log.e(TAG, "notify() llamado con id " + (scanNotifId - 1))
      }
}
