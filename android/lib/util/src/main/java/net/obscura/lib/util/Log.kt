package net.obscura.lib.util

import android.util.Log
import kotlin.reflect.KClass

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class LogParams(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val messageId: String?,
    val tr: Throwable?,
)

private val ACCOUNT_NUMBER = Regex("(?<!\\d)\\d{20}(?!\\d)")

/**
 * An account number is 20 digits and is the whole login: it must not reach logcat or a debug bundle. Status updates
 * carry it (`accountId`), so every message is masked here instead of at each call site.
 */
fun maskAccountNumbers(text: String): String = text.replace(ACCOUNT_NUMBER, "<account>")

class Logger(val tag: String, val cb: ((LogParams) -> Unit)? = null) {
    constructor(
        classRef: KClass<*>,
        cb: ((LogParams) -> Unit)? = null,
    ) : this(classRef.simpleName ?: "AnonymousClass", cb)

    fun event(level: LogLevel, rawMessage: String, messageId: String? = null, tr: Throwable? = null) {
        val message = maskAccountNumbers(rawMessage)
        when (level) {
            LogLevel.TRACE -> Log.v(this.tag, message, tr)
            LogLevel.DEBUG -> Log.d(this.tag, message, tr)
            LogLevel.INFO -> Log.i(this.tag, message, tr)
            LogLevel.WARN -> Log.w(this.tag, message, tr)
            LogLevel.ERROR -> Log.e(this.tag, message, tr)
        }
        if (this.cb != null) {
            this.cb(LogParams(level, this.tag, message, messageId, tr))
        }
    }

    fun trace(
        message: String,
        messageId: String? = null,
        tr: Throwable? = null,
    ) = this.event(LogLevel.TRACE, message, messageId, tr)

    fun debug(
        message: String,
        messageId: String? = null,
        tr: Throwable? = null,
    ) = this.event(LogLevel.DEBUG, message, messageId, tr)

    fun info(
        message: String,
        messageId: String? = null,
        tr: Throwable? = null,
    ) = this.event(LogLevel.INFO, message, messageId, tr)

    fun warn(
        message: String,
        messageId: String? = null,
        tr: Throwable? = null,
    ) = this.event(LogLevel.WARN, message, messageId, tr)

    fun error(
        message: String,
        messageId: String? = null,
        tr: Throwable? = null,
    ) = this.event(LogLevel.ERROR, message, messageId, tr)
}
