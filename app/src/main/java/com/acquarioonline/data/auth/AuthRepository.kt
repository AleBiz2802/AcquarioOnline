package com.acquarioonline.data.auth

import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import com.acquarioonline.data.local.User
import com.acquarioonline.data.local.userDatas
import com.acquarioonline.data.local.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val password: String?
)

class EmailAlreadyInUseException : Exception()
class InvalidCredentialsException : Exception()

class UserNotKnownException : Exception()

class AuthRepository(
    private val userDao: UserDao,
    private val prefs: SharedPreferences
) {
    private val _currentUser = MutableStateFlow(readSession())
    val currentUser: Flow<UserProfile?> = _currentUser.asStateFlow()

    fun currentUserSnapshot(): UserProfile? = _currentUser.value

    suspend fun signUp(name: String, email: String, password: String) {
        val normalizedEmail = normalize(email)

        withContext(Dispatchers.Default) {
            if (userDao.findByEmail(normalizedEmail) != null) throw EmailAlreadyInUseException()

            val salt = PasswordHasher.newSalt()
            val hash = PasswordHasher.hash(password, salt)
            try {
                userDao.insert(
                    User(
                        email = normalizedEmail,
                        passwordHash = PasswordHasher.encode(hash),
                        passwordSalt = PasswordHasher.encode(salt),
                        userDatas = userDatas(name,password)
                    )
                )
            } catch (e: SQLiteConstraintException) {
                throw EmailAlreadyInUseException()
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        val normalizedEmail = normalize(email)
        withContext(Dispatchers.Default) {
            val user = userDao.findByEmail(normalizedEmail) ?: throw UserNotKnownException()
            val valid = PasswordHasher.verify(
                password = password,
                salt = PasswordHasher.decode(user.passwordSalt),
                expectedHash = PasswordHasher.decode(user.passwordHash)
            )
            if (!valid) throw InvalidCredentialsException()
            startSession(UserProfile(user.id.toString(), user.userDatas.name, user.email, user.userDatas.password))
        }
    }

    suspend fun changeHashedPassword(email:String, newPassword: String){
        val normalizedEmail = normalize(email)
        withContext(Dispatchers.Default){
            val user = userDao.findByEmail(normalizedEmail)?:throw InvalidCredentialsException()
            val userdata = user.userDatas
            userdata.password=newPassword
            val salt = PasswordHasher.newSalt()
            val hash = PasswordHasher.hash(newPassword,salt)
            val updateUserCredential = user.copy(
                passwordHash = PasswordHasher.encode(hash),
                passwordSalt = PasswordHasher.encode(salt)
            )
            userDao.insert(updateUserCredential)
            startSession(UserProfile(user.id.toString(), user.userDatas.name, user.email, user.userDatas.password))

        }
    }
    suspend fun changeUserName(email:String, newName:String){
        val normaizedEmail = normalize(email)
        withContext(Dispatchers.Default){
            val user = userDao.findByEmail(normaizedEmail)?:throw InvalidCredentialsException()
            val userdata = user.userDatas
            userdata.name=newName
            val updated = user.copy(
                userDatas = userdata
            )
            userDao.insert(updated)
            startSession(UserProfile(user.id.toString(), newName, user.email, user.userDatas.password))

        }
    }

    suspend fun changeUserEmail(email: String, newEmail:String){
        val normalizedEmail = normalize(email)
        val newEmailNormalized = normalize(newEmail)
        withContext(Dispatchers.Default){
            val user = userDao.findByEmail(normalizedEmail)?:throw InvalidCredentialsException()
            val updated = user.copy(
                email = if(userDao.findByEmail(newEmailNormalized)==null) newEmailNormalized else throw EmailAlreadyInUseException()
            )
            userDao.insert(updated)
            startSession(UserProfile(user.id.toString(), user.userDatas.name, newEmailNormalized,user.userDatas.password))

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
            .putString(KEY_PASSWORD, profile.password)
            .apply()
        _currentUser.value = profile
    }

    private fun readSession(): UserProfile? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return UserProfile(uid, prefs.getString(KEY_NAME, null), prefs.getString(KEY_EMAIL, null),prefs.getString(KEY_PASSWORD,null))
    }

    private fun normalize(email: String) = email.trim().lowercase()

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}
