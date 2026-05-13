package com.example.squadlink.model

data class SquadMemberProfile(
    val uid: String,
    val displayName: String,
    val callsign: String,
    val squadRole: SquadRole,
    val accountRole: AccountRole
)
