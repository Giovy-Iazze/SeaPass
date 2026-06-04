package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Certificate
import com.example.ui.theme.*
import com.example.ui.viewmodel.CertificateUiState
import com.example.ui.viewmodel.CertificateViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class SortType(val label: String) {
    EXPIRY_DATE("Scadenza"),
    ALPHABETICAL("Alfabetico"),
    NEWEST("Più recente"),
    OLDEST("Meno recente")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: CertificateViewModel,
    onAddDocumentClick: () -> Unit,
    onEditDocumentClick: (Int) -> Unit,
    uiState: CertificateUiState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mariner's Wallet",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBgLight
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDocumentClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Icon") },
                text = { Text("Add Document") },
                containerColor = PolishFABBackground,
                contentColor = PolishFABText,
                modifier = Modifier
                    .testTag("add_document_fab")
                    .padding(bottom = 16.dp, end = 8.dp)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            when (uiState) {
                is CertificateUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is CertificateUiState.Success -> {
                    DashboardContent(
                        successState = uiState,
                        daysBeforeExpiryAlert = 30,
                        onCertificateClick = onEditDocumentClick,
                        onDeleteCertificate = { viewModel.deleteCertificate(it) },
                        onUpdateCertificateFolder = { cert, folder -> viewModel.updateCertificateFolder(cert, folder) },
                        folders = emptyList(),
                        onCreateFolder = {},
                        onDeleteFolder = {}
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    successState: CertificateUiState.Success,
    daysBeforeExpiryAlert: Int,
    onCertificateClick: (Int) -> Unit,
    onDeleteCertificate: (Certificate) -> Unit,
    onUpdateCertificateFolder: (Certificate, String?) -> Unit,
    folders: List<String>,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var certificateToDelete by remember { mutableStateOf<Certificate?>(null) }
    var currentSort by remember { mutableStateOf(SortType.EXPIRY_DATE) }
    var isSortExpanded by remember { mutableStateOf(false) }
    var folderToCreate by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var activeFolder by remember { mutableStateOf<String?>(null) }

    // Dynamic computations based on current time and selected threshold
    val now = System.currentTimeMillis()
    val stats = remember(successState.certificates, daysBeforeExpiryAlert) {
        var active = 0
        var expiring = 0
        var expired = 0

        successState.certificates.forEach { cert ->
            val daysLeft = if (cert.expiryDate > now) {
                ((cert.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
            } else {
                -1
            }
            when {
                daysLeft < 0 -> expired++
                daysLeft in 0..daysBeforeExpiryAlert -> expiring++
                else -> active++
            }
        }
        Triple(active + expiring, expiring, expired)
    }
    val totalActiveCount = stats.first
    val expiringSoonCount = stats.second
    val expiredCount = stats.third

    // Nearest upcoming expiration countdown
    val nextDaysLeft = remember(successState.certificates) {
        val nonExpired = successState.certificates.filter { cert ->
            val daysLeft = if (cert.expiryDate > now) {
                ((cert.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
            } else {
                -1
            }
            daysLeft >= 0
        }
        val soonest = nonExpired.minByOrNull { it.expiryDate }
        soonest?.let { cert ->
            ((cert.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
        } ?: -1
    }

    // Sort certificates
    val sortedCertificates = remember(successState.certificates, currentSort) {
        when (currentSort) {
            SortType.EXPIRY_DATE -> successState.certificates.sortedBy { it.expiryDate }
            SortType.ALPHABETICAL -> successState.certificates.sortedBy { it.title }
            SortType.NEWEST -> successState.certificates.sortedByDescending { it.issueDate }
            SortType.OLDEST -> successState.certificates.sortedBy { it.issueDate }
        }
    }

    // Number of certificates inside each folder
    val folderCounts = remember(successState.certificates) {
        successState.certificates.groupBy { it.folderName }.mapValues { it.value.size }
    }

    // Deletion confirmation logic for certificates
    if (certificateToDelete != null) {
        AlertDialog(
            onDismissRequest = { certificateToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to permanently delete \"${certificateToDelete?.title}\" from your wallet?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        certificateToDelete?.let { onDeleteCertificate(it) }
                        certificateToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { certificateToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Card Section
        item {
            SummaryCard(
                totalActive = totalActiveCount,
                expiringSoon = expiringSoonCount,
                expired = expiredCount,
                nextDaysLeft = nextDaysLeft,
                modifier = Modifier.testTag("summary_card")
            )
        }

        // Search/Sort Controls & Create Folder Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("certificates_tab"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(onClick = { isSortExpanded = true }) {
                            Text(currentSort.label)
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sort By", modifier = Modifier.padding(start = 4.dp))
                        }
                        DropdownMenu(
                            expanded = isSortExpanded,
                            onDismissRequest = { isSortExpanded = false }
                        ) {
                            SortType.entries.forEach { sortType ->
                                DropdownMenuItem(
                                    text = { Text(sortType.label) },
                                    onClick = {
                                        currentSort = sortType
                                        isSortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Folder")
                    }
                }
            }
        }

        if (successState.certificates.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Empty list icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = t("no_certificates"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (activeFolder == null) {
                // ROOT VIEW of our File Explorer
                if (folders.isNotEmpty()) {
                    item {
                        Text(
                            text = "CARTELLE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(folders) { folderName ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeFolder = folderName },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("📁", fontSize = 24.sp)
                                    Column {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${folderCounts[folderName] ?: 0} certificati",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteFolder(folderName) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete folder",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ROOT/GENERAL CERTIFICATES LISTING
                val generalCerts = sortedCertificates.filter { it.folderName.isNullOrBlank() }
                item {
                    Text(
                        text = "CERTIFICATI GENERALI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                if (generalCerts.isEmpty()) {
                    item {
                        Text(
                            text = "Nessun certificato libero. Tutti i certificati sono organizzati nelle cartelle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    items(generalCerts, key = { it.id }) { certificate ->
                        CertificateCard(
                            certificate = certificate,
                            daysBeforeExpiryAlert = daysBeforeExpiryAlert,
                            onClick = { onCertificateClick(certificate.id) },
                            onDeleteClick = { certificateToDelete = certificate },
                            onMoveClick = { onUpdateCertificateFolder(certificate, it) },
                            folders = folders,
                            modifier = Modifier.testTag("certificate_card_${certificate.id}")
                        )
                    }
                }

            } else {
                // INSIDE A FOLDER
                val folderName = activeFolder!!
                val folderCerts = sortedCertificates.filter { it.folderName == folderName }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeFolder = null }
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🏠 Torna indietro",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " / $folderName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (folderCerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "📁",
                                    fontSize = 40.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Questa cartella è vuota",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(folderCerts, key = { it.id }) { certificate ->
                        CertificateCard(
                            certificate = certificate,
                            daysBeforeExpiryAlert = daysBeforeExpiryAlert,
                            onClick = { onCertificateClick(certificate.id) },
                            onDeleteClick = { certificateToDelete = certificate },
                            onMoveClick = { onUpdateCertificateFolder(certificate, it) },
                            folders = folders,
                            modifier = Modifier.testTag("certificate_card_${certificate.id}")
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Nuova Cartella") },
            text = {
                OutlinedTextField(
                    value = folderToCreate,
                    onValueChange = { folderToCreate = it },
                    label = { Text("Nome Cartella") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderToCreate.isNotBlank()) {
                        onCreateFolder(folderToCreate.trim())
                    }
                    showCreateFolderDialog = false
                    folderToCreate = ""
                }) {
                    Text("Crea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
fun SummaryCard(
    totalActive: Int,
    expiringSoon: Int,
    expired: Int,
    nextDaysLeft: Int,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = PolishSurfaceVariant
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Under 1 month (30 days) -> Red; Between 1 and 3 months (30..90 days) -> Yellow/Orange; Over 3 months -> Green
            val countdownColor = when {
                nextDaysLeft < 0 -> Color(0xFF4CAF50)
                nextDaysLeft < 30 -> Color(0xFFE53935)      // under 1 month
                nextDaysLeft in 30..90 -> Color(0xFFFFB300)  // between 1 and 3 months
                else -> Color(0xFF4CAF50)                    // over 3 months
            }

            val countdownText = when {
                nextDaysLeft < 0 -> t("no_expiring_certificates")
                nextDaysLeft == 1 -> t("next_expiry_day")
                else -> t("next_expiry_days").format(nextDaysLeft)
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = t("next_expiry_label"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PolishOnSurfaceVariantText.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = countdownText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = countdownColor,
                    lineHeight = 32.sp
                )
            }

            // Stat columns below
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = t("active_short"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PolishOnSurfaceVariantText.copy(alpha = 0.6f)
                    )
                    Text(
                        text = totalActive.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PolishOnSurfaceVariantText
                    )
                }

                // Expiring column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = t("expiring_soon"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PolishOnSurfaceVariantText.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = TagExpiringText
                        ) {}
                        Text(
                            text = expiringSoon.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PolishOnSurfaceVariantText
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun CertificateCard(
    certificate: Certificate,
    daysBeforeExpiryAlert: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveClick: (String?) -> Unit,
    folders: List<String>,
    modifier: Modifier = Modifier
) {
    var isMoveExpanded by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val daysLeft = if (certificate.expiryDate > now) {
        ((certificate.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        -1
    }

    val (statusLabel, statusColor, statusBg) = when {
        daysLeft < 0 -> Triple(t("expired").uppercase(), TagExpiredText, TagExpiredBg)
        daysLeft in 0..daysBeforeExpiryAlert -> Triple(t("expiring_soon").uppercase(), TagExpiringText, TagExpiringBg)
        else -> Triple(t("valid").uppercase(), TagValidText, TagValidBg)
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val formattedExpiry = dateFormatter.format(Date(certificate.expiryDate))

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = certificate.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!certificate.certNumber.isNullOrBlank()) {
                        Text(
                            text = "No: ${certificate.certNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Row {
                    Box {
                        IconButton(
                            onClick = { isMoveExpanded = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.List,
                                contentDescription = "Sposta in cartella",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                        DropdownMenu(
                            expanded = isMoveExpanded,
                            onDismissRequest = { isMoveExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nessuna cartella") },
                                onClick = {
                                    onMoveClick(null)
                                    isMoveExpanded = false
                                }
                            )
                            folders.forEach { folderName ->
                                DropdownMenuItem(
                                    text = { Text(folderName) },
                                    onClick = {
                                        onMoveClick(folderName)
                                        isMoveExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                .putExtra(android.provider.CalendarContract.Events.TITLE, "Expiry: ${certificate.title}")
                                .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Certificate ${certificate.title} (${certificate.certNumber ?: ""}) is expiring.")
                                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, certificate.expiryDate - (daysBeforeExpiryAlert * 24L * 60L * 60L * 1000L))
                                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Add to Calendar",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }

                    // Delete Action Button (keep simple and accessible)
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("delete_button_${certificate.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Certificate",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EXPIRY DATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formattedExpiry,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (daysLeft < 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status chip
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
