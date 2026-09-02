package com.mimes.app.rtc

import android.Manifest
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    peerName: String = "",
    isIncoming: Boolean = false,
    incomingCallId: String = "",
    isVideo: Boolean = false,
    autoAccept: Boolean = false,
    viewModel: CallViewModel = hiltViewModel(),
    onEndCall: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val displayPeerName by viewModel.peerName.collectAsState()
    val isVideoCall by viewModel.isVideo.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasCamPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        hasMicPermission = perms[Manifest.permission.RECORD_AUDIO] ?: false
        hasCamPermission = perms[Manifest.permission.CAMERA] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission || (isVideoCall && !hasCamPermission)) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (isVideoCall && !hasCamPermission) perms.add(Manifest.permission.CAMERA)
            permLauncher.launch(perms.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        if (isIncoming && incomingCallId.isNotBlank()) {
            viewModel.incomingCall(
                if (peerName.startsWith("@")) peerName else "@$peerName",
                incomingCallId,
                isVideo,
                autoAccept
            )
        } else if (peerName.isNotBlank()) {
            viewModel.callUser(if (peerName.startsWith("@")) peerName else "@$peerName", isVideo)
        }
    }

    LaunchedEffect(callState) {
        if (callState is CallState.Ended) {
            onEndCall()
        }
    }

    // Setup video renderers when video call starts
    if (isVideoCall && (callState is CallState.Connected || callState is CallState.Outgoing)) {
        LaunchedEffect(Unit) {
            if (RtcManager.localRenderer == null) {
                RtcManager.localRenderer = SurfaceViewRenderer(context)
                RtcManager.localRenderer?.setMirror(true)
                RtcManager.localRenderer?.setEnableHardwareScaler(true)
                RtcManager.videoTrack?.addSink(RtcManager.localRenderer)
            }
        }
    }

    if (isVideoCall && callState is CallState.Connected) {
        LaunchedEffect(Unit) {
            if (RtcManager.remoteRenderer == null) {
                RtcManager.remoteRenderer = SurfaceViewRenderer(context)
                RtcManager.remoteRenderer?.setMirror(false)
                RtcManager.remoteRenderer?.setEnableHardwareScaler(true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Video call connected — show remote video + local preview
            isVideoCall && callState is CallState.Connected -> {
                VideoCallContent(
                    peerName = displayPeerName,
                    isCameraOn = isCameraOn,
                    onToggleCamera = { viewModel.toggleCamera() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onEndCall = { viewModel.endCall(); onEndCall() }
                )
            }
            // Video call outgoing — show local preview
            isVideoCall && callState is CallState.Outgoing -> {
                VideoCallContent(
                    peerName = displayPeerName,
                    isCameraOn = isCameraOn,
                    onToggleCamera = { viewModel.toggleCamera() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onEndCall = { viewModel.endCall(); onEndCall() }
                )
            }
            // Incoming call (audio or video)
            callState is CallState.Ringing && isIncoming -> {
                IncomingCallContent(
                    displayPeerName = displayPeerName,
                    isVideo = isVideoCall,
                    onReject = {
                        (callState as? CallState.Ringing)?.let { viewModel.rejectCall(it.callId) }
                    },
                    onAccept = {
                        val state = callState as? CallState.Ringing ?: return@IncomingCallContent
                        viewModel.acceptCall(state.callId, state.callerId)
                    }
                )
            }
            // Audio call connected
            callState is CallState.Connected -> {
                AudioCallContent(
                    displayPeerName = displayPeerName,
                    onEndCall = { viewModel.endCall(); onEndCall() }
                )
            }
            // Audio call outgoing
            callState is CallState.Outgoing -> {
                OutgoingCallContent(displayPeerName = displayPeerName)
            }
            // Default
            else -> {
                OutgoingCallContent(displayPeerName = displayPeerName)
            }
        }
    }
}

@Composable
private fun VideoCallContent(
    peerName: String,
    isCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Remote video (full screen)
        val remoteRenderer = RtcManager.remoteRenderer
        if (remoteRenderer != null) {
            AndroidView(
                factory = { remoteRenderer.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Local preview (small, top-right corner)
        val localRenderer = RtcManager.localRenderer
        if (localRenderer != null) {
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        // Peer name overlay
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(peerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Соединено", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallButton(
                icon = if (isCameraOn) "📷" else "📷",
                label = if (isCameraOn) "Выкл камеру" else "Вкл камеру",
                color = if (isCameraOn) Color(0xFF6C63FF) else Color(0xFF555555),
                onClick = onToggleCamera
            )
            CallButton(
                icon = "🔄",
                label = "Сменить",
                color = Color(0xFF6C63FF),
                onClick = onSwitchCamera
            )
            CallButton(
                icon = "✕",
                label = "Завершить",
                color = Color(0xFFE53935),
                onClick = onEndCall
            )
        }
    }
}

@Composable
private fun IncomingCallContent(
    displayPeerName: String,
    isVideo: Boolean,
    onReject: () -> Unit,
    onAccept: () -> Unit
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
            text = if (isVideo) "Входящий видеозвонок" else "Входящий звонок",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallButton(
                icon = "✕",
                label = "Отклонить",
                color = Color(0xFFE53935),
                onClick = onReject
            )
            CallButton(
                icon = "✓",
                label = "Принять",
                color = Color(0xFF4CAF50),
                onClick = onAccept
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AudioCallContent(
    displayPeerName: String,
    onEndCall: () -> Unit
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
            text = "Соединено",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(0.4f))

        CallButton(
            icon = "✕",
            label = "Завершить",
            color = Color(0xFFE53935),
            onClick = onEndCall
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun OutgoingCallContent(displayPeerName: String) {
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
            text = "Исходящий звонок...",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.weight(0.4f))

        CallButton(
            icon = "✕",
            label = "Отмена",
            color = Color(0xFFE53935),
            onClick = { /* ViewModel handles endCall from callState Ended */ }
        )

        Spacer(modifier = Modifier.height(32.dp))
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
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
