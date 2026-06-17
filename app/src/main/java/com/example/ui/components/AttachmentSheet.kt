package com.example.ui.components

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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpSidebarBg
import com.example.ui.theme.ZarpTextPrimary
import com.example.ui.theme.ZarpTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onFileSelected: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

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
            AttachmentOption(
                icon = Icons.Outlined.CameraAlt,
                text = "Camera",
                onClick = onFileSelected
            )
            AttachmentOption(
                icon = Icons.Outlined.PhotoLibrary,
                text = "Photo & Video Library",
                onClick = onFileSelected
            )
            AttachmentOption(
                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                text = "File",
                onClick = onFileSelected
            )
            Spacer(modifier = Modifier.height(16.dp))
            AttachmentOption(
                icon = Icons.Rounded.Folder, // Placeholder for drive icon
                text = "Connect to Google Drive",
                onClick = onFileSelected
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
