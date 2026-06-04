package com.mimes.app.ui.chatdetail

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mimes.app.data.Message
import com.mimes.app.ui.auth.Session
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "ChatScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    peerName: String,
    onBackClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val currentUid = Session.currentUserId
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val receiverId = if (peerName == "Бот") "bot" else peerName
            viewModel.uploadAndSendFile(chatId, receiverId, messageText, uri, context.contentResolver)
            messageText = ""
        }
    }

    Log.d(TAG, "ChatScreen opened: ID=, Name=, User=")

    // Защита от пустого ID
    if (chatId.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ошибка: Неверный ID чата", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LaunchedEffect(chatId) {
        Log.d(TAG, "Loading messages for: ")
        try {
            viewModel.loadMessages(chatId)
            viewModel.markChatAsRead(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages", e)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(uploadProgress) {
        when (uploadProgress) {
            is UploadState.Uploading -> {
                snackbarHostState.showSnackbar("Загрузка файла...", duration = SnackbarDuration.Short)
            }
            is UploadState.Error -> {
                snackbarHostState.showSnackbar(
                    (uploadProgress as UploadState.Error).message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = peerName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d(TAG, "Back button clicked")
                        onBackClick()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (uploadProgress !is UploadState.Uploading) {
                                filePickerLauncher.launch("*/*")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83D\uDCCE",
                        fontSize = 28.sp
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            Log.d(TAG, "Sending message:  to ")
                            val receiverId = if (peerName == "Бот") "bot" else peerName
                            try {
                                viewModel.sendMessage(chatId, receiverId, messageText)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error sending message", e)
                            }
                            messageText = ""
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            state = listState,
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { message ->
                val isMe = message.senderId == currentUid
                MessageBubble(message = message, isMe = isMe)
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            
            // Если сообщений нет
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Нет сообщений. Напишите первым!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isMe: Boolean) {
    val context = LocalContext.current
    val bgColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val timeColor = if (isMe) contentColor.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = if (isMe) 12.dp else 2.dp,
                        bottomEnd = if (isMe) 2.dp else 12.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                if (message.hasFile && message.fileUrl.isNotBlank()) {
                    FileAttachmentBlock(
                        message = message,
                        bgColor = bgColor,
                        contentColor = contentColor,
                        timeColor = timeColor,
                        isMe = isMe,
                        context = context
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                    fontSize = 10.sp,
                    color = timeColor
                )
            }
        }
    }
}

@Composable
private fun FileAttachmentBlock(
    message: Message,
    bgColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    timeColor: androidx.compose.ui.graphics.Color,
    isMe: Boolean,
    context: android.content.Context
) {
    val fileIconColor = if (isMe) contentColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val mime = when (message.fileType) {
                        "image" -> "image/*"
                        "video" -> "video/*"
                        "audio" -> "audio/*"
                        else -> "*/*"
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(message.fileUrl), mime)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) { }
            },
        verticalAlignment = Alignment.Top
    ) {
        if (message.fileType == "image") {
            AsyncImage(
                model = message.fileUrl,
                contentDescription = message.fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(top = 4.dp)
                    .background(
                        color = fileIconColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (message.fileType) {
                        "video" -> "\uD83C\uDFA5"
                        "audio" -> "\uD83C\uDFB5"
                        "document" -> "\uD83D\uDCC4"
                        else -> "\uD83D\uDCCE"
                    },
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.fileName.ifBlank { "Файл" },
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(message.fileSize),
                    color = timeColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }
}
