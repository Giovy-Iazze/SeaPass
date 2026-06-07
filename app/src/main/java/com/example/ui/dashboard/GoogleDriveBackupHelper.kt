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
import java.io.File
import java.io.FileOutputStream

data class DriveBackupInfo(
    val id: String,
    val name: String,
    val timestamp: Long
)

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

    suspend fun uploadBackup(context: Context, account: GoogleSignInAccount, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)

            // Save the new backup with timestamp
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "seapass_backup_${System.currentTimeMillis()}.zip"
                parents = listOf("appDataFolder")
            }
            
            val mediaContent = com.google.api.client.http.InputStreamContent("application/zip", java.io.FileInputStream(backupFile))
            service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            // Pull list and delete backups beyond index 4 (keep only the 5 most recent)
            val backups = listBackupsInternal(service)
            if (backups.size > 5) {
                for (i in 5 until backups.size) {
                    try {
                        service.files().delete(backups[i].id).execute()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
                
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun listBackupsInternal(service: Drive): List<DriveBackupInfo> {
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name, createdTime, modifiedTime)")
            .execute()
            
        val files = result.files ?: emptyList()
        return files.filter { 
            (it.name?.startsWith("seapass_backup_") == true && (it.name?.endsWith(".json") == true || it.name?.endsWith(".zip") == true))
        }.map { file ->
            val name = file.name ?: ""
            var ts = 0L
            if (name.startsWith("seapass_backup_") && (name.endsWith(".json") || name.endsWith(".zip"))) {
                try {
                    val suffix = if (name.endsWith(".json")) ".json" else ".zip"
                    ts = name.substring("seapass_backup_".length, name.length - suffix.length).toLong()
                } catch (_: Exception) {}
            }
            if (ts == 0L) {
                val driveTime = file.createdTime ?: file.modifiedTime
                if (driveTime != null) {
                    ts = driveTime.value
                }
            }
            DriveBackupInfo(
                id = file.id,
                name = name,
                timestamp = ts
            )
        }.sortedByDescending { it.timestamp }
    }

    suspend fun listBackups(context: Context, account: GoogleSignInAccount): List<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)
            return@withContext listBackupsInternal(service)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun downloadBackup(context: Context, account: GoogleSignInAccount, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)
            val zipFile = File(context.cacheDir, "temp_backup_$fileId.zip")
            
            service.files().get(fileId).executeMediaAndDownloadTo(FileOutputStream(zipFile))
            
            val json = com.example.ui.dashboard.BackupFileManager.extractZipBackup(zipFile, context.filesDir)
            zipFile.delete()
            return@withContext json
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun downloadLatestBackup(context: Context, account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(context, account)
            val backups = listBackupsInternal(service)
            if (backups.isEmpty()) return@withContext null
            val fileId = backups[0].id
            val inputStream = service.files().get(fileId).executeMediaAsInputStream()
            return@withContext inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
