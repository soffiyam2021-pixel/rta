package com.yami.autoreply

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Llama a la API gratuita de Gemini (Google AI Studio) para generar una respuesta
 * en base al mensaje recibido y las instrucciones del usuario.
 */
object ClaudeApiClient {

    private val client = OkHttpClient()

    // Modelo estable con capa gratuita en Google AI Studio.
    private const val MODEL = "gemini-2.5-flash"

    private fun endpoint(apiKey: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

    /**
     * @param appName nombre de la app de origen (ej: "WhatsApp")
     * @param sender remitente del mensaje, si está disponible
     * @param messageText contenido del mensaje entrante
     * @param userInstructions instrucciones del usuario sobre cómo responder
     * @return la respuesta generada, o null si falló
     */
    fun generateReply(
        apiKey: String,
        appName: String,
        sender: String,
        messageText: String,
        userInstructions: String
    ): String? {
        if (apiKey.isBlank() || messageText.isBlank()) return null

        val systemPrompt = """
            Sos un asistente que redacta respuestas cortas para mensajes entrantes en el teléfono de un usuario.
            Instrucciones del usuario sobre cómo responder: $userInstructions
            Respondé SOLO con el texto del mensaje a enviar, sin comillas, sin explicaciones, sin firma.
            Mantené la respuesta breve (1-3 oraciones), natural y apropiada para un chat.
        """.trimIndent()

        val userContent = "App: $appName\nDe: $sender\nMensaje recibido: $messageText"

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userContent)))
        )

        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)

        val request = Request.Builder()
            .url(endpoint(apiKey))
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                if (parts.length() == 0) return null
                parts.getJSONObject(0).optString("text").ifBlank { null }
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
