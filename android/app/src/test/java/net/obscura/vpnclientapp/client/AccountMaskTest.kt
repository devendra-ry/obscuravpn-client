package net.obscura.vpnclientapp.client

import net.obscura.lib.util.maskAccountNumbers
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMaskTest {
    @Test
    fun accountNumbersAreMasked() {
        assertEquals(
            """{"accountId":"<account>","inNewAccountFlow":false}""",
            maskAccountNumbers("""{"accountId":"12345678901234567890","inNewAccountFlow":false}"""),
        )
        assertEquals(
            "GetStatus(accountId=<account>, x=1)",
            maskAccountNumbers("GetStatus(accountId=12345678901234567890, x=1)"),
        )
    }

    @Test
    fun otherNumbersAreKept() {
        // A network handle (12 digits), a 21-digit run and a version id stay readable.
        val text = "networkHandle=437197393933 big=123456789012345678901 version=9b9b0ef9-1be3"
        assertEquals(text, maskAccountNumbers(text))
    }
}
