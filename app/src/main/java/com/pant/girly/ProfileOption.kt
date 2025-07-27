package com.pant.girly

data class ProfileOption(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val type: ProfileOptionType
)

enum class ProfileOptionType {
    USER_INFO, SOS_ALERTS, EMERGENCY_CONTACTS, REGISTERED_ADDRESS, APP_SETTINGS, LOGOUT
}
