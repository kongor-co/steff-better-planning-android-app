package com.safestart.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

private val Ink = Color(0xFF17332D)
private val Cream = Color(0xFFF7F5EF)
private val Paper = Color(0xFFFFFDF8)
private val Sage = Color(0xFFDDE8DD)
private val Moss = Color(0xFF315D50)
private val Clay = Color(0xFFB85C42)
private val Amber = Color(0xFFF3C969)
private val Muted = Color(0xFF61706B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SafeStartApp() }
    }
}

private sealed interface Screen {
    data object Plan : Screen
    data object Start : Screen
    data class Activity(val activityId: String? = null, val templateId: String? = null) : Screen
    data class Anchor(val anchorId: String? = null) : Screen
    data object Library : Screen
    data object Help : Screen
}

@Composable
private fun SafeStartApp() {
    val context = LocalContext.current
    val store = remember { PlanStore(context) }
    var plan by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Plan) }
    var showOnboarding by remember { mutableStateOf(!plan.onboardingSeen) }

    fun commit(updated: Plan) {
        plan = updated
        store.save(updated)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Moss,
            onPrimary = Color.White,
            secondary = Clay,
            background = Cream,
            surface = Paper,
            onSurface = Ink,
            onBackground = Ink
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
            when (val destination = screen) {
                Screen.Plan -> PlanScreen(
                    plan = plan,
                    onStart = { screen = Screen.Start },
                    onAddActivity = { screen = Screen.Activity() },
                    onEditActivity = { screen = Screen.Activity(activityId = it) },
                    onAddAnchor = { screen = Screen.Anchor() },
                    onEditAnchor = { screen = Screen.Anchor(it) },
                    onLibrary = { screen = Screen.Library },
                    onHelp = { screen = Screen.Help },
                    onReorder = { firstId, secondId ->
                        val mutable = plan.activities.toMutableList()
                        val first = mutable.indexOfFirst { it.id == firstId }
                        val second = mutable.indexOfFirst { it.id == secondId }
                        if (first >= 0) {
                            if (second >= 0) {
                                val held = mutable[first]
                                mutable[first] = mutable[second]
                                mutable[second] = held
                                commit(plan.copy(activities = mutable))
                            }
                        }
                    }
                )
                Screen.Start -> StartEditor(
                    initial = plan.startMinute,
                    onBack = { screen = Screen.Plan },
                    onSave = { minute ->
                        val candidate = plan.copy(startMinute = minute)
                        val error = if (candidate.anchors.isEmpty()) null else PlannerEngine.validate(candidate)
                        if (error == null) {
                            commit(candidate)
                            screen = Screen.Plan
                        }
                        error
                    }
                )
                is Screen.Activity -> ActivityEditor(
                    plan = plan,
                    activityId = destination.activityId,
                    templateId = destination.templateId,
                    onBack = { screen = Screen.Plan },
                    onCommit = { updated -> commit(updated) },
                    onDone = { screen = Screen.Plan },
                    onEditOther = { screen = Screen.Activity(activityId = it) }
                )
                is Screen.Anchor -> AnchorEditor(
                    plan = plan,
                    anchorId = destination.anchorId,
                    onBack = { screen = Screen.Plan },
                    onSave = { candidate ->
                        val error = PlannerEngine.validate(candidate)
                        if (error == null) {
                            commit(candidate)
                            screen = Screen.Plan
                        }
                        error
                    },
                    onDelete = { anchorId ->
                        commit(plan.copy(
                            anchors = plan.anchors.filterNot { it.id == anchorId },
                            activities = plan.activities.filterNot { it.windowEndAnchorId == anchorId }
                        ))
                        screen = Screen.Plan
                    }
                )
                Screen.Library -> LibraryScreen(
                    plan = plan,
                    onBack = { screen = Screen.Plan },
                    onInsert = { screen = Screen.Activity(templateId = it) },
                    onDelete = { id ->
                        commit(plan.copy(reusableActivities = plan.reusableActivities.filterNot { it.id == id }))
                    }
                )
                Screen.Help -> HelpScreen(onBack = { screen = Screen.Plan })
            }
        }

        if (showOnboarding) {
            OnboardingDialog {
                showOnboarding = false
                commit(plan.copy(onboardingSeen = true))
            }
        }
    }
}

