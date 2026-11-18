package com.k1adili.walkietalkie.engine

data class Peer(
    val id: Int,
    val name: String,
    val channel: Int,
    val talking: Boolean,
)

data class UiState(
    val running: Boolean = false,
    val name: String = "",
    val channel: Int = 1,
    val peers: List<Peer> = emptyList(),
    val transmitting: Boolean = false,
    /** Name of whoever is talking on my channel right now, if anyone. */
    val talkerName: String? = null,
    val localIp: String? = null,
    val tapToggle: Boolean = false,
    val keepScreenOn: Boolean = false,
)
