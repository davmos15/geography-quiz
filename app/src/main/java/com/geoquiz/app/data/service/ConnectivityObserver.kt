package com.geoquiz.app.data.service

import kotlinx.coroutines.flow.Flow

/** Whether the device currently has a validated internet connection. Gameplay never needs it. */
interface ConnectivityObserver {
    /** Emits the current state on collection, then every change. */
    val isOnline: Flow<Boolean>
}
