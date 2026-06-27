package com.example.network

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object DriveUploader {
    private const val TAG = "DriveUploader"
    private val client = OkHttpClient()

    val DRIVE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_SCOPE)
            .build()
    }

    suspend fun getAccessToken(context: Context, account: GoogleSignInAccount): String? {
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    account.account ?: return@withContext null,
                    "oauth2:https://www.googleapis.com/auth/drive.file"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error obtaining access token", e)
                null
            }
        }
    }

    suspend fun uploadCsvToDrive(
        accessToken: String,
        fileName: String,
        csvContent: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("mimeType", "application/vnd.google-apps.spreadsheet") // Auto-converts to Google Sheets
                }

                val mediaTypeJson = "application/json; charset=UTF-8".toMediaTypeOrNull()
                val mediaTypeCsv = "text/csv; charset=UTF-8".toMediaTypeOrNull()

                val metadataPart = metadata.toString().toRequestBody(mediaTypeJson)
                val filePart = csvContent.toRequestBody(mediaTypeCsv)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(
                        Headers.Builder()
                            .add("Content-Type", "application/json; charset=UTF-8")
                            .build(),
                        metadataPart
                    )
                    .addPart(
                        Headers.Builder()
                            .add("Content-Type", "text/csv; charset=UTF-8")
                            .build(),
                        filePart
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(responseStr)
                        val fileId = json.optString("id", null)
                        Log.d(TAG, "Uploaded successfully to Drive, file ID: $fileId")
                        fileId
                    } else {
                        Log.e(TAG, "Failed to upload to Drive: Code ${response.code}, Body: $responseStr")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during upload", e)
                null
            }
        }
    }
}
