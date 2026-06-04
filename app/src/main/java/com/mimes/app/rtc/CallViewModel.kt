package com.mimes.app.rtc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName

    init {
        RtcManager.initialize(application)
    }

    fun incomingCall(callerId: String, callId: String) {
        _peerName.value = callerId
        _callState.value = CallState.Ringing(callerId, callId)
    }

    fun callUser(userId: String) {
        _peerName.value = userId
        RtcManager.startCall(userId) { state ->
            _callState.value = state
        }
    }

    fun acceptCall(callId: String, callerId: String) {
        RtcManager.acceptCall(callId, callerId) { state ->
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

    override fun onCleared() {
        super.onCleared()
    }
}
