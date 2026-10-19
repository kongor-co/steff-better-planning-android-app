package com.safestart.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class PlanStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): WorkspaceLoadResult {
        val primaryRaw = preferences.getString(PRIMARY_WORKSPACE, null)
        val primary = verifiedDecode(primaryRaw, preferences.getString(PRIMARY_CHECKSUM, null))
        if (primary != null) return WorkspaceLoadResult(primary)

        val backupRaw = preferences.getString(BACKUP_WORKSPACE, null)
        val backup = verifiedDecode(backupRaw, preferences.getString(BACKUP_CHECKSUM, null))
        if (backup != null) {
            preserveCorrupt(primaryRaw)
            save(backup)
            return WorkspaceLoadResult(
                state = backup,
                notice = "The latest saved data could not be read. The app restored the previous safe copy."
            )
        }

        val legacyRaw = preferences.getString(LEGACY_PLAN, null)
        if (legacyRaw != null) {
            val legacyPlan = runCatching { decodeLegacyPlan(JSONObject(legacyRaw)) }.getOrNull()
            if (legacyPlan != null) {
                val meaningful = WorkspaceOperations.hasMeaningfulDraft(legacyPlan)
                val state = WorkspaceState(
                    onboardingSeen = legacyPlan.onboardingSeen,
                    reusableActivities = legacyPlan.reusableActivities,
                    draftPlan = if (meaningful) legacyPlan else null,
                    draftDirty = meaningful
                )
                save(state)
                return WorkspaceLoadResult(
                    state = state,
                    notice = if (meaningful) {
                        "Your previous plan was preserved as an unsaved draft."
                    } else {
                        null
                    }
                )
            }
            preserveCorrupt(legacyRaw)
        }

        val hadUnreadableData = (primaryRaw != null).or(backupRaw != null).or(legacyRaw != null)
        return WorkspaceLoadResult(
            state = WorkspaceState(),
            notice = if (hadUnreadableData) {
                "Saved planning data could not be read. A protected copy was kept on this device."
            } else {
                null
            }
        )
    }

    fun save(state: WorkspaceState): Boolean {
        val payload = encodeWorkspace(state).toString()
        val verified = runCatching { decodeWorkspace(JSONObject(payload)) }.getOrNull()
        if (verified == null) return false

        val currentPayload = preferences.getString(PRIMARY_WORKSPACE, null)
        val currentChecksum = preferences.getString(PRIMARY_CHECKSUM, null)
        val currentIsValid = verifiedDecode(currentPayload, currentChecksum) != null
        val editor = preferences.edit()
        if (currentIsValid) {
            editor.putString(BACKUP_WORKSPACE, currentPayload)
            editor.putString(BACKUP_CHECKSUM, currentChecksum)
        }
        return editor
            .putString(PRIMARY_WORKSPACE, payload)
            .putString(PRIMARY_CHECKSUM, checksum(payload))
            .remove(LEGACY_PLAN)
            .commit()
    }

    private fun verifiedDecode(raw: String?, expectedChecksum: String?): WorkspaceState? {
        if (raw == null || expectedChecksum == null) return null
        if (checksum(raw) != expectedChecksum) return null
        return runCatching { decodeWorkspace(JSONObject(raw)) }.getOrNull()
    }

    private fun preserveCorrupt(raw: String?) {
        if (raw == null) return
        preferences.edit().putString(CORRUPT_COPY, raw).commit()
    }

    private fun checksum(value: String): String {
        val crc = CRC32()
        crc.update(value.toByteArray(StandardCharsets.UTF_8))
        return crc.value.toString(16)
    }

    private fun encodeWorkspace(state: WorkspaceState): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("onboardingSeen", state.onboardingSeen)
        put("reusableActivities", JSONArray().apply {
            state.reusableActivities.forEach { put(encodeReusableActivity(it)) }
        })
        put("savedPlans", JSONArray().apply {
            state.savedPlans.forEach { saved ->
                put(JSONObject().apply {
                    put("id", saved.id)
                    put("name", saved.name)
                    put("createdAtMillis", saved.createdAtMillis)
                    put("updatedAtMillis", saved.updatedAtMillis)
                    put("plan", encodePlan(saved.plan))
                })
            }
        })
        put("draftPlan", state.draftPlan?.let(::encodePlan) ?: JSONObject.NULL)
        put("draftSourcePlanId", state.draftSourcePlanId ?: JSONObject.NULL)
        put("draftName", state.draftName ?: JSONObject.NULL)
        put("draftDirty", state.draftDirty)
    }

    private fun decodeWorkspace(json: JSONObject): WorkspaceState {
        require(json.getInt("schemaVersion") == SCHEMA_VERSION)
        val onboardingSeen = json.optBoolean("onboardingSeen", false)
        val reusable = json.optJSONArray("reusableActivities")?.objects()?.map(::decodeReusableActivity).orEmpty()
        val savedPlans = json.getJSONArray("savedPlans").objects().map { item ->
            SavedPlan(
                id = item.getString("id"),
                name = item.getString("name").trim(),
                plan = decodePlan(item.getJSONObject("plan"), reusable, onboardingSeen),
                createdAtMillis = item.getLong("createdAtMillis"),
                updatedAtMillis = item.getLong("updatedAtMillis")
            )
        }
        val draft = if (json.isNull("draftPlan")) {
            null
        } else {
            decodePlan(json.getJSONObject("draftPlan"), reusable, onboardingSeen)
        }
        val state = WorkspaceState(
            onboardingSeen = onboardingSeen,
            savedPlans = savedPlans,
            reusableActivities = reusable,
            draftPlan = draft,
            draftSourcePlanId = json.nullableString("draftSourcePlanId"),
            draftName = json.nullableString("draftName"),
            draftDirty = json.optBoolean("draftDirty", false)
        )
        validateWorkspace(state)
        return state
    }

    private fun validateWorkspace(state: WorkspaceState) {
        require(state.savedPlans.map { it.id }.distinct().size == state.savedPlans.size)
        require(state.savedPlans.none { it.name.isBlank() })
        require(
            state.savedPlans.map { WorkspaceOperations.normalizedName(it.name) }.distinct().size ==
                state.savedPlans.size
        )
        require(state.reusableActivities.map { it.id }.distinct().size == state.reusableActivities.size)
        state.savedPlans.forEach { validatePlanLinks(it.plan) }
        state.draftPlan?.let(::validatePlanLinks)
        state.draftSourcePlanId?.let { sourceId ->
            require(state.savedPlans.any { it.id == sourceId })
        }
    }

    private fun validatePlanLinks(plan: Plan) {
        val anchorIds = plan.anchors.map { it.id }
        require(anchorIds.distinct().size == anchorIds.size)
        require(plan.activities.map { it.id }.distinct().size == plan.activities.size)
        require(plan.activities.all { it.windowEndAnchorId in anchorIds })
    }

    private fun encodePlan(plan: Plan): JSONObject = JSONObject().apply {
        put("startMinute", plan.startMinute)
        put("anchors", JSONArray().apply {
            plan.anchors.forEach { anchor ->
                put(JSONObject().apply {
                    put("id", anchor.id)
                    put("title", anchor.title)
                    put("startMinute", anchor.startMinute)
                    put("durationMinutes", anchor.durationMinutes ?: JSONObject.NULL)
                })
            }
        })
        put("activities", JSONArray().apply {
            plan.activities.forEach { put(encodeActivity(it)) }
        })
    }

    private fun decodePlan(
        json: JSONObject,
        reusableActivities: List<ReusableActivity>,
        onboardingSeen: Boolean
    ): Plan = Plan(
        startMinute = json.getInt("startMinute"),
        anchors = json.getJSONArray("anchors").objects().map { item ->
            Anchor(
                id = item.getString("id"),
                title = item.getString("title"),
                startMinute = item.getInt("startMinute"),
                durationMinutes = item.nullableInt("durationMinutes")
            )
        },
        activities = json.getJSONArray("activities").objects().map(::decodeActivity),
        reusableActivities = reusableActivities,
        onboardingSeen = onboardingSeen
    )

    private fun encodeActivity(activity: PlannedActivity): JSONObject = JSONObject().apply {
        put("id", activity.id)
        put("title", activity.title)
        put("kind", activity.kind.name)
        put("durationMinutes", activity.durationMinutes)
        put("complexity", activity.complexity.name)
        put("bufferPercent", activity.bufferPercent)
        put("demandingness", activity.demandingness.name)
        put("pauseMinutes", activity.pauseMinutes)
        put("windowEndAnchorId", activity.windowEndAnchorId)
        put("templateId", activity.templateId ?: JSONObject.NULL)
        put("completed", activity.completed)
    }

    private fun decodeActivity(item: JSONObject) = PlannedActivity(
        id = item.getString("id"),
        title = item.getString("title"),
        kind = ActivityKind.valueOf(item.getString("kind")),
        durationMinutes = item.getInt("durationMinutes"),
        complexity = Complexity.valueOf(item.getString("complexity")),
        bufferPercent = item.getInt("bufferPercent"),
        demandingness = Demandingness.valueOf(item.getString("demandingness")),
        pauseMinutes = item.getInt("pauseMinutes"),
        windowEndAnchorId = item.getString("windowEndAnchorId"),
        templateId = item.nullableString("templateId"),
        completed = item.optBoolean("completed", false)
    )

    private fun encodeReusableActivity(activity: ReusableActivity): JSONObject = JSONObject().apply {
        put("id", activity.id)
        put("title", activity.title)
        put("kind", activity.kind.name)
        put("durationMinutes", activity.durationMinutes)
        put("complexity", activity.complexity.name)
        put("bufferPercent", activity.bufferPercent)
        put("demandingness", activity.demandingness.name)
        put("pauseMinutes", activity.pauseMinutes)
    }

    private fun decodeReusableActivity(item: JSONObject) = ReusableActivity(
        id = item.getString("id"),
        title = item.getString("title"),
        kind = ActivityKind.valueOf(item.getString("kind")),
        durationMinutes = item.getInt("durationMinutes"),
        complexity = Complexity.valueOf(item.getString("complexity")),
        bufferPercent = item.getInt("bufferPercent"),
        demandingness = Demandingness.valueOf(item.getString("demandingness")),
        pauseMinutes = item.getInt("pauseMinutes")
    )

    private fun decodeLegacyPlan(json: JSONObject): Plan {
        val reusable = json.optJSONArray("templates")?.objects()?.map(::decodeReusableActivity).orEmpty()
        return decodePlan(json, reusable, json.optBoolean("onboardingSeen", false))
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private companion object {
        const val PREFERENCES_NAME = "safe_start_plan"
        const val SCHEMA_VERSION = 2
        const val PRIMARY_WORKSPACE = "workspace_v2"
        const val PRIMARY_CHECKSUM = "workspace_v2_checksum"
        const val BACKUP_WORKSPACE = "workspace_v2_backup"
        const val BACKUP_CHECKSUM = "workspace_v2_backup_checksum"
        const val CORRUPT_COPY = "workspace_unreadable_copy"
        const val LEGACY_PLAN = "plan"
    }
}
