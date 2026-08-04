package com.yami.autoreply

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Escucha las notificaciones de TODAS las apps instaladas.
 * Cuando llega un mensaje de una app que soporta "respuesta rápida"
 * (WhatsApp, Telegram, SMS, Instagram, etc. lo soportan de forma nativa),
 * genera una respuesta con IA y la envía usando esa misma acción,
 * sin necesidad de abrir la app ni simular toques en pantalla.
 */
class ReplyNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Evita responder dos veces a la misma notificación en poco tiempo
    private val recentlyHandled = mutableSetOf<String>()

    companion object {
        private const val TAG = "AutoReplyListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (!SecurePrefs.isActive(applicationContext)) return

        // Ignorar notificaciones propias y de sistema para evitar loops
        if (sbn.packageName == applicationContext.packageName) return
        if (sbn.packageName.startsWith("android")) return

        // Si el usuario eligió apps específicas, ignorar el resto
        val selectedApps = SecurePrefs.getSelectedApps(applicationContext)
        if (selectedApps.isNotEmpty() && !selectedApps.contains(sbn.packageName)) return

        val notification = sbn.notification
        val extras = notification.extras

        val messageText = extractMessageText(extras) ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Desconocido"

        val notifKey = sbn.key
        if (recentlyHandled.contains(notifKey)) return
        recentlyHandled.add(notifKey)
        if (recentlyHandled.size > 200) recentlyHandled.clear()

        val replyAction = findReplyAction(notification) ?: run {
            Log.d(TAG, "La notificación de ${sbn.packageName} no soporta respuesta directa, se omite.")
            return
        }

        val appName = appLabelForPackage(sbn.packageName)

        scope.launch {
            val apiKey = SecurePrefs.getApiKey(applicationContext)
            val instructions = SecurePrefs.getPrompt(applicationContext)

            val reply = ClaudeApiClient.generateReply(
                apiKey = apiKey,
                appName = appName,
                sender = sender,
                messageText = messageText,
                userInstructions = instructions
            ) ?: return@launch

            sendReply(replyAction, reply)
        }
    }

    /** Extrae el texto del último mensaje de la notificación (soporta chats con historial). */
    private fun extractMessageText(extras: Bundle): String? {
        // Notificaciones tipo MessagingStyle (WhatsApp, Telegram, etc.)
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val last = messages.last() as? Bundle
            val text = last?.getCharSequence("text")?.toString()
            if (!text.isNullOrBlank()) return text
        }
        // Fallback: texto simple de la notificación
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    /** Busca la acción de "Responder" (la que trae un RemoteInput) dentro de la notificación. */
    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                return action
            }
        }
        return null
    }

    /** Envía la respuesta rellenando el RemoteInput y disparando el PendingIntent de la acción. */
    private fun sendReply(action: Notification.Action, replyText: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val intent = Intent()
            val bundle = Bundle()

            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            action.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "Respuesta enviada: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar la respuesta", e)
        }
    }

    private fun appLabelForPackage(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
