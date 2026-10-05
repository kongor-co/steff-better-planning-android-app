package com.safestart.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlanStore(context: Context) {
    private val preferences = context.getSharedPreferences("safe_start_plan", Context.MODE_PRIVATE)

    fun save(plan: Plan) {
        preferences.edit().putString("plan", encode(plan).toString()).apply()
    }

    fun load(): Plan {
        val raw = preferences.getString("plan", null) ?: return Plan()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(Plan())
    }

    private fun encode(plan: Plan): JSONObject = JSONObject().apply {
        put("startMinute", plan.startMinute)
        put("onboardingSeen", plan.onboardingSeen)
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
        put("templates", JSONArray().apply {
            plan.reusableActivities.forEach { template ->
                put(JSONObject().apply {
                    put("id", template.id)
                    put("title", template.title)
                    put("kind", template.kind.name)
                    put("durationMinutes", template.durationMinutes)
                    put("complexity", template.complexity.name)
                    put("bufferPercent", template.bufferPercent)
                    put("demandingness", template.demandingness.name)
                    put("pauseMinutes", template.pauseMinutes)
                })
            }
        })
    }

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
    }

    private fun decode(json: JSONObject): Plan {
        val anchors = json.getJSONArray("anchors").objects().map { item ->
            Anchor(
                id = item.getString("id"),
                title = item.getString("title"),
                startMinute = item.getInt("startMinute"),
                durationMinutes = item.nullableInt("durationMinutes")
            )
        }
        val activities = json.getJSONArray("activities").objects().map(::decodeActivity)
        val templatesArray = json.optJSONArray("templates") ?: JSONArray()
        val templates = templatesArray.objects().map { item ->
            ReusableActivity(
                id = item.getString("id"),
                title = item.getString("title"),
                kind = ActivityKind.valueOf(item.getString("kind")),
                durationMinutes = item.getInt("durationMinutes"),
                complexity = Complexity.valueOf(item.getString("complexity")),
                bufferPercent = item.getInt("bufferPercent"),
                demandingness = Demandingness.valueOf(item.getString("demandingness")),
                pauseMinutes = item.getInt("pauseMinutes")
            )
        }
        return Plan(
            startMinute = json.getInt("startMinute"),
            anchors = anchors,
            activities = activities,
            reusableActivities = templates,
            onboardingSeen = json.optBoolean("onboardingSeen", false)
        )
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
        templateId = if (item.isNull("templateId")) null else item.getString("templateId")
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
}
