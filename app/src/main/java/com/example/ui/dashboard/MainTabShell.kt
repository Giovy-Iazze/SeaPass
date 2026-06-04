package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Embarkation
import com.example.ui.theme.*
import com.example.ui.viewmodel.CertificateUiState
import com.example.ui.viewmodel.CertificateViewModel
import com.example.ui.viewmodel.EmbarkationViewModel
import com.example.ui.viewmodel.VesselLookupUiState
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch

fun shouldDisplayFlag(flag: String?): Boolean {
    if (flag.isNullOrBlank()) return false
    val trimmed = flag.trim().lowercase()
    return trimmed != "unknown" && trimmed != "unknown_flag" && trimmed != "unknown flag" && trimmed != "null"
}

fun rotateLocalBackups(context: android.content.Context, backupJsonContent: String) {
    try {
        val backupsDir = java.io.File(context.filesDir, "backups")
        if (!backupsDir.exists()) {
            backupsDir.mkdirs()
        }
        // Save the new backup with timestamp
        val newBackupFile = java.io.File(backupsDir, "seapass_backup_${System.currentTimeMillis()}.json")
        newBackupFile.writeText(backupJsonContent)

        // List all backup files, sort by modified time descending, and delete any past index 2 (meaning keeping newest 3)
        val files = backupsDir.listFiles { _, name -> name.startsWith("seapass_backup_") && name.endsWith(".json") }
        if (files != null && files.size > 3) {
            val sorted = files.sortedByDescending { it.lastModified() }
            for (i in 3 until sorted.size) {
                sorted[i].delete()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("BackupRotation", "Error rotating backups: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabShell(
    certificateViewModel: CertificateViewModel,
    embarkationViewModel: EmbarkationViewModel,
    certificateUiState: CertificateUiState,
    onAddDocumentClick: () -> Unit,
    onEditDocumentClick: (Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val currentLanguage by embarkationViewModel.currentLanguage.collectAsState()
    val seaServiceSummary by embarkationViewModel.seaServiceSummary.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Pass the selected language context down throughout the child elements
    CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = t("app_name") + if (seaServiceSummary.totalSeaDays > 1000) " \uD83E\uDD20" else "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                when (selectedTab) {
                                    0 -> t("certificates_tab")
                                    1 -> t("embarkation_tab")
                                    else -> t("settings_tab")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = PolishSecondary
                            )
                        }
                    },
                    actions = {
                        // Quick language selection chip in top bar for flawless UX!
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            LanguageMenuButton(
                                currentLanguage = currentLanguage,
                                onLanguageSelected = { embarkationViewModel.setLanguage(it) }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = t("certificates_tab")) },
                        label = { Text(t("certificates_tab")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PolishPrimary,
                            selectedTextColor = PolishPrimary,
                            indicatorColor = PolishSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = t("embarkation_tab")) },
                        label = { Text(t("embarkation_tab")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PolishPrimary,
                            selectedTextColor = PolishPrimary,
                            indicatorColor = PolishSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = t("settings_tab")) },
                        label = { Text(t("settings_tab")) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PolishPrimary,
                            selectedTextColor = PolishPrimary,
                            indicatorColor = PolishSurfaceVariant
                        )
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Certificates View (Wallet tab)
                        val daysBeforeExpiryAlert by embarkationViewModel.daysBeforeExpiryAlert.collectAsState()
                        val customFolders by embarkationViewModel.customFolders.collectAsState()
                        DashboardTabContent(
                            viewModel = certificateViewModel,
                            uiState = certificateUiState,
                            daysBeforeExpiryAlert = daysBeforeExpiryAlert,
                            folders = customFolders,
                            onCreateFolder = { embarkationViewModel.addCustomFolder(it) },
                            onDeleteFolder = { deletedFolder ->
                                val certs = (certificateUiState as? CertificateUiState.Success)?.certificates ?: emptyList()
                                certs.forEach { cert ->
                                    if (cert.folderName == deletedFolder) {
                                        certificateViewModel.updateCertificateFolder(cert, null)
                                    }
                                }
                                embarkationViewModel.removeCustomFolder(deletedFolder)
                            },
                            onAddDocumentClick = onAddDocumentClick,
                            onEditDocumentClick = onEditDocumentClick
                        )
                    }
                    1 -> {
                        // Sea Service / Embarkations View
                        SeaServiceTabContent(
                            viewModel = embarkationViewModel
                        )
                    }
                    2 -> {
                        // Dynamic Language and Appearance Settings
                        val themeMode by embarkationViewModel.themeMode.collectAsState()
                        val daysBeforeExpiryAlert by embarkationViewModel.daysBeforeExpiryAlert.collectAsState()
                        LanguageSettingsTabContent(
                            currentLanguage = currentLanguage,
                            onSelectLanguage = { embarkationViewModel.setLanguage(it) },
                            themeMode = themeMode,
                            onSelectThemeMode = { embarkationViewModel.setThemeMode(it) },
                            daysBeforeExpiryAlert = daysBeforeExpiryAlert,
                            onDaysBeforeExpiryAlertChange = { embarkationViewModel.setDaysBeforeExpiryAlert(it) },
                            certificateViewModel = certificateViewModel,
                            embarkationViewModel = embarkationViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageMenuButton(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        InputChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text("${currentLanguage.flag} ${currentLanguage.label}") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "dropdown") },
            colors = InputChipDefaults.inputChipColors(
                selectedContainerColor = PolishSurfaceVariant,
                selectedLabelColor = PolishOnSurfaceVariantText
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppLanguage.values().forEach { lang ->
                DropdownMenuItem(
                    text = { Text("${lang.flag} ${lang.label}") },
                    onClick = {
                        onLanguageSelected(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardTabContent(
    viewModel: CertificateViewModel,
    uiState: CertificateUiState,
    daysBeforeExpiryAlert: Int,
    folders: List<String>,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onAddDocumentClick: () -> Unit,
    onEditDocumentClick: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (uiState) {
                is CertificateUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PolishPrimary)
                    }
                }
                is CertificateUiState.Success -> {
                    DashboardContent(
                        successState = uiState,
                        daysBeforeExpiryAlert = daysBeforeExpiryAlert,
                        onCertificateClick = onEditDocumentClick,
                        onDeleteCertificate = { viewModel.deleteCertificate(it) },
                        onUpdateCertificateFolder = { cert, folder -> viewModel.updateCertificateFolder(cert, folder) },
                        folders = folders,
                        onCreateFolder = onCreateFolder,
                        onDeleteFolder = onDeleteFolder
                    )
                }
            }
        }

        // Floating button aligned perfectly
        ExtendedFloatingActionButton(
            onClick = onAddDocumentClick,
            icon = { Icon(Icons.Filled.Add, contentDescription = t("add_document")) },
            text = { Text(t("add_document")) },
            containerColor = PolishFABBackground,
            contentColor = PolishFABText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .testTag("add_document_fab")
                .padding(16.dp)
        )
    }
}

@Composable
fun SeaServiceTabContent(
    viewModel: EmbarkationViewModel
) {
    val summaryState by viewModel.seaServiceSummary.collectAsState()
    var isShowingAddDialog by remember { mutableStateOf(false) }
    var embarkationToDelete by remember { mutableStateOf<Embarkation?>(null) }
    var embarkationToSignOff by remember { mutableStateOf<Embarkation?>(null) }

    if (isShowingAddDialog) {
        AddEmbarkationDialog(
            viewModel = viewModel,
            onDismiss = { isShowingAddDialog = false }
        )
    }

    if (embarkationToSignOff != null) {
        RegisterSignOffDialog(
            embark = embarkationToSignOff!!,
            viewModel = viewModel,
            onDismiss = { embarkationToSignOff = null }
        )
    }

    if (embarkationToDelete != null) {
        AlertDialog(
            onDismissRequest = { embarkationToDelete = null },
            title = { Text(t("delete")) },
            text = { Text("Are you sure you want to delete this sea service record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        embarkationToDelete?.let { viewModel.deleteEmbarkation(it) }
                        embarkationToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(t("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { embarkationToDelete = null }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats summary card (Pocket STCW style)
            item {
                SeaServiceStatsCard(
                    totalDays = summaryState.totalSeaDays,
                    activeOnboard = summaryState.activeOnboardCount
                )
            }

            item {
                Text(
                    text = t("sea_service_stats"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (summaryState.embarkationList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = PolishSecondary.copy(alpha = 0.5f)
                            )
                            Text(
                                text = t("no_embarkations"),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PolishSecondary
                            )
                        }
                    }
                }
            } else {
                items(summaryState.embarkationList) { embarkation ->
                    EmbarkationItemCard(
                        embark = embarkation,
                        onDeleteClick = { embarkationToDelete = embarkation },
                        onRegisterSignOffClick = { embarkationToSignOff = it }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isShowingAddDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = t("add_embarkation")) },
            text = { Text(t("add_embarkation")) },
            containerColor = PolishFABBackground,
            contentColor = PolishFABText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
fun SeaServiceStatsCard(
    totalDays: Int,
    activeOnboard: Int
) {
    Card(
        modifier = Modifier
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
            Column {
                Text(
                    text = t("total_sea_days").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PolishOnSurfaceVariantText.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
                val emojiSuffix = if (totalDays >= 2000) " 🐴" else ""
                Text(
                    text = "$totalDays ${t("sea_days").lowercase()}$emojiSuffix",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PolishOnSurfaceVariantText,
                    lineHeight = 44.sp
                )
            }

            Divider(color = PolishOnSurfaceVariantText.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = t("active_contracts"),
                        style = MaterialTheme.typography.bodySmall,
                        color = PolishOnSurfaceVariantText.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (activeOnboard > 0) TagValidText else Color.Gray
                        ) {}
                        Text(
                            text = activeOnboard.toString(),
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
fun EmbarkationItemCard(
    embark: Embarkation,
    onDeleteClick: () -> Unit,
    onRegisterSignOffClick: (Embarkation) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val daysEarned = embark.calculateSeaDays()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = PolishPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = embark.vesselName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (shouldDisplayFlag(embark.vesselFlag)) {
                            Text(
                                text = "(${embark.vesselFlag})",
                                fontSize = 12.sp,
                                color = PolishSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${t("rank")}: ${embark.rank}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PolishSecondary
                    )
                    if (!embark.vesselImo.isBlank()) {
                        Text(
                            text = "IMO: ${embark.vesselImo}",
                            fontSize = 11.sp,
                            color = PolishSecondary.copy(alpha = 0.8f)
                        )
                    }

                    if (!embark.vesselType.isNullOrBlank() || !embark.grossTonnage.isNullOrBlank() || !embark.vesselDimensions.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!embark.vesselType.isNullOrBlank()) {
                                Text(
                                    text = "🚢 ${embark.vesselType}",
                                    fontSize = 11.sp,
                                    color = PolishSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (!embark.grossTonnage.isNullOrBlank()) {
                                Text(
                                    text = "⚖️ ${embark.grossTonnage}",
                                    fontSize = 11.sp,
                                    color = PolishSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (!embark.vesselDimensions.isNullOrBlank()) {
                                Text(
                                    text = "📏 ${embark.vesselDimensions}",
                                    fontSize = 11.sp,
                                    color = PolishSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete record",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dates Column with Ports and Journey End
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🟢 ${t("sign_on")}: ${dateFormatter.format(Date(embark.signOnDate))}${
                            if (!embark.signOnPort.isNullOrBlank()) " (${embark.signOnPort})" else ""
                        }",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "🔴 ${t("journey_end")}: ${
                            if (embark.signOffDate != null) {
                                dateFormatter.format(Date(embark.signOffDate)) + (if (!embark.signOffPort.isNullOrBlank()) " (${embark.signOffPort})" else "")
                            } else {
                                t("still_onboard")
                            }
                        }",
                        fontSize = 12.sp,
                        fontWeight = if (embark.signOffDate == null) FontWeight.Bold else FontWeight.Normal,
                        color = if (embark.signOffDate == null) TagValidText else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Days Counter Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (embark.signOffDate == null) TagValidBg else PolishSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$daysEarned",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = if (embark.signOffDate == null) TagValidText else PolishOnSurfaceVariantText
                        )
                        Text(
                            text = t("sea_days").lowercase(),
                            fontSize = 11.sp,
                            color = if (embark.signOffDate == null) TagValidText else PolishOnSurfaceVariantText.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (embark.signOffDate == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onRegisterSignOffClick(embark) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = t("update_signoff"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RoleSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textFieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var showPickerDialog by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("departments") } // "departments" or "ranks"
    var selectedDepartment by remember { mutableStateOf("") }

    val copertaRanks = remember { listOf(
        "Comandante",
        "Primo Ufficiale di Coperta",
        "Secondo Ufficiale di Coperta",
        "Terzo Ufficiale di Coperta",
        "Allievo Ufficiale di Coperta",
        "Nostromo",
        "Marinaio",
        "Giovanotto di Coperta",
        "Mozzo"
    ) }
    val macchinaRanks = remember { listOf(
        "Direttore di Macchina",
        "Primo Ufficiale di Macchina",
        "Secondo Ufficiale di Macchina",
        "Terzo Ufficiale di Macchina",
        "Allievo Ufficiale di Macchina",
        "Ufficiale Elettronico (ETO)",
        "Allievo Ufficiale Elettronico",
        "Capo Operaio",
        "Motorista",
        "Giovanotto di Macchina"
    ) }
    val hotelRanks = remember { listOf(
        "Capo Commissario",
        "Commissario",
        "Primo Cameriere",
        "Cameriere",
        "Piccolo di Camera",
        "Garzone di Camera"
    ) }
    val cucinaRanks = remember { listOf(
        "Cuoco Equipaggio",
        "Capo Cuoco",
        "Cuoco",
        "Garzone di Cucina"
    ) }

    val departments = listOf("Coperta", "Macchina", "Hotel", "Cucina", "Altro")

    // Determine if we should allow manual typing (Altro-mode)
    val isInitiallyCustom = remember(value) {
        value.isNotBlank() &&
        value !in departments &&
        value !in copertaRanks &&
        value !in macchinaRanks &&
        value !in hotelRanks &&
        value !in cucinaRanks
    }
    var isCustomMode by rememberSaveable { mutableStateOf(isInitiallyCustom) }

    // Sync state isCustomMode if value changes from outside (e.g. new record initial load)
    LaunchedEffect(value) {
        val isCustom = value.isNotBlank() &&
                value !in departments &&
                value !in copertaRanks &&
                value !in macchinaRanks &&
                value !in hotelRanks &&
                value !in cucinaRanks
        if (isCustom) {
            isCustomMode = true
        } else if (value.isNotBlank() &&
            (value in copertaRanks || value in macchinaRanks || value in hotelRanks || value in cucinaRanks || value in departments)
        ) {
            isCustomMode = false
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (isCustomMode) {
                    onValueChange(it)
                }
            },
            readOnly = !isCustomMode,
            label = { Text(label) },
            colors = textFieldColors,
            trailingIcon = {
                IconButton(onClick = {
                    currentStage = "departments"
                    showPickerDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select Role"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // For standard roles (non-custom mode), draw an invisible overlay to intercept clicks and open the dialog safely.
        if (!isCustomMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        currentStage = "departments"
                        showPickerDialog = true
                    }
            )
        }
    }

    if (showPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPickerDialog = false },
            title = {
                Text(
                    text = if (currentStage == "departments") "Seleziona Settore" else "Seleziona Qualifica ($selectedDepartment)",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentStage == "departments") {
                        departments.forEach { dept ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (dept == "Altro") {
                                            isCustomMode = true
                                            onValueChange("") // let user type from scratch
                                            showPickerDialog = false
                                        } else {
                                            selectedDepartment = dept
                                            currentStage = "ranks"
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dept,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(16.dp).rotate(180f)
                                    )
                                }
                            }
                        }
                    } else {
                        // BACK BUTTON inside dialog stage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentStage = "departments"
                                }
                                .padding(vertical = 8.dp),
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
                                text = "Torna indietro",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val rankList = when (selectedDepartment) {
                            "Coperta" -> copertaRanks
                            "Macchina" -> macchinaRanks
                            "Hotel" -> hotelRanks
                            "Cucina" -> cucinaRanks
                            else -> emptyList()
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(rankList) { r ->
                                Text(
                                    text = r,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isCustomMode = false
                                            onValueChange(r)
                                            showPickerDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                )
                                Divider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerDialog = false }) {
                    Text(t("cancel"))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEmbarkationDialog(
    viewModel: EmbarkationViewModel,
    onDismiss: () -> Unit
) {
    var vesselName by remember { mutableStateOf("") }
    var vesselImo by remember { mutableStateOf("") }
    var vesselMmsi by remember { mutableStateOf("") }
    val initialRole = remember { viewModel.defaultRole.value }
    var rank by remember { mutableStateOf(initialRole) }
    var vesselType by remember { mutableStateOf("") }
    var vesselFlag by remember { mutableStateOf("") }
    var signOnPort by remember { mutableStateOf("") }
    var signOffPort by remember { mutableStateOf("") }
    var grossTonnage by remember { mutableStateOf("") }
    var vesselDimensions by remember { mutableStateOf("") }

    var signOnDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var signOffDate by remember { mutableStateOf<Long?>(null) }
    var stillOnBoard by remember { mutableStateOf(true) }

    val lookupState by viewModel.lookupState.collectAsState()

    // Trigger Vessel Selection autofill if lookup matches
    LaunchedEffect(lookupState) {
        if (lookupState is VesselLookupUiState.Success) {
            val v = (lookupState as VesselLookupUiState.Success).vessel
            vesselName = v.name ?: ""
            vesselImo = v.imo ?: ""
            vesselMmsi = v.mmsi ?: ""
            vesselType = v.type ?: ""
            vesselFlag = v.flag ?: ""
            grossTonnage = v.grossTonnage ?: ""
            vesselDimensions = v.vesselDimensions ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearLookupState()
            onDismiss()
        },
        title = {
            Text(
                t("add_embarkation"),
                fontWeight = FontWeight.Bold,
                color = PolishOnSurfaceVariantText
            )
        },
        text = {
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Easy VesselFinder helper look up right inside add boarding form! Beautiful!
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PolishSurfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                t("search_from_vf"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PolishOnSurfaceVariantText
                            )

                            var searchVal by remember { mutableStateOf("") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchVal,
                                    onValueChange = { searchVal = it },
                                    placeholder = { Text("IMO/MMSI") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = textFieldColors
                                )

                                Button(
                                    onClick = { viewModel.searchVessel(searchVal) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            }

                            when (lookupState) {
                                is VesselLookupUiState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PolishPrimary)
                                        Text(t("loading"), fontSize = 12.sp, color = PolishSecondary)
                                    }
                                }
                                is VesselLookupUiState.Success -> {
                                    val isDemo = (lookupState as VesselLookupUiState.Success).isDemoMode
                                    Column {
                                        Text(
                                            "✓ Autofilled: ${(lookupState as VesselLookupUiState.Success).vessel.name}",
                                            color = TagValidText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        if (isDemo) {
                                            Text(t("warning_no_key"), fontSize = 10.sp, color = TagExpiringText, lineHeight = 12.sp)
                                        }
                                    }
                                }
                                is VesselLookupUiState.Error -> {
                                    Text(
                                        (lookupState as VesselLookupUiState.Error).errorMessage,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = vesselName,
                        onValueChange = { vesselName = it },
                        label = { Text(t("vessel_name")) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vesselImo,
                            onValueChange = { vesselImo = it },
                            label = { Text("IMO") },
                            singleLine = true,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = vesselFlag,
                            onValueChange = { vesselFlag = it },
                            label = { Text(t("flag")) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    RoleSelectionField(
                        value = rank,
                        onValueChange = { rank = it },
                        label = t("rank"),
                        modifier = Modifier.fillMaxWidth(),
                        textFieldColors = textFieldColors
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vesselType,
                            onValueChange = { vesselType = it },
                            label = { Text(t("type")) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = grossTonnage,
                            onValueChange = { grossTonnage = it },
                            label = { Text(t("gross_tonnage")) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = vesselDimensions,
                        onValueChange = { vesselDimensions = it },
                        label = { Text(t("vessel_dimensions")) },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = signOnPort,
                            onValueChange = { signOnPort = it },
                            label = { Text(t("sign_on_port")) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stillOnBoard = !stillOnBoard }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = stillOnBoard,
                            onCheckedChange = { stillOnBoard = it },
                            colors = CheckboxDefaults.colors(checkedColor = PolishPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("still_onboard"), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Sign on picker
                item {
                    Column {
                        Text(t("sign_on"), style = MaterialTheme.typography.labelMedium, color = PolishSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        DatePickerField(
                            initialSelectedDate = signOnDate,
                            onDateChange = { signOnDate = it }
                        )
                    }
                }

                // Sign off picker (only shown if not still on board!)
                if (!stillOnBoard) {
                    item {
                        Column {
                            Text(t("sign_off"), style = MaterialTheme.typography.labelMedium, color = PolishSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            DatePickerField(
                                initialSelectedDate = signOffDate ?: System.currentTimeMillis(),
                                onDateChange = { signOffDate = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (vesselName.isNotBlank() && rank.isNotBlank()) {
                        viewModel.saveEmbarkation(
                            vesselName = vesselName,
                            vesselImo = vesselImo,
                            vesselMmsi = vesselMmsi,
                            rank = rank,
                            vesselType = vesselType,
                            vesselFlag = vesselFlag,
                            signOnDate = signOnDate,
                            signOffDate = if (stillOnBoard) null else (signOffDate ?: System.currentTimeMillis()),
                            seaDaysOverride = null,
                            signOnPort = signOnPort.trim().ifBlank { null },
                            signOffPort = signOffPort.trim().ifBlank { null },
                            grossTonnage = grossTonnage.trim().ifBlank { null },
                            vesselDimensions = vesselDimensions.trim().ifBlank { null },
                            onComplete = {
                                viewModel.clearLookupState()
                                onDismiss()
                            }
                        )
                    }
                },
                enabled = vesselName.isNotBlank() && rank.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
            ) {
                Text(t("btn_save"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearLookupState()
                    onDismiss()
                }
            ) {
                Text(t("cancel"))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun DatePickerField(
    initialSelectedDate: Long,
    onDateChange: (Long) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initialSelectedDate } }
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    var displayedText by remember(initialSelectedDate) { mutableStateOf(formatter.format(Date(initialSelectedDate))) }

    OutlinedCard(
        onClick = {
            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val selected = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }.timeInMillis
                    onDateChange(selected)
                    displayedText = formatter.format(Date(selected))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(displayedText, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = PolishPrimary)
        }
    }
}

@Composable
fun VesselFinderTabContent(
    viewModel: EmbarkationViewModel
) {
    var queryValue by remember { mutableStateOf("") }
    val lookupState by viewModel.lookupState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    t("search_vessel"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    t("search_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PolishSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = queryValue,
                        onValueChange = { queryValue = it },
                        placeholder = { Text(t("search_placeholder")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.searchVessel(queryValue) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Text(t("btn_search"))
                        }
                    }
                }
            }
        }

        item {
            when (lookupState) {
                is VesselLookupUiState.Idle -> {
                    // Pre-populate some examples to make it super intuitive to use!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Pro-Tip: Try searching one of these well-known maritime vessel IMO/MMSI numbers to test:",
                            style = MaterialTheme.typography.labelSmall,
                            color = PolishSecondary
                        )

                        val suggestions = listOf(
                            "247313000" to "Amerigo Vespucci 🇮🇹",
                            "219156000" to "Emma Maersk 🇩🇰",
                            "353136000" to "Ever Given 🇵🇦",
                            "548123456" to "Magsaysay Explorer 🇵🇭"
                        )

                        suggestions.forEach { (mmsi, name) ->
                            SuggestionChip(
                                onClick = {
                                    queryValue = mmsi
                                    viewModel.searchVessel(mmsi)
                                },
                                label = { Text("$name ($mmsi)") }
                            )
                        }
                    }
                }
                is VesselLookupUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = PolishPrimary)
                            Text(t("loading"), style = MaterialTheme.typography.bodyMedium, color = PolishSecondary)
                        }
                    }
                }
                is VesselLookupUiState.Success -> {
                    val vessel = (lookupState as VesselLookupUiState.Success).vessel
                    val isDemo = (lookupState as VesselLookupUiState.Success).isDemoMode

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PolishSurfaceVariant),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = t("vessel_details").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PolishPrimary,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = vessel.name ?: "Unknown",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = PolishOnSurfaceVariantText
                                    )
                                }

                                Surface(
                                    color = PolishPrimary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = vessel.flag ?: "No Flag",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = PolishPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Divider(color = PolishOnSurfaceVariantText.copy(alpha = 0.1f))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    DetailMetaRow(label = "IMO", value = vessel.imo ?: "-")
                                    DetailMetaRow(label = "MMSI", value = vessel.mmsi ?: "-")
                                    DetailMetaRow(label = t("callsign"), value = vessel.callsign ?: "-")
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    DetailMetaRow(label = t("type"), value = vessel.type ?: "-")
                                    DetailMetaRow(label = t("destination"), value = vessel.destination ?: "-")
                                    DetailMetaRow(label = t("eta"), value = vessel.eta ?: "-")
                                }
                            }

                            if (isDemo) {
                                Surface(
                                    color = TagExpiringBg,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = TagExpiringText, modifier = Modifier.size(18.dp))
                                        Text(
                                            t("warning_no_key"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TagExpiringText,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is VesselLookupUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TagExpiredBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = TagExpiredText)
                            Text(
                                (lookupState as VesselLookupUiState.Error).errorMessage,
                                color = TagExpiredText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailMetaRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = PolishOnSurfaceVariantText.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PolishOnSurfaceVariantText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsTabContent(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    themeMode: String,
    onSelectThemeMode: (String) -> Unit,
    daysBeforeExpiryAlert: Int,
    onDaysBeforeExpiryAlertChange: (Int) -> Unit,
    certificateViewModel: com.example.ui.viewmodel.CertificateViewModel,
    embarkationViewModel: com.example.ui.viewmodel.EmbarkationViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val certs = (certificateViewModel.uiState.value as? CertificateUiState.Success)?.certificates ?: emptyList()
                val embs = embarkationViewModel.seaServiceSummary.value.embarkationList
                
                val json = org.json.JSONObject()
                val certsArray = org.json.JSONArray()
                certs.forEach { cert ->
                    val obj = org.json.JSONObject()
                    obj.put("title", cert.title)
                    obj.put("certNumber", cert.certNumber)
                    obj.put("issueDate", cert.issueDate)
                    obj.put("expiryDate", cert.expiryDate)
                    obj.put("category", cert.category)
                    obj.put("isMandatory", cert.isMandatory)
                    certsArray.put(obj)
                }
                json.put("certificates", certsArray)

                val embsArray = org.json.JSONArray()
                embs.forEach { emb ->
                    val obj = org.json.JSONObject()
                    obj.put("vesselName", emb.vesselName)
                    obj.put("vesselImo", emb.vesselImo)
                    obj.put("vesselMmsi", emb.vesselMmsi ?: "")
                    obj.put("rank", emb.rank)
                    obj.put("vesselType", emb.vesselType ?: "")
                    obj.put("vesselFlag", emb.vesselFlag ?: "")
                    obj.put("signOnDate", emb.signOnDate)
                    if (emb.signOffDate != null) obj.put("signOffDate", emb.signOffDate)
                    if (emb.signOnPort != null) obj.put("signOnPort", emb.signOnPort)
                    if (emb.signOffPort != null) obj.put("signOffPort", emb.signOffPort)
                    embsArray.put(obj)
                }
                json.put("embarkations", embsArray)
                
                val jsonStr = json.toString(4)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonStr.toByteArray())
                }
                // Maintain local rotation count (max 3 backups)
                rotateLocalBackups(context, jsonStr)
                android.widget.Toast.makeText(context, "Backup saved to Drive!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error saving backup: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val openDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonString != null) {
                    val jsonObj = org.json.JSONObject(jsonString)
                    val certsArray = jsonObj.optJSONArray("certificates")
                    if (certsArray != null) {
                        for (i in 0 until certsArray.length()) {
                            val c = certsArray.getJSONObject(i)
                            certificateViewModel.saveCertificate(
                                title = c.getString("title"),
                                category = if (c.has("category")) c.getString("category") else "Other",
                                issueDate = c.getLong("issueDate"),
                                expiryDate = c.getLong("expiryDate"),
                                certNumber = c.getString("certNumber"),
                                isMandatory = if (c.has("isMandatory")) c.getBoolean("isMandatory") else true,
                                attachmentUriString = null,
                                context = context,
                                onComplete = {}
                            )
                        }
                    }
                    val embsArray = jsonObj.optJSONArray("embarkations")
                    if (embsArray != null) {
                        for (i in 0 until embsArray.length()) {
                            val e = embsArray.getJSONObject(i)
                            embarkationViewModel.saveEmbarkation(
                                id = 0,
                                vesselName = e.getString("vesselName"),
                                vesselImo = e.getString("vesselImo"),
                                vesselMmsi = e.optString("vesselMmsi").takeIf { it.isNotBlank() },
                                rank = e.getString("rank"),
                                vesselType = e.optString("vesselType").takeIf { it.isNotBlank() },
                                vesselFlag = e.optString("vesselFlag").takeIf { it.isNotBlank() },
                                signOnDate = e.getLong("signOnDate"),
                                signOffDate = if (e.has("signOffDate")) e.getLong("signOffDate") else null,
                                signOnPort = if (e.has("signOnPort")) e.getString("signOnPort") else null,
                                signOffPort = if (e.has("signOffPort")) e.getString("signOffPort") else null,
                                seaDaysOverride = null,
                                grossTonnage = null,
                                vesselDimensions = null,
                                onComplete = {}
                            )
                        }
                    }
                    android.widget.Toast.makeText(context, "Backup restored from Drive!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error restoring backup: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            t("settings_tab"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            "Select your preferred language interface. The entire experience will adapt instantaneously.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        AppLanguage.values().forEach { lang ->
            val isSelected = lang == currentLanguage
            Card(
                onClick = { onSelectLanguage(lang) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) PolishSurfaceVariant else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) null else BorderStroke(1.dp, PolishOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.flag, fontSize = 28.sp)
                        Text(
                            text = lang.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PolishOnSurfaceVariantText else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = PolishPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            t("theme_mode"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        listOf(
            "system" to t("theme_system"),
            "light" to t("theme_light"),
            "dark" to t("theme_dark")
        ).forEach { (mode, label) ->
            val isSelected = themeMode == mode
            Card(
                onClick = { onSelectThemeMode(mode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) PolishSurfaceVariant else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) null else BorderStroke(1.dp, PolishOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) PolishOnSurfaceVariantText else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = PolishPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Calendar Expiry Alert",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Days before expiry to automatically set for calendar alerts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        val intervals = remember { listOf(30, 60, 90, 120) }
        val currentIndex = remember(daysBeforeExpiryAlert) {
            val idx = intervals.indexOf(daysBeforeExpiryAlert)
            if (idx == -1) 1 else idx
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { index ->
                        val selectedValue = intervals[index.toInt()]
                        onDaysBeforeExpiryAlertChange(selectedValue)
                    },
                    valueRange = 0f..3f,
                    steps = 2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$daysBeforeExpiryAlert days",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                intervals.forEach { days ->
                    Text(
                        text = "$days",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (daysBeforeExpiryAlert == days) FontWeight.Bold else FontWeight.Normal,
                        color = if (daysBeforeExpiryAlert == days) PolishPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ruolo Predefinito (Settings)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Imposta il ruolo predefinito che verrà compilato automaticamente quando crei una nuova scheda di imbraco.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        val defaultRole by embarkationViewModel.defaultRole.collectAsState()

        RoleSelectionField(
            value = defaultRole,
            onValueChange = { embarkationViewModel.setDefaultRole(it) },
            label = "Ruolo Default",
            modifier = Modifier.fillMaxWidth(),
            textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Cloud Backup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Authenticate with Google Workspace to seamlessly back up and sync your credentials, embarkations, and settings on your Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
        
        LaunchedEffect(Unit) {
            signedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        }

        val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                signedInAccount = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Google Sign-In failed (${e.statusCode}): ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Google Sign-In failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        val coroutineScope = rememberCoroutineScope()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "User",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Google Drive Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Backup e Ripristino con Google Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (signedInAccount == null) {
                        Button(
                            onClick = { 
                                googleSignInLauncher.launch(GoogleDriveBackupHelper.getGoogleSignInIntent(context))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Accedi con Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Backup/Restore buttons here, using signedInAccount!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    coroutineScope.launch {
                                        android.widget.Toast.makeText(context, "Backup in corso...", android.widget.Toast.LENGTH_SHORT).show()
                                        
                                        val certs = (certificateViewModel.uiState.value as? CertificateUiState.Success)?.certificates ?: emptyList()
                                        val embs = embarkationViewModel.seaServiceSummary.value.embarkationList
                                        
                                        val json = org.json.JSONObject()
                                        val certsArray = org.json.JSONArray()
                                        certs.forEach { cert ->
                                            val obj = org.json.JSONObject()
                                            obj.put("title", cert.title)
                                            obj.put("certNumber", cert.certNumber)
                                            obj.put("issueDate", cert.issueDate)
                                            obj.put("expiryDate", cert.expiryDate)
                                            obj.put("category", cert.category)
                                            obj.put("isMandatory", cert.isMandatory)
                                            certsArray.put(obj)
                                        }
                                        json.put("certificates", certsArray)
                        
                                        val embsArray = org.json.JSONArray()
                                        embs.forEach { emb ->
                                            val obj = org.json.JSONObject()
                                            obj.put("vesselName", emb.vesselName)
                                            obj.put("vesselImo", emb.vesselImo)
                                            obj.put("vesselMmsi", emb.vesselMmsi ?: "")
                                            obj.put("rank", emb.rank)
                                            obj.put("vesselType", emb.vesselType ?: "")
                                            obj.put("vesselFlag", emb.vesselFlag ?: "")
                                            obj.put("signOnDate", emb.signOnDate)
                                            if (emb.signOffDate != null) obj.put("signOffDate", emb.signOffDate)
                                            if (emb.signOnPort != null) obj.put("signOnPort", emb.signOnPort)
                                            if (emb.signOffPort != null) obj.put("signOffPort", emb.signOffPort)
                                            embsArray.put(obj)
                                        }
                                        json.put("embarkations", embsArray)
                                        
                                        val jsonStr = json.toString(4)

                                        val success = GoogleDriveBackupHelper.uploadBackup(context, signedInAccount!!, jsonStr)
                                        if (success) {
                                            android.widget.Toast.makeText(context, "Backup caricato su Drive!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Errore nel backup", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Backup",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup su Drive", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { 
                                    coroutineScope.launch {
                                        android.widget.Toast.makeText(context, "Ripristino in corso...", android.widget.Toast.LENGTH_SHORT).show()
                                        val jsonString = GoogleDriveBackupHelper.downloadLatestBackup(context, signedInAccount!!)
                                        if (jsonString != null) {
                                            try {
                                                val jsonObj = org.json.JSONObject(jsonString)
                                                val certsArr = jsonObj.optJSONArray("certificates")
                                                if (certsArr != null) {
                                                    for (i in 0 until certsArr.length()) {
                                                        val c = certsArr.getJSONObject(i)
                                                        certificateViewModel.saveCertificate(
                                                            title = c.getString("title"),
                                                            category = if (c.has("category")) c.getString("category") else "Other",
                                                            issueDate = c.getLong("issueDate"),
                                                            expiryDate = c.getLong("expiryDate"),
                                                            certNumber = c.getString("certNumber"),
                                                            isMandatory = if (c.has("isMandatory")) c.getBoolean("isMandatory") else true,
                                                            attachmentUriString = null,
                                                            context = context,
                                                            onComplete = {}
                                                        )
                                                    }
                                                }
                                                val embsArr = jsonObj.optJSONArray("embarkations")
                                                if (embsArr != null) {
                                                    for (i in 0 until embsArr.length()) {
                                                        val e = embsArr.getJSONObject(i)
                                                        embarkationViewModel.saveEmbarkation(
                                                            id = 0,
                                                            vesselName = e.getString("vesselName"),
                                                            vesselImo = e.getString("vesselImo"),
                                                            vesselMmsi = e.optString("vesselMmsi").takeIf { it.isNotBlank() },
                                                            rank = e.getString("rank"),
                                                            vesselType = e.optString("vesselType").takeIf { it.isNotBlank() },
                                                            vesselFlag = e.optString("vesselFlag").takeIf { it.isNotBlank() },
                                                            signOnDate = e.getLong("signOnDate"),
                                                            signOffDate = if (e.has("signOffDate")) e.getLong("signOffDate") else null,
                                                            signOnPort = if (e.has("signOnPort")) e.getString("signOnPort") else null,
                                                            signOffPort = if (e.has("signOffPort")) e.getString("signOffPort") else null,
                                                            seaDaysOverride = null,
                                                            grossTonnage = null,
                                                            vesselDimensions = null,
                                                            onComplete = {}
                                                        )
                                                    }
                                                }
                                                android.widget.Toast.makeText(context, "Ripristino completato", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch(e: Exception) {
                                                e.printStackTrace()
                                                android.widget.Toast.makeText(context, "Errore durante il ripristino", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Nessun backup trovato su Drive", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_cloud),
                                    contentDescription = "Restore",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ottieni da Drive", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterSignOffDialog(
    embark: Embarkation,
    viewModel: EmbarkationViewModel,
    onDismiss: () -> Unit
) {
    var signOffPort by remember { mutableStateOf("") }
    var signOffDate by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                t("update_signoff"),
                fontWeight = FontWeight.Bold,
                color = PolishOnSurfaceVariantText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${t("vessel_name")}: ${embark.vesselName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = signOffPort,
                    onValueChange = { signOffPort = it },
                    label = { Text(t("journey_end")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )

                Column {
                    Text(t("sign_off"), style = MaterialTheme.typography.labelMedium, color = PolishSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    DatePickerField(
                        initialSelectedDate = signOffDate,
                        onDateChange = { signOffDate = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.registerSignOff(
                        id = embark.id,
                        signOffDate = signOffDate,
                        signOffPort = signOffPort,
                        onComplete = onDismiss
                    )
                }
            ) {
                Text(t("btn_save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("cancel"))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
