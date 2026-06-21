package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AgentLoopManager(
    private val geminiRepository: GeminiRepository,
    private val codeExecutionManager: CodeExecutionManager,
    private val gitHubAgent: GitHubAgent,
    private val context: Context
) {

    companion object {
        private const val TAG = "AgentLoop"
        private const val PLANNING_MODEL = "models/gemini-2.5-flash"
        private const val MAX_RETRIES = 3
    }

    data class AgentTask(
        val id: String = UUID.randomUUID().toString(),
        val userRequest: String,
        val steps: List<AgentStep> = emptyList(),
        val currentStep: Int = 0,
        val status: AgentStatus = AgentStatus.IDLE,
        val result: AgentResult? = null
    )

    data class AgentStep(
        val id: String = UUID.randomUUID().toString(),
        val description: String,
        val action: StepAction,
        val status: StepStatus = StepStatus.PENDING,
        val output: String? = null,
        val files: List<GitHubAgent.FileInfo> = emptyList(),
        val executionResult: CodeExecutionManager.ExecutionResult? = null,
        val reviewResult: CodeExecutionManager.CodeReview? = null
    )

    enum class AgentStatus { IDLE, PLANNING, EXECUTING, REVIEWING, FIXING, PUSHING, COMPLETED, FAILED }
    enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }
    enum class StepAction { GENERATE_CODE, EXECUTE_CODE, REVIEW_CODE, PUSH_TO_GITHUB, CREATE_PR, TEXT_RESPONSE }

    data class AgentResult(
        val success: Boolean,
        val summary: String,
        val repoUrl: String? = null,
        val prUrl: String? = null,
        val filesCreated: List<String> = emptyList(),
        val executionOutput: String? = null,
        val errors: List<String> = emptyList()
    )

    data class AgentProgress(
        val taskId: String,
        val status: AgentStatus,
        val currentStep: Int,
        val totalSteps: Int,
        val stepDescription: String,
        val message: String,
        val percentage: Float
    )

    // ═══════════════════════════════════════════
    // Main Agent Loop
    // ═══════════════════════════════════════════

    suspend fun executeAgentTask(
        userRequest: String,
        chatHistory: List<Message> = emptyList(),
        onProgress: (AgentProgress) -> Unit = {}
    ): AgentTask = withContext(Dispatchers.IO) {
        val task = AgentTask(userRequest = userRequest)

        try {
            // Phase 1: Plan
            onProgress(AgentProgress(task.id, AgentStatus.PLANNING, 0, 0, "Planning...", "Creating task plan", 0.1f))
            val plan = createPlan(userRequest, chatHistory)
            if (plan.isEmpty()) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Failed to create plan"))
            }

            val steps = parsePlanSteps(plan)
            val plannedTask = task.copy(steps = steps, status = AgentStatus.EXECUTING)
            val totalSteps = steps.size

            // Phase 2-4: Execute each step
            val completedSteps = mutableListOf<AgentStep>()
            val gitHubFiles = mutableListOf<GitHubAgent.FileInfo>()
            var executionOutput: String? = null

            for ((index, step) in steps.withIndex()) {
                val stepNum = index + 1
                val progress = (0.2f + (0.6f * stepNum / totalSteps))

                onProgress(AgentProgress(task.id, AgentStatus.EXECUTING, stepNum, totalSteps, step.description, "Executing step $stepNum/$totalSteps", progress))

                val executedStep = when (step.action) {
                    StepAction.GENERATE_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.EXECUTING, stepNum, totalSteps, "Generating code...", "Writing ${step.description}", progress))
                        executeGenerateCode(step, userRequest, chatHistory)
                    }
                    StepAction.EXECUTE_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.EXECUTING, stepNum, totalSteps, "Running code...", "Executing ${step.description}", progress))
                        executeRunCode(step)
                    }
                    StepAction.REVIEW_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.REVIEWING, stepNum, totalSteps, "Reviewing...", "Checking code quality", progress))
                        executeReviewCode(step)
                    }
                    StepAction.PUSH_TO_GITHUB -> {
                        onProgress(AgentProgress(task.id, AgentStatus.PUSHING, stepNum, totalSteps, "Pushing...", "Uploading to GitHub", progress))
                        executePushToGitHub(step, gitHubFiles)
                    }
                    StepAction.CREATE_PR -> {
                        onProgress(AgentProgress(task.id, AgentStatus.PUSHING, stepNum, totalSteps, "Creating PR...", "Opening pull request", progress))
                        executeCreatePR(step)
                    }
                    StepAction.TEXT_RESPONSE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.EXECUTING, stepNum, totalSteps, "Generating response...", step.description, progress))
                        executeTextResponse(step, userRequest, chatHistory)
                    }
                }

                completedSteps.add(executedStep)
                if (executedStep.files.isNotEmpty()) {
                    gitHubFiles.addAll(executedStep.files)
                }
                if (executedStep.executionResult?.output != null) {
                    executionOutput = executedStep.executionResult.output
                }
            }

            // Phase 5: Build result
            val success = completedSteps.all { it.status == StepStatus.COMPLETED }
            val summary = buildResultSummary(completedSteps, success)

            onProgress(AgentProgress(task.id, AgentStatus.COMPLETED, totalSteps, totalSteps, "Done", summary, 1f))

            plannedTask.copy(
                steps = completedSteps,
                status = if (success) AgentStatus.COMPLETED else AgentStatus.COMPLETED,
                result = AgentResult(
                    success = success,
                    summary = summary,
                    filesCreated = gitHubFiles.map { it.path },
                    executionOutput = executionOutput
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Agent loop failed", e)
            task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Error: ${e.localizedMessage}"))
        }
    }

    // ═══════════════════════════════════════════
    // Phase 1: Create Plan
    // ═══════════════════════════════════════════

    private suspend fun createPlan(
        userRequest: String,
        chatHistory: List<Message> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext ""
            val contextBlock = chatHistory.joinToString("\n") { "${if (it.isUser) "User" else "Zarp"}: ${it.text}" }

            val model = GenerativeModel(
                modelName = PLANNING_MODEL,
                apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                    temperature = 0.3f
                    maxOutputTokens = 2048
                }
            )

            val prompt = """
You are an AI task planner. Break down this user request into sequential steps.

User request: "$userRequest"

${if (contextBlock.isNotBlank()) "Conversation context:\n$contextBlock" else ""}

Return ONLY a JSON array of steps. Each step must have:
- "description": What this step does
- "action": One of: GENERATE_CODE, EXECUTE_CODE, REVIEW_CODE, PUSH_TO_GITHUB, CREATE_PR, TEXT_RESPONSE
- "language": Programming language if GENERATE_CODE (kotlin, python, java, etc.)

Example:
[
  {"description": "Create LoginViewModel.kt", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "Review all files for errors", "action": "REVIEW_CODE", "language": "kotlin"},
  {"description": "Push to GitHub", "action": "PUSH_TO_GITHUB", "language": ""}
]

Return ONLY the JSON array. No other text.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            response.text ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Plan creation failed", e)
            ""
        }
    }

    private fun parsePlanSteps(planJson: String): List<AgentStep> {
        return try {
            val cleaned = planJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONArray(cleaned)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AgentStep(
                    description = obj.optString("description", "Step ${i + 1}"),
                    action = try {
                        StepAction.valueOf(obj.optString("action", "TEXT_RESPONSE"))
                    } catch (e: Exception) {
                        StepAction.TEXT_RESPONSE
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse plan failed", e)
            listOf(AgentStep(description = "Fallback step", action = StepAction.TEXT_RESPONSE))
        }
    }

    // ═══════════════════════════════════════════
    // Step Executors
    // ═══════════════════════════════════════════

    private suspend fun executeGenerateCode(step: AgentStep, userRequest: String, chatHistory: List<Message>): AgentStep {
        return try {
            val response = geminiRepository.generateResponse(
                prompt = "Generate code for: ${step.description}. Full context: $userRequest. Return ONLY the code.",
                modelName = PLANNING_MODEL,
                chatHistory = chatHistory
            )
            val fileName = extractFileName(step.description) ?: "generated.kt"
            val file = GitHubAgent.FileInfo(path = fileName, content = extractCode(response, "kotlin"))
            step.copy(status = StepStatus.COMPLETED, output = response, files = listOf(file))
        } catch (e: Exception) {
            step.copy(status = StepStatus.FAILED, output = "Generation failed: ${e.localizedMessage}")
        }
    }

    private suspend fun executeRunCode(step: AgentStep): AgentStep {
        val code = step.output ?: return step.copy(status = StepStatus.FAILED, output = "No code to execute")
        val result = codeExecutionManager.executePython(code)
        return step.copy(status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED, executionResult = result, output = result.output)
    }

    private suspend fun executeReviewCode(step: AgentStep): AgentStep {
        val code = step.output ?: return step.copy(status = StepStatus.FAILED, output = "No code to review")
        val fixResult = codeExecutionManager.autoFixAndValidate(code, "kotlin", maxAttempts = MAX_RETRIES)
        return if (fixResult.success) {
            step.copy(status = StepStatus.COMPLETED, reviewResult = CodeExecutionManager.CodeReview(fixResult.score, emptyList(), emptyList(), true), output = fixResult.finalCode)
        } else {
            step.copy(status = StepStatus.FAILED, output = "Review and fix failed after ${fixResult.attempts.size} attempts")
        }
    }

    private suspend fun executePushToGitHub(step: AgentStep, files: List<GitHubAgent.FileInfo>): AgentStep {
        if (files.isEmpty()) return step.copy(status = StepStatus.SKIPPED, output = "No files to push")
        val repoName = "zarp-generated-${System.currentTimeMillis()}"
        val owner = "user" // TODO: get from authenticated user
        val result = gitHubAgent.pushFiles(owner, repoName, files)
        return if (result.success) {
            step.copy(status = StepStatus.COMPLETED, output = "Pushed to ${result.repo?.htmlUrl ?: repoName}")
        } else {
            step.copy(status = StepStatus.FAILED, output = "Push failed: ${result.error}")
        }
    }

    private suspend fun executeCreatePR(step: AgentStep): AgentStep {
        return step.copy(status = StepStatus.COMPLETED, output = "Pull request created (placeholder)")
    }

    private suspend fun executeTextResponse(step: AgentStep, userRequest: String, chatHistory: List<Message>): AgentStep {
        val response = geminiRepository.generateResponse(
            prompt = "Respond to: ${step.description}. Original request: $userRequest",
            modelName = PLANNING_MODEL,
            chatHistory = chatHistory
        )
        return step.copy(status = StepStatus.COMPLETED, output = response)
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun extractFileName(description: String): String? {
        val match = Regex("""(\w+\.(kt|java|py|js|ts|xml|json|gradle|kts))""").find(description)
        return match?.value
    }

    private fun extractCode(response: String, language: String): String {
        val regex = Regex("```$language\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return regex.find(response)?.groupValues?.get(1)?.trim() ?: response
    }

    private fun buildResultSummary(steps: List<AgentStep>, success: Boolean): String {
        val completed = steps.count { it.status == StepStatus.COMPLETED }
        val failed = steps.count { it.status == StepStatus.FAILED }
        val skipped = steps.count { it.status == StepStatus.SKIPPED }

        return buildString {
            appendLine(if (success) "✅ Task completed!" else "⚠️ Task completed with issues.")
            appendLine()
            appendLine("📊 Summary:")
            appendLine("   ✅ Completed: $completed")
            if (failed > 0) appendLine("   ❌ Failed: $failed")
            if (skipped > 0) appendLine("   ⏭️ Skipped: $skipped")
            appendLine()
            steps.forEachIndexed { i, step ->
                val icon = when (step.status) {
                    StepStatus.COMPLETED -> "✅"
                    StepStatus.FAILED -> "❌"
                    StepStatus.SKIPPED -> "⏭️"
                    else -> "⏳"
                }
                appendLine("$icon Step ${i + 1}: ${step.description}")
            }
        }
    }
}
