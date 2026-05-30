package com.firechat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CallScreen(
    chatId: String,
    peerName: String,
    callType: String,
    isIncoming: Boolean,
    onCallEnd: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    
    // Background can be dark for call screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peerName.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = peerName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isIncoming) "Входящий $callType вызов..." else "Разговор: 00:15",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Call Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.White else Color.DarkGray)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.Clear else Icons.Default.Call,
                        contentDescription = "Микрофон",
                        tint = if (isMuted) Color.Black else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // End Call
                IconButton(
                    onClick = onCallEnd,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Завершить",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // Speaker
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Динамик",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
