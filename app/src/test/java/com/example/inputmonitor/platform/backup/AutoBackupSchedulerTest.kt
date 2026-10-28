package com.example.inputmonitor.platform.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoBackupSchedulerTest {

    private class FakeWorkManagerScheduler : AutoBackupScheduler {
        var isPeriodicScheduled = false
        var isImmediateTriggered = false
        var lastWifiOnlySetting: Boolean? = null
        var isCancelled = false

        override fun schedulePeriodicBackup(wifiOnly: Boolean) {
            isPeriodicScheduled = true
            lastWifiOnlySetting = wifiOnly
            isCancelled = false
        }

        override fun triggerImmediateBackup(wifiOnly: Boolean) {
            isImmediateTriggered = true
            lastWifiOnlySetting = wifiOnly
        }

        override fun cancelPeriodicBackup() {
            isCancelled = true
            isPeriodicScheduled = false
        }
    }

    private lateinit var scheduler: FakeWorkManagerScheduler

    @Before
    fun setUp() {
        scheduler = FakeWorkManagerScheduler()
    }

    @Test
    fun schedulePeriodicBackup_recordsWifiOnlyConstraint() {
        scheduler.schedulePeriodicBackup(wifiOnly = true)
        assertTrue(scheduler.isPeriodicScheduled)
        assertEquals(true, scheduler.lastWifiOnlySetting)

        // Reschedule with any connected network
        scheduler.schedulePeriodicBackup(wifiOnly = false)
        assertTrue(scheduler.isPeriodicScheduled)
        assertEquals(false, scheduler.lastWifiOnlySetting)
    }

    @Test
    fun triggerImmediateBackup_setsImmediateTriggerFlag() {
        scheduler.triggerImmediateBackup(wifiOnly = true)
        assertTrue(scheduler.isImmediateTriggered)
        assertEquals(true, scheduler.lastWifiOnlySetting)
    }

    @Test
    fun cancelPeriodicBackup_cancelsWork() {
        scheduler.schedulePeriodicBackup(wifiOnly = true)
        assertTrue(scheduler.isPeriodicScheduled)

        scheduler.cancelPeriodicBackup()
        assertTrue(scheduler.isCancelled)
        assertEquals(false, scheduler.isPeriodicScheduled)
    }
}
