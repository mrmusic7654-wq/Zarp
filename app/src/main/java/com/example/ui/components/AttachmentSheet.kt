package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.ZarpSidebarBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onFileSelected: (Uri, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    // Camera URI (stable reference)
    val cameraUri = remember {
        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // CAMERA launcher — dismiss AFTER success
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            onImageSelected(cameraUri)
            onDismiss()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(cameraUri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // GALLERY launcher — dismiss AFTER result
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri)
            onDismiss()
        }
    }

    // FILE picker — dismiss AFTER result
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    } else "File"
                } ?: "File"
            } catch (e: Exception) {
                "File"
            }
            onFileSelected(uri, name)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZarpSidebarBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Camera
            AttachmentOption(
                icon = Icons.Outlined.CameraAlt,
                text = "Camera",
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        cameraLauncher.launch(cameraUri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    // DO NOT dismiss here — wait for result callback
                }
            )

            // Gallery
            AttachmentOption(
                icon = Icons.Outlined.PhotoLibrary,
                text = "Photo & Video Library",
                onClick = {
                    galleryLauncher.launch("image/*")
                    // DO NOT dismiss here
                }
            )

            // File
            AttachmentOption(
                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                text = "File",
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                    // DO NOT dismiss here
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            AttachmentOption(
                icon = Icons.Rounded.Folder,
                text = "Connect to Google Drive",
                onClick = {
                    Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun AttachmentOption(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ZarpTextTertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = ZarpTextPrimary,
            fontSize = 16.sp
        )
    }
}
