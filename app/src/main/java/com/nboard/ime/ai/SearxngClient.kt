package com.nboard.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SearxngClient {
    suspend fun search(searxngUrl: String, query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$searxngUrl/search?q=$encodedQuery&format=json"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Nboard-Keyboard")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                return@withContext "Error: Search failed with code ${connection.responseCode}"
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val results = json.optJSONArray("results")
            
            if (results == null || results.length() == 0) {
                return@withContext "No results found."
            }

            val sb = java.lang.StringBuilder()
            val maxResults = minOf(3, results.length())
            for (i in 0 until maxResults) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "No Title")
                val content = item.optString("content", "No Content")
                sb.append("Title: $title\nContent: $content\n\n")
            }
            sb.toString().trim()
        } catch (e: Exception) {
            "Error performing search: ${e.message}"
        }
    }
}
