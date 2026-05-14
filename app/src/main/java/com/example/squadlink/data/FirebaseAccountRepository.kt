package com.example.squadlink.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.example.squadlink.model.AccountRole
import com.example.squadlink.model.SquadMemberProfile
import com.example.squadlink.model.SquadRole
import com.example.squadlink.model.SquadSummary
import com.example.squadlink.model.UserAccountProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseAccountRepository(
    private val preferencesRepository: UserPreferencesRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val SQUADS_COLLECTION = "squads"

        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_ROLE = "role"
        private const val FIELD_CALLSIGN = "callsign"
        private const val FIELD_PROFILE_PHOTO_URI = "profilePhotoUri"
        private const val FIELD_SQUAD_ID = "squadId"
        private const val FIELD_SQUAD_NAME = "squadName"
        private const val FIELD_SQUAD_CODE = "squadCode"
        private const val FIELD_SQUAD_ROLE = "squadRole"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_JOIN_CODE = "joinCode"
        private const val FIELD_CREATED_BY = "createdBy"
        private const val FIELD_PHOTO_URL = "photoUrl"
        private const val FIELD_FCM_TOKEN = "fcmToken"
        private const val PROFILE_PHOTO_MAX_SIZE = 256
        private const val PROFILE_PHOTO_MAX_BYTES = 700_000
    }

    suspend fun restoreSession(): UserAccountProfile? {
        val currentUser = auth.currentUser ?: run {
            preferencesRepository.clearSession()
            return null
        }

        val profile = fetchOrCreateProfile(currentUser)
        syncCurrentFcmToken()
        syncLocalProfile(profile, resetGameCode = false)
        return profile
    }

    suspend fun signIn(email: String, password: String): UserAccountProfile {
        val authResult = auth
            .signInWithEmailAndPassword(email.trim(), password)
            .awaitResult()
        val firebaseUser = authResult.user ?: error("No se pudo recuperar el usuario autenticado.")
        val profile = fetchOrCreateProfile(firebaseUser)
        syncCurrentFcmToken()
        syncLocalProfile(profile, resetGameCode = true)
        return profile
    }

    suspend fun register(
        displayName: String,
        email: String,
        password: String
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
            role = AccountRole.PLAYER,
            callsign = normalizedName,
            profilePhotoUri = "",
            squadRole = SquadRole.RIFLEMAN
        )
        saveProfile(profile, isNewUser = true)
        syncCurrentFcmToken()
        syncLocalProfile(profile, resetGameCode = true)
        return profile
    }

    fun observeCurrentProfile(): Flow<UserAccountProfile?> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = firestore
            .collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val profile = snapshot
                    ?.takeIf { it.exists() }
                    ?.toUserAccountProfile(currentUser)
                    ?: fallbackProfile(currentUser)

                trySend(profile)
            }

        awaitClose { registration.remove() }
    }

    fun observeSquad(squadId: String): Flow<SquadSummary?> = callbackFlow {
        val registration = firestore
            .collection(SQUADS_COLLECTION)
            .document(squadId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.toSquadSummary())
            }

        awaitClose { registration.remove() }
    }

    fun observeSquads(): Flow<List<SquadSummary>> = callbackFlow {
        val registration = firestore
            .collection(SQUADS_COLLECTION)
            .orderBy(FIELD_DISPLAY_NAME, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toSquadSummary() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    fun observeSquadMembers(squadId: String): Flow<List<SquadMemberProfile>> = callbackFlow {
        val registration = firestore
            .collection(USERS_COLLECTION)
            .whereEqualTo(FIELD_SQUAD_ID, squadId)
            .orderBy(FIELD_CALLSIGN, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val members = snapshot?.documents
                    ?.mapNotNull { document ->
                        val uid = document.id
                        val displayName = document.getString(FIELD_DISPLAY_NAME)?.trim().orEmpty()
                        val email = document.getString(FIELD_EMAIL)?.trim().orEmpty()
                        if (displayName.isBlank() && email.isBlank()) {
                            null
                        } else {
                            SquadMemberProfile(
                                uid = uid,
                                displayName = displayName.ifBlank { email.substringBefore("@") },
                                callsign = document.getString(FIELD_CALLSIGN)
                                    ?.trim()
                                    .orEmpty()
                                    .ifBlank {
                                        document.getString(FIELD_DISPLAY_NAME)
                                            ?.trim()
                                            .orEmpty()
                                    },
                                squadRole = SquadRole.fromWireValue(
                                    document.getString(FIELD_SQUAD_ROLE)
                                ),
                                accountRole = AccountRole.fromWireValue(
                                    document.getString(FIELD_ROLE)
                                )
                            )
                        }
                    }
                    .orEmpty()
                    .sortedWith(
                        compareBy<SquadMemberProfile> { it.callsign.lowercase() }
                            .thenBy { it.displayName.lowercase() }
                    )

                trySend(members)
            }

        awaitClose { registration.remove() }
    }

    suspend fun updateCurrentProfile(
        displayName: String,
        callsign: String,
        squadRole: SquadRole
    ): UserAccountProfile {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        val updatedProfile = currentProfile.copy(
            displayName = displayName.trim(),
            callsign = callsign.trim(),
            squadRole = squadRole
        )
        saveProfile(updatedProfile, isNewUser = false)
        syncLocalProfile(updatedProfile, resetGameCode = false)
        return updatedProfile
    }

    suspend fun updateProfilePhoto(uri: String): UserAccountProfile {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        val trimmedUri = uri.trim()
        val updatedProfile = currentProfile.copy(
            profilePhotoUri = trimmedUri,
            photoUrl = if (trimmedUri.isBlank()) "" else currentProfile.photoUrl
        )
        saveProfile(updatedProfile, isNewUser = false)
        syncLocalProfile(updatedProfile, resetGameCode = false)
        return updatedProfile
    }

    suspend fun createSquad(name: String): SquadSummary {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        if (currentProfile.squadId.isNotBlank()) {
            error("Ya formas parte de un escuadron.")
        }

        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "El nombre del escuadron no puede estar vacio." }

        val squadRef = firestore.collection(SQUADS_COLLECTION).document()
        val squad = SquadSummary(
            id = squadRef.id,
            name = normalizedName,
            joinCode = generateUniqueJoinCode(),
            createdBy = currentUser.uid
        )

        squadRef.set(
            mapOf(
                FIELD_DISPLAY_NAME to squad.name,
                FIELD_JOIN_CODE to squad.joinCode,
                FIELD_CREATED_BY to squad.createdBy,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).awaitResult()

        val updatedProfile = currentProfile.copy(
            squadId = squad.id,
            squadName = squad.name,
            squadCode = squad.joinCode
        )
        saveProfile(updatedProfile, isNewUser = false)
        syncLocalProfile(updatedProfile, resetGameCode = false)
        return squad
    }

    suspend fun joinSquad(joinCode: String): SquadSummary {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        if (currentProfile.squadId.isNotBlank()) {
            error("Sal del escuadron actual antes de unirte a otro.")
        }
        val normalizedCode = joinCode.trim().uppercase()
        require(normalizedCode.isNotBlank()) { "Introduce un codigo de escuadron valido." }

        val snapshot = firestore
            .collection(SQUADS_COLLECTION)
            .whereEqualTo(FIELD_JOIN_CODE, normalizedCode)
            .limit(1)
            .get()
            .awaitResult()

        val squadDocument = snapshot.documents.firstOrNull()
            ?: error("No existe ningun escuadron con ese codigo.")

        val squad = SquadSummary(
            id = squadDocument.id,
            name = squadDocument.getString(FIELD_DISPLAY_NAME).orEmpty(),
            joinCode = squadDocument.getString(FIELD_JOIN_CODE).orEmpty(),
            createdBy = squadDocument.getString(FIELD_CREATED_BY).orEmpty()
        )

        val updatedProfile = currentProfile.copy(
            squadId = squad.id,
            squadName = squad.name,
            squadCode = squad.joinCode
        )
        saveProfile(updatedProfile, isNewUser = false)
        syncLocalProfile(updatedProfile, resetGameCode = false)
        return squad
    }

    suspend fun removeMember(memberId: String) {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        require(currentProfile.squadId.isNotBlank()) { "No perteneces a ningun escuadron." }
        require(memberId != currentUser.uid) { "Usa la opcion de salir para abandonar tu escuadron." }

        val squad = fetchSquadSummary(currentProfile.squadId)
            ?: error("No se encontro el escuadron actual.")
        require(squad.createdBy == currentUser.uid) {
            "Solo el Team Leader puede expulsar miembros."
        }

        val memberSnapshot = firestore
            .collection(USERS_COLLECTION)
            .document(memberId)
            .get()
            .awaitResult()

        require(memberSnapshot.exists()) { "No se encontro el miembro seleccionado." }
        val memberSquadId = memberSnapshot.getString(FIELD_SQUAD_ID)?.trim().orEmpty()
        require(memberSquadId == currentProfile.squadId) {
            "Ese usuario ya no pertenece a tu escuadron."
        }

        clearUserSquadAssignment(memberId)
    }

    suspend fun leaveSquad() {
        val currentUser = requireCurrentUser()
        val currentProfile = fetchOrCreateProfile(currentUser)
        if (currentProfile.squadId.isBlank()) {
            return
        }

        val squadId = currentProfile.squadId
        val squad = fetchSquadSummary(squadId)

        if (squad?.createdBy == currentUser.uid) {
            val successorId = findNextLeaderId(
                squadId = squadId,
                excludingUserId = currentUser.uid
            )

            if (successorId == null) {
                firestore
                    .collection(SQUADS_COLLECTION)
                    .document(squadId)
                    .delete()
                    .awaitResult()
            } else {
                firestore
                    .collection(SQUADS_COLLECTION)
                    .document(squadId)
                    .update(
                        mapOf(
                            FIELD_CREATED_BY to successorId,
                            FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                        )
                    )
                    .awaitResult()
            }
        }

        clearUserSquadAssignment(currentUser.uid)
        val updatedProfile = currentProfile.clearSquad()
        syncLocalProfile(updatedProfile, resetGameCode = false)
    }

    suspend fun signOut() {
        auth.signOut()
        preferencesRepository.clearSession()
    }

    suspend fun uploadProfilePicture(uri: Uri): String {
        require(preferencesRepository.canUseNetworkForSync()) {
            "La sincronizacion esta limitada a Wi-Fi. Conectate a una red Wi-Fi para sincronizar la foto."
        }
        val currentUser = requireCurrentUser()
        val encodedPhoto = encodeProfilePhotoAsDataUrl(uri)
        
        firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .set(
                mapOf(
                    FIELD_PHOTO_URL to encodedPhoto,
                    FIELD_PROFILE_PHOTO_URI to "",
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
            
        return encodedPhoto
    }

    private fun encodeProfilePhotoAsDataUrl(uri: Uri): String {
        val source = ImageDecoder.createSource(preferencesRepository.context.contentResolver, uri)
        val original = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bitmap = original.scaleToMax(PROFILE_PHOTO_MAX_SIZE)
        if (bitmap !== original) {
            original.recycle()
        }

        var quality = 82
        var jpegBytes: ByteArray
        do {
            jpegBytes = bitmap.toJpegBytes(quality)
            quality -= 10
        } while (jpegBytes.size > PROFILE_PHOTO_MAX_BYTES && quality >= 42)

        if (bitmap !== original) {
            bitmap.recycle()
        }

        require(jpegBytes.size <= PROFILE_PHOTO_MAX_BYTES) {
            "La foto es demasiado grande. Prueba con una imagen mas sencilla."
        }

        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    suspend fun syncCurrentFcmToken() {
        val currentUser = auth.currentUser ?: return
        val token = FirebaseMessaging.getInstance().token.awaitResult()
        firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .set(
                mapOf(
                    FIELD_FCM_TOKEN to token,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun saveFcmToken(token: String) {
        val currentUser = auth.currentUser ?: return
        firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .set(
                mapOf(
                    FIELD_FCM_TOKEN to token,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    private suspend fun fetchOrCreateProfile(firebaseUser: FirebaseUser): UserAccountProfile {
        val snapshot = firestore
            .collection(USERS_COLLECTION)
            .document(firebaseUser.uid)
            .get()
            .awaitResult()

        val profile = if (snapshot.exists()) {
            snapshot.toUserAccountProfile(firebaseUser)
        } else {
            fallbackProfile(firebaseUser)
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
            FIELD_CALLSIGN to profile.callsign.ifBlank { profile.displayName },
            FIELD_PROFILE_PHOTO_URI to profile.profilePhotoUri,
            FIELD_PHOTO_URL to profile.photoUrl,
            FIELD_SQUAD_ID to profile.squadId,
            FIELD_SQUAD_NAME to profile.squadName,
            FIELD_SQUAD_CODE to profile.squadCode,
            FIELD_SQUAD_ROLE to profile.squadRole.wireValue,
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

    private suspend fun clearUserSquadAssignment(userId: String) {
        firestore
            .collection(USERS_COLLECTION)
            .document(userId)
            .update(
                mapOf(
                    FIELD_SQUAD_ID to "",
                    FIELD_SQUAD_NAME to "",
                    FIELD_SQUAD_CODE to "",
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            .awaitResult()
    }

    private suspend fun syncLocalProfile(
        profile: UserAccountProfile,
        resetGameCode: Boolean
    ) {
        preferencesRepository.setActiveUserName(profile.displayName)
        preferencesRepository.setActiveUserEmail(profile.email)
        preferencesRepository.setPlayerName(profile.callsign.ifBlank { profile.displayName })
        if (resetGameCode) {
            preferencesRepository.setIsGameMaster(false)
            preferencesRepository.clearActiveGameCode()
        }
    }

    private fun fallbackProfile(firebaseUser: FirebaseUser): UserAccountProfile {
        val fallbackName = firebaseUser.displayName
            ?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Operador"

        return UserAccountProfile(
            uid = firebaseUser.uid,
            displayName = fallbackName,
            email = firebaseUser.email ?: "",
            role = AccountRole.PLAYER,
            callsign = fallbackName,
            profilePhotoUri = "",
            squadRole = SquadRole.RIFLEMAN
        )
    }

    private suspend fun generateUniqueJoinCode(): String {
        repeat(10) {
            val candidate = buildString {
                val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                repeat(6) {
                    append(alphabet[Random.nextInt(alphabet.length)])
                }
            }

            val snapshot = firestore
                .collection(SQUADS_COLLECTION)
                .whereEqualTo(FIELD_JOIN_CODE, candidate)
                .limit(1)
                .get()
                .awaitResult()

            if (snapshot.isEmpty) {
                return candidate
            }
        }

        error("No se pudo generar un codigo de escuadron unico. Intentalo de nuevo.")
    }

    private fun requireCurrentUser(): FirebaseUser {
        return auth.currentUser ?: error("Necesitas iniciar sesion para realizar esta accion.")
    }

    private suspend fun fetchSquadSummary(squadId: String): SquadSummary? {
        return firestore
            .collection(SQUADS_COLLECTION)
            .document(squadId)
            .get()
            .awaitResult()
            .toSquadSummary()
    }

    private suspend fun findNextLeaderId(
        squadId: String,
        excludingUserId: String
    ): String? {
        val snapshot = firestore
            .collection(USERS_COLLECTION)
            .whereEqualTo(FIELD_SQUAD_ID, squadId)
            .orderBy(FIELD_CALLSIGN, Query.Direction.ASCENDING)
            .get()
            .awaitResult()

        return snapshot.documents
            .firstOrNull { document -> document.id != excludingUserId }
            ?.id
    }

    private fun DocumentSnapshot.toUserAccountProfile(firebaseUser: FirebaseUser): UserAccountProfile {
        val fallbackName = firebaseUser.displayName
            ?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "Operador"

        return UserAccountProfile(
            uid = firebaseUser.uid,
            displayName = getString(FIELD_DISPLAY_NAME)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName,
            email = getString(FIELD_EMAIL)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: (firebaseUser.email ?: ""),
            role = AccountRole.fromWireValue(getString(FIELD_ROLE)),
            callsign = getString(FIELD_CALLSIGN)
                ?.trim()
                .orEmpty()
                .ifBlank { getString(FIELD_DISPLAY_NAME)?.trim().orEmpty().ifBlank { fallbackName } },
            profilePhotoUri = getString(FIELD_PROFILE_PHOTO_URI)?.trim().orEmpty(),
            squadId = getString(FIELD_SQUAD_ID)?.trim().orEmpty(),
            squadName = getString(FIELD_SQUAD_NAME)?.trim().orEmpty(),
            squadCode = getString(FIELD_SQUAD_CODE)?.trim().orEmpty(),
            squadRole = SquadRole.fromWireValue(getString(FIELD_SQUAD_ROLE)),
            photoUrl = getString(FIELD_PHOTO_URL).orEmpty()
        )
    }

    private fun DocumentSnapshot.toSquadSummary(): SquadSummary? {
        if (!exists()) {
            return null
        }

        return SquadSummary(
            id = id,
            name = getString(FIELD_DISPLAY_NAME)?.trim().orEmpty(),
            joinCode = getString(FIELD_JOIN_CODE)?.trim().orEmpty(),
            createdBy = getString(FIELD_CREATED_BY)?.trim().orEmpty()
        )
    }

    private fun UserAccountProfile.clearSquad(): UserAccountProfile {
        return copy(
            squadId = "",
            squadName = "",
            squadCode = ""
        )
    }
}

private fun Bitmap.scaleToMax(maxSize: Int): Bitmap {
    val longestSide = max(width, height)
    if (longestSide <= maxSize) return this

    val scale = maxSize.toFloat() / longestSide.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
    return ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        output.toByteArray()
    }
}
