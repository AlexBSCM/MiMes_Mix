package com.mimes.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

private val VISIBILITY_OPTIONS = listOf("public" to "Всем", "contacts" to "Контактам", "private" to "Никому")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onSignOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val uploading by viewModel.uploadProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    LaunchedEffect(saveState) {
        if (saveState is ProfileViewModel.SaveState.Success) {
            viewModel.resetSaveState()
            onBackClick()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadPhoto(it, context.contentResolver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Photo section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = "Фото",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("+", fontSize = 40.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Nickname
            ProfileField(
                label = "Никнейм",
                value = profile.nickname,
                onValueChange = { viewModel.updateNickname(it) },
                visibility = profile.visibility["nickname"] ?: "public",
                onVisibilityChange = { viewModel.updateVisibility("nickname", it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Email
            ProfileField(
                label = "Email",
                value = profile.email,
                onValueChange = { viewModel.updateEmail(it) },
                visibility = profile.visibility["email"] ?: "contacts",
                onVisibilityChange = { viewModel.updateVisibility("email", it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Phone
            ProfileField(
                label = "Телефон",
                value = profile.phone,
                onValueChange = { viewModel.updatePhone(it) },
                visibility = profile.visibility["phone"] ?: "private",
                onVisibilityChange = { viewModel.updateVisibility("phone", it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                enabled = saveState !is ProfileViewModel.SaveState.Saving
            ) {
                if (saveState is ProfileViewModel.SaveState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Сохранить")
                }
            }

            if (saveState is ProfileViewModel.SaveState.Error) {
                Text(
                    text = (saveState as ProfileViewModel.SaveState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
            ) {
                Text("Выйти из аккаунта")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visibility: String,
    onVisibilityChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val visLabel = VISIBILITY_OPTIONS.firstOrNull { it.first == visibility }?.second ?: "Всем"

    Column(modifier = Modifier.padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Показывать: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick = { showMenu = true }) { Text(visLabel, fontSize = 12.sp) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    VISIBILITY_OPTIONS.forEach { (key, name) ->
                        DropdownMenuItem(
                            text = { Text(if (key == visibility) "✓ $name" else name) },
                            onClick = { onVisibilityChange(key); showMenu = false }
                        )
                    }
                }
            }
        }
    }
}
