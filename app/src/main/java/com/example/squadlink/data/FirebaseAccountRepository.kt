package com.example.squadlink.data

import com.example.squadlink.model.AccountRole
import com.example.squadlink.model.UserAccountProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirebaseAccountRepository(
    private val preferencesRepository: UserPreferencesRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_ROLE = "role"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
    }

    suspend fun restoreSession(): UserAccountProfile? {
        val currentUser = auth.currentUser ?: run {
            preferencesRepository.clearSession()
            return null
        }

        val profile = fetchOrCreateProfile(currentUser)
        syncLocalProfile(profile, resetGameCode = false)
        return profile
    }

    suspend fun signIn(email: String, password: String): UserAccountProfile {
        val authResult = auth
            .signInWithEmailAndPassword(email.trim(), password)
            .awaitResult()
        val firebaseUser = authResult.user ?: error("No se pudo recuperar el usuario autenticado.")
        val profile = fetchOrCreateProfile(firebaseUser)
        syncLocalProfile(profile, resetGameCode = true)
        return profile
    }

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        role: AccountRole
    ): UserAccountProfile {
        val normalizedEmail = email.trim()
        val normalizedName = displayName.trim()
        val authResult = auth
            .createUserWithEmailAndPassword(normalizedEmail, password)
            .awaitResult()
        val firebaseUser = authResult.user ?: error("No se pudo crear la cuenta.")
        val profile = UserAccountProfile(
            uid = firebaseUser.uid,
            displayName = normalizedName,
            email = normalizedEmail,
            role = role
        )
        saveProfile(profile, isNewUser = true)
        syncLocalProfile(profile, resetGameCode = true)
        return profile
    }

    suspend fun signOut() {
        auth.signOut()
        preferencesRepository.clearSession()
    }

    private suspend fun fetchOrCreateProfile(firebaseUser: FirebaseUser): UserAccountProfile {
        val snapshot = firestore
            .collection(USERS_COLLECTION)
            .document(firebaseUser.uid)
            .get()
            .awaitResult()

        val profile = if (snapshot.exists()) {
            UserAccountProfile(
                uid = firebaseUser.uid,
                displayName = snapshot.getString(FIELD_DISPLAY_NAME)
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackDisplayName(firebaseUser),
                email = snapshot.getString(FIELD_EMAIL)
                    ?.takeIf { it.isNotBlank() }
                    ?: (firebaseUser.email ?: ""),
                role = AccountRole.fromWireValue(snapshot.getString(FIELD_ROLE))
            )
        } else {
            UserAccountProfile(
                uid = firebaseUser.uid,
                displayName = fallbackDisplayName(firebaseUser),
                email = firebaseUser.email ?: "",
                role = AccountRole.PLAYER
            )
        }

        saveProfile(profile, isNewUser = !snapshot.exists())
        return profile
    }

    private suspend fun saveProfile(
        profile: UserAccountProfile,
        isNewUser: Boolean
    ) {
        val payload = hashMapOf<String, Any>(
            FIELD_DISPLAY_NAME to profile.displayName,
            FIELD_EMAIL to profile.email,
            FIELD_ROLE to profile.role.wireValue,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        if (isNewUser) {
            payload[FIELD_CREATED_AT] = FieldValue.serverTimestamp()
        }

        firestore
            .collection(USERS_COLLECTION)
            .document(profile.uid)
            .set(payload, SetOptions.merge())
            .awaitResult()
    }

    private suspend fun syncLocalProfile(
        profile: UserAccountProfile,
        resetGameCode: Boolean
    ) {
        preferencesRepository.setActiveUserName(profile.displayName)
        preferencesRepository.setActiveUserEmail(profile.email)
        preferencesRepository.setPlayerName(profile.displayName)
        preferencesRepository.setIsGameMaster(profile.role == AccountRole.GAME_MASTER)
        if (resetGameCode) {
            preferencesRepository.clearActiveGameCode()
        }
    }

    private fun fallbackDisplayName(firebaseUser: FirebaseUser): String {
        return firebaseUser.displayName
            ?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Operador"
    }
}
