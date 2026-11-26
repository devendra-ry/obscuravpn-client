package net.obscura.vpnclientapp.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundHealthHelperTest {
    @Test
    fun xiaomiFamilyDetectionMatchesXiaomiBrands() {
        assertTrue(BackgroundHealthHelper.isXiaomiFamily("Xiaomi", "Xiaomi"))
        assertTrue(BackgroundHealthHelper.isXiaomiFamily("Xiaomi", "Redmi"))
        assertTrue(BackgroundHealthHelper.isXiaomiFamily("Xiaomi", "POCO"))
        assertTrue(BackgroundHealthHelper.isXiaomiFamily("blackshark", "blackshark"))
        assertFalse(BackgroundHealthHelper.isXiaomiFamily("Google", "google"))
        assertFalse(BackgroundHealthHelper.isXiaomiFamily("samsung", "samsung"))
    }
}
