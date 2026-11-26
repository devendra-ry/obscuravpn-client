package net.obscura.vpnclientapp.ui

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.client.ErrorCodeException
import net.obscura.vpnclientapp.services.IObscuraVpnService

private val log = Logger("JsonFfiResults")

/**
 * Matches the results of [IObscuraVpnService.jsonFfi] to their callers. The service and the UI share one process, so
 * the service hands each result straight to [deliverInProcess]; no broadcast is involved.
 */
object JsonFfiResults {
    private val waiting = ConcurrentHashMap<Long, CompletableDeferred<String>>()

    internal fun waitForResponse(binder: IObscuraVpnService, cmd: String) =
        CompletableDeferred<String>().also { job ->
            var id: Long
            do {
                id = Random.nextLong()
            } while (waiting.putIfAbsent(id, job) != null)
            log.trace("job $id registered: $cmd")
            try {
                binder.jsonFfi(id, cmd)
            } catch (e: Throwable) {
                log.error("job $id failed: $e", tr = e)
                waiting.remove(id)
                job.completeExceptionally(e)
            }
        }

    internal fun deliverInProcess(id: Long, value: String?, exception: Throwable?) {
        val result =
            when {
                value != null -> Result.success(value)
                exception is ErrorCodeException -> Result.failure(exception)
                else -> {
                    if (exception != null) {
                        log.error("job $id threw unexpected exception type: $exception", tr = exception)
                    } else {
                        log.error("job $id completed with no response")
                    }
                    Result.failure(ErrorCodeException.other())
                }
            }
        waiting.remove(id)?.completeWith(result) ?: log.error("job $id already completed or never registered")
    }
}
