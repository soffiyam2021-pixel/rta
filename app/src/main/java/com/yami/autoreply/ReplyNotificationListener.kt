package com.yami.autoreply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val recentlyHandled = mutableSetOf<String>()

    companion object {
        private const val TAG = "AutoReplyListener"
        private const val DEBUG_CHANNEL_ID = "auto_reply_debug"
        private var debugNotifId = 5000
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (!SecurePrefs.isActive(applicationContext)) return
        if (sbn.packageName == applicationContext.packageName) return
        if (sbn.packageName.startsWith("android")) return

        val selectedApps = SecurePrefs.getSelectedApps(applicationContext)
        if (selectedApps.isNotEmpty() && !selectedApps.contains(sbn.packageName)) return

        val appName = appLabelForPackage(sbn.packageName)

        val notification = sbn.notification
        val extras = notification.extras

        val messageText = extractMessageText(extras)
        if (messageText.isNullOrBlank()) {
            showDebugNotification("Sin texto", appName + ": la notificacion no traia texto de mensaje, se omite.")
            return
        }
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Desconocido"

        val notifKey = sbn.key
        if (recentlyHandled.contains(notifKey)) return
        recentlyHandled.add(notifKey)
        if (recentlyHandled.size > 200) recentlyHandled.clear()

        val replyAction = findReplyAction(notification)
        if (replyAction == null) {
            showDebugNotification(
                "No se puede responder",
                appName + ": esta notificacion no tiene boton de Responder rapido, no se puede contestar automaticamente."
                )
            Log.d(TAG, "La notificacion de " + sbn.packageName + " no soporta respuesta directa, se omite.")
            return
        }

        scope.launch {
            val apiKey = SecurePrefs.getApiKey(applicationContext)
            val instructions = SecurePrefs.getPrompt(applicationContext)

            when (val result = ClaudeApiClient.generateReply(
                apiKey = apiKey,
                appName = appName,
                sender = sender,
                messageText = messageText,
                userInstructions = instructions
                )) {
                is ReplyResult.Error -> {
                    showDebugNotification("Error al generar respuesta", appName + ": " + result.message)
                }
                is ReplyResult.Success -> {
                    val sent = sendReply(replyAction, result.text)
                    if (sent) {
                        showDebugNotification("Respondido", appName + " a " + sender + ": " + result.text)
                    } else {
                        showDebugNotification("Error al enviar", appName + ": se genero la respuesta pero fallo el envio.")
                    }
                }
            }
        }
    }

    private fun extractMessageText(extras: Bundle): String? {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val last = messages.last() as? Bundle
            val text = last?.getCharSequence("text")?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                return action
            }
        }
        return null
    }

    private fun sendReply(action: Notification.Action, replyText: String): Boolean {
        return try {
            val remoteInputs = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()

            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            action.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "Respuesta enviada: " + replyText)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar la respuesta", e)
            false
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

    private fun showDebugNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DEBUG_CHANNEL_ID,
                "Auto Reply - Diagnostico",
                NotificationManager.IMPORTANCE_DEFAULT
                )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, DEBUG_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setAutoCancel(true)
        .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(debugNotifId++, notification)
    }
}
