package com.mimes.app.rtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mimes.app.ui.auth.Session
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import java.util.UUID

data class CallInfo(
    val callId: String,
    val callerId: String,
    val receiverId: String,
    val status: String
)

sealed class CallState {
    object Idle : CallState()
    data class Ringing(val callerId: String, val callId: String) : CallState()
    data class Outgoing(val callId: String, val receiverId: String) : CallState()
    data class Connected(val callId: String, val peerId: String) : CallState()
    data class Ended(val reason: String = "") : CallState()
}

object RtcManager {
    private const val TAG = "RtcManager"
    private val db = FirebaseFirestore.getInstance()

    private var peerConnection: PeerConnection? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    var videoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var eglBase: EglBase? = null

    var isVideoCall = false
    private var remoteVideoTrack: VideoTrack? = null
    private var localRenderSink: SurfaceViewRenderer? = null
    private var remoteRenderSink: SurfaceViewRenderer? = null
    private var audioManager: AudioManager? = null

    private fun setAudioRoute(context: Context, speakerOn: Boolean) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = speakerOn
    }

    private fun resetAudioRoute() {
        audioManager?.isSpeakerphoneOn = false
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager = null
    }

    fun toggleSpeaker(): Boolean {
        audioManager?.let { it.isSpeakerphoneOn = !it.isSpeakerphoneOn }
        return audioManager?.isSpeakerphoneOn ?: false
    }

    fun setLocalSink(renderer: SurfaceViewRenderer) {
        localRenderSink?.let { videoTrack?.removeSink(it) }
        localRenderSink = renderer
        videoTrack?.addSink(renderer)
    }

    fun setRemoteSink(renderer: SurfaceViewRenderer) {
        remoteRenderSink?.let { remoteVideoTrack?.removeSink(it) }
        remoteRenderSink = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    fun createRenderer(context: Context, mirror: Boolean): SurfaceViewRenderer {
        val renderer = SurfaceViewRenderer(context)
        renderer.init(eglBase?.eglBaseContext, null)
        renderer.setMirror(mirror)
        renderer.setEnableHardwareScaler(true)
        return renderer
    }

    private var incomingCallListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var answerListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var candidateListener: com.google.firebase.firestore.ListenerRegistration? = null

    var currentCallId: String? = null
    var currentPeerId: String? = null

    private val _incomingCallFlow = kotlinx.coroutines.flow.MutableSharedFlow<Triple<String, String, Boolean>>(replay = 1)
    val incomingCallFlow: kotlinx.coroutines.flow.SharedFlow<Triple<String, String, Boolean>> = _incomingCallFlow

    private val _callCancelledFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1)
    val callCancelledFlow: kotlinx.coroutines.flow.SharedFlow<String> = _callCancelledFlow

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private var pendingCandidates = mutableListOf<IceCandidate>()
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
    }

    fun createVideoCapturer(context: Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        // fallback to first available
        for (name in deviceNames) {
            return enumerator.createCapturer(name, null)
        }
        return null
    }

    fun startVideo(context: Context): Boolean {
        if (videoSource != null) return true
        if (eglBase == null) return false
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        videoSource = peerConnectionFactory?.createVideoSource(false) ?: return false
        videoCapturer = createVideoCapturer(context) ?: return false
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext),
            context,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(1280, 720, 30)
        videoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
        return videoTrack != null
    }

    fun stopVideo() {
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        remoteVideoTrack = null
        localRenderSink = null
        remoteRenderSink = null
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    private val handledCallIds = mutableSetOf<String>()
    private val activeIncomingCalls = mutableSetOf<String>()
    private val acceptedCallIds = mutableSetOf<String>()

    suspend fun listenForIncomingCalls() {
        incomingCallListener?.remove()
        val myId = Session.currentUserId
        Log.d(TAG, "listenForIncomingCalls: userId=$myId")
        if (myId.isBlank()) return

        try {
            // Delete ALL stale "ringing" calls before setting up the listener
            val stale = db.collection("calls")
                .whereEqualTo("status", "ringing")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
            Log.d(TAG, "Stale ringing docs found: ${stale.documents.size}")
            stale.documents.forEach { doc ->
                val r = doc.getString("receiverId")
                val c = doc.getString("callerId")
                val id = doc.id
                Log.d(TAG, "  doc: id=$id caller=$c receiver=$r")
                if (r == myId || c == myId) {
                    doc.reference.delete().await()
                    Log.d(TAG, "  -> deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stale call cleanup failed", e)
        }

        Log.d(TAG, "Setting up incoming call listener")
        setupIncomingCallListener(myId)
    }

    private fun setupIncomingCallListener(myId: String) {
        incomingCallListener = db.collection("calls")
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ ->
                if (myId.isBlank()) return@addSnapshotListener

                val currentMatchingIds = mutableSetOf<String>()

                snap?.documents?.forEach { doc ->
                    val receiverId = doc.getString("receiverId") ?: return@forEach
                    if (receiverId != myId) return@forEach
                    val callId = doc.id
                    currentMatchingIds.add(callId)

                    val callerId = doc.getString("callerId") ?: return@forEach
                    val isVideo = doc.getString("type") == "video"
                    if (callerId != myId && callId !in handledCallIds) {
                        handledCallIds.add(callId)
                        _incomingCallFlow.tryEmit(Triple(callerId, callId, isVideo))
                    }
                }

                // Detect cancellations: call was active but no longer in snapshot (and not accepted)
                activeIncomingCalls.removeAll { id ->
                    if (id !in currentMatchingIds && id !in acceptedCallIds) {
                        _callCancelledFlow.tryEmit(id)
                        true
                    } else false
                }
                activeIncomingCalls.addAll(currentMatchingIds)
            }
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory?.createPeerConnection(config, observer)
    }

    fun startCall(receiverId: String, isVideo: Boolean = false, context: Context? = null, onStateChange: (CallState) -> Unit) {
        val callId = "call_${UUID.randomUUID()}"
        currentCallId = callId
        currentPeerId = receiverId
        this.isVideoCall = isVideo
        onStateChange(CallState.Outgoing(callId, receiverId))
        if (context != null) setAudioRoute(context, isVideo)

        val callData = hashMapOf(
            "callerId" to Session.currentUserId,
            "receiverId" to receiverId,
            "status" to "ringing",
            "type" to if (isVideo) "video" else "audio",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("calls").document(callId).set(callData)

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    onStateChange(CallState.Connected(callId, receiverId))
                    db.collection("calls").document(callId).update("status", "connected")
                }
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    endCall(callId, onStateChange)
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                db.collection("calls").document(callId).collection("candidates")
                    .add(hashMapOf(
                        "candidate" to candidate.sdp,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "from" to Session.currentUserId
                    ))
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(track: RtpReceiver, streams: Array<out MediaStream>) {
                val remoteTrack = track.track() as? VideoTrack
                if (remoteTrack != null) {
                    Log.d(TAG, "Remote video track received")
                    remoteVideoTrack = remoteTrack
                }
            }
        }

        peerConnection?.close()
        peerConnection = createPeerConnection(observer)?.apply {
            audioTrack?.let { addTrack(it) }
            if (isVideoCall && context != null && startVideo(context)) {
                videoTrack?.let { addTrack(it) }
            }
            createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            db.collection("calls").document(callId).collection("offer")
                                .document("offer").set(hashMapOf("sdp" to sdp.description, "type" to sdp.type.canonicalForm()))
                        }
                        override fun onSetFailure(msg: String) { Log.e(TAG, "setLocalDescription error: $msg") }
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onCreateFailure(msg: String) {}
                    }, sdp)
                }
                override fun onCreateFailure(msg: String) { Log.e(TAG, "createOffer error: $msg") }
                override fun onSetSuccess() {}
                override fun onSetFailure(msg: String) {}
            }, MediaConstraints())
        }

        listenForAnswer(callId, onStateChange)
    }

    fun acceptCall(callId: String, callerId: String, isVideo: Boolean = false, context: Context? = null, onStateChange: (CallState) -> Unit) {
        currentCallId = callId
        currentPeerId = callerId
        this.isVideoCall = isVideo
        acceptedCallIds.add(callId)
        if (context != null) setAudioRoute(context, isVideo)
        db.collection("calls").document(callId).update("status", "accepted")

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    onStateChange(CallState.Connected(callId, callerId))
                    db.collection("calls").document(callId).update("status", "connected")
                }
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    endCall(callId, onStateChange)
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                db.collection("calls").document(callId).collection("candidates")
                    .add(hashMapOf(
                        "candidate" to candidate.sdp,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "from" to Session.currentUserId
                    ))
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(track: RtpReceiver, streams: Array<out MediaStream>) {
                val remoteTrack = track.track() as? VideoTrack
                if (remoteTrack != null) {
                    Log.d(TAG, "Remote video track received")
                    remoteVideoTrack = remoteTrack
                }
            }
        }

        val pc = createPeerConnection(observer)
        peerConnection?.close()
        peerConnection = pc
        audioTrack?.let { pc?.addTrack(it) }
        val videoStarted = isVideoCall && context != null && startVideo(context)
        if (videoStarted) {
            videoTrack?.let { pc?.addTrack(it) }
        }

        db.collection("calls").document(callId).collection("offer").document("offer").get()
            .addOnSuccessListener { snap ->
                val sdp = snap.getString("sdp") ?: return@addOnSuccessListener
                val sd = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pc?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        addPendingCandidates()
                        pc?.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(sdp: SessionDescription) {
                                pc?.setLocalDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        db.collection("calls").document(callId).collection("answer")
                                            .document("answer").set(hashMapOf("sdp" to sdp.description, "type" to sdp.type.canonicalForm()))
                                    }
                                    override fun onSetFailure(msg: String) {}
                                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                                    override fun onCreateFailure(msg: String) {}
                                }, sdp)
                            }
                            override fun onCreateFailure(msg: String) { Log.e(TAG, "createAnswer error: $msg") }
                            override fun onSetSuccess() {}
                            override fun onSetFailure(msg: String) {}
                        }, MediaConstraints())
                    }
                    override fun onSetFailure(msg: String) { Log.e(TAG, "setRemoteDescription error: $msg") }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(msg: String) {}
                }, sd)
            }

        listenForCandidates(callId, Session.currentUserId)
    }

    fun rejectCall(callId: String) {
        db.collection("calls").document(callId).delete()
        endCall(callId)
    }

    fun endCall(callId: String, onStateChange: ((CallState) -> Unit)? = null) {
        // Track missed calls before deleting
        db.collection("calls").document(callId).get().addOnSuccessListener { doc ->
            val status = doc.getString("status")
            val receiverId = doc.getString("receiverId")
            val callerId = doc.getString("callerId")
            if (status == "ringing" && callerId != null) {
                if (callerId == Session.currentUserId && receiverId != null && receiverId != Session.currentUserId) {
                    // Caller cancelled → receiver missed the call
                    db.collection("users").document(receiverId)
                        .update("missedCalls.$callerId", com.google.firebase.firestore.FieldValue.increment(1))
                } else if (receiverId == Session.currentUserId && callerId != Session.currentUserId) {
                    // Receiver rejected intentionally → don't count as missed
                }
            }
        }
        resetAudioRoute()
        db.collection("calls").document(callId).delete()
        handledCallIds.add(callId)
        stopVideo()
        peerConnection?.close()
        peerConnection = null
        answerListener?.remove()
        answerListener = null
        candidateListener?.remove()
        candidateListener = null
        currentCallId = null
        currentPeerId = null
        pendingCandidates.clear()
        isVideoCall = false
        activeIncomingCalls.clear()
        acceptedCallIds.remove(callId)
        onStateChange?.invoke(CallState.Ended())
    }

    private fun listenForAnswer(callId: String, onStateChange: (CallState) -> Unit) {
        answerListener?.remove()
        answerListener = db.collection("calls").document(callId).collection("answer").document("answer")
            .addSnapshotListener { snap, _ ->
                val sdp = snap?.getString("sdp") ?: return@addSnapshotListener
                val sd = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        addPendingCandidates()
                        listenForCandidates(callId, Session.currentUserId)
                    }
                    override fun onSetFailure(msg: String) {}
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(msg: String) {}
                }, sd)
            }
    }

    private fun listenForCandidates(callId: String, myId: String) {
        candidateListener?.remove()
        candidateListener = db.collection("calls").document(callId).collection("candidates")
            .whereNotEqualTo("from", myId)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val candidate = IceCandidate(
                            data["sdpMid"] as? String ?: "",
                            (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0,
                            data["candidate"] as? String ?: ""
                        )
                        if (peerConnection?.remoteDescription != null) {
                            peerConnection?.addIceCandidate(candidate)
                        } else {
                            pendingCandidates.add(candidate)
                        }
                    }
                }
            }
    }

    fun addPendingCandidates() {
        pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    fun release() {
        currentCallId?.let { endCall(it) }
        stopVideo()
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        eglBase?.release()
        eglBase = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        incomingCallListener?.remove()
        incomingCallListener = null
        initialized = false
        activeIncomingCalls.clear()
        acceptedCallIds.clear()
    }
}