@Composable
private fun AppHeader(title: String, onBack: (() -> Unit)? = null, action: (() -> Unit)? = null, actionLabel: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(6.dp))
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = action) { Text(actionLabel) }
    }
}

@Composable
private fun PlanScreen(
    plan: Plan,
    onStart: () -> Unit,
    onAddActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
    onAddAnchor: () -> Unit,
    onEditAnchor: (String) -> Unit,
    onLibrary: () -> Unit,
    onHelp: () -> Unit,
    onReorder: (String, String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("Safe Start", action = onHelp, actionLabel = "How it works")
        if (plan.anchors.isEmpty()) {
            EmptyPlan(onStart, onAddAnchor)
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AVAILABLE FROM", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(PlannerEngine.formatTime(plan.startMinute), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onStart) { Text("Change") }
                }
                val overall = PlannerEngine.overallCapacity(plan)
                Text("$overall% planned overall", color = Muted)
                LinearProgressIndicator(
                    progress = { (overall.coerceAtMost(100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = capacityColor(overall),
                    trackColor = Sage
                )
            }
            items(PlannerEngine.windows(plan), key = { it.endAnchor.id }) { window ->
                WindowCard(window, onEditActivity, onEditAnchor, onReorder)
            }
        }
        Surface(color = Paper, tonalElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("Reusable") }
                OutlinedButton(onClick = onAddAnchor, modifier = Modifier.weight(1f)) { Text("Add Anchor") }
                Button(onClick = onAddActivity, modifier = Modifier.weight(1f)) { Text("Add activity") }
            }
        }
    }
}

@Composable
private fun EmptyPlan(onStart: () -> Unit, onAddAnchor: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(74.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
            Text("5", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Moss)
        }
        Spacer(Modifier.height(24.dp))
        Text("What do you need to be ready for?", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Choose when you are available, add a fixed commitment, then fit only what can realistically happen before it.",
            color = Muted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Set available time") }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onAddAnchor, modifier = Modifier.fillMaxWidth()) { Text("Create a plan") }
    }
}

@Composable
private fun WindowCard(
    window: PlanningWindow,
    onEditActivity: (String) -> Unit,
    onEditAnchor: (String) -> Unit,
    onReorder: (String, String) -> Unit
) {
    val planned = PlannerEngine.plannedMinutes(window)
    val available = PlannerEngine.availableMinutes(window)
    val capacity = PlannerEngine.capacityPercent(window)
    val schedule = PlannerEngine.schedule(window)
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${PlannerEngine.formatTime(window.startMinute)} to ${PlannerEngine.formatTime(window.endAnchor.startMinute)}",
                fontSize = 13.sp,
                color = Muted,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("LATEST SAFE START", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(PlannerEngine.formatTime(PlannerEngine.latestSafeStart(window)), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Text("$capacity% planned", color = capacityColor(capacity), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { capacity.coerceAtMost(100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = capacityColor(capacity),
                trackColor = Sage
            )
            if (capacity >= 80) {
                Surface(color = if (capacity == 100) Clay.copy(alpha = 0.13f) else Amber.copy(alpha = 0.24f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (capacity == 100) "This planning window is full." else "Your plan is getting full. Keep the remaining time free for unexpected events.",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                }
            }
            Text(
                "${PlannerEngine.formatDuration(planned)} reserved of ${PlannerEngine.formatDuration(available)}",
                color = Muted
            )
            val freeEnd = PlannerEngine.latestSafeStart(window)
            if (freeEnd > window.startMinute) {
                Surface(color = Sage.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Free", fontWeight = FontWeight.SemiBold)
                        Text("${PlannerEngine.formatTime(window.startMinute)} to ${PlannerEngine.formatTime(freeEnd)}", color = Muted)
                    }
                }
            }
            schedule.forEachIndexed { index, item ->
                ActivityRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < schedule.lastIndex,
                    onEdit = { onEditActivity(item.activity.id) },
                    onMoveUp = { onReorder(item.activity.id, schedule[index - 1].activity.id) },
                    onMoveDown = { onReorder(item.activity.id, schedule[index + 1].activity.id) }
                )
            }
            Surface(
                onClick = { onEditAnchor(window.endAnchor.id) },
                color = Ink,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ANCHOR", color = Sage, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(window.endAnchor.title, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(PlannerEngine.formatTime(window.endAnchor.startMinute), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: ScheduledActivity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(onClick = onEdit, color = Cream, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (item.activity.kind == ActivityKind.OPTIONAL) {
                    Text("OPTIONAL", color = Clay, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(item.activity.title, fontWeight = FontWeight.Bold)
                Text(
                    "${PlannerEngine.formatTime(item.startMinute)} to ${PlannerEngine.formatTime(item.endMinute)}  ·  ${PlannerEngine.formatDuration(PlannerEngine.reservedMinutes(item.activity))}",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
            Column {
                TextButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.height(36.dp)) { Text("Up") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.height(36.dp)) { Text("Down") }
            }
        }
    }
}

@Composable
private fun StartEditor(initial: Int, onBack: () -> Unit, onSave: (Int) -> String?) {
    var minute by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Available from", onBack)
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This is the earliest time you can begin planned activities.", color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            TimeStepper(minute = minute, onChange = { minute = it })
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                val now = LocalTime.now()
                minute = PlannerEngine.roundClockUpToFive(now.hour, now.minute)
            }) { Text("Use Now, rounded up") }
            error?.let { ErrorText(it) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { error = onSave(minute) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) { Text("Save available time") }
        }
    }
}

