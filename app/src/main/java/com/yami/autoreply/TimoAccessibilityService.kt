package com.yami.autoreply

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Path
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
            private const val MAX_UNREAD_PER_RUN = 30
            private const val HEADER_ZONE_TOP = 350
            private val TIMO_PACKAGES = setOf("com.hwsj.chat", "com.hwsj.club")
            private const val AUTO_CHECK_COOLDOWN_MS = 3000L
            @Volatile private var isProcessing = false
            @Volatile private var lastAutoCheckTime = 0L
            private val KNOWN_UI_LABELS = setOf(
                  "Saudação à correspondência",
                  "Video",
                  "Voz",
                  "Mensaje oficial",
                  "En línea",
                  "Charlamos ayer",
                  "Solo uno",
                  "Visibilidad",
                  "Di algo..."
                  )
            private val KEYWORD_BLOCKLIST = listOf(
                  "recompensa",
                  "diamante",
                  "encanto",
                  "cautivado",
                  "obtendrás",
                  "Responde para ganar",
                  "Al recibir",
                  "Felicidades",
                  "llamada de voz",
                  "videollamada",
                  "Invite ahora",
                  "Invitación",
                  "nivel de intimidad",
                  "Envíen fotos",
                  "Enviar fotos",
                  "desbloquear",
                  "acercarte más",
                  "Deseo diario",
                  "penalidades",
                  "conteúdo adulto",
                  "transferências",
                  "aplicativos de terceiros",
                  "regalo de amor",
                  "vaya numero",
                  "vaya número"
                  )
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
            try {
                  if (!SecurePrefs.isActive(applicationContext)) return

                  val pkg = event?.packageName?.toString() ?: return
                  if (!TIMO_PACKAGES.contains(pkg)) return
                  if (isProcessing) return

                  val now = System.currentTimeMillis()
                  if (now - lastAutoCheckTime < AUTO_CHECK_COOLDOWN_MS) return
                  lastAutoCheckTime = now

                  val root = rootInActiveWindow ?: return
                  val unreadRow = findFirstUnreadRow(root)
                  if (unreadRow == null) return

                  Log.e(TAG, "onAccessibilityEvent: mensajes sin leer detectados con Timo abierto, disparando respuesta automatica")
                  Thread {
                        respondToAllUnread()
                  }.start()
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en onAccessibilityEvent: " + e.toString())
            }
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

      private fun isStillInChat(): Boolean {
            val root = rootInActiveWindow ?: return false
            return findBestEditText(root) != null
      }

      fun respondToAllUnread() {
            if (isProcessing) {
                  Log.e(TAG, "respondToAllUnread: ya hay un proceso en curso, se ignora")
                  return
            }
            if (!SecurePrefs.isActive(applicationContext)) {
                  Log.e(TAG, "respondToAllUnread: la auto-respuesta esta desactivada, se ignora")
                  return
            }
            isProcessing = true
            try {
                  Log.e(TAG, "respondToAllUnread iniciado")
                  var attempts = 0
                  var repliedCount = 0
                  while (attempts < MAX_UNREAD_PER_RUN) {
                        if (!SecurePrefs.isActive(applicationContext)) {
                              Log.e(TAG, "respondToAllUnread: se desactivo durante la ejecucion, freno")
                              break
                        }
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

                        val sent = autoReplyWithAI()
                        Log.e(TAG, "respondToAllUnread: autoReplyWithAI devolvio " + sent)
                        if (sent) repliedCount++

                        Thread.sleep(500)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Thread.sleep(700)
                        if (isStillInChat()) {
                              performGlobalAction(GLOBAL_ACTION_BACK)
                              Thread.sleep(1000)
                        } else {
                              Thread.sleep(300)
                        }
                  }
                  Log.e(TAG, "respondToAllUnread: termino, respondidas=" + repliedCount + " intentos=" + attempts)
                  showScanNotification("Respuestas automaticas", "Se respondieron " + repliedCount + " conversaciones sin leer.")
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en respondToAllUnread: " + e.toString())
            } finally {
                  lastAutoCheckTime = System.currentTimeMillis()
                  isProcessing = false
            }
      }

      private fun findFirstUnreadRow(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val badges = mutableListOf<AccessibilityNodeInfo>()
            collectUnreadBadges(root, badges, 0)

            var best: AccessibilityNodeInfo? = null
            var bestY = Int.MAX_VALUE
            for (badge in badges) {
                  val container = findClickableAncestor(badge, 10)
                  if (container != null) {
                        val cb = Rect()
                        container.getBoundsInScreen(cb)
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
            val root = rootInActiveWindow ?: return false
            val editText = findBestEditText(root) ?: return false
            val editBounds = Rect()
            editText.getBoundsInScreen(editBounds)
            return typeAndSend(editText, editBounds, replyText)
      }

      fun autoReplyWithAI(): Boolean {
            try {
                  Log.e(TAG, "autoReplyWithAI iniciado")
                  val root = rootInActiveWindow
                  if (root == null) {
                        Log.e(TAG, "autoReplyWithAI: root es null")
                        return false
                  }

                  val editText = findBestEditText(root)
                  if (editText == null) {
                        Log.e(TAG, "autoReplyWithAI: ningun EditText con coordenadas validas")
                        return false
                  }
                  val editBounds = Rect()
                  editText.getBoundsInScreen(editBounds)

                  val incomingMessage = findLatestIncomingMessage(root, editBounds)
                  Log.e(TAG, "autoReplyWithAI: mensaje entrante detectado: " + incomingMessage)

                  if (incomingMessage.isNullOrBlank()) {
                        Log.e(TAG, "autoReplyWithAI: no se detecto mensaje entrante, cancelando")
                        return false
                  }

                  val apiKeys = SecurePrefs.getApiKeysList(applicationContext)
                  val userInstructions = SecurePrefs.getPrompt(applicationContext)

                  val result = ClaudeApiClient.generateReply(
                        apiKeys,
                        "Timo",
                        "",
                        incomingMessage,
                        userInstructions
                        )

                  val replyText: String
                  when (result) {
                        is ReplyResult.Success -> {
                              replyText = result.text
                              Log.e(TAG, "autoReplyWithAI: IA genero: " + replyText)
                        }
                        is ReplyResult.Error -> {
                              Log.e(TAG, "autoReplyWithAI: error de IA: " + result.message)
                              return false
                        }
                  }

                  return typeAndSend(editText, editBounds, replyText)
            } catch (e: Exception) {
                  Log.e(TAG, "EXCEPCION en autoReplyWithAI: " + e.toString())
                  return false
            }
      }

      private fun findBestEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val editCandidates = mutableListOf<AccessibilityNodeInfo>()
            collectEditTexts(root, editCandidates, 0)
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
            return editText
      }

      private fun hasEmoji(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                  val codePoint = text.codePointAt(i)
                  if (
                        (codePoint in 0x1F300..0x1FAFF) ||
                              (codePoint in 0x2600..0x27BF) ||
                              (codePoint in 0x2190..0x21FF) ||
                              (codePoint in 0x2B00..0x2BFF) ||
                              (codePoint in 0x1F1E6..0x1F1FF)
                              ) {
                        return true
                  }
                  i += Character.charCount(codePoint)
            }
            return false
      }

      private fun looksLikeNoise(text: String): Boolean {
            val hasLetter = text.any { it.isLetter() }
            if (hasLetter) return false
            if (hasEmoji(text)) return false
            if (text.contains("<img>")) return true
            return true
      }

      private fun findLatestIncomingMessage(root: AccessibilityNodeInfo, editBounds: Rect): String? {
            val candidates = mutableListOf<Pair<String, Rect>>()
            collectMessageTexts(root, candidates, 0)

            val filtered = candidates.filter { (text, bounds) ->
                  val isBlocked = KEYWORD_BLOCKLIST.any { kw -> text.contains(kw, ignoreCase = true) }
                  bounds.top < editBounds.top - 20 &&
                  bounds.top > HEADER_ZONE_TOP &&
                  text.length > 1 &&
                  !KNOWN_UI_LABELS.contains(text) &&
                  !isBlocked &&
                  !looksLikeNoise(text) &&
                  text != "99+"
            }

            val best = filtered.maxByOrNull { it.second.top }
            return best?.first
      }

      private fun collectMessageTexts(node: AccessibilityNodeInfo, found: MutableList<Pair<String, Rect>>, depth: Int) {
            if (depth > 25) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                  val b = Rect()
                  node.getBoundsInScreen(b)
                  if (isValidBounds(b)) {
                        found.add(Pair(text, b))
                  }
            }
            for (i in 0 until node.childCount) {
                  val child = node.getChild(i) ?: continue
                  collectMessageTexts(child, found, depth + 1)
            }
      }

      private fun currentEditTextContent(): String? {
            val root = rootInActiveWindow ?: return null
            val editText = findBestEditText(root) ?: return null
            return editText.text?.toString()
      }

      /** Toca la pantalla en un punto exacto, simulando un toque real con el dedo. Se usa
       * para tocar el boton de enviar por coordenadas en vez de adivinar cual nodo del
        * arbol de accesibilidad es el correcto (mas confiable cuando hay varios botones
         * parecidos cerca, como en usuarios nuevos o mensajes largos). */
         private fun tapAt(x: Int, y: Int): Boolean {
               try {
                     val path = Path()
                     path.moveTo(x.toFloat(), y.toFloat())
                     val gestureBuilder = GestureDescription.Builder()
                     val stroke = GestureDescription.StrokeDescription(path, 0, 80)
                     gestureBuilder.addStroke(stroke)
                     val dispatched = dispatchGesture(gestureBuilder.build(), null, null)
                     Log.e(TAG, "tapAt: toque en (" + x + "," + y + ") dispatchGesture devolvio " + dispatched)
                     return dispatched
               } catch (e: Exception) {
                     Log.e(TAG, "EXCEPCION en tapAt: " + e.toString())
                     return false
               }
         }

         /** Escribe el texto y toca el boton de enviar por coordenadas, apuntando al borde
          * derecho de la pantalla y a la parte inferior del cuadro de texto (donde esta el
           * icono de enviar en Timo, sin importar si el cuadro crecio por un mensaje largo).
            * Un solo intento: no se prueban otros botones para evitar tocar regalos, llamadas
             * u otras funciones por error. */
             private fun typeAndSend(editText: AccessibilityNodeInfo, editBounds: Rect, replyText: String): Boolean {
                   try {
                         editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                         editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                         val args = Bundle()
                         args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
                         val setOk = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                         Log.e(TAG, "typeAndSend: ACTION_SET_TEXT devolvio " + setOk)

                         Thread.sleep(1000)

                         val freshRoot = rootInActiveWindow ?: return false
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
                         Log.e(TAG, "typeAndSend: cuadro de texto actual " + freshEditBounds.toString())

                         val screenBounds = Rect()
                         freshRoot.getBoundsInScreen(screenBounds)

                         val tapX = (screenBounds.right - 45).coerceAtLeast(screenBounds.left)
                         val tapY = (freshEditBounds.bottom - 40).coerceIn(freshEditBounds.top, freshEditBounds.bottom)
                         Log.e(TAG, "typeAndSend: tocando en (" + tapX + "," + tapY + ")")

                         tapAt(tapX, tapY)
                         Thread.sleep(900)

                         val remaining = currentEditTextContent()
                         Log.e(TAG, "typeAndSend: contenido restante tras el toque: '" + remaining + "'")

                         if (remaining.isNullOrBlank()) {
                               Log.e(TAG, "typeAndSend: envio confirmado")
                               return true
                         }

                         Log.e(TAG, "typeAndSend: no se pudo confirmar el envio")
                         return false
                   } catch (e: Exception) {
                         Log.e(TAG, "EXCEPCION en typeAndSend: " + e.toString())
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
