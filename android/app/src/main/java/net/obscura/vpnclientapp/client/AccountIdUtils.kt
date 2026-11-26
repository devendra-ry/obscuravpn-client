package net.obscura.vpnclientapp.client

import java.security.SecureRandom
import java.util.Locale

object AccountIdUtils {
    private const val ACCOUNT_DIGITS = 19
    const val TOTAL_LENGTH = 20
    private const val CHUNK_SIZE = 4

    private val D =
        arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
            intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
            intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
            intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
            intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
            intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
            intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
            intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
            intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        )

    private val P =
        arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
            intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
            intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
            intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
            intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
            intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
            intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
        )

    private val INVERSE = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    private val random = SecureRandom()

    fun normalizeDigits(input: String): String = input.filter { it.isDigit() }

    fun formatGrouped(digits: String): String = normalizeDigits(digits).chunked(CHUNK_SIZE).joinToString(" - ")

    fun validChecksum(digits: String): Boolean =
        digits.length == TOTAL_LENGTH && digits.all { it.isDigit() } && rawChecksum(digits) == 0

    fun generateAccountNumber(): String {
        val body = buildString(ACCOUNT_DIGITS) { repeat(ACCOUNT_DIGITS) { append(random.nextInt(10)) } }
        val check = INVERSE[rawChecksum("${body}0")]
        return body + check.toString()
    }

    private fun rawChecksum(digits: String): Int {
        var acc = 0
        for (i in digits.indices.reversed()) {
            val digit = digits[i] - '0'
            val pos = (digits.length - 1 - i) % P.size
            acc = D[acc][P[pos][digit]]
        }
        return acc
    }

    fun countryDisplayName(countryCode: String): String {
        val code = countryCode.uppercase(Locale.ROOT)
        val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
        return name.ifBlank { code }
    }

    fun countryFlagEmoji(countryCode: String): String {
        val upper = countryCode.uppercase(Locale.ROOT)
        if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return ""
        val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        return String(first) + String(second)
    }
}