@Composable
private fun TimeStepper(minute: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = { onChange((minute - 5 + 1440) % 1440) }) { Text("− 5") }
        Text(PlannerEngine.formatTime(minute), fontSize = 42.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange((minute + 5) % 1440) }) { Text("+ 5") }
    }
}

@Composable
private fun ActivityEditor(
    plan: Plan,
    activityId: String?,
    templateId: String?,
    onBack: () -> Unit,
    onCommit: (Plan) -> Unit,
    onDone: () -> Unit,
    onEditOther: (String) -> Unit
) {
    val existing = plan.activities.find { it.id == activityId }
    val template = plan.reusableActivities.find { it.id == templateId }
    var title by remember { mutableStateOf(existing?.title ?: template?.title.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: template?.kind ?: ActivityKind.TASK) }
    var duration by remember { mutableStateOf(existing?.durationMinutes ?: template?.durationMinutes ?: 0) }
    var complexity by remember { mutableStateOf(existing?.complexity ?: template?.complexity ?: Complexity.MEDIUM) }
    var buffer by remember { mutableStateOf(existing?.bufferPercent ?: template?.bufferPercent ?: complexity.suggestedBuffer) }
    var demandingness by remember { mutableStateOf(existing?.demandingness ?: template?.demandingness ?: Demandingness.NORMAL) }
    var pause by remember { mutableStateOf(existing?.pauseMinutes ?: template?.pauseMinutes ?: demandingness.suggestedPause) }
    var anchorId by remember { mutableStateOf(existing?.windowEndAnchorId ?: plan.anchors.sortedBy { it.startMinute }.firstOrNull()?.id.orEmpty()) }
    var saveReusable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var bufferAcknowledged by remember { mutableStateOf(false) }
    var pauseAcknowledged by remember { mutableStateOf(false) }
    var showBufferConfirm by remember { mutableStateOf(false) }
    var showPauseConfirm by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<CapacityConflict?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun makeActivity() = PlannedActivity(
        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
        title = title.trim(),
        kind = kind,
        durationMinutes = duration,
        complexity = complexity,
        bufferPercent = buffer,
        demandingness = demandingness,
        pauseMinutes = pause,
        windowEndAnchorId = anchorId,
        templateId = existing?.templateId ?: template?.id
    )

    fun saveActivity() {
        if (title.isBlank()) {
            error = "Add a name for this activity."
            return
        }
        if (duration <= 0) {
            error = "Add a duration before saving this activity."
            return
        }
        if (anchorId.isBlank()) {
            error = "Add an Anchor before adding activities."
            return
        }
        if (buffer < complexity.suggestedBuffer) {
            if (!bufferAcknowledged) {
                showBufferConfirm = true
                return
            }
        }
        if (pause == 0) {
            if (!pauseAcknowledged) {
                showPauseConfirm = true
                return
            }
        }
        val activity = makeActivity()
        val activities = if (existing == null) plan.activities + activity else plan.activities.map { if (it.id == existing.id) activity else it }
        var candidate = plan.copy(activities = activities)
        val target = PlannerEngine.windows(candidate).find { it.endAnchor.id == anchorId }
        if (target != null) {
            val remaining = PlannerEngine.remainingMinutes(target)
            if (remaining < 0) {
                conflict = CapacityConflict(-remaining, target, activity.title, activity.id)
                return
            }
        }
        if (saveReusable) {
            val reusable = ReusableActivity(
                title = activity.title,
                kind = activity.kind,
                durationMinutes = activity.durationMinutes,
                complexity = activity.complexity,
                bufferPercent = activity.bufferPercent,
                demandingness = activity.demandingness,
                pauseMinutes = activity.pauseMinutes
            )
            candidate = candidate.copy(reusableActivities = candidate.reusableActivities + reusable)
        }
        onCommit(candidate)
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(if (existing == null) "Add activity" else "Edit activity", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SectionLabel("PRIORITY")
                ChoiceRow(ActivityKind.entries, kind, { kind = it }) { if (it == ActivityKind.TASK) "Task" else "Optional" }
            }
            item {
                SectionLabel("WHAT ARE YOU DOING?")
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Activity name") })
            }
            item {
                SectionLabel("ACTIVE DURATION")
                DurationStepper(duration) { duration = it.coerceIn(0, 415) }
            }
            item {
                SectionLabel("COMPLEXITY")
                ChoiceRow(Complexity.entries, complexity, {
                    complexity = it
                    buffer = it.suggestedBuffer
                    bufferAcknowledged = false
                }) { it.name.lowercase().replaceFirstChar(Char::uppercase) }
                Text(complexity.description, color = Muted, fontSize = 13.sp)
            }
            item {
                SectionLabel("BUFFER")
                ScrollChoice((20..60 step 5).toList(), buffer, {
                    buffer = it
                    bufferAcknowledged = false
                }) { "$it%" }
                Text("Suggested: ${complexity.suggestedBuffer}%  ·  Adds ${PlannerEngine.formatDuration(PlannerEngine.bufferMinutes(duration, buffer))}", color = Muted, fontSize = 13.sp)
            }
            item {
                SectionLabel("DEMANDINGNESS")
                ChoiceRow(Demandingness.entries, demandingness, {
                    demandingness = it
                    pause = it.suggestedPause
                    pauseAcknowledged = false
                }) { it.name.lowercase().replaceFirstChar(Char::uppercase) }
            }
            item {
                SectionLabel("PAUSE")
                ScrollChoice(listOf(0, 5, 10, 15, 20, 30), pause, {
                    pause = it
                    pauseAcknowledged = false
                }) { if (it == 0) "None" else "$it min" }
                Text("Suggested: ${demandingness.suggestedPause} min", color = Muted, fontSize = 13.sp)
            }
            if (plan.anchors.size > 1) {
                item {
                    SectionLabel("PLANNING WINDOW")
                    ScrollChoice(plan.anchors.sortedBy { it.startMinute }, plan.anchors.find { it.id == anchorId }, { anchorId = it.id }) {
                        "Before ${it.title}"
                    }
                }
            }
            item {
                val preview = makeActivity()
                Surface(color = Sage, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("TOTAL RESERVED", color = Moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(PlannerEngine.formatDuration(PlannerEngine.reservedMinutes(preview)), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Duration, buffer, and pause", color = Muted)
                    }
                }
            }
            if (existing == null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveReusable, onCheckedChange = { saveReusable = it })
                        Text("Save as reusable activity")
                    }
                }
            }
            if (existing?.templateId != null) {
                item {
                    OutlinedButton(
                        onClick = {
                            val activity = makeActivity()
                            val templates = plan.reusableActivities.map {
                                if (it.id == existing.templateId) {
                                    it.copy(
                                        title = activity.title,
                                        kind = activity.kind,
                                        durationMinutes = activity.durationMinutes,
                                        complexity = activity.complexity,
                                        bufferPercent = activity.bufferPercent,
                                        demandingness = activity.demandingness,
                                        pauseMinutes = activity.pauseMinutes
                                    )
                                } else it
                            }
                            onCommit(plan.copy(reusableActivities = templates))
                            error = "Saved activity updated."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update saved activity") }
                }
            }
            item { error?.let { ErrorText(it) } }
            if (existing != null) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove activity", color = Clay)
                    }
                }
            }
        }
        Button(
            onClick = ::saveActivity,
            modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()
        ) { Text(if (existing == null) "Add to plan" else "Save changes") }
    }

    if (showBufferConfirm) {
        AlertDialog(
            onDismissRequest = { showBufferConfirm = false },
            title = { Text("Reduce the recommended buffer?") },
            text = { Text("${complexity.name.lowercase().replaceFirstChar(Char::uppercase)} complexity suggests ${complexity.suggestedBuffer}%. Are you sure you want to use $buffer%?") },
            dismissButton = { TextButton(onClick = { buffer = complexity.suggestedBuffer; showBufferConfirm = false }) { Text("Keep ${complexity.suggestedBuffer}%") } },
            confirmButton = { Button(onClick = { bufferAcknowledged = true; showBufferConfirm = false; saveActivity() }) { Text("Use $buffer%") } }
        )
    }
    if (showPauseConfirm) {
        AlertDialog(
            onDismissRequest = { showPauseConfirm = false },
            title = { Text("Skip the pause?") },
            text = { Text("A pause was suggested because this activity takes effort. Continue without one?") },
            dismissButton = { TextButton(onClick = { pause = demandingness.suggestedPause; showPauseConfirm = false }) { Text("Keep pause") } },
            confirmButton = { Button(onClick = { pauseAcknowledged = true; showPauseConfirm = false; saveActivity() }) { Text("Skip pause") } }
        )
    }
    conflict?.let { activeConflict ->
        ConflictDialog(
            conflict = activeConflict,
            proposedId = activeConflict.proposedActivityId,
            onCancel = { conflict = null },
            onEdit = { id -> conflict = null; onEditOther(id) },
            onRemove = { id ->
                onCommit(plan.copy(activities = plan.activities.filterNot { it.id == id }))
                conflict = null
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this activity?") },
            text = { Text("It will be removed from this plan. A reusable version, if present, stays in your library.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            confirmButton = { Button(onClick = {
                onCommit(plan.copy(activities = plan.activities.filterNot { it.id == existing?.id }))
                onDone()
            }) { Text("Remove") } }
        )
    }
}

