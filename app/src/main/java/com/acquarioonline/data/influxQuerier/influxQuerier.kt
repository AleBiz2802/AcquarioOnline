package com.acquarioonline.data.influxQuerier
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.*
import org.json.JSONArray


val client = OkHttpClient()

private suspend fun queryInflux(): String = withContext(Dispatchers.IO) {
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

private suspend fun querySensorDatas(aquariumName: String): String = withContext(Dispatchers.IO){
    val body = JSONObject().apply {
        put("db", "influxAcquario")
        put("q", "SELECT * FROM \"Acquari\" WHERE \"Acquario\" = '$aquariumName'")
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

suspend fun getSensorData(aquariumName:String): List<InfluxSensorData>{
    val listOfJson =JSONArray(querySensorDatas(aquariumName))
    return (0 until listOfJson.length()).map { i ->
        val obj = listOfJson.getJSONObject(i)
        val type =  if(!obj.isNull("ph")) "ph"
        else if(!obj.isNull("lvl")) "lvl"
        else if (!obj.isNull("tmp"))"tmp"
        else null
        InfluxSensorData(
            aquariumName = obj.getString("Acquario"),
            time = obj.getString("time"),
            type =type,
            sensorVal = if (type!=null)obj.getDouble(type).toFloat()
                        else null
        )
    }
}
 suspend fun deleteAquariumData(aquariumName: String) = withContext(Dispatchers.IO){
    val bodyAcquari= JSONObject().apply {
        put("db", "influxAcquario")

        put("q", "DELETE  FROM \"Acquari\" WHERE \"Acquario\" = '$aquariumName'")
        put("format", "json")
            }.toString().toRequestBody("application/json".toMediaType())

    val requestAcquari = Request.Builder()
        .url("http://100.117.199.35:8181/api/v3/query_sql")
        .addHeader("Authorization", "Bearer apiv3_BfCLS3FBjq5GHOoJC9WKnPB6sJCzUqM4KScPaAs_UV9Us_AItsUuRmua8SN2u1X23AR6XifaE-RMg2CIcdWoNw")
        .post(bodyAcquari)
        .build()
     val bodyAttuatori= JSONObject().apply {
         put("db", "influxAcquario")

         put("q", "DELETE  FROM \"Attuatori\" WHERE \"Acquario\" = '$aquariumName'")
         put("format", "json")
     }.toString().toRequestBody("application/json".toMediaType())

     val requestAttuatori= Request.Builder()
         .url("http://100.117.199.35:8181/api/v3/query_sql")
         .addHeader("Authorization", "Bearer apiv3_BfCLS3FBjq5GHOoJC9WKnPB6sJCzUqM4KScPaAs_UV9Us_AItsUuRmua8SN2u1X23AR6XifaE-RMg2CIcdWoNw")
         .post(bodyAttuatori)
         .build()

    try {
        client.newCall(requestAcquari).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Errore eliminazione: ${response.code} ${response.message}")
            }
        }
        client.newCall(requestAttuatori).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Errore eliminazione: ${response.code} ${response.message}")
            }
        }

    } catch (e: Exception) {
        throw Exception("Eccezione durante l'eliminazione: ${e.message}")
    }
}