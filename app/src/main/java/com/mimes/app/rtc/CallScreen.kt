package com.mimes.app.rtc

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CallScreen(
    peerName: String = "",
    isIncoming: Boolean = false,
    incomingCallId: String = "",
    viewModel: CallViewModel = viewModel(),
    onEndCall: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val displayPeerName by viewModel.peerName.collectAsState()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (isIncoming && incomingCallId.isNotBlank()) {
            viewModel.incomingCall(if (peerName.startsWith("@")) peerName else "@$peerName", incomingCallId)
        } else if (peerName.isNotBlank()) {
            viewModel.callUser(if (peerName.startsWith("@")) peerName else "@$peerName")
        }
    }

    LaunchedEffect(Unit) {
        RtcManager.incomingCallFlow.collect { (callerId, callId) ->
            if (callState is CallState.Idle) {
                viewModel.incomingCall(callerId, callId)
            }
        }
    }

    LaunchedEffect(callState) {
        if (callState is CallState.Ended) {
            onEndCall()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            Text(
                text = "MiMes",
                color = Color(0xFF6C63FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6C63FF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayPeerName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = displayPeerName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (callState) {
                    is CallState.Outgoing -> "Исходящий звонок..."
                    is CallState.Ringing -> "Входящий звонок"
                    is CallState.Connected -> "Соединено"
                    is CallState.Ended -> "Звонок завершён"
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.weight(0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (callState is CallState.Ringing) {
                    CallButton(
                        icon = "✕",
                        label = "Отклонить",
                        color = Color(0xFFE53935),
                        onClick = {
                            (callState as? CallState.Ringing)?.let {
                                viewModel.rejectCall(it.callId)
                            }
                        }
                    )
                    CallButton(
                        icon = "✓",
                        label = "Принять",
                        color = Color(0xFF4CAF50),
                        onClick = {
                            val state = callState as? CallState.Ringing ?: return@CallButton
                            viewModel.acceptCall(state.callId, state.callerId)
                        }
                    )
                } else if (callState is CallState.Outgoing || callState is CallState.Connected) {
                    CallButton(
                        icon = "✕",
                        label = "Завершить",
                        color = Color(0xFFE53935),
                        onClick = { viewModel.endCall() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CallButton(
    icon: String,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}
