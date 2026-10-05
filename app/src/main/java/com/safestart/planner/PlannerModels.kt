package com.safestart.planner

import java.util.UUID

enum class ActivityKind { TASK, OPTIONAL }

enum class Complexity(val suggestedBuffer: Int, val description: String) {
    LOW(20, "Predictable and familiar"),
    MEDIUM(40, "Some uncertainty or variation"),
    HIGH(60, "Difficult to estimate")
}

enum class Demandingness(val suggestedPause: Int) {
    LIGHT(5), NORMAL(10), HIGH(20)
}

data class Anchor(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startMinute: Int,
    val durationMinutes: Int? = null
)

data class PlannedActivity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: ActivityKind,
    val durationMinutes: Int,
    val complexity: Complexity,
    val bufferPercent: Int,
    val demandingness: Demandingness,
    val pauseMinutes: Int,
    val windowEndAnchorId: String,
    val templateId: String? = null,
    val completed: Boolean = false
)

data class ReusableActivity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: ActivityKind,
    val durationMinutes: Int,
    val complexity: Complexity,
    val bufferPercent: Int,
    val demandingness: Demandingness,
    val pauseMinutes: Int
)

data class Plan(
    val startMinute: Int = 9 * 60,
    val anchors: List<Anchor> = emptyList(),
    val activities: List<PlannedActivity> = emptyList(),
    val reusableActivities: List<ReusableActivity> = emptyList(),
    val onboardingSeen: Boolean = false
)

data class PlanningWindow(
    val startMinute: Int,
    val endAnchor: Anchor,
    val activities: List<PlannedActivity>
)

data class ScheduledActivity(
    val activity: PlannedActivity,
    val startMinute: Int,
    val activeEndMinute: Int,
    val bufferEndMinute: Int,
    val endMinute: Int
)

data class CapacityConflict(
    val shortageMinutes: Int,
    val window: PlanningWindow,
    val proposedTitle: String,
    val proposedActivityId: String
)
