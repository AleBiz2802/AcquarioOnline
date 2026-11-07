package com.acquarioonline.data.influxQuerier
import com.acquarioonline.data.local.Aquarium
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.*
import org.json.JSONArray


val client = OkHttpClient()

suspend fun queryInflux(): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("db", "influxAcquario")
            put("q", "SELECT * FROM \"Attuatori\"")
            put("format", "json")
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://100.117.199.35:8181/api/v3/query_sql")
            .addHeader("Authorization", "Bearer apiv3_BfCLS3FBjq5GHOoJC9WKnPB6sJCzUqM4KScPaAs_UV9Us_AItsUuRmua8SN2u1X23AR6XifaE-RMg2CIcdWoNw")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.body?.string()}")
            }
            response.body?.string() ?: ""
        }
    }

suspend fun getSummarizable(): List<Summarizable>{
    val listOfJson =JSONArray(queryInflux())
    return (0 until listOfJson.length()).map { i ->
        val obj = listOfJson.getJSONObject(i)
        Summarizable(
            aquariumName = obj.getString("Acquario"),
            time = obj.getString("time"),
            actuator = obj.getString("Attuatore"),
            state = obj.getString("stato")
        )
    }
}

