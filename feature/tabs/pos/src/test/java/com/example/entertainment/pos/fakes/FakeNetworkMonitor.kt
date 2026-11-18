package com.example.entertainment.pos.fakes

import com.example.entertainment.pos.domain.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(online)
    override val isOnline = this.online

    fun goOffline() { online.value = false }
    fun goOnline() { online.value = true }
}
