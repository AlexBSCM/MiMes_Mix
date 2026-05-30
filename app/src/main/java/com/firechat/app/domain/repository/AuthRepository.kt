package com.firechat.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun signInAnonymously(nickname: String): Flow<Result<String>>
    fun checkSession(): Flow<Boolean>
    fun updateOnlineStatus(isOnline: Boolean): Flow<Result<Unit>>
    fun signOut(): Flow<Result<Unit>>
}
