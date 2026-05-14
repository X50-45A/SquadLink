package com.example.squadlink.model

data class UserAccountProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: AccountRole,
    val callsign: String = "",
    val profilePhotoUri: String = "",
    val squadId: String = "",
    val squadName: String = "",
    val squadCode: String = "",
    val squadRole: SquadRole = SquadRole.RIFLEMAN,
    val photoUrl: String = ""
)
