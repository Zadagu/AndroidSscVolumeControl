package io.github.zadagu.sscvolumecontrol.ssc

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

/**
 * A connection that automatically reconnects when needed and disconnects after a period of inactivity
 */
class AutoManagingConnection(private val connectFn: () -> ConnectionBase) {
    private var connection: ConnectionBase = AlwaysClosedConnection()
    private val connectionMutex = Mutex()
    private val idleTimeoutMillis = TimeUnit.SECONDS.toMillis(30)
    private var lastActivityTime = System.currentTimeMillis()
    private var idleTimeoutJob: Job? = null

    suspend fun <R> withConnection(fn: suspend (connection: SscConnection) -> R): R {
        connectionMutex.withLock {
            if (!connection.isConnected()) {
                Log.i("AutoManaging Connection", "Reconnecting due closed connection")
                connection = connectFn()
            }
            try {
                return fn(connection)
            } finally {
                rescheduleDisconnect()
            }
        }
    }

    suspend fun rescheduleDisconnect() {
        lastActivityTime = System.currentTimeMillis()
        if (idleTimeoutJob == null || idleTimeoutJob?.isCompleted == true) {
            idleTimeoutJob = GlobalScope.launch {
                while (connection.isConnected()) {
                    delay(idleTimeoutMillis + 50)
                    Log.i("AutoManaging Connection", "Checking for timeout")
                    if (isTimeout()) {
                        Log.i("AutoManaging Connection", "Closing Connection due to inactivity timeout")
                        connectionMutex.withLock {
                            connection.disconnect()
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private fun isTimeout(): Boolean {
        return (lastActivityTime + idleTimeoutMillis) < System.currentTimeMillis()
    }
}

class AlwaysClosedConnection() : ConnectionBase() {
    init {
        super.disconnect()
    }

    override suspend fun sendImpl(json: JsonObject): JsonObject {
        throw IllegalStateException("Connection is always closed")
    }
}
