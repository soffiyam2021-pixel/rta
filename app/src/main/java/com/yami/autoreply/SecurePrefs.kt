package com.yami.autoreply

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda la API key y la configuración de forma cifrada en el dispositivo.
  */
object SecurePrefs {

     private const val FILE_NAME = "auto_reply_secure_prefs"
     private const val KEY_API_KEY = "api_key"
     private const val KEY_API_KEYS_LIST = "api_keys_list"
     private const val KEY_PROMPT = "prompt"
     private const val KEY_ACTIVE = "active"
     private const val KEY_SELECTED_APPS = "selected_apps"

     private fun prefs(context: Context): SharedPreferences {
              val masterKey = MasterKey.Builder(context)
                          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                      .build()

                                              return EncryptedSharedPreferences.create(
                                                           context,
                                                           FILE_NAME,
                                                           masterKey,
                                                           EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                                           EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                                       )
     }

         fun saveApiKey(context: Context, apiKey: String) {
                  prefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
         }

             fun getApiKey(context: Context): String {
                      return prefs(context).getString(KEY_API_KEY, "") ?: ""
             }

                 /** Lista de API keys para rotar automaticamente cuando una se queda sin cuota.
                      * Separadas por salto de linea. Si esta vacia, se usa solo getApiKey(). */
                          fun saveApiKeysList(context: Context, keysNewlineSeparated: String) {
                                   prefs(context).edit().putString(KEY_API_KEYS_LIST, keysNewlineSeparated).apply()
                          }

                              fun getApiKeysList(context: Context): List<String> {
                                       val raw = prefs(context).getString(KEY_API_KEYS_LIST, "") ?: ""
                                       val fromList = raw.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                                               if (fromList.isNotEmpty()) return fromList
                                       val single = getApiKey(context)
                                               return if (single.isNotBlank()) listOf(single) else emptyList()
                              }

                                  fun savePrompt(context: Context, prompt: String) {
                                           prefs(context).edit().putString(KEY_PROMPT, prompt).apply()
                                  }

                                      fun getPrompt(context: Context): String {
                                               val default = "Responde en tono informal, amigable y simpatica. Soy mujer, con lo cual tus palabras deben ser en femenino. No des informacion personal, ni ubicacion, ni redes sociales, ni numero de telefono, ni profesion ni oficio; evada esas preguntas con respuestas evasivas que no se noten que lo son. No des tu nombre ni tu apellido bajo ninguna circunstancia. No menciones el nombre de la otra persona en tus respuestas. No uses los signos de apertura de interrogacion ni exclamacion (¿ ni ¡); escribi las preguntas y exclamaciones usando solo el signo de cierre al final (? o !), como se escribe en ingles. Nunca digas que sos un bot, una inteligencia artificial o un asistente automatico; comportate siempre como una persona real. No uses la palabra 'rico' en tus respuestas."
                                               return prefs(context).getString(KEY_PROMPT, default) ?: default
                                      }

                                          fun setActive(context: Context, active: Boolean) {
                                                   prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()
                                          }

                                              fun isActive(context: Context): Boolean {
                                                       return prefs(context).getBoolean(KEY_ACTIVE, false)
                                              }

                                                  fun saveSelectedApps(context: Context, packageNames: Set<String>) {
                                                           prefs(context).edit().putStringSet(KEY_SELECTED_APPS, packageNames).apply()
                                                  }

                                                      /** Vacio = responder en todas las apps (comportamiento por defecto). */
                                                          fun getSelectedApps(context: Context): Set<String> {
                                                                   return prefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
                                                          }
}
