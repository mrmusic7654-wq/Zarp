package com.example.data

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class AutomationEngine(
    private val deviceController: DeviceController,
    private val geminiRepository: GeminiRepository,
    private val context: Context
) {

    companion object {
        private const val TAG = "Automation"
        private const val PLANNING_MODEL = "models/gemini-2.5-flash"
        private const val STEP_DELAY_MS = 600L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val SCREEN_READ_MAX_CHARS = 3000
    }

    // ═══════════════════════════════════════════
    // Safety Manager
    // ═══════════════════════════════════════════

    val safetyManager = AutomationSafetyManager(context)
    private var isCancelled = false
    private var isPaused = false
    private var pauseLock = Object()

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class AutomationTask(
        val id: String = java.util.UUID.randomUUID().toString(),
        val userRequest: String,
        val steps: List<AutomationStep> = emptyList(),
        val status: TaskStatus = TaskStatus.IDLE,
        val result: AutomationResult? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class AutomationStep(
        val id: String = java.util.UUID.randomUUID().toString(),
        val description: String,
        val action: StepType,
        val target: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val text: String? = null,
        val packageName: String? = null,
        val waitMs: Long? = null,
        val status: StepStatus = StepStatus.PENDING,
        val output: String? = null,
        val screenshotPath: String? = null,
        val retryCount: Int = 0,
        val durationMs: Long = 0
    )

    enum class TaskStatus { IDLE, PLANNING, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED }
    enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }

    enum class StepType {
        OPEN_APP, TAP, LONG_PRESS, TYPE, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
        SCROLL_UP, SCROLL_DOWN, BACK, HOME, RECENTS, WAIT, READ_SCREEN, TAKE_SCREENSHOT,
        FIND_AND_TAP, FIND_AND_TYPE, SEND_MESSAGE, OPEN_URL, AI_ANALYZE_SCREEN, AI_DECIDE_NEXT
    }

    data class AutomationResult(
        val success: Boolean,
        val summary: String,
        val completedSteps: Int = 0,
        val totalSteps: Int = 0,
        val failedSteps: Int = 0,
        val cancelledByUser: Boolean = false,
        val errors: List<String> = emptyList(),
        val screenshots: List<String> = emptyList(),
        val totalDurationMs: Long = 0
    )

    data class AutomationProgress(
        val currentStep: Int,
        val totalSteps: Int,
        val description: String,
        val percentage: Float,
        val stepType: StepType? = null,
        val estimatedRemainingMs: Long = 0
    )

    // ═══════════════════════════════════════════
    // Main Automation Loop — with Safety
    // ═══════════════════════════════════════════

    suspend fun executeAutomation(
        userRequest: String,
        onProgress: (AutomationProgress) -> Unit = {},
        onScreenshot: ((String) -> Unit)? = null
    ): AutomationTask = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val task = AutomationTask(userRequest = userRequest)
        var consecutiveFailures = 0
        val allScreenshots = mutableListOf<String>()
        isCancelled = false
        isPaused = false

        // Start safety monitoring
        safetyManager.startMonitoring(
            onResume = {
                synchronized(pauseLock) {
                    isPaused = false
                    pauseLock.notifyAll()
                }
            },
            onCancel = {
                isCancelled = true
                synchronized(pauseLock) {
                    isPaused = false
                    pauseLock.notifyAll()
                }
            }
        )

        try {
            // Phase 1: Plan
            onProgress(AutomationProgress(0, 0, "Analyzing request...", 0.02f))
            Log.d(TAG, "🤖 Planning automation: ${userRequest.take(80)}")

            val planJson = createAutomationPlan(userRequest)
            if (planJson.isBlank()) {
                safetyManager.stopMonitoring()
                return@withContext task.copy(status = TaskStatus.FAILED, result = AutomationResult(false, "Failed to create a plan. Try rephrasing your request."))
            }

            val steps = parseAutomationSteps(planJson)
            if (steps.isEmpty()) {
                safetyManager.stopMonitoring()
                return@withContext task.copy(status = TaskStatus.FAILED, result = AutomationResult(false, "No steps generated. Try a simpler task."))
            }

            Log.d(TAG, "✅ Plan: ${steps.size} steps")
            val totalSteps = steps.size
            val completedSteps = mutableListOf<AutomationStep>()

            // Phase 2: Execute steps with pause/cancel checks
            for ((index, step) in steps.withIndex()) {
                // Check cancel
                if (isCancelled) {
                    Log.d(TAG, "⏹️ Cancelled by user")
                    completedSteps.add(step.copy(status = StepStatus.SKIPPED, output = "Cancelled by user"))
                    safetyManager.stopMonitoring()
                    return@withContext task.copy(
                        steps = completedSteps, status = TaskStatus.CANCELLED,
                        result = AutomationResult(false, "Automation cancelled by user.", completedSteps.count { it.status == StepStatus.COMPLETED }, totalSteps, completedSteps.count { it.status == StepStatus.FAILED }, true, completedSteps.filter { it.status == StepStatus.FAILED }.map { it.output ?: "Unknown" }, allScreenshots, System.currentTimeMillis() - startTime)
                    )
                }

                // Check pause
                synchronized(pauseLock) {
                    while (isPaused && !isCancelled) {
                        pauseLock.wait()
                    }
                }
                if (isCancelled) continue

                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.w(TAG, "⚠️ Stopping after $consecutiveFailures consecutive failures")
                    completedSteps.add(step.copy(status = StepStatus.SKIPPED, output = "Skipped — too many failures"))
                    break
                }

                val stepNum = index + 1
                val progress = stepNum.toFloat() / totalSteps
                val elapsed = System.currentTimeMillis() - startTime
                val estimatedRemaining = if (stepNum > 0) (elapsed / stepNum) * (totalSteps - stepNum) else 0

                onProgress(AutomationProgress(stepNum, totalSteps, step.description, progress, step.action, estimatedRemaining))
                safetyManager.updateProgress(stepNum, totalSteps, step.description)
                Log.d(TAG, "▶️ Step $stepNum/$totalSteps: ${step.action} | ${step.description}")

                val stepStart = System.currentTimeMillis()
                val executed = executeStep(step)
                val stepDuration = System.currentTimeMillis() - stepStart

                if (executed.status == StepStatus.COMPLETED) {
                    consecutiveFailures = 0
                    if (executed.screenshotPath != null) {
                        allScreenshots.add(executed.screenshotPath)
                        onScreenshot?.invoke(executed.screenshotPath)
                    }
                } else {
                    consecutiveFailures++
                    Log.w(TAG, "  ⚠️ Failed: ${executed.output}")
                }

                completedSteps.add(executed.copy(durationMs = stepDuration))
                if (index < steps.size - 1 && consecutiveFailures == 0) {
                    delay(step.waitMs ?: STEP_DELAY_MS)
                }
            }

            // Phase 3: Result
            val success = completedSteps.all { it.status == StepStatus.COMPLETED }
            val totalDuration = System.currentTimeMillis() - startTime
            val summary = buildSummary(completedSteps, success, totalDuration)

            onProgress(AutomationProgress(totalSteps, totalSteps, if (success) "✅ Complete!" else "⚠️ Done", 1f))
            safetyManager.stopMonitoring()

            task.copy(
                steps = completedSteps,
                status = if (success) TaskStatus.COMPLETED else TaskStatus.COMPLETED,
                result = AutomationResult(
                    success = success, summary = summary,
                    completedSteps = completedSteps.count { it.status == StepStatus.COMPLETED },
                    totalSteps = totalSteps,
                    failedSteps = completedSteps.count { it.status == StepStatus.FAILED },
                    errors = completedSteps.filter { it.status == StepStatus.FAILED }.map { it.output ?: "Unknown" },
                    screenshots = allScreenshots, totalDurationMs = totalDuration
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Automation crashed", e)
            safetyManager.stopMonitoring()
            task.copy(status = TaskStatus.FAILED, result = AutomationResult(false, "Crashed: ${e.localizedMessage}", errors = listOf(e.localizedMessage ?: "Crash")))
        }
    }

    // ═══════════════════════════════════════════
    // Step Executor
    // ═══════════════════════════════════════════

    private suspend fun executeStep(step: AutomationStep): AutomationStep {
        return try {
            when (step.action) {
                StepType.OPEN_APP -> executeOpenApp(step)
                StepType.TAP -> executeTap(step)
                StepType.LONG_PRESS -> executeLongPress(step)
                StepType.TYPE -> executeType(step)
                StepType.SWIPE_UP -> executeSwipe(step, DeviceController.ActionType.SWIPE_UP)
                StepType.SWIPE_DOWN -> executeSwipe(step, DeviceController.ActionType.SWIPE_DOWN)
                StepType.SWIPE_LEFT -> executeSwipe(step, DeviceController.ActionType.SWIPE_LEFT)
                StepType.SWIPE_RIGHT -> executeSwipe(step, DeviceController.ActionType.SWIPE_RIGHT)
                StepType.SCROLL_UP -> executeScroll(step, DeviceController.ActionType.SCROLL_UP)
                StepType.SCROLL_DOWN -> executeScroll(step, DeviceController.ActionType.SCROLL_DOWN)
                StepType.BACK -> executeSystemAction(DeviceController.ActionType.BACK, "Back")
                StepType.HOME -> executeSystemAction(DeviceController.ActionType.HOME, "Home")
                StepType.RECENTS -> executeSystemAction(DeviceController.ActionType.RECENTS, "Recents")
                StepType.WAIT -> executeWait(step)
                StepType.READ_SCREEN -> executeReadScreen()
                StepType.TAKE_SCREENSHOT -> executeTakeScreenshot()
                StepType.FIND_AND_TAP -> executeFindAndTap(step)
                StepType.FIND_AND_TYPE -> executeFindAndType(step)
                StepType.SEND_MESSAGE -> executeSendMessage(step)
                StepType.AI_ANALYZE_SCREEN -> executeAiAnalyze(step)
                StepType.AI_DECIDE_NEXT -> executeAiDecide(step)
                StepType.OPEN_URL -> executeOpenApp(step.copy(packageName = "com.android.chrome"))
                else -> step.copy(status = StepStatus.SKIPPED, output = "Action not implemented")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Step failed", e)
            step.copy(status = StepStatus.FAILED, output = "Error: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════
    // Action Implementations (unchanged from fixed version)
    // ═══════════════════════════════════════════

    private suspend fun executeOpenApp(step: AutomationStep): AutomationStep {
        val pkg = step.packageName ?: return step.copy(status = StepStatus.FAILED, output = "No package")
        val result = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.OPEN_APP, packageName = pkg, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeTap(step: AutomationStep): AutomationStep {
        val action = if (step.target != null) DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = step.target, description = step.description)
        else DeviceController.DeviceAction(DeviceController.ActionType.TAP, x = step.x ?: 540, y = step.y ?: 960, description = step.description)
        val result = deviceController.executeAction(action)
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeLongPress(step: AutomationStep): AutomationStep {
        val result = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.LONG_PRESS, x = step.x ?: 540, y = step.y ?: 960, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeType(step: AutomationStep): AutomationStep {
        val text = step.text ?: return step.copy(status = StepStatus.FAILED, output = "No text")
        val result = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.TYPE_TEXT, text = text, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeSwipe(step: AutomationStep, direction: DeviceController.ActionType): AutomationStep {
        val result = deviceController.executeAction(DeviceController.DeviceAction(direction, x = step.x ?: 540, y = step.y ?: 1200, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeScroll(step: AutomationStep, direction: DeviceController.ActionType): AutomationStep {
        val result = deviceController.executeAction(DeviceController.DeviceAction(direction, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeSystemAction(action: DeviceController.ActionType, label: String): AutomationStep {
        val s = AutomationStep(description = "Press $label", action = StepType.BACK)
        val result = deviceController.executeAction(DeviceController.DeviceAction(action, description = label))
        return s.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeWait(step: AutomationStep): AutomationStep {
        val waitMs = step.waitMs ?: step.text?.toLongOrNull() ?: 2000L
        delay(waitMs)
        return step.copy(status = StepStatus.COMPLETED, output = "Waited ${waitMs}ms", waitMs = waitMs)
    }

    private suspend fun executeReadScreen(): AutomationStep {
        val s = AutomationStep(description = "Read screen", action = StepType.READ_SCREEN)
        val content = deviceController.readScreen()
        return s.copy(status = StepStatus.COMPLETED, output = content?.take(SCREEN_READ_MAX_CHARS))
    }

    private suspend fun executeTakeScreenshot(): AutomationStep {
        val s = AutomationStep(description = "Screenshot", action = StepType.TAKE_SCREENSHOT)
        val result = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.SCREENSHOT, description = "Screenshot"))
        return s.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message, screenshotPath = if (result.success) "screenshot_${System.currentTimeMillis()}" else null)
    }

    private suspend fun executeFindAndTap(step: AutomationStep): AutomationStep {
        val target = step.target ?: return step.copy(status = StepStatus.FAILED, output = "No target")
        val result = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = target, description = step.description))
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, output = result.message)
    }

    private suspend fun executeFindAndType(step: AutomationStep): AutomationStep {
        val target = step.target ?: return step.copy(status = StepStatus.FAILED, output = "No target")
        val text = step.text ?: return step.copy(status = StepStatus.FAILED, output = "No text")
        val findResult = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = target, description = "Find $target"))
        if (!findResult.success) return step.copy(status = StepStatus.FAILED, output = "Could not find: $target")
        delay(500)
        val typeResult = deviceController.executeAction(DeviceController.DeviceAction(DeviceController.ActionType.TYPE_TEXT, text = text, description = "Type into $target"))
        return step.copy(status = if (typeResult.success) StepStatus.COMPLETED else StepStatus.FAILED, output = "Typed into $target")
    }

    private suspend fun executeSendMessage(step: AutomationStep): AutomationStep {
        val contact = step.target ?: return step.copy(status = StepStatus.FAILED, output = "No contact")
        val message = step.text ?: return step.copy(status = StepStatus.FAILED, output = "No message")
        val sequence = listOf(
            DeviceController.DeviceAction(DeviceController.ActionType.OPEN_APP, packageName = "com.whatsapp", description = "Open WhatsApp"),
            DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = "Search", description = "Tap search"),
            DeviceController.DeviceAction(DeviceController.ActionType.TYPE_TEXT, text = contact, description = "Type contact"),
            DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = contact, description = "Select contact"),
            DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = "Message", description = "Tap message field"),
            DeviceController.DeviceAction(DeviceController.ActionType.TYPE_TEXT, text = message, description = "Type message"),
            DeviceController.DeviceAction(DeviceController.ActionType.FIND_AND_TAP, target = "Send", description = "Send")
        )
        val results = deviceController.executeSequence(sequence)
        val allSuccess = results.all { it.success }
        return step.copy(status = if (allSuccess) StepStatus.COMPLETED else StepStatus.FAILED, output = if (allSuccess) "Sent to $contact" else "Send failed")
    }

    private suspend fun executeAiAnalyze(step: AutomationStep): AutomationStep {
        return try {
            val prompt = step.text ?: "Describe what's on the screen"
            val response = geminiRepository.generateResponse("Analyze: $prompt", PLANNING_MODEL)
            step.copy(status = StepStatus.COMPLETED, output = response)
        } catch (e: Exception) { step.copy(status = StepStatus.FAILED, output = "AI analysis failed") }
    }

    private suspend fun executeAiDecide(step: AutomationStep): AutomationStep {
        return try {
            val context = step.text ?: "What should I do next?"
            val response = geminiRepository.generateResponse("Based on: \"$context\"\nChoose ONE: TAP [element], SWIPE_UP, SWIPE_DOWN, TYPE [text], BACK, SCROLL_DOWN, DONE. Return ONLY the action.", PLANNING_MODEL)
            step.copy(status = StepStatus.COMPLETED, output = response.trim())
        } catch (e: Exception) { step.copy(status = StepStatus.FAILED, output = "AI decision failed") }
    }

    // ═══════════════════════════════════════════
    // Planning
    // ═══════════════════════════════════════════

    private suspend fun createAutomationPlan(userRequest: String): String = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext ""
            val model = GenerativeModel(modelName = PLANNING_MODEL, apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig { temperature = 0.2f; maxOutputTokens = 2048 })
            val prompt = "Create step-by-step automation for: $userRequest\nReturn ONLY JSON array. Actions: OPEN_APP, TAP, TYPE, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, SCROLL_UP, SCROLL_DOWN, BACK, HOME, WAIT, READ_SCREEN, TAKE_SCREENSHOT, FIND_AND_TAP, FIND_AND_TYPE, SEND_MESSAGE, AI_ANALYZE_SCREEN, AI_DECIDE_NEXT, OPEN_URL\nFormat: {\"description\":\"...\",\"action\":\"...\",\"target\":\"...\",\"text\":\"...\",\"packageName\":\"...\",\"waitMs\":...}"
            val response = model.generateContent(content { text(prompt) })
            response.text?.trim() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parseAutomationSteps(json: String): List<AutomationStep> {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONArray(cleaned)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AutomationStep(
                    description = obj.optString("description", "Step ${i + 1}"),
                    action = try { StepType.valueOf(obj.optString("action", "WAIT")) } catch (e: Exception) { StepType.WAIT },
                    target = obj.optString("target", null), text = obj.optString("text", null),
                    packageName = obj.optString("packageName", null),
                    waitMs = if (obj.has("waitMs")) obj.optLong("waitMs") else null
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun buildSummary(steps: List<AutomationStep>, success: Boolean, totalMs: Long): String = buildString {
        appendLine(if (success) "✅ Automation complete! (${totalMs / 1000}s)" else "⚠️ Done with issues")
        steps.forEachIndexed { i, step ->
            val icon = when (step.status) { StepStatus.COMPLETED -> "✅"; StepStatus.FAILED -> "❌"; StepStatus.SKIPPED -> "⏭️"; else -> "⏳" }
            appendLine("$icon ${i + 1}. ${step.description} (${step.durationMs}ms)")
        }
    }
}
