package com.example.entertainment.core.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageHealthMonitorTest {

    @Test
    fun `starts healthy`() {
        val monitor = StorageHealthMonitor()

        assertFalse(monitor.isCompromised)
        assertFalse(monitor.compromised.value)
    }

    @Test
    fun `reportFailure flags storage as compromised`() {
        val monitor = StorageHealthMonitor()

        monitor.reportFailure(IllegalStateException("disk full"))

        assertTrue(monitor.isCompromised)
        assertTrue(monitor.compromised.value)
    }

    @Test
    fun `reportFailure stays compromised across repeated failures`() {
        val monitor = StorageHealthMonitor()

        monitor.reportFailure(IllegalStateException("first failure"))
        monitor.reportFailure(IllegalStateException("second failure"))

        assertTrue(monitor.isCompromised)
    }
}
