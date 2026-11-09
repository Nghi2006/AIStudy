package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object GeminiClient {

    private const val TAG = "GeminiClient"
<<<<<<< HEAD
    private const val MODEL = "gemini-3.5-flash"
=======
    private const val MODEL = "gemini-1.5-flash"
>>>>>>> Develop

    fun ask(apiKey: String, prompt: String): String {
        Log.d(TAG, "request start model=$MODEL promptLength=${prompt.length}")
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val part = JSONObject()
        part.put("text", prompt)

        val content = JSONObject()
        content.put("parts", JSONArray().put(part))

<<<<<<< HEAD
        val body = JSONObject()
        body.put("contents", JSONArray().put(content))
=======
        val generationConfig = JSONObject()
        generationConfig.put("response_mime_type", "application/json")

        val body = JSONObject()
        body.put("contents", JSONArray().put(content))
        body.put("generationConfig", generationConfig)
>>>>>>> Develop

        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        Log.d(TAG, "request body bytes=${bytes.size}")
        conn.outputStream.use { os ->
            os.write(bytes)
        }

        val code = conn.responseCode
        Log.d(TAG, "response httpCode=$code")
        val reader = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
        } else {
            BufferedReader(InputStreamReader(conn.errorStream, StandardCharsets.UTF_8))
        }

        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
        reader.close()

        val responseBody = sb.toString()
        if (code < 200 || code >= 300) {
            Log.e(TAG, "request failed httpCode=$code body=${truncate(responseBody, 500)}")
            throw Exception("HTTP $code: $responseBody")
        }

        Log.d(TAG, "response body length=${responseBody.length}")
        val json = JSONObject(responseBody)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        Log.d(TAG, "parsed text length=${text.length} preview=${truncate(text, 200)}")
        return text
    }

    private fun truncate(s: String?, max: Int): String {
        if (s == null) return "null"
        if (s.length <= max) return s
        return s.substring(0, max) + "..."
    }
}
