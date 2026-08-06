package com.yami.autoreply

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
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
                  val root = rootInActiveWindow
                  if (root == null) {
                        showScanNotification("Escaneo fallo", "No se pudo leer la pantalla actual.")
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
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en scanAndNotify: " + e.toString())
            }
      }

      /**
       * Escribe el texto en el primer EditText de la pantalla y toca el boton
        * que este mas a la derecha, en la misma fila (mismo eje Y aproximado),
         * asumiendo que ese es el boton de enviar de la barra de chat.
          * Devuelve true si pudo completar el envio.
           */
           fun autoReplyCurrentChat(replyText: String): Boolean {
                 try {
                       Log.e(TAG, "autoReplyCurrentChat iniciado")
                       val root = rootInActiveWindow
                       if (root == null) {
                             Log.e(TAG, "autoReply: root es null")
                             return false
                       }

                       val editText = findEditText(root)
                       if (editText == null) {
                             Log.e(TAG, "autoReply: no se encontro EditText")
                             return false
                       }

                       val editBounds = Rect()
                       editText.getBoundsInScreen(editBounds)
                       Log.e(TAG, "autoReply: EditText encontrado en " + editBounds.toString())

                       editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                       editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                       val args = Bundle()
                       args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
                       val setOk = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                       Log.e(TAG, "autoReply: ACTION_SET_TEXT devolvio " + setOk)

                       Thread.sleep(600)

                       val freshRoot = rootInActiveWindow
                       if (freshRoot == null) {
                             Log.e(TAG, "autoReply: freshRoot es null despues de escribir")
                             return false
                       }

                       val sendButton = findSendButton(freshRoot, editBounds)
                       if (sendButton == null) {
                             Log.e(TAG, "autoReply: no se encontro boton de enviar")
                             return false
                       }

                       val sendBounds = Rect()
                       sendButton.getBoundsInScreen(sendBounds)
                       Log.e(TAG, "autoReply: boton enviar en " + sendBounds.toString())

                       val clickOk = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                       Log.e(TAG, "autoReply: click en enviar devolvio " + clickOk)
                       return clickOk
                 } catch (e: Exception) {
                       Log.e(TAG, "EXCEPCION en autoReplyCurrentChat: " + e.toString())
                       return false
                 }
           }

           private fun findEditText(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
                 if (depth > 25) return null
                 val className = node.className?.toString() ?: ""
                 if (className.contains("EditText")) return node
                 for (i in 0 until node.childCount) {
                       val child = node.getChild(i) ?: continue
                       val result = findEditText(child, depth + 1)
                       if (result != null) return result
                       child.recycle()
                 }
                 return null
           }

           /** Busca el nodo clickeable mas a la derecha, en la misma franja vertical que el EditText. */
           private fun findSendButton(node: AccessibilityNodeInfo, editBounds: Rect, depth: Int = 0): AccessibilityNodeInfo? {
                 val candidates = mutableListOf<AccessibilityNodeInfo>()
                 collectSendCandidates(node, editBounds, candidates, 0)
                 if (candidates.isEmpty()) return null
                 var best = candidates[0]
                 var bestX = -1
                 for (c in candidates) {
                       val b = Rect()
                       c.getBoundsInScreen(b)
                       if (b.centerX() > bestX) {
                             bestX = b.centerX()
                             best = c
                       }
                 }
                 return best
           }

           private fun collectSendCandidates(node: AccessibilityNodeInfo, editBounds: Rect, candidates: MutableList<AccessibilityNodeInfo>, depth: Int) {
                 if (depth > 25) return
                 if (node.isClickable) {
                       val b = Rect()
                       node.getBoundsInScreen(b)
                       val sameRow = Math.abs(b.centerY() - editBounds.centerY()) < 60
                       val toTheRight = b.centerX() > editBounds.right
                       val notEditTextItself = !(node.className?.toString() ?: "").contains("EditText")
                       if (sameRow && toTheRight && notEditTextItself) {
                             candidates.add(node)
                       }
                 }
                 for (i in 0 until node.childCount) {
                       val child = node.getChild(i) ?: continue
                       collectSendCandidates(child, editBounds, candidates, depth + 1)
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
                       val bounds = Rect()
                       node.getBoundsInScreen(bounds)

                       val parts = mutableListOf<String>()
                       if (!text.isNullOrBlank()) parts.add("texto=\"$text\"")
                       if (!desc.isNullOrBlank()) parts.add("desc=\"$desc\"")
                       if (!id.isNullOrBlank()) parts.add("id=$id")
                       parts.add("clase=$className")
                       parts.add("pos=(" + bounds.centerX() + "," + bounds.centerY() + ")")
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
