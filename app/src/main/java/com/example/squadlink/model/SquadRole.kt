package com.example.squadlink.model

enum class SquadRole(
    val wireValue: String,
    val label: String
) {
    RIFLEMAN("RIFLEMAN", "Fusilero"),
    DMR("DMR", "DMR"),
    SUPPORT("SUPPORT", "Apoyo"),
    MEDIC("MEDIC", "Medico");

    companion object {
        fun fromWireValue(value: String?): SquadRole {
            return entries.firstOrNull { it.wireValue == value } ?: RIFLEMAN
        }
    }
}
