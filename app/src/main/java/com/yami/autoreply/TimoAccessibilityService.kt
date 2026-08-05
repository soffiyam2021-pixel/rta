package com.yami.autoreply

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class TimoAccessibilityService : AccessibilityService() {

      companion object {
                var instance: TimoAccessibilityService? = null
                private const val SCAN_CHANNEL_ID = "auto_reply_scan"
                private var scanNotifId = 8000
      }

          override fun onServiceConnected() {
                    super.onServiceConnected()
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
                                    val root = rootInActiveWindow
                                    if (root == null) {
                                                  showScanNotification("Escaneo fallo", "No se pudo leer la pantalla actual. Asegurate de que el permiso de Accesibilidad este activo.")
                                                              return
                                    }

                                            val found = mutableListOf<String>()
                                                    collectNodes(root, found, depth = 0)

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

                                                                                                    chunks.forEachIndexed { index, chunk ->
                                                                                                                  showScanNotification("Escaneo (${index + 1}/${chunks.size})", chunk.toString())
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
                                  }
}
