package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Certificate
import com.example.data.CertificateRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface CertificateUiState {
    object Loading : CertificateUiState
    data class Success(
        val certificates: List<Certificate>,
        val totalActive: Int,
        val expiringSoon: Int,
        val expired: Int
    ) : CertificateUiState
}

class CertificateViewModel(
    private val repository: CertificateRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    // Reactive StateFlow for UI updates
    val uiState: StateFlow<CertificateUiState> = repository.allCertificates
        .map { list ->
            val now = System.currentTimeMillis()
            var active = 0
            var expiring = 0
            var expired = 0

            list.forEach { cert ->
                val daysLeft = if (cert.expiryDate > now) {
                    ((cert.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
                } else {
                    -1
                }
                when {
                    daysLeft < 0 -> expired++
                    daysLeft in 0..90 -> expiring++
                    else -> active++
                }
            }

            CertificateUiState.Success(
                certificates = list,
                totalActive = active + expiring, // Non-expired certificates are active
                expiringSoon = expiring,
                expired = expired
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CertificateUiState.Loading
        )

    init {
        // Automatically reschedule notifications on ViewModel creation (satisfies "every time the app launches")
        rescheduleAllNotifications()
    }

    fun rescheduleAllNotifications() {
        viewModelScope.launch {
            repository.allCertificates.collect { list ->
                Log.d("CertificateViewModel", "App launch: Rescheduling reminders for all ${list.size} certificates.")
                list.forEach { cert ->
                    notificationHelper.scheduleExpiries(cert)
                }
            }
        }
    }

    fun saveCertificate(
        id: Int = 0,
        title: String,
        category: String,
        issueDate: Long,
        expiryDate: Long,
        certNumber: String?,
        attachmentUriString: String?,
        context: Context,
        isMandatory: Boolean = true,
        folderName: String? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            var finalPath = attachmentUriString

            // Copy file to internal storage folder to preserve it offline/locally
            if (attachmentUriString != null && attachmentUriString.startsWith("content://")) {
                val copiedPath = copyUriToInternalStorage(context, Uri.parse(attachmentUriString))
                if (copiedPath != null) {
                    finalPath = copiedPath
                }
            }

            val certificate = Certificate(
                id = id,
                title = title,
                category = category,
                issueDate = issueDate,
                expiryDate = expiryDate,
                certNumber = certNumber,
                attachmentPath = finalPath,
                isMandatory = isMandatory,
                folderName = folderName
            )

            // Insert into local DB
            val savedId = repository.insert(certificate)
            val finalCert = if (id == 0) certificate.copy(id = savedId.toInt()) else certificate

            // Refresh alarm schedule for this specific certificate (satisfies "a new certificate is saved" requirement)
            notificationHelper.scheduleExpiries(finalCert)
            
            onComplete()
        }
    }

    fun deleteCertificate(certificate: Certificate) {
        viewModelScope.launch {
            notificationHelper.cancelReminders(certificate.id)
            repository.delete(certificate)
        }
    }

    fun updateCertificateFolder(certificate: Certificate, folderName: String?) {
        viewModelScope.launch {
            repository.insert(certificate.copy(folderName = folderName))
        }
    }

    private fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val extension = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
            val fileName = "cert_attachment_${System.currentTimeMillis()}.$extension"
            val outputFile = File(context.filesDir, fileName)
            
            outputFile.outputStream().use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e("CertificateViewModel", "Failed to preserve attachment: ${e.message}", e)
            null
        }
    }
}

class CertificateViewModelFactory(
    private val repository: CertificateRepository,
    private val notificationHelper: NotificationHelper
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CertificateViewModel::class.java)) {
            return CertificateViewModel(repository, notificationHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
