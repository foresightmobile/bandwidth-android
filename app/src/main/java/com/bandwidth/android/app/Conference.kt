package com.bandwidth.android.app

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class Conference private constructor() {
    @Throws(IOException::class)
    fun requestDeviceToken(path: String?): String? {
        val url = URL(path)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connect()
        val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
        val stringBuilder = StringBuilder()
        var output: String?
        while (bufferedReader.readLine().also { output = it } != null) {
            stringBuilder.append(output)
        }
        output = stringBuilder.toString()
        val response = Gson().fromJson(output, ParticipantsResponse::class.java)
        return response.deviceToken
    }

    companion object {
        var instance: Conference? = null
            get() {
                if (field == null) {
                    field = Conference()
                }
                return field
            }
            private set
    }
}