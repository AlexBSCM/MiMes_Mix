package com.mimes.app.rtc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName

    private val _isVideo = MutableStateFlow(false)
    val isVideo: StateFlow<Boolean> = _isVideo

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn

    init {
        RtcManager.initialize(application)
    }

    fun incomingCall(callerId: String, callId: String, video: Boolean = false) {
        _peerName.value = callerId
        _isVideo.value = video
        _callState.value = CallState.Ringing(callerId, callId)
    }

    fun callUser(userId: String, video: Boolean = false) {
        _peerName.value = userId
        _isVideo.value = video
        RtcManager.startCall(userId, video) { state ->
            _callState.value = state
        }
    }

    fun acceptCall(callId: String, callerId: String) {
        val ctx = getApplication<Application>()
        RtcManager.acceptCall(callId, callerId, _isVideo.value, ctx) { state ->
            _callState.value = state
        }
    }

    fun rejectCall(callId: String) {
        RtcManager.rejectCall(callId)
        _callState.value = CallState.Ended()
    }

    fun endCall() {
        val callId = RtcManager.currentCallId ?: return
        RtcManager.endCall(callId) { state ->
            _callState.value = state
        }
    }

    fun toggleCamera() {
        val newState = !_isCameraOn.value
        _isCameraOn.value = newState
        RtcManager.videoTrack?.setEnabled(newState)
    }

    fun switchCamera() {
        RtcManager.switchCamera()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
