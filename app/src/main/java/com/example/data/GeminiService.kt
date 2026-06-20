package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateLyrics(songTitle: String, artist: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Chave do Gemini API não configurada. Configure-a no painel de Secrets."))
        }

        val prompt = """
            Você é um estruturador profissional de letras de música para teleprompters de palco.
            Por favor, encontre e retorne a letra completa da música "$songTitle" do artista "$artist".
            Siga estas regras estritas para a formatação da resposta:
            1. Retorne APENAS os versos da letra da música, linha por linha.
            2. NÃO inclua títulos, metadados, autores, seções como "[Refrão]", "[Verso]", ou notas explicativas.
            3. Use letras minúsculas e remova pontuações complexas se preferir, ou apenas retorne as linhas limpas prontas para serem cantadas.
            4. Cada verso deve ser uma única linha. Não deixe linhas em branco dentro da música.
            5. Retorne puramente o texto da música, sem blocos de código markdown ou aspas ao redor.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            // Add system instruction for extra formatting safety
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Você é um formatador de letras de música estrito. Forneça apenas a letra da música sem textos adicionais, sem markdown e sem decorações.")
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val urlWithKey = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(urlWithKey)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("GeminiService", "Erro na API: ${response.code} - $errorBody")
                    return@withContext Result.failure(Exception("Erro na API do Gemini: ${response.code}"))
                }

                val responseBodyStr = response.body?.string()
                if (responseBodyStr.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Resposta vazia da API do Gemini."))
                }

                val jsonResponse = JSONObject(responseBodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(Exception("Nenhum candidato de resposta retornado pela I.A."))
                }

                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    return@withContext Result.failure(Exception("Parts da resposta vazias."))
                }

                val responseText = parts.getJSONObject(0).optString("text", "")
                if (responseText.isNotBlank()) {
                    // Clean response text from markdown block quotes if Gemini added any despite instructions
                    val cleaned = responseText
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()
                    Result.success(cleaned)
                } else {
                    Result.failure(Exception("Texto nulo retornado pela I.A."))
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Exceção ao chamar Gemini", e)
            Result.failure(e)
        }
    }
}
