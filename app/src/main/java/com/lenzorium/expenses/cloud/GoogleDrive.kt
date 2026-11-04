package com.lenzorium.expenses.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/** The Google account the user signed in with. */
data class GoogleProfile(val email: String, val name: String, val photoUrl: String?)

/** One backup file in the app's private Google Drive folder. */
data class DriveFile(val id: String, val name: String, val size: Long, val createdMillis: Long)

/** Signing in needs the user to pick an account / approve on a Google screen. */
class NeedsUserApproval(val pendingIntent: PendingIntent) : Exception("Google needs you to approve access.")

/**
 * Google sign-in and backups in the app's hidden Google Drive folder ("appDataFolder").
 * Only this app can read that folder; the developer has no server and never receives the data.
 * Scopes: basic profile (name, email, photo) and drive.appdata (the app's own hidden folder only,
 * no access to any of the user's other Drive files).
 */
object GoogleDrive {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/userinfo.email"),
        Scope("https://www.googleapis.com/auth/userinfo.profile"),
        Scope("https://www.googleapis.com/auth/drive.appdata"),
    )
    private const val API = "https://www.googleapis.com"

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    /**
     * Gets an access token. When Google needs the user (first sign-in, account choice, consent),
     * fails with [NeedsUserApproval]; launch its pendingIntent and pass the result to [tokenFromResult].
     */
    suspend fun token(context: Context): String = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { r: AuthorizationResult ->
                val pi = r.pendingIntent
                val t = r.accessToken
                when {
                    r.hasResolution() && pi != null -> cont.resumeWith(Result.failure(NeedsUserApproval(pi)))
                    t != null -> cont.resume(t)
                    else -> cont.resumeWith(Result.failure(IOException("Google didn't return access.")))
                }
            }
            .addOnFailureListener { e -> cont.resumeWith(Result.failure(friendly(e))) }
    }

    fun tokenFromResult(context: Context, data: Intent?): String {
        val r = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        return r.accessToken ?: throw IOException("Sign-in was cancelled.")
    }

    private fun friendly(e: Exception): Exception {
        val msg = e.message ?: ""
        return if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR")) IOException(
            "Google sign-in isn't set up for this app yet (the app's Google Cloud project needs an Android OAuth client). See the setup steps."
        ) else if (msg.contains("7:") || msg.contains("NETWORK")) IOException("No internet connection. Try again when you're online.")
        else IOException(msg.ifBlank { "Couldn't connect to Google." })
    }

    // ---------------- HTTP helpers ----------------

    private fun open(token: String, url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.check(): HttpURLConnection {
        val code = responseCode
        if (code !in 200..299) {
            val body = runCatching { errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
            disconnect()
            throw IOException(
                when (code) {
                    401 -> "Your Google sign-in has expired. Sign in again."
                    403 -> if (body.contains("storageQuota", true)) "Your Google Drive is full." else if (body.contains("accessNotConfigured") || body.contains("has not been used")) "The Google Drive API isn't turned on in the app's Google Cloud project." else "Google Drive refused access ($code)."
                    else -> "Google Drive error $code."
                }
            )
        }
        return this
    }

    private fun json(c: HttpURLConnection): JSONObject = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }.also { c.disconnect() }

    suspend fun profile(token: String): GoogleProfile = withContext(Dispatchers.IO) {
        val o = json(open(token, "https://www.googleapis.com/oauth2/v3/userinfo").check())
        GoogleProfile(o.optString("email"), o.optString("name").ifBlank { o.optString("email").substringBefore('@') }, o.optString("picture").ifBlank { null })
    }

    suspend fun list(token: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val q = "spaces=appDataFolder&orderBy=createdTime%20desc&pageSize=100&fields=" +
            URLEncoder.encode("files(id,name,size,createdTime)", "UTF-8")
        val arr = json(open(token, "$API/drive/v3/files?$q").check()).optJSONArray("files")
        (0 until (arr?.length() ?: 0)).map { i ->
            val f = arr!!.getJSONObject(i)
            DriveFile(
                f.getString("id"), f.getString("name"), f.optString("size", "0").toLongOrNull() ?: 0L,
                runCatching { java.time.Instant.parse(f.getString("createdTime")).toEpochMilli() }.getOrDefault(0L),
            )
        }
    }

    /** Resumable upload into the app's hidden folder. */
    suspend fun upload(token: String, file: File, name: String): DriveFile = withContext(Dispatchers.IO) {
        val start = open(token, "$API/upload/drive/v3/files?uploadType=resumable&fields=id,name,size,createdTime", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            setRequestProperty("X-Upload-Content-Length", file.length().toString())
        }
        start.outputStream.use { it.write(JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString().toByteArray()) }
        start.check()
        val location = start.getHeaderField("Location") ?: throw IOException("Google Drive didn't accept the upload.")
        start.disconnect()
        val put = (URL(location).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true
            connectTimeout = 20_000; readTimeout = 120_000
            setFixedLengthStreamingMode(file.length())
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        file.inputStream().use { input -> put.outputStream.use { input.copyTo(it) } }
        val o = json(put.check())
        DriveFile(o.getString("id"), o.optString("name", name), o.optString("size", "0").toLongOrNull() ?: file.length(), System.currentTimeMillis())
    }

    suspend fun download(token: String, id: String, to: File) = withContext(Dispatchers.IO) {
        val c = open(token, "$API/drive/v3/files/$id?alt=media").check()
        c.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
        c.disconnect()
    }

    suspend fun delete(token: String, id: String) = withContext(Dispatchers.IO) {
        val c = open(token, "$API/drive/v3/files/$id", "DELETE")
        val code = c.responseCode
        c.disconnect()
        if (code !in 200..299 && code != 404) throw IOException("Couldn't delete a backup from Google Drive ($code).")
    }

    /** Tells Google to remove this app's access to the account. */
    suspend fun revoke(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("https://oauth2.googleapis.com/revoke?token=" + URLEncoder.encode(token, "UTF-8")).openConnection() as HttpURLConnection)
            c.requestMethod = "POST"; c.doOutput = true; c.outputStream.close(); c.responseCode; c.disconnect()
        }
    }
}
