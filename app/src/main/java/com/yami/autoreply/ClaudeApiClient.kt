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
            private const val MODEL = "llama-3.3-70b-versatile"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

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

                    val messages = JSONArray()
                            messages.put(
                                            JSONObject()
                                                            .put("role", "system")
                                                                            .put("content", systemPrompt)
                                                                                    )
                                    messages.put(
                                                    JSONObject()
                                                                    .put("role", "user")
                                                                                    .put("content", userContent)
                                                                                            )

                                            val payload = JSONObject()
                                                        .put("model", MODEL)
                                                                    .put("messages", messages)
                                                                                .put("temperature", 0.8)
                                                                                            .put("max_tokens", 200)

                                                                                                    val body = payload.toString().toRequestBody("application/json".toMediaType())
                                                                                                            val request = Request.Builder()
                                                                                                                        .url(ENDPOINT)
                                                                                                                                    .addHeader("Authorization", "Bearer " + apiKey)
                                                                                                                                                .addHeader("Content-Type", "application/json")
                                                                                                                                                            .post(body)
                                                                                                                                                                        .build()
                                                                                                                                                                        
                                                                                                                                                                                return try {
                                                                                                                                                                                                client.newCall(request).execute().use { response ->
                                                                                                                                                                                                                    val responseBody = response.body?.string() ?: ""
                                                                                                                                                                                                                    if (!response.isSuccessful) {
                                                                                                                                                                                                                                            return ReplyResult.Error("HTTP " + response.code + ": " + responseBody)
                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                    val json = JSONObject(responseBody)
                                                                                                                                                                                                                                                    val choices = json.optJSONArray("choices")
                                                                                                                                                                                                                                                                    if (choices == null || choices.length() == 0) {
                                                                                                                                                                                                                                                                                            return ReplyResult.Error("Respuesta vacia de la IA: " + responseBody)
                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                    val text = choices.getJSONObject(0)
                                                                                                                                                                                                                                                                                                        .getJSONObject("message")
                                                                                                                                                                                                                                                                                                                            .getString("content")
                                                                                                                                                                                                                                                                                                                                                .trim()
                                                                                                                                                                                                                                                                                                                                                                if (text.isBlank()) {
                                                                                                                                                                                                                                                                                                                                                                                        ReplyResult.Error("La IA devolvio texto vacio")
                                                                                                                                                                                                                                                                                                                                                                                                        } else {
                                                                                                                                                                                                                                                                                                                                                                                        ReplyResult.Success(text)
                                                                                                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                        } catch (e: IOException) {
                                                                                                                                                                                                ReplyResult.Error("Error de red: " + e.message)
                                                                                                                                                                                                        } catch (e: Exception) {
                                                                                                                                                                                                ReplyResult.Error("Error inesperado: " + e.message)
                                                                                                                                                                                                        }
        }
}
