package com.firechat.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.firechat.app.data.model.User
import com.firechat.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

    override fun signInAnonymously(nickname: String): Flow<Result<String>> = flow {
        val result = auth.signInAnonymously().await()
        val user = result.user ?: throw Exception("Не удалось авторизоваться")

        val fireChatUser = User(
            id = user.uid,
            nickname = nickname,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )

        firestore.collection("users").document(user.uid).set(fireChatUser).await()

        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
        }

        emit(Result.success(user.uid))
    }.catch { e ->
        emit(Result.failure(e))
    }

    override fun checkSession(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[IS_LOGGED_IN] ?: false
        }
    }

    override fun updateOnlineStatus(isOnline: Boolean): Flow<Result<Unit>> = flow {
        val uid = auth.currentUser?.uid ?: throw Exception("Пользователь не авторизован")
        firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                )
            ).await()
        emit(Result.success(Unit))
    }.catch { e ->
        emit(Result.failure(e))
    }

    override fun signOut(): Flow<Result<Unit>> = flow {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try {
                firestore.collection("users").document(uid)
                    .update(
                        mapOf(
                            "isOnline" to false,
                            "lastSeen" to System.currentTimeMillis()
                        )
                    ).await()
            } catch (ignored: Exception) {}
        }
        auth.signOut()

        context.dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = false
        }

        emit(Result.success(Unit))
    }.catch { e ->
        emit(Result.failure(e))
    }
}