@Composable
private fun ConflictDialog(
    conflict: CapacityConflict,
    proposedId: String?,
    onCancel: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val candidates = conflict.window.activities
        .filterNot { it.id == proposedId }
        .sortedBy { if (it.kind == ActivityKind.OPTIONAL) 0 else 1 }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("This does not fit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You need ${PlannerEngine.formatDuration(conflict.shortageMinutes)} more before ${conflict.window.endAnchor.title}.")
                Text("Shorten the new activity, or free capacity:", fontWeight = FontWeight.SemiBold)
                candidates.take(4).forEach { candidate ->
                    Surface(color = Cream, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(candidate.title, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onEdit(candidate.id) }) { Text("Edit") }
                            TextButton(onClick = { onRemove(candidate.id) }) { Text("Remove", color = Clay) }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        confirmButton = { Button(onClick = onCancel) { Text("Shorten this activity") } }
    )
}

@Composable
private fun AnchorEditor(
    plan: Plan,
    anchorId: String?,
    onBack: () -> Unit,
    onSave: (Plan) -> String?,
    onDelete: (String) -> Unit
) {
    val existing = plan.anchors.find { it.id == anchorId }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var time by remember { mutableStateOf(existing?.startMinute ?: ((plan.startMinute + 120) / 5 * 5)) }
    var hasDuration by remember { mutableStateOf(existing?.durationMinutes != null) }
    var duration by remember { mutableStateOf(existing?.durationMinutes ?: 60) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppHeader(if (existing == null) "Add Anchor" else "Edit Anchor", onBack)
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A fixed-time commitment that planned activities cannot cross.", color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Anchor title") }, placeholder = { Text("Appointment") })
            Spacer(Modifier.height(24.dp))
            SectionLabel("START TIME")
            TimeStepper(time) { time = it }
            Text("Choose the closest earlier time if the exact time is unavailable.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = hasDuration, onCheckedChange = { hasDuration = it })
                Column {
                    Text("This Anchor has a duration", fontWeight = FontWeight.SemiBold)
                    Text("Required when another Anchor follows", color = Muted, fontSize = 12.sp)
                }
            }
            if (hasDuration) DurationStepper(duration) { duration = it.coerceIn(5, 415) }
            error?.let { ErrorText(it) }
            Spacer(Modifier.weight(1f))
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }) { Text("Remove Anchor", color = Clay) }
            }
            Button(onClick = {
                if (title.isBlank()) {
                    error = "Add a title for this Anchor."
                } else {
                    val anchor = Anchor(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        title = title.trim(),
                        startMinute = time,
                        durationMinutes = if (hasDuration) duration else null
                    )
                    val anchors = if (existing == null) plan.anchors + anchor else plan.anchors.map { if (it.id == existing.id) anchor else it }
                    error = onSave(plan.copy(anchors = anchors.sortedBy { it.startMinute }))
                }
            }, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text("Save Anchor") }
        }
    }
    if (confirmDelete) {
        val count = plan.activities.count { it.windowEndAnchorId == existing?.id }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this Anchor?") },
            text = { Text(if (count == 0) "The planning window will be removed." else "$count activities in this planning window will also be removed.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
            confirmButton = { Button(onClick = { existing?.id?.let(onDelete) }) { Text("Remove") } }
        )
    }
}

