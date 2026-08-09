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

      private fun isValidBounds(b: Rect): Boolean {
            val w = b.width()
            val h = b.height()
            return w > 10 && h > 10 && w < 3000 && h < 3000
      }

      fun respondToAllUnread(replyText: String) {
            try {
                  Log.e(TAG, "respondToAllUnread iniciado")
                  var attempts = 0
                  var repliedCount = 0
                  while (attempts < 10) {
                        attempts++
                        val root = rootInActiveWindow
                        if (root == null) {
                              Log.e(TAG, "respondToAllUnread: root es null, freno")
                              break
                        }

                        val unreadRow = findFirstUnreadRow(root)
                        if (unreadRow == null) {
                              Log.e(TAG, "respondToAllUnread: no hay mas conversaciones sin leer visibles")
                              break
                        }

                        val rowBounds = Rect()
                        unreadRow.getBoundsInScreen(rowBounds)
                        Log.e(TAG, "respondToAllUnread: abriendo fila en " + rowBounds.toString())

                        val clickOk = unreadRow.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.e(TAG, "respondToAllUnread: click en fila devolvio " + clickOk)
                        if (!clickOk) break

                        Thread.sleep(1200)

                        val sent = autoReplyCurrentChat(replyText)
                        Log.e(TAG, "respondToAllUnread: autoReplyCurrentChat devolvio " + sent)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Thread.sleep(700)
                        if (sent) repliedCount++

                        Thread.sleep(500)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Thread.sleep(1000)
                  }
                  Log.e(TAG, "respondToAllUnread: termino, respondidas=" + repliedCount)
                  showScanNotification("Respuestas automaticas", "Se respondieron " + repliedCount + " conversaciones sin leer.")
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en respondToAllUnread: " + e.toString())
            }
      }

      private fun findFirstUnreadRow(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val badges = mutableListOf<AccessibilityNodeInfo>()
            collectUnreadBadges(root, badges, 0)
            Log.e(TAG, "findFirstUnreadRow: globos encontrados=" + badges.size)

            var best: AccessibilityNodeInfo? = null
            var bestY = Int.MAX_VALUE
            for (badge in badges) {
                  val b = Rect()
                  badge.getBoundsInScreen(b)
                  Log.e(TAG, "findFirstUnreadRow: candidato globo bounds=" + b.toString())
                  val container = findClickableAncestor(badge, 10)
                  if (container != null) {
                        val cb = Rect()
                        container.getBoundsInScreen(cb)
                        Log.e(TAG, "findFirstUnreadRow: contenedor bounds=" + cb.toString())
                        if (isValidBounds(cb) && cb.height() > 100 && cb.width() > 300 && cb.top < bestY) {
                              bestY = cb.top
                              best = container
                        }
                  }
            }
            return best
      }

      private fun collectUnreadBadges(node: AccessibilityNodeInfo, found: MutableList<AccessibilityNodeInfo>, depth: Int) {
            if (depth > 25) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.matches(Regex("^[0-9]{1,3}$")) || text == "99+")) {
                  val b = Rect()
                  node.getBoundsInScreen(b)
                  if (isValidBounds(b) && b.width() < 60 && b.height() < 60 && b.left < 250) {
                        found.add(node)
                  }
            }
            for (i in 0 until node.childCount) {
                  val child = node.getChild(i) ?: continue
                  collectUnreadBadges(child, found, depth + 1)
            }
      }

      private fun findClickableAncestor(node: AccessibilityNodeInfo, maxUp: Int): AccessibilityNodeInfo? {
            var current: AccessibilityNodeInfo? = node
            var steps = 0
            while (current != null && steps < maxUp) {
                  if (current.isClickable) return current
                  current = current.parent
                  steps++
            }
            return null
      }

      fun autoReplyCurrentChat(replyText: String): Boolean {
            try {
                  Log.e(TAG, "autoReplyCurrentChat iniciado")
                  val root = rootInActiveWindow
                  if (root == null) {
                        Log.e(TAG, "autoReply: root es null")
                        return false
                  }

                  val editCandidates = mutableListOf<AccessibilityNodeInfo>()
                  collectEditTexts(root, editCandidates, 0)
                  Log.e(TAG, "autoReply: EditText candidatos encontrados: " + editCandidates.size)

                  var editText: AccessibilityNodeInfo? = null
                  var bestY = -1
                  for (c in editCandidates) {
                        val b = Rect()
                        c.getBoundsInScreen(b)
                        if (isValidBounds(b) && b.bottom > bestY) {
                              bestY = b.bottom
                              editText = c
                        }
                  }

                  if (editText == null) {
                        Log.e(TAG, "autoReply: ningun EditText con coordenadas validas")
                        return false
                  }

                  val editBounds = Rect()
                  editText.getBoundsInScreen(editBounds)
                  Log.e(TAG, "autoReply: EditText elegido en " + editBounds.toString())

                  editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                  editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                  val args = Bundle()
                  args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
                  val setOk = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                  Log.e(TAG, "autoReply: ACTION_SET_TEXT devolvio " + setOk)

                  Thread.sleep(900)

                  val freshRoot = rootInActiveWindow
                  if (freshRoot == null) {
                        Log.e(TAG, "autoReply: freshRoot es null despues de escribir")
                        return false
                  }

                  val freshEditCandidates = mutableListOf<AccessibilityNodeInfo>()
                  collectEditTexts(freshRoot, freshEditCandidates, 0)
                  var freshEditBounds = editBounds
                  for (c in freshEditCandidates) {
                        val b = Rect()
                        c.getBoundsInScreen(b)
                        if (isValidBounds(b)) {
                              freshEditBounds = b
                        }
                  }

                  val rowCandidates = mutableListOf<AccessibilityNodeInfo>()
                  collectRowCandidates(freshRoot, freshEditBounds, rowCandidates, 0)
                  Log.e(TAG, "autoReply: candidatos en la fila: " + rowCandidates.size)

                  var sendButton: AccessibilityNodeInfo? = null
                  var bestX = -1
                  for (c in rowCandidates) {
                        val b = Rect()
                        c.getBoundsInScreen(b)
                        if (isValidBounds(b) && b.centerX() > bestX) {
                              bestX = b.centerX()
                              sendButton = c
                        }
                  }

                  if (sendButton == null) {
                        Log.e(TAG, "autoReply: no se encontro boton de enviar con coordenadas validas")
                        return false
                  }

                  val sendBounds = Rect()
                  sendButton.getBoundsInScreen(sendBounds)
                  Log.e(TAG, "autoReply: boton enviar elegido en " + sendBounds.toString())

                  val clickOk = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                  Log.e(TAG, "autoReply: click en enviar devolvio " + clickOk)
                  return clickOk
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en autoReplyCurrentChat: " + e.toString())
                  return false
            }
      }

      private fun collectEditTexts(node: AccessibilityNodeInfo, found: MutableList<AccessibilityNodeInfo>, depth: Int) {
            if (depth > 25) return
            val className = node.className?.toString() ?: ""
            if (className.contains("EditText")) {
                  found.add(node)
            }
            for (i in 0 until node.childCount) {
                  val child = node.getChild(i) ?: continue
                  collectEditTexts(child, found, depth + 1)
            }
      }

      private fun collectRowCandidates(node: AccessibilityNodeInfo, editBounds: Rect, candidates: MutableList<AccessibilityNodeInfo>, depth: Int) {
            if (depth > 25) return
            if (node.isClickable) {
                  val b = Rect()
                  node.getBoundsInScreen(b)
                  val sameRow = Math.abs(b.centerY() - editBounds.centerY()) < 100
                  val notEditTextItself = !(node.className?.toString() ?: "").contains("EditText")
                  val notTooWide = b.width() < editBounds.width()
                  if (sameRow && notEditTextItself && notTooWide) {
                        candidates.add(node)
                  }
            }
            for (i in 0 until node.childCount) {
                  val child = node.getChild(i) ?: continue
                  collectRowCandidates(child, editBounds, candidates, depth + 1)
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
