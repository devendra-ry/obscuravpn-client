package net.obscura.vpnclientapp.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemKindTest {
    @Test
    fun theKindLeadsTheUsersWords() {
        assertEquals("[Slow] Frankfurt, 2 Mbit/s", reportText(ProblemKind.SLOW, "  Frankfurt, 2 Mbit/s "))
        assertEquals("[Can't connect]", reportText(ProblemKind.CANT_CONNECT, ""))
    }

    @Test
    fun nothingChosenAndNothingTypedSendsNoText() {
        assertNull(reportText(null, "  "))
    }
}
