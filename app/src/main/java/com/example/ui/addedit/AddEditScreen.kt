package com.example.ui.addedit

import android.app.DatePickerDialog
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.Certificate
import com.example.ui.theme.*
import com.example.ui.viewmodel.CertificateViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel: CertificateViewModel,
    certId: Int, // -1 means add document, else edit
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Screen State
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("STCW") }
    var certNumber by remember { mutableStateOf("") }
    var issueDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var expiryDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000) } // Default 1 year from now
    var attachmentPath by remember { mutableStateOf<String?>(null) }
    var isMandatory by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val sharedPrefs = remember { context.getSharedPreferences("seapass_prefs", android.content.Context.MODE_PRIVATE) }
    val customFolders = remember {
        sharedPrefs.getStringSet("custom_folders", emptySet())?.toList() ?: emptyList()
    }
    var folderName by remember { mutableStateOf<String?>(null) }

    // Dropdown options
    val certificateTitles = remember { listOf(
        "Basic firefighting",
        "Personal survival techniques",
        "Elementary first aid",
        "PSSR",
        "Advanced fire fighting",
        "Refresher advanced fire fighting",
        "Radar observation & plotting",
        "RADAR - A.R.P.A. Use of automatic radar plotting aids (operational level)",
        "RADAR - A.R.P.A. Bridge teamwork search & rescue (management level)",
        "ECDIS",
        "High voltage technologies",
        "Basic training for oil & chemical tanker cargo operations",
        "Certificate of proficiency on advanced training for oil tanker cargo operations",
        "Advanced training for chemical tariker cargo operations",
        "Basic training for liquefied gas tanker cargo operations",
        "Advanced training for liquefied gas tanker cargo operations",
        "Passenger ship",
        "MAMS",
        "MABEV",
        "Medical first aid",
        "Medical care",
        "Ship security officer",
        "Security awareness",
        "Security duties",
        "Bridge resource management - application leadership & teamwork",
        "Use of leadership and managerial skills training",
        "Other"
    ) }
    var isTitleExpanded by remember { mutableStateOf(false) }

    // Date display formatters
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    // File picker launcher supporting images and PDFs (*/*)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            attachmentPath = it.toString()
        }
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        filePickerLauncher.launch("*/*")
    }

    val requestPermissionAndPickFile = {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val hasAll = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasAll) {
            filePickerLauncher.launch("*/*")
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.TakePicture() {
            override fun createIntent(context: android.content.Context, input: android.net.Uri): android.content.Intent {
                val intent = super.createIntent(context, input)
                intent.clipData = android.content.ClipData.newRawUri(null, input)
                intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                return intent
            }
        }
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                attachmentPath = file.absolutePath
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val fileName = "cert_camera_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            photoFile = file
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.giorninave.app.fileprovider",
                    file
                )
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Errore fotocamera: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Permesso fotocamera negato", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val requestPermissionAndLaunchCamera = {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val fileName = "cert_camera_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            photoFile = file
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.giorninave.app.fileprovider",
                    file
                )
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Errore fotocamera: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Load existing certificate if in edit mode
    LaunchedEffect(certId) {
        if (certId != -1) {
            isEditMode = true
            // Grab item from local DB
            val certUiState = viewModel.uiState.value
            if (certUiState is com.example.ui.viewmodel.CertificateUiState.Success) {
                val cert = certUiState.certificates.find { it.id == certId }
                if (cert != null) {
                    title = cert.title
                    category = cert.category
                    certNumber = cert.certNumber ?: ""
                    issueDateMillis = cert.issueDate
                    expiryDateMillis = cert.expiryDate
                    attachmentPath = cert.attachmentPath
                    isMandatory = cert.isMandatory
                    folderName = cert.folderName
                }
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) t("edit_document") else t("add_document"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                var isTitleDialogVisible by remember { mutableStateOf(false) }
                val isCustomTitle = remember(title) { title.isNotEmpty() && !certificateTitles.contains(title) }
                var showOtherField by remember { mutableStateOf(isCustomTitle) }

                // Title Selection Read-Only Field
                Box(modifier = Modifier.fillMaxWidth().clickable { isTitleDialogVisible = true }) {
                    OutlinedTextField(
                        value = if (showOtherField && !isCustomTitle) "Other" else title,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(t("doc_title")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Select Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (showOtherField) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Custom Title") },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Folder Selection (Optional)
                var isFolderDialogVisible by remember { mutableStateOf(false) }
                var foldersListState by remember(customFolders) { mutableStateOf(customFolders) }
                var newFolderInputText by remember { mutableStateOf("") }

                Box(modifier = Modifier.fillMaxWidth().clickable { isFolderDialogVisible = true }) {
                    OutlinedTextField(
                        value = folderName ?: "Nessuna cartella",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Cartella") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sposta in cartella") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (isFolderDialogVisible) {
                    AlertDialog(
                        onDismissRequest = { isFolderDialogVisible = false },
                        title = { Text("Seleziona Cartella") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Add a on-the-fly folder creation row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newFolderInputText,
                                        onValueChange = { newFolderInputText = it },
                                        placeholder = { Text("Crea nuova...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = textFieldColors,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val newFolderName = newFolderInputText.trim()
                                            if (newFolderName.isNotBlank()) {
                                                val updatedSet = (foldersListState.toSet() + newFolderName)
                                                foldersListState = updatedSet.toList()
                                                sharedPrefs.edit().putStringSet("custom_folders", updatedSet).apply()
                                                folderName = newFolderName
                                                newFolderInputText = ""
                                                isFolderDialogVisible = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+")
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                    item {
                                        Text(
                                            text = "Nessuna cartella",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    folderName = null
                                                    isFolderDialogVisible = false
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    }
                                    items(foldersListState) { folder ->
                                        Text(
                                            text = folder,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    folderName = folder
                                                    isFolderDialogVisible = false
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { isFolderDialogVisible = false }) {
                                Text(t("cancel"))
                            }
                        }
                    )
                }

                if (isTitleDialogVisible) {
                    AlertDialog(
                        onDismissRequest = { isTitleDialogVisible = false },
                        title = { Text(t("doc_title")) },
                        text = {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(certificateTitles) { selection ->
                                    Text(
                                        text = selection,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selection == "Other") {
                                                    showOtherField = true
                                                    title = ""
                                                } else {
                                                    showOtherField = false
                                                    title = selection
                                                }
                                                isTitleDialogVisible = false
                                            }
                                            .padding(vertical = 12.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { isTitleDialogVisible = false }) {
                                Text(t("cancel"))
                            }
                        }
                    )
                }

                // Cert Number Field (Optional)
                OutlinedTextField(
                    value = certNumber,
                    onValueChange = { certNumber = it },
                    label = { Text(t("doc_num")) },
                    placeholder = { Text("e.g. STCW-12345") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cert_number_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Date Picker Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Issue Date Picker
                    Box(modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val calendar = Calendar.getInstance().apply { timeInMillis = issueDateMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                        set(Calendar.HOUR_OF_DAY, 12) // Keep standard midday
                                    }
                                    issueDateMillis = cal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .testTag("issue_date_picker_container")
                    ) {
                        OutlinedTextField(
                            value = dateFormatter.format(Date(issueDateMillis)),
                            onValueChange = {},
                            enabled = false,
                            label = { Text(t("issue_date")) },
                            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Expiry Date Picker
                    Box(modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val calendar = Calendar.getInstance().apply { timeInMillis = expiryDateMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                        set(Calendar.HOUR_OF_DAY, 12) // Keep standard midday
                                    }
                                    expiryDateMillis = cal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .testTag("expiry_date_picker_container")
                    ) {
                        OutlinedTextField(
                            value = dateFormatter.format(Date(expiryDateMillis)),
                            onValueChange = {},
                            enabled = false,
                            label = { Text(t("expiry_date")) },
                            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Attachment Section (Photo/PDF)
                Text(
                    t("attachment"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (attachmentPath != null) {
                            val path = attachmentPath!!
                            val isImage = path.startsWith("content://") || 
                                           path.endsWith(".jpg") || 
                                           path.endsWith(".jpeg") || 
                                           path.endsWith(".png") || 
                                           path.endsWith(".webp")

                            if (isImage) {
                                // Draw image thumbnail
                                Image(
                                    painter = rememberAsyncImagePainter(model = path),
                                    contentDescription = "Attachment Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                        .clickable { openFile(context, path) }
                                )
                            } else {
                                // Show file name badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clickable { openFile(context, path) }
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "File Icon",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = File(path).name.takeLast(25),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Remove attachment button
                            OutlinedButton(
                                onClick = { attachmentPath = null },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("remove_attachment_btn")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(t("delete"))
                            }

                        } else {
                            // Empty State Attachment Button
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                t("select_attachment"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Side-by-side Option Buttons (Choose File vs Camera)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { requestPermissionAndPickFile() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("attach_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(t("choose_file"))
                                }

                                Button(
                                    onClick = { requestPermissionAndLaunchCamera() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("camera_button")
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(t("camera"))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Action Button
                val isFormValid = title.isNotBlank()

                Button(
                    onClick = {
                        if (isFormValid) {
                            viewModel.saveCertificate(
                                id = if (isEditMode) certId else 0,
                                title = title.trim(),
                                category = category,
                                issueDate = issueDateMillis,
                                expiryDate = expiryDateMillis,
                                certNumber = certNumber.trim().ifBlank { null },
                                attachmentUriString = attachmentPath,
                                context = context,
                                isMandatory = isMandatory,
                                folderName = folderName,
                                onComplete = { onNavigateBack() }
                            )
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PolishPrimary
                    )
                ) {
                    Text(
                        text = t("btn_save"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun openFile(context: android.content.Context, pathString: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        val uri = if (pathString.startsWith("content://")) {
            android.net.Uri.parse(pathString)
        } else {
            val file = java.io.File(pathString)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "com.giorninave.app.fileprovider",
                file
            )
        }
        val mimeType = context.contentResolver.getType(uri) ?: when {
            pathString.lowercase().endsWith(".pdf") -> "application/pdf"
            pathString.lowercase().endsWith(".jpg") || pathString.lowercase().endsWith(".jpeg") -> "image/jpeg"
            pathString.lowercase().endsWith(".png") -> "image/png"
            pathString.lowercase().endsWith(".webp") -> "image/webp"
            else -> "*/*"
        }
        intent.setDataAndType(uri, mimeType)
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(null, uri)
        val chooser = android.content.Intent.createChooser(intent, "Apri certificato")
        chooser.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Impossibile aprire il file: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

