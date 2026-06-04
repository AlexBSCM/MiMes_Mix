package com.mimes.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimes.app.rtc.RtcManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (chatId: String, peerName: String) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        RtcManager.listenForIncomingCalls()
    }

    LaunchedEffect(searchText) {
        viewModel.searchUsers(searchText.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiMes") },
                actions = {
                    IconButton(onClick = {
                        searchText = ""
                        viewModel.toggleSearch()
                    }) {
                        Icon(
                            if (isSearching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (isSearching) "Закрыть поиск" else "Поиск"
                        )
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).windowInsetsPadding(WindowInsets.navigationBars)) {
            // Поисковая строка
            if (isSearching) {
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        placeholder = { Text("Поиск по @username...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Результаты поиска
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Результаты поиска",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(searchResults) { userId ->
                    SearchResultRow(
                        userId = userId,
                        onAdd = { viewModel.addContact(userId) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            // Список чатов
            items(chats) { chat ->
                ChatItemRow(chat = chat, onClick = { onChatClick(chat.id, chat.name) })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchResultRow(userId: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userId.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = userId,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Добавить")
        }
    }
}

@Composable
fun ChatItemRow(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (chat.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = chat.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
