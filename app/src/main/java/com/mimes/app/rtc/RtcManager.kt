package com.mimes.app.rtc

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mimes.app.ui.auth.Session
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
    private var localAudioTrack: AudioTrack? = null

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    var currentCallId: String? = null
    var currentPeerId: String? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private var pendingCandidates = mutableListOf<IceCandidate>()

    fun initialize(context: Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
    }

    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory?.createPeerConnection(config, observer)
    }

    fun startCall(receiverId: String, onStateChange: (CallState) -> Unit) {
        val callId = "call_${UUID.randomUUID()}"
        currentCallId = callId
        currentPeerId = receiverId
        onStateChange(CallState.Outgoing(callId, receiverId))

        val callData = hashMapOf(
            "callerId" to Session.currentUserId,
            "receiverId" to receiverId,
            "status" to "ringing",
            "type" to "audio",
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
            override fun onAddTrack(track: RtpReceiver, streams: Array<out MediaStream>) {}
        }

        peerConnection?.close()
        peerConnection = createPeerConnection(observer)?.apply {
            audioTrack?.let { addTrack(it) }
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

    fun acceptCall(callId: String, callerId: String, onStateChange: (CallState) -> Unit) {
        currentCallId = callId
        currentPeerId = callerId
        onStateChange(CallState.Connected(callId, callerId))
        db.collection("calls").document(callId).update("status", "connected")

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    onStateChange(CallState.Connected(callId, callerId))
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
            override fun onAddTrack(track: RtpReceiver, streams: Array<out MediaStream>) {}
        }

        val pc = createPeerConnection(observer)
        peerConnection?.close()
        peerConnection = pc
        audioTrack?.let { pc?.addTrack(it) }

        db.collection("calls").document(callId).collection("offer").document("offer").get()
            .addOnSuccessListener { snap ->
                val sdp = snap.getString("sdp") ?: return@addOnSuccessListener
                val sd = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pc?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
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
        db.collection("calls").document(callId).update("status", "ended")
        endCall(callId)
    }

    fun endCall(callId: String, onStateChange: ((CallState) -> Unit)? = null) {
        db.collection("calls").document(callId).update("status", "ended")
        peerConnection?.close()
        peerConnection = null
        listenerRegistration?.remove()
        listenerRegistration = null
        currentCallId = null
        currentPeerId = null
        pendingCandidates.clear()
        onStateChange?.invoke(CallState.Ended())
    }

    fun listenForIncomingCalls(onRinging: (String, String) -> Unit) {
        val userId = Session.currentUserId
        if (userId.isBlank()) return

        listenerRegistration?.remove()
        listenerRegistration = db.collection("calls")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ ->
                snap?.documents?.forEach { doc ->
                    val callerId = doc.getString("callerId") ?: return@forEach
                    val callId = doc.id
                    if (callerId != Session.currentUserId) {
                        onRinging(callerId, callId)
                    }
                }
            }
    }

    private fun listenForAnswer(callId: String, onStateChange: (CallState) -> Unit) {
        db.collection("calls").document(callId).collection("answer").document("answer")
            .addSnapshotListener { snap, _ ->
                val sdp = snap?.getString("sdp") ?: return@addSnapshotListener
                val sd = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        listenForCandidates(callId, Session.currentUserId)
                    }
                    override fun onSetFailure(msg: String) {}
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(msg: String) {}
                }, sd)
            }
    }

    private fun listenForCandidates(callId: String, myId: String) {
        db.collection("calls").document(callId).collection("candidates")
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
        peerConnection?.close()
        peerConnection = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
