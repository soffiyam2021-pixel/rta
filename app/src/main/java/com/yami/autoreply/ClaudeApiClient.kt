package com.yami.autoreply

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

sealed class ReplyResult {
    data class Success(val text: String) : ReplyResult()
    data class Error(val message: String) : ReplyResult()
}

object ClaudeApiClient {

    private val client = OkHttpClient()
    private const val MODEL = "gemini-2.5-flash"

    private fun endpoint(apiKey: String) =
    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

    fun generateReply(
        apiKey: String,
        appName: String,
        sender: String,
        messageText: String,
        userInstructions: String
        ): ReplyResult {
        if (apiKey.isBlank()) return ReplyResult.Error("Falta la API key")
        if (messageText.isBlank()) return ReplyResult.Error("Mensaje vacio")

        val systemPrompt = "Sos un asistente que redacta respuestas cortas para mensajes entrantes en el telefono de un usuario. Instrucciones del usuario sobre como responder: " + userInstructions + " Respondé SOLO con el texto del mensaje a enviar, sin comillas, sin explicaciones, sin firma. Mantené la respuesta breve (1-3 oraciones), natural y apropiada para un chat."

        val userContent = "App: " + appName + "\nDe: " + sender + "\nMensaje recibido: " + messageText

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
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return ReplyResult.Error("HTTP " + response.code + ": " + responseBody.take(150))
                }
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                ?: return ReplyResult.Error("Sin candidates: " + responseBody.take(150))
                if (candidates.length() == 0) return ReplyResult.Error("Respuesta vacia (posible bloqueo de seguridad)")
                val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return ReplyResult.Error("Sin texto en la respuesta")
                if (parts.length() == 0) return ReplyResult.Error("Sin texto en la respuesta")
                val text = parts.getJSONObject(0).optString("text")
                if (text.isBlank()) ReplyResult.Error("Texto vacio") else ReplyResult.Success(text)
            }
        } catch (e: IOException) {
            ReplyResult.Error("Error de red: " + e.message)
        } catch (e: Exception) {
            ReplyResult.Error("Error: " + e.message)
        }
    }
}
