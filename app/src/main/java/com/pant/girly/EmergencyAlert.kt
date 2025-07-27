package com.pant.girly.models

data class EmergencyAlert(
    val userId: String? = null,
    val userName: String? = null,
    val timestamp: Long = 0,
    val location: String? = null,
    val status: String? = "Pending"
)