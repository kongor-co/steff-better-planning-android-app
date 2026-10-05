package com.safestart.planner

import kotlin.math.ceil

object PlannerEngine {
    fun roundUpToFive(minutes: Int): Int = ((minutes + 4) / 5) * 5

    fun roundClockUpToFive(hour: Int, minute: Int): Int {
        val total = hour * 60 + minute
        return roundUpToFive(total) % (24 * 60)
    }

    fun bufferMinutes(durationMinutes: Int, bufferPercent: Int): Int {
        val raw = durationMinutes * bufferPercent / 100.0
        return roundUpToFive(ceil(raw).toInt())
    }

    fun reservedMinutes(activity: PlannedActivity): Int =
        activity.durationMinutes + bufferMinutes(activity.durationMinutes, activity.bufferPercent) + activity.pauseMinutes

    fun windows(plan: Plan): List<PlanningWindow> {
        val sortedAnchors = plan.anchors.sortedBy { it.startMinute }
        return sortedAnchors.mapIndexed { index, anchor ->
            val start = if (index == 0) {
                plan.startMinute
            } else {
                val previous = sortedAnchors[index - 1]
                previous.startMinute + (previous.durationMinutes ?: 0)
            }
            PlanningWindow(
                startMinute = start,
                endAnchor = anchor,
                activities = plan.activities.filter { it.windowEndAnchorId == anchor.id }
            )
        }
    }

    fun availableMinutes(window: PlanningWindow): Int = window.endAnchor.startMinute - window.startMinute

    fun plannedMinutes(window: PlanningWindow): Int = window.activities.sumOf(::reservedMinutes)

    fun remainingMinutes(window: PlanningWindow): Int = availableMinutes(window) - plannedMinutes(window)

    fun capacityPercent(window: PlanningWindow): Int {
        val available = availableMinutes(window)
        if (available <= 0) return 100
        return (plannedMinutes(window) * 100) / available
    }

    fun latestSafeStart(window: PlanningWindow): Int =
        maxOf(window.startMinute, window.endAnchor.startMinute - plannedMinutes(window))

    fun schedule(window: PlanningWindow): List<ScheduledActivity> {
        var cursor = window.endAnchor.startMinute
        val reversed = window.activities.asReversed().map { activity ->
            val pauseStart = cursor - activity.pauseMinutes
            val buffer = bufferMinutes(activity.durationMinutes, activity.bufferPercent)
            val activeEnd = pauseStart - buffer
            val start = activeEnd - activity.durationMinutes
            cursor = start
            ScheduledActivity(activity, start, activeEnd, pauseStart, pauseStart + activity.pauseMinutes)
        }
        return reversed.asReversed()
    }

    fun validate(plan: Plan): String? {
        val anchors = plan.anchors.sortedBy { it.startMinute }
        if (anchors.isEmpty()) return "Add at least one Anchor."
        if (anchors.first().startMinute <= plan.startMinute) {
            return "Your Anchor must be later than your available start time."
        }
        anchors.forEachIndexed { index, anchor ->
            if (index < anchors.lastIndex) {
                val duration = anchor.durationMinutes
                    ?: return "Add a duration to ${anchor.title} so the next planning window can begin."
                if (anchor.startMinute + duration >= anchors[index + 1].startMinute) {
                    return "${anchor.title} must finish before the next Anchor begins."
                }
            }
        }
        windows(plan).forEach { window ->
            if (availableMinutes(window) < 0) return "An Anchor starts before the preceding boundary."
            if (remainingMinutes(window) < 0) {
                return "The window before ${window.endAnchor.title} needs ${-remainingMinutes(window)} more minutes."
            }
        }
        return null
    }

    fun overallCapacity(plan: Plan): Int {
        val windows = windows(plan)
        val available = windows.sumOf(::availableMinutes)
        if (available <= 0) return 0
        return windows.sumOf(::plannedMinutes) * 100 / available
    }

    fun formatTime(totalMinutes: Int): String {
        val normalized = ((totalMinutes % 1440) + 1440) % 1440
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }

    fun formatDuration(minutes: Int): String {
        if (minutes == 0) return "0 min"
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }
}
