package com.safestart.planner

import java.util.Locale
import java.util.UUID

object WorkspaceOperations {
    fun normalizedName(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun nameError(name: String, plans: List<SavedPlan>, ignoredPlanId: String? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Enter a plan name."
        val normalized = normalizedName(trimmed)
        val duplicate = plans.any { saved ->
            (saved.id != ignoredPlanId).and(normalizedName(saved.name) == normalized)
        }
        return if (duplicate) "A plan with this name already exists." else null
    }

    fun withoutWorkspaceData(plan: Plan): Plan = plan.copy(
        reusableActivities = emptyList(),
        onboardingSeen = true
    )

    fun withWorkspaceData(
        plan: Plan,
        reusableActivities: List<ReusableActivity>,
        onboardingSeen: Boolean
    ): Plan = plan.copy(
        reusableActivities = reusableActivities,
        onboardingSeen = onboardingSeen
    )

    fun duplicatePlan(plan: Plan): Plan {
        val anchorIds = plan.anchors.associate { anchor -> anchor.id to UUID.randomUUID().toString() }
        val anchors = plan.anchors.map { anchor ->
            anchor.copy(id = anchorIds.getValue(anchor.id))
        }
        val activities = plan.activities.map { activity ->
            activity.copy(
                id = UUID.randomUUID().toString(),
                windowEndAnchorId = anchorIds.getValue(activity.windowEndAnchorId)
            )
        }
        return withoutWorkspaceData(plan.copy(anchors = anchors, activities = activities))
    }

    fun hasMeaningfulDraft(plan: Plan): Boolean =
        (plan.startMinute != 9 * 60).or(plan.anchors.isNotEmpty()).or(plan.activities.isNotEmpty())
}
