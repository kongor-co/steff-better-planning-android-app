package com.safestart.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEngineTest {
    private val anchor = Anchor(id = "anchor", title = "Appointment", startMinute = 17 * 60 + 30)

    @Test
    fun nowRoundsUpToNextFiveMinutes() {
        assertEquals(14 * 60 + 10, PlannerEngine.roundClockUpToFive(14, 7))
        assertEquals(14 * 60 + 10, PlannerEngine.roundClockUpToFive(14, 10))
    }

    @Test
    fun bufferAlwaysRoundsUp() {
        assertEquals(5, PlannerEngine.bufferMinutes(10, 20))
        assertEquals(10, PlannerEngine.bufferMinutes(30, 20))
        assertEquals(20, PlannerEngine.bufferMinutes(45, 40))
        assertEquals(25, PlannerEngine.bufferMinutes(60, 40))
    }

    @Test
    fun reservedTimeIncludesDurationBufferAndPause() {
        val activity = activity(duration = 60, buffer = 40, pause = 10)
        assertEquals(95, PlannerEngine.reservedMinutes(activity))
    }

    @Test
    fun schedulesBackwardsLeavingFreeTimeFirst() {
        val first = activity(id = "a", duration = 30, buffer = 20, pause = 5)
        val second = activity(id = "b", duration = 45, buffer = 20, pause = 10)
        val window = PlanningWindow(13 * 60, anchor, listOf(first, second))
        val schedule = PlannerEngine.schedule(window)
        assertEquals(15 * 60 + 40, schedule.first().startMinute)
        assertEquals(17 * 60 + 30, schedule.last().endMinute)
        assertEquals(schedule.first().startMinute, PlannerEngine.latestSafeStart(window))
    }

    @Test
    fun overCapacityPlanIsRejected() {
        val plan = Plan(
            startMinute = 17 * 60,
            anchors = listOf(anchor),
            activities = listOf(activity(duration = 30, buffer = 20, pause = 5))
        )
        assertTrue(PlannerEngine.validate(plan)?.contains("more minutes") == true)
    }

    @Test
    fun exactlyFullPlanIsValid() {
        val plan = Plan(
            startMinute = 16 * 60 + 30,
            anchors = listOf(anchor),
            activities = listOf(activity(duration = 45, buffer = 20, pause = 5))
        )
        assertNull(PlannerEngine.validate(plan))
        assertEquals(100, PlannerEngine.capacityPercent(PlannerEngine.windows(plan).first()))
    }

    private fun activity(
        id: String = "activity",
        duration: Int,
        buffer: Int,
        pause: Int
    ) = PlannedActivity(
        id = id,
        title = id,
        kind = ActivityKind.TASK,
        durationMinutes = duration,
        complexity = Complexity.LOW,
        bufferPercent = buffer,
        demandingness = Demandingness.LIGHT,
        pauseMinutes = pause,
        windowEndAnchorId = anchor.id
    )
}
