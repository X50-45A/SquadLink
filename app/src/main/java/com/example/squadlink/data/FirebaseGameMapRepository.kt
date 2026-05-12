package com.example.squadlink.data

import com.example.squadlink.ui.map.DynamicObjective
import com.example.squadlink.ui.map.MarkerType
import com.example.squadlink.ui.map.ObjectiveType
import com.example.squadlink.ui.map.TacticalMarker
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseGameMapRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val GAMES_COLLECTION = "games"
        private const val TACTICAL_MARKERS_COLLECTION = "tacticalMarkers"
        private const val DYNAMIC_OBJECTIVES_COLLECTION = "dynamicObjectives"

        private const val FIELD_LABEL = "label"
        private const val FIELD_TYPE = "type"
        private const val FIELD_DESCRIPTION = "description"
        private const val FIELD_LATITUDE = "latitude"
        private const val FIELD_LONGITUDE = "longitude"
        private const val FIELD_TARGET_TEAM = "targetTeam"
        private const val FIELD_OWNER_NAME = "ownerName"
        private const val FIELD_OWNER_TEAM = "ownerTeam"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
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
            ownerTeam = getString(FIELD_OWNER_TEAM).orEmpty()
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
}
