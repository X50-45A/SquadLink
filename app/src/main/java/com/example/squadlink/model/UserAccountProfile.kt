package com.example.squadlink.model

data class UserAccountProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: AccountRole
)
