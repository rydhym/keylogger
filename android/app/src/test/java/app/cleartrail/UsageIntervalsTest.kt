package app.cleartrail

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageIntervalsTest {
    private val app = "com.whatsapp"
    private fun event(t: Long, kind: Transition.Kind, pkg: String = app, activity: String = "main") = Transition(pkg, t, kind, activity)
    @Test fun clipsToSessionAndIgnoresOtherApps() {
        val values = listOf(event(50, Transition.Kind.RESUME), event(150, Transition.Kind.PAUSE), event(160, Transition.Kind.RESUME, "other"))
        assertEquals(listOf(UsageInterval(100, 150)), UsageIntervals.derive(values, setOf(app), 100, 200)[app])
    }
    @Test fun duplicateResumeDoesNotResetStart() {
        val values = listOf(event(110, Transition.Kind.RESUME), event(120, Transition.Kind.RESUME), event(150, Transition.Kind.PAUSE))
        assertEquals(listOf(UsageInterval(110, 150)), UsageIntervals.derive(values, setOf(app), 100, 200)[app])
    }
    @Test fun closesAtScreenOffAndDoesNotInventUnknownStart() {
        val values = listOf(event(105, Transition.Kind.PAUSE), event(110, Transition.Kind.RESUME), event(150, Transition.Kind.SCREEN_OFF, ""))
        assertEquals(listOf(UsageInterval(110, 150)), UsageIntervals.derive(values, setOf(app), 100, 200)[app])
    }
    @Test fun handlesMultipleActivitiesAndClipsOpenInterval() {
        val values = listOf(event(110, Transition.Kind.RESUME), event(130, Transition.Kind.RESUME, activity = "second"), event(140, Transition.Kind.PAUSE))
        assertEquals(listOf(UsageInterval(110, 200)), UsageIntervals.derive(values, setOf(app), 100, 200)[app])
    }
    @Test fun activityFinishedBeforeStartProducesNothing() {
        assertEquals(emptyList<UsageInterval>(), UsageIntervals.derive(listOf(event(20, Transition.Kind.RESUME), event(40, Transition.Kind.PAUSE)), setOf(app), 100, 200)[app])
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedWindowRejected() {
        UsageIntervals.derive(emptyList(), setOf(app), 0, 86_400_001)
    }
}
