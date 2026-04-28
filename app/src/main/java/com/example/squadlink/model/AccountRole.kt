package com.example.squadlink.model

enum class AccountRole(
    val wireValue: String,
    val label: String
) {
    PLAYER("PLAYER", "Jugador"),
    GAME_MASTER("GAME_MASTER", "Game Master");

    companion object {
        fun fromWireValue(value: String?): AccountRole {
            return entries.firstOrNull { it.wireValue == value } ?: PLAYER
        }
    }
}
