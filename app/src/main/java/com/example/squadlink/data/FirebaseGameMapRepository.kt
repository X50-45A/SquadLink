package com.example.squadlink.data

import com.example.squadlink.ui.map.DynamicObjective
import com.example.squadlink.ui.map.MarkerType
import com.example.squadlink.ui.map.ObjectiveType
import com.example.squadlink.ui.map.SafeZoneArea
import com.example.squadlink.ui.map.TacticalMarker
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseGameMapRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val GAMES_COLLECTION = "games"
        private const val TACTICAL_MARKERS_COLLECTION = "tacticalMarkers"
        private const val SAFE_ZONE_AREAS_COLLECTION = "safeZoneAreas"
        private const val DYNAMIC_OBJECTIVES_COLLECTION = "dynamicObjectives"
        private const val PLAYERS_COLLECTION = "players"

        private const val USERS_COLLECTION = "users"
        private const val FIELD_LABEL = "label"
        private const val FIELD_TYPE = "type"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_LATITUDE = "latitude"
        private const val FIELD_LONGITUDE = "longitude"
        private const val FIELD_TARGET_TEAM = "targetTeam"
        private const val FIELD_OWNER_NAME = "ownerName"
        private const val FIELD_OWNER_TEAM = "ownerTeam"
        private const val FIELD_RADIUS_METERS = "radiusMeters"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_CODE = "code"
        private const val FIELD_FIELD_ID = "fieldId"
        private const val FIELD_PHASE = "phase"
        private const val FIELD_RADIUS = "radius"
        private const val FIELD_POINTS = "points"
        private const val FIELD_GM_UID = "gmUid"
        private const val FIELD_GM_NAME = "gmName"
        private const val FIELD_MISSION_TYPE = "missionType"
        private const val FIELD_MISSION_DESCRIPTION = "missionDescription"
        private const val FIELD_UID = "uid"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_CALLSIGN = "callsign"
        private const val FIELD_SQUAD_NAME = "squadName"
        private const val FIELD_SQUAD_ROLE = "squadRole"
        private const val FIELD_TEAM = "team"
        private const val FIELD_EXPELLED = "expelled"
        private const val FIELD_OUT_OF_BOUNDS = "outOfBounds"
        private const val FIELD_START_TIME = "startTime"
        private const val FIELD_REST_START_TIME = "restStartTime"
        private const val FIELD_REST_END_TIME = "restEndTime"
        private const val FIELD_END_TIME = "endTime"
    }

    fun observeGame(gameCode: String): Flow<ActiveGame?> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toActiveGame())
            }

        awaitClose { registration.remove() }
    }

    fun observeGamePlayers(gameCode: String): Flow<List<GamePlayer>> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toGamePlayer() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    fun observeCurrentPlayerStatus(gameCode: String): Flow<GamePlayerStatus?> = callbackFlow {
        val currentUser = auth.currentUser
        if (gameCode.isBlank() || currentUser == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toGamePlayerStatus())
            }

        awaitClose { registration.remove() }
    }

    suspend fun createGame(
        gameCode: String,
        fieldId: String,
        missionType: ObjectiveType,
        missionDescription: String,
        startTime: String,
        restStartTime: String,
        restEndTime: String,
        endTime: String
    ) {
        val currentUser = requireCurrentUser()
        val profile = fetchCurrentProfile()
        val normalizedCode = gameCode.trim().uppercase()
        require(normalizedCode.isNotBlank()) { "El codigo de partida no puede estar vacio." }
        require(fieldId.isNotBlank()) { "Selecciona un campo para la partida." }

        gameDocument(normalizedCode)
            .set(
                mapOf(
                    FIELD_CODE to normalizedCode,
                    FIELD_FIELD_ID to fieldId,
                    FIELD_PHASE to GamePhase.BRIEFING.name,
                    FIELD_GM_UID to currentUser.uid,
                    FIELD_GM_NAME to profile.callsign.ifBlank { profile.displayName },
                    FIELD_MISSION_TYPE to missionType.name,
                    FIELD_MISSION_DESCRIPTION to missionDescription.trim(),
                    FIELD_START_TIME to startTime,
                    FIELD_REST_START_TIME to restStartTime,
                    FIELD_REST_END_TIME to restEndTime,
                    FIELD_END_TIME to endTime,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun startGame(gameCode: String) {
        require(gameCode.isNotBlank()) { "No hay partida activa." }
        ensureCurrentUserIsGameMaster(gameCode)
        gameDocument(gameCode)
            .update(
                mapOf(
                    FIELD_PHASE to GamePhase.RUNNING.name,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            )
            .awaitResult()
    }

    suspend fun joinGameTeam(gameCode: String, team: GameTeam, playerName: String) {
        val currentUser = requireCurrentUser()
        val game = gameDocument(gameCode).get().awaitResult().toActiveGame()
            ?: error("No existe ninguna partida con ese codigo.")
        require(game.phase == GamePhase.BRIEFING) {
            "La partida ya ha empezado. Espera instrucciones del Game Master."
        }

        val existing = gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .document(currentUser.uid)
            .get()
            .awaitResult()
        require(existing.getBoolean(FIELD_EXPELLED) != true) {
            "Has sido expulsado de esta partida."
        }

        val profile = fetchCurrentProfile()
        gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .document(currentUser.uid)
            .set(
                mapOf(
                    FIELD_UID to currentUser.uid,
                    FIELD_DISPLAY_NAME to profile.displayName,
                    FIELD_CALLSIGN to playerName.trim().ifBlank {
                        profile.callsign.ifBlank { profile.displayName }
                    },
                    FIELD_SQUAD_NAME to profile.squadName,
                    FIELD_SQUAD_ROLE to profile.squadRole,
                    FIELD_TEAM to team.name,
                    FIELD_EXPELLED to false,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun expelPlayer(gameCode: String, playerId: String) {
        require(gameCode.isNotBlank()) { "No hay partida activa." }
        ensureCurrentUserIsGameMaster(gameCode)
        gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .document(playerId)
            .set(
                mapOf(
                    FIELD_EXPELLED to true,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun updatePlayerLocation(
        gameCode: String,
        position: LatLng,
        outOfBounds: Boolean
    ) {
        val currentUser = requireCurrentUser()
        require(gameCode.isNotBlank()) { "No hay partida activa." }
        gameDocument(gameCode)
            .collection(PLAYERS_COLLECTION)
            .document(currentUser.uid)
            .set(
                mapOf(
                    FIELD_LATITUDE to position.latitude,
                    FIELD_LONGITUDE to position.longitude,
                    FIELD_OUT_OF_BOUNDS to outOfBounds,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    fun observeTacticalMarkers(gameCode: String): Flow<List<TacticalMarker>> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .collection(TACTICAL_MARKERS_COLLECTION)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toTacticalMarker() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    fun observeSafeZoneAreas(gameCode: String): Flow<List<SafeZoneArea>> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .collection(SAFE_ZONE_AREAS_COLLECTION)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toSafeZoneArea() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    fun observeDynamicObjectives(gameCode: String): Flow<List<DynamicObjective>> = callbackFlow {
        if (gameCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = gameDocument(gameCode)
            .collection(DYNAMIC_OBJECTIVES_COLLECTION)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toDynamicObjective() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    suspend fun upsertTacticalMarker(gameCode: String, marker: TacticalMarker) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para sincronizar el marcador." }
        gameDocument(gameCode)
            .collection(TACTICAL_MARKERS_COLLECTION)
            .document(marker.id)
            .set(
                mapOf(
                    FIELD_LABEL to marker.label,
                    FIELD_TYPE to marker.type.name,
                    FIELD_LATITUDE to marker.position.latitude,
                    FIELD_LONGITUDE to marker.position.longitude,
                    FIELD_OWNER_NAME to marker.ownerName,
                    FIELD_OWNER_TEAM to marker.ownerTeam,
                    FIELD_RADIUS_METERS to marker.radiusMeters,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_CREATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun deleteTacticalMarker(gameCode: String, markerId: String) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para eliminar el marcador." }
        gameDocument(gameCode)
            .collection(TACTICAL_MARKERS_COLLECTION)
            .document(markerId)
            .delete()
            .awaitResult()
    }

    suspend fun upsertSafeZoneArea(gameCode: String, area: SafeZoneArea) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para sincronizar la zona segura." }
        gameDocument(gameCode)
            .collection(SAFE_ZONE_AREAS_COLLECTION)
            .document(area.id)
            .set(
                mapOf(
                    FIELD_DISPLAY_NAME to area.name,
                    FIELD_LATITUDE to area.center.latitude,
                    FIELD_LONGITUDE to area.center.longitude,
                    FIELD_RADIUS to area.radius,
                    FIELD_POINTS to area.points.map { mapOf(FIELD_LATITUDE to it.latitude, FIELD_LONGITUDE to it.longitude) },
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_CREATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun deleteSafeZoneArea(gameCode: String, areaId: String) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para eliminar la zona segura." }
        gameDocument(gameCode)
            .collection(SAFE_ZONE_AREAS_COLLECTION)
            .document(areaId)
            .delete()
            .awaitResult()
    }

    suspend fun upsertDynamicObjective(gameCode: String, objective: DynamicObjective) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para sincronizar el objetivo." }
        gameDocument(gameCode)
            .collection(DYNAMIC_OBJECTIVES_COLLECTION)
            .document(objective.id)
            .set(
                mapOf(
                    FIELD_TYPE to objective.type.name,
                    FIELD_DESCRIPTION to objective.description,
                    FIELD_LATITUDE to objective.position.latitude,
                    FIELD_LONGITUDE to objective.position.longitude,
                    FIELD_TARGET_TEAM to objective.targetTeam.orEmpty(),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_CREATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .awaitResult()
    }

    suspend fun deleteDynamicObjective(gameCode: String, objectiveId: String) {
        require(gameCode.isNotBlank()) { "No hay una partida activa para eliminar el objetivo." }
        gameDocument(gameCode)
            .collection(DYNAMIC_OBJECTIVES_COLLECTION)
            .document(objectiveId)
            .delete()
            .awaitResult()
    }

    private fun gameDocument(gameCode: String) =
        firestore.collection(GAMES_COLLECTION).document(gameCode.trim().uppercase())

    private suspend fun requireCurrentUser() =
        auth.currentUser ?: error("Necesitas iniciar sesion para realizar esta accion.")

    private suspend fun ensureCurrentUserIsGameMaster(gameCode: String) {
        val currentUser = requireCurrentUser()
        val game = gameDocument(gameCode).get().awaitResult().toActiveGame()
            ?: error("No existe la partida activa.")
        require(game.gmUid == currentUser.uid) {
            "Solo el Game Master puede hacer esta accion."
        }
    }

    private suspend fun fetchCurrentProfile(): LightweightProfile {
        val currentUser = requireCurrentUser()
        val snapshot = firestore
            .collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .get()
            .awaitResult()
        val fallbackName = currentUser.displayName
            ?: currentUser.email?.substringBefore("@")
            ?: "Operador"
        return LightweightProfile(
            displayName = snapshot.getString(FIELD_DISPLAY_NAME)?.trim()?.takeIf { it.isNotBlank() }
                ?: fallbackName,
            callsign = snapshot.getString(FIELD_CALLSIGN)?.trim().orEmpty(),
            squadName = snapshot.getString(FIELD_SQUAD_NAME)?.trim().orEmpty(),
            squadRole = snapshot.getString(FIELD_SQUAD_ROLE)?.trim().orEmpty()
        )
    }

    private fun DocumentSnapshot.toActiveGame(): ActiveGame? {
        if (!exists()) return null
        return ActiveGame(
            code = getString(FIELD_CODE)?.trim().orEmpty().ifBlank { id },
            fieldId = getString(FIELD_FIELD_ID)?.trim().orEmpty(),
            phase = GamePhase.fromWireValue(getString(FIELD_PHASE)),
            gmUid = getString(FIELD_GM_UID)?.trim().orEmpty(),
            gmName = getString(FIELD_GM_NAME)?.trim().orEmpty(),
            missionType = ObjectiveType.fromWireValue(getString(FIELD_MISSION_TYPE)),
            missionDescription = getString(FIELD_MISSION_DESCRIPTION)?.trim().orEmpty(),
            startTime = getString(FIELD_START_TIME).orEmpty(),
            restStartTime = getString(FIELD_REST_START_TIME).orEmpty(),
            restEndTime = getString(FIELD_REST_END_TIME).orEmpty(),
            endTime = getString(FIELD_END_TIME).orEmpty()
        )
    }

    private fun DocumentSnapshot.toGamePlayer(): GamePlayer? {
        if (getBoolean(FIELD_EXPELLED) == true) {
            return null
        }
        val playerId = getString(FIELD_UID)?.trim().orEmpty().ifBlank { id }
        return GamePlayer(
            uid = playerId,
            displayName = getString(FIELD_DISPLAY_NAME)?.trim().orEmpty(),
            callsign = getString(FIELD_CALLSIGN)?.trim().orEmpty(),
            squadName = getString(FIELD_SQUAD_NAME)?.trim().orEmpty(),
            squadRole = getString(FIELD_SQUAD_ROLE)?.trim().orEmpty(),
            team = GameTeam.fromWireValue(getString(FIELD_TEAM)),
            position = readLatLngOrNull(),
            isOutOfBounds = getBoolean(FIELD_OUT_OF_BOUNDS) == true
        )
    }

    private fun DocumentSnapshot.toGamePlayerStatus(): GamePlayerStatus? {
        if (!exists()) return null
        return GamePlayerStatus(
            uid = getString(FIELD_UID)?.trim().orEmpty().ifBlank { id },
            team = GameTeam.fromWireValue(getString(FIELD_TEAM)),
            expelled = getBoolean(FIELD_EXPELLED) == true
        )
    }

    private fun DocumentSnapshot.toTacticalMarker(): TacticalMarker? {
        val latitude = getDouble(FIELD_LATITUDE) ?: return null
        val longitude = getDouble(FIELD_LONGITUDE) ?: return null
        return TacticalMarker(
            id = id,
            label = getString(FIELD_LABEL).orEmpty().ifBlank { "Marcador" },
            position = LatLng(latitude, longitude),
            type = runCatching {
                MarkerType.valueOf(getString(FIELD_TYPE).orEmpty())
            }.getOrDefault(MarkerType.CUSTOM),
            ownerName = getString(FIELD_OWNER_NAME).orEmpty(),
            ownerTeam = getString(FIELD_OWNER_TEAM).orEmpty(),
            radiusMeters = getDouble(FIELD_RADIUS_METERS) ?: 0.0
        )
    }

    private fun DocumentSnapshot.toSafeZoneArea(): SafeZoneArea? {
        val latitude = getDouble(FIELD_LATITUDE) ?: return null
        val longitude = getDouble(FIELD_LONGITUDE) ?: return null
        val radius = getDouble(FIELD_RADIUS)?.toFloat() ?: 50f
        val pointsRaw = get(FIELD_POINTS) as? List<Map<String, Double>> ?: emptyList()
        val points = pointsRaw.mapNotNull { 
            val lat = it[FIELD_LATITUDE]
            val lon = it[FIELD_LONGITUDE]
            if (lat != null && lon != null) LatLng(lat, lon) else null
        }
        
        return SafeZoneArea(
            id = id,
            name = getString(FIELD_DISPLAY_NAME).orEmpty().ifBlank { "Zona Segura" },
            center = LatLng(latitude, longitude),
            radius = radius,
            points = points
        )
    }

    private fun DocumentSnapshot.toDynamicObjective(): DynamicObjective? {
        val latitude = getDouble(FIELD_LATITUDE) ?: return null
        val longitude = getDouble(FIELD_LONGITUDE) ?: return null
        return DynamicObjective(
            id = id,
            type = ObjectiveType.fromWireValue(getString(FIELD_TYPE)),
            description = getString(FIELD_DESCRIPTION).orEmpty(),
            position = LatLng(latitude, longitude),
            targetTeam = getString(FIELD_TARGET_TEAM)?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun DocumentSnapshot.readLatLngOrNull(): LatLng? {
        val latitude = getDouble(FIELD_LATITUDE) ?: return null
        val longitude = getDouble(FIELD_LONGITUDE) ?: return null
        return LatLng(latitude, longitude)
    }
}

data class ActiveGame(
    val code: String,
    val fieldId: String,
    val phase: GamePhase,
    val gmUid: String,
    val gmName: String,
    val missionType: ObjectiveType,
    val missionDescription: String,
    val startTime: String = "",
    val restStartTime: String = "",
    val restEndTime: String = "",
    val endTime: String = ""
)

data class GamePlayer(
    val uid: String,
    val displayName: String,
    val callsign: String,
    val squadName: String,
    val squadRole: String,
    val team: GameTeam,
    val position: LatLng? = null,
    val isOutOfBounds: Boolean = false
)

data class GamePlayerStatus(
    val uid: String,
    val team: GameTeam,
    val expelled: Boolean
)

enum class GamePhase {
    BRIEFING,
    RUNNING,
    ENDED;

    companion object {
        fun fromWireValue(value: String?): GamePhase {
            return entries.firstOrNull { it.name == value } ?: BRIEFING
        }
    }
}

enum class GameTeam(val label: String) {
    RED("Equipo rojo"),
    BLUE("Equipo azul");

    companion object {
        fun fromWireValue(value: String?): GameTeam {
            return entries.firstOrNull { it.name == value } ?: RED
        }
    }
}

private data class LightweightProfile(
    val displayName: String,
    val callsign: String,
    val squadName: String,
    val squadRole: String
)
