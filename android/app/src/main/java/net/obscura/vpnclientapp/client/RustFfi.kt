package net.obscura.vpnclientapp.client

import android.content.Context
import java.util.concurrent.CompletableFuture
import net.obscura.lib.util.Logger

class RustFfi(
    context: Context,
    userAgent: String,
    wgSecretKey: ByteArray?,
    configKey: ByteArray?,
    persistLogs: Boolean,
) {
    private val handle: ObscuraLibrary.FfiHandle =
        try {
            ObscuraLibrary.load(
                context,
                userAgent,
                wgSecretKey,
                configKey,
                persistLogs,
            )
        } finally {
            wgSecretKey?.fill(0)
            configKey?.fill(0)
        }

    fun logger(tag: String) =
        Logger(tag) { params ->
            ObscuraLibrary.forwardLog(
                params.level.ordinal,
                params.tag,
                params.message,
                params.messageId ?: "JavaNoID",
                params.tr?.toString(),
            )
        }

    fun jsonFfi(json: String, future: CompletableFuture<String>) {
        ObscuraLibrary.jsonFfi(handle, json, future)
    }

    fun setNetworkInterface(name: String, index: Int, networkHandle: Long, mtuHint: Int) {
        ObscuraLibrary.setNetworkInterface(handle, name, index, networkHandle, mtuHint)
    }

    fun unsetNetworkInterface() {
        ObscuraLibrary.unsetNetworkInterface(handle)
    }

    fun wake() {
        ObscuraLibrary.wake(handle)
    }

    fun flushAndStopPersistedLog() {
        ObscuraLibrary.flushAndStopPersistedLog()
    }
}
