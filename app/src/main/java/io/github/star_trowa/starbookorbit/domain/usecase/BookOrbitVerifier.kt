package io.github.star_trowa.starbookorbit.domain.usecase

import io.github.star_trowa.starbookorbit.domain.model.ServerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

class BookOrbitVerifier : ServerVerifier {
    override val serverType = ServerType.BOOK_ORBIT

    override suspend fun verify(baseUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URI("$baseUrl/api/v1/health").toURL()
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                // This fixes the issue where certain domains block default Java/HttpURLConnection user agents
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.setRequestProperty("Accept", "application/json")


                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    body.replace("\\s+".toRegex(), "").contains("\"status\":\"ok\"", ignoreCase = true)
                } else {
                    false
                }
            } catch (e: java.io.IOException) {
                // The server is actually dead/offline. Bubble this up.
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Parsing failed or bad stream. Server is alive, but not BookOrbit.
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}