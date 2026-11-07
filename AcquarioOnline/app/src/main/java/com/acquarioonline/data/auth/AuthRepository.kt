package com.acquarioonline.data.auth

import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import com.acquarioonline.data.local.User
import com.acquarioonline.data.local.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?
)

class EmailAlreadyInUseException : Exception()
class InvalidCredentialsException : Exception()

/**
 * Profili e sessione salvati sul dispositivo (nessun server).
 * Gli utenti stanno in Room, la sessione corrente in SharedPreferences.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val prefs: SharedPreferences
) {
    private val _currentUser = MutableStateFlow(readSession())

    /** Utente corrente (null = nessuna sessione). Emette subito il valore iniziale. */
    val currentUser: Flow<UserProfile?> = _currentUser.asStateFlow()

    fun currentUserSnapshot(): UserProfile? = _currentUser.value

    suspend fun signUp(name: String, email: String, password: String) {
        val normalizedEmail = normalize(email)
        withContext(Dispatchers.Default) {
            if (userDao.findByEmail(normalizedEmail) != null) throw EmailAlreadyInUseException()

            val salt = PasswordHasher.newSalt()
            val hash = PasswordHasher.hash(password, salt)
            val id = try {
                userDao.insert(
                    User(
                        name = name,
                        email = normalizedEmail,
                        passwordHash = PasswordHasher.encode(hash),
                        passwordSalt = PasswordHasher.encode(salt)
                    )
                )
            } catch (e: SQLiteConstraintException) {
                throw EmailAlreadyInUseException()
            }
            startSession(UserProfile(id.toString(), name, normalizedEmail))
        }
    }

    suspend fun signIn(email: String, password: String) {
        val normalizedEmail = normalize(email)
        withContext(Dispatchers.Default) {
            val user = userDao.findByEmail(normalizedEmail) ?: throw InvalidCredentialsException()
            val valid = PasswordHasher.verify(
                password = password,
                salt = PasswordHasher.decode(user.passwordSalt),
                expectedHash = PasswordHasher.decode(user.passwordHash)
            )
            if (!valid) throw InvalidCredentialsException()
            startSession(UserProfile(user.id.toString(), user.name, user.email))
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    private fun startSession(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_UID, profile.uid)
            .putString(KEY_NAME, profile.displayName)
            .putString(KEY_EMAIL, profile.email)
            .apply()
        _currentUser.value = profile
    }

    private fun readSession(): UserProfile? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return UserProfile(uid, prefs.getString(KEY_NAME, null), prefs.getString(KEY_EMAIL, null))
    }

    private fun normalize(email: String) = email.trim().lowercase()

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
    }
}