@Composable
private fun LibraryScreen(plan: Plan, onBack: () -> Unit, onInsert: (String) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("Reusable activities", onBack)
        if (plan.reusableActivities.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your reusable library is empty", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("Choose “Save as reusable activity” when adding something you often plan.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(plan.reusableActivities, key = { it.id }) { template ->
                    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(template.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${PlannerEngine.formatDuration(template.durationMinutes)} active  ·  ${template.bufferPercent}% buffer  ·  ${template.pauseMinutes} min pause",
                                color = Muted,
                                fontSize = 13.sp
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onDelete(template.id) }) { Text("Delete", color = Clay) }
                                Button(onClick = { onInsert(template.id) }, enabled = plan.anchors.isNotEmpty()) { Text("Add to plan") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppHeader("How it works", onBack)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HelpCard("Anchor", "A fixed-time commitment, such as an appointment or train departure.") }
            item { HelpCard("Buffer", "Mandatory extra time for the uncertainty in every Task. It is always rounded up.") }
            item { HelpCard("Pause", "Intentional recovery after an activity. You can skip it after confirming.") }
            item { HelpCard("Latest safe start", "The latest time the first Task can begin and still reach the next Anchor on time.") }
            item { HelpCard("Capacity", "How much of a planning window is allocated. At 80% the app warns you. Above 100% is never saved.") }
            item {
                Surface(color = Sage, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("A quick example", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Appointment at 17:30", color = Muted)
                        Text("1h Task + 25m buffer + 10m pause", color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Text("Latest safe start: 15:55", fontWeight = FontWeight.Bold, color = Moss)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Muted, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun OnboardingDialog(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Plan what actually fits") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Safe Start works backwards from your fixed commitment.")
                HelpCard("1. Add an Anchor", "Tell the app when you have to be somewhere.")
                HelpCard("2. Add activities", "Every Task gets a realistic buffer and a suggested pause.")
                HelpCard("3. Start safely", "See the latest time you can begin without running late.")
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Start planning") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun DurationStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onChange(value - 5) }, enabled = value > 0) { Text("− 5") }
        Text(PlannerEngine.formatDuration(value), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + 5) }, enabled = value < 415) { Text("+ 5") }
    }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun <T> ScrollChoice(values: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Surface(color = Clay.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(message, color = Clay, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
    }
}

private fun capacityColor(capacity: Int): Color = when {
    capacity >= 100 -> Clay
    capacity >= 80 -> Color(0xFF9B6A00)
    else -> Moss
}
