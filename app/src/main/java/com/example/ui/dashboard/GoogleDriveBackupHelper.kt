package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleDriveBackupHelper {

    fun getGoogleSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SeaPass").build()
    }

    suspend fun uploadBackup(context: Context, account: GoogleSignInAccount, backupJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "backup.json"
                parents = listOf("appDataFolder")
            }
            
            val mediaContent = ByteArrayContent.fromString("application/json", backupJson)

            val existing = service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'backup.json'")
                .execute()
                .files

            if (existing.isNullOrEmpty()) {
                service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            } else {
                service.files().update(existing[0].id, null, mediaContent)
                    .execute()
            }
                
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun downloadLatestBackup(context: Context, account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)
            
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'backup.json'")
                .setFields("nextPageToken, files(id, name)")
                .execute()
                
            val files = result.files
            if (files.isNullOrEmpty()) {
                return@withContext null
            }
            val fileId = files[0].id
            
            val inputStream = service.files().get(fileId).executeMediaAsInputStream()
            return@withContext inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
