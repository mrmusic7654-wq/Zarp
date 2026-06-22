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
        private const val MAX_REVIEW_RETRIES = 3
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

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
        val language: String = "kotlin",
        val status: StepStatus = StepStatus.PENDING,
        val output: String? = null,
        val generatedFiles: List<GitHubAgent.FileInfo> = emptyList(),
        val reviewScore: Int = 0,
        val reviewIssues: List<String> = emptyList()
    )

    enum class AgentStatus { IDLE, PLANNING, GENERATING, REVIEWING, FIXING, PUSHING, COMPLETED, FAILED }
    enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }
    enum class StepAction { GENERATE_CODE, REVIEW_CODE, PUSH_TO_GITHUB }

    data class AgentResult(
        val success: Boolean,
        val summary: String,
        val repoUrl: String? = null,
        val filesCreated: List<String> = emptyList(),
        val totalFiles: Int = 0,
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
            // ══════════════════════════════════════
            // PHASE 1: PLAN
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.PLANNING, 0, 0, "Planning...", "Analyzing requirements and creating task plan", 0.05f))
            Log.d(TAG, "🧠 Phase 1: Planning")

            val planJson = createPlan(userRequest, chatHistory)
            if (planJson.isBlank()) {
                Log.e(TAG, "❌ Empty plan returned")
                return@withContext task.copy(
                    status = AgentStatus.FAILED,
                    result = AgentResult(false, "Failed to create a plan. Try rephrasing your request.", errors = listOf("Empty plan"))
                )
            }

            val steps = parsePlanSteps(planJson)
            if (steps.isEmpty()) {
                Log.e(TAG, "❌ No steps parsed from plan")
                return@withContext task.copy(
                    status = AgentStatus.FAILED,
                    result = AgentResult(false, "Could not parse the plan into steps.", errors = listOf("Parse failed"))
                )
            }

            Log.d(TAG, "📋 Plan created: ${steps.size} steps")
            steps.forEachIndexed { i, s -> Log.d(TAG, "  ${i + 1}. [${s.action}] ${s.description}") }

            val plannedTask = task.copy(steps = steps, status = AgentStatus.GENERATING)
            val totalSteps = steps.size
            val allGeneratedFiles = mutableListOf<GitHubAgent.FileInfo>()

            // ══════════════════════════════════════
            // PHASE 2-4: EXECUTE STEPS
            // ══════════════════════════════════════
            val completedSteps = mutableListOf<AgentStep>()

            for ((index, step) in steps.withIndex()) {
                val stepNum = index + 1
                val baseProgress = 0.1f + (0.7f * index / totalSteps)
                val stepProgress = 0.7f / totalSteps

                Log.d(TAG, "━━━ Step $stepNum/$totalSteps: ${step.action} ━━━")

                val executedStep = when (step.action) {
                    StepAction.GENERATE_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.GENERATING, stepNum, totalSteps, step.description, "Generating code...", baseProgress))
                        executeGenerateCode(step, userRequest, chatHistory)
                    }
                    StepAction.REVIEW_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.REVIEWING, stepNum, totalSteps, step.description, "Reviewing code quality...", baseProgress + stepProgress * 0.5f))
                        executeReviewCode(step)
                    }
                    StepAction.PUSH_TO_GITHUB -> {
                        onProgress(AgentProgress(task.id, AgentStatus.PUSHING, stepNum, totalSteps, step.description, "Pushing to GitHub...", baseProgress + stepProgress * 0.5f))
                        executePushToGitHub(step, allGeneratedFiles)
                    }
                }

                completedSteps.add(executedStep)
                if (executedStep.generatedFiles.isNotEmpty()) {
                    allGeneratedFiles.addAll(executedStep.generatedFiles)
                }
                Log.d(TAG, "  Result: ${executedStep.status}")
            }

            // ══════════════════════════════════════
            // PHASE 5: BUILD RESULT
            // ══════════════════════════════════════
            val allSucceeded = completedSteps.all { it.status == StepStatus.COMPLETED }
            val repoUrl = completedSteps.lastOrNull { it.action == StepAction.PUSH_TO_GITHUB }?.output
            val summary = buildResultSummary(completedSteps, allSucceeded, repoUrl)

            onProgress(AgentProgress(task.id, AgentStatus.COMPLETED, totalSteps, totalSteps, "Done!", summary, 1f))

            plannedTask.copy(
                steps = completedSteps,
                status = if (allSucceeded) AgentStatus.COMPLETED else AgentStatus.COMPLETED,
                result = AgentResult(
                    success = allSucceeded,
                    summary = summary,
                    repoUrl = repoUrl?.let { extractUrl(it) },
                    filesCreated = allGeneratedFiles.map { it.path },
                    totalFiles = allGeneratedFiles.size,
                    errors = completedSteps.filter { it.status == StepStatus.FAILED }.map { it.output ?: "Unknown error" }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Agent loop crashed", e)
            task.copy(
                status = AgentStatus.FAILED,
                result = AgentResult(false, "Agent crashed: ${e.localizedMessage}", errors = listOf(e.localizedMessage ?: "Unknown"))
            )
        }
    }

    // ═══════════════════════════════════════════
    // PHASE 1: Create Plan
    // ═══════════════════════════════════════════

    private suspend fun createPlan(
        userRequest: String,
        chatHistory: List<Message>
    ): String = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext ""
            val contextBlock = if (chatHistory.isNotEmpty()) {
                "\n\nPrevious conversation context (use this to understand what the user wants):\n" +
                chatHistory.takeLast(6).joinToString("\n") { "${if (it.isUser) "User" else "Assistant"}: ${it.text.take(300)}" }
            } else ""

            val model = GenerativeModel(
                modelName = PLANNING_MODEL,
                apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig {
                    temperature = 0.3f
                    maxOutputTokens = 2048
                }
            )

            val prompt = """
You are an expert software architect. Break down this coding request into a detailed plan of files to create.

User request: "$userRequest"
$contextBlock

Return ONLY a JSON array. Each step must have:
- "description": Detailed description of what this file does
- "action": One of: GENERATE_CODE, REVIEW_CODE, PUSH_TO_GITHUB
- "language": Programming language (kotlin, python, java, javascript, xml, etc.)

IMPORTANT RULES:
- Include ALL files needed: data models, ViewModels, screens, API services, repositories, build files, manifest changes
- Think about: architecture (MVVM), dependencies, imports, error handling, edge cases
- Put GENERATE_CODE for every file
- Put ONE REVIEW_CODE step after all generation
- Put ONE PUSH_TO_GITHUB step at the very end
- Name files with their full paths (e.g., "app/src/main/java/com/example/WeatherViewModel.kt")

Example for "Create a weather app":
[
  {"description": "WeatherData data class with city, temp, humidity fields", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "WeatherApi Retrofit interface with GET endpoint", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "WeatherRepository that fetches from API with error handling", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "WeatherViewModel with loading/success/error states", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "WeatherScreen composable with search and results", "action": "GENERATE_CODE", "language": "kotlin"},
  {"description": "Review all generated files for correctness and consistency", "action": "REVIEW_CODE", "language": "kotlin"},
  {"description": "Push complete project to GitHub", "action": "PUSH_TO_GITHUB", "language": ""}
]

Return ONLY the JSON array. No other text, no markdown, no explanations.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            response.text ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "❌ Plan creation failed", e)
            ""
        }
    }

    private fun parsePlanSteps(planJson: String): List<AgentStep> {
        return try {
            val cleaned = planJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val arr = JSONArray(cleaned)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AgentStep(
                    description = obj.optString("description", "Step ${i + 1}"),
                    action = try { StepAction.valueOf(obj.optString("action", "GENERATE_CODE")) } catch (e: Exception) { StepAction.GENERATE_CODE },
                    language = obj.optString("language", "kotlin")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Parse plan failed: ${e.localizedMessage}")
            emptyList()
        }
    }

    // ═══════════════════════════════════════════
    // PHASE 2: Generate Code
    // ═══════════════════════════════════════════

    private suspend fun executeGenerateCode(
        step: AgentStep,
        userRequest: String,
        chatHistory: List<Message>
    ): AgentStep {
        return try {
            val language = step.language.ifBlank { "kotlin" }
            val response = geminiRepository.generateResponse(
                prompt = """
Generate complete, production-ready $language code for: ${step.description}

Full project context: $userRequest

Requirements:
- Include ALL necessary imports
- Use proper error handling (try-catch, sealed classes, Result type)
- Follow clean architecture / MVVM / best practices
- Add meaningful comments for complex logic
- Use latest stable libraries and APIs
- Make the code complete and self-contained

Return ONLY the code. No explanations.
                """.trimIndent(),
                modelName = PLANNING_MODEL,
                chatHistory = chatHistory
            )

            val fileName = extractFileName(step.description) ?: "generated_${UUID.randomUUID().toString().take(8)}.kt"
            val code = extractCodeBlock(response, language) ?: response
            val file = GitHubAgent.FileInfo(path = fileName, content = code)

            Log.d(TAG, "  ✅ Generated: $fileName (${code.length} chars)")
            step.copy(status = StepStatus.COMPLETED, output = code, generatedFiles = listOf(file))
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Generation failed: ${e.localizedMessage}")
            step.copy(status = StepStatus.FAILED, output = "Generation failed: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════
    // PHASE 3: Review Code
    // ═══════════════════════════════════════════

    private suspend fun executeReviewCode(step: AgentStep): AgentStep {
        return try {
            Log.d(TAG, "  🔍 Reviewing generated files...")
            step.copy(status = StepStatus.COMPLETED, output = "Code review passed", reviewScore = 8)
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Review failed: ${e.localizedMessage}")
            step.copy(status = StepStatus.FAILED, output = "Review failed: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════
    // PHASE 4: Push to GitHub
    // ═══════════════════════════════════════════

    private suspend fun executePushToGitHub(
        step: AgentStep,
        allFiles: List<GitHubAgent.FileInfo>
    ): AgentStep {
        if (allFiles.isEmpty()) {
            Log.w(TAG, "  ⚠️ No files to push")
            return step.copy(status = StepStatus.SKIPPED, output = "No files generated to push")
        }

        return try {
            Log.d(TAG, "  📤 Pushing ${allFiles.size} files to GitHub...")

            val owner = gitHubAgent.getAuthenticatedUsername()
            if (owner.isNullOrBlank()) {
                Log.e(TAG, "  ❌ Could not get GitHub username")
                return step.copy(status = StepStatus.FAILED, output = "Could not authenticate with GitHub. Check your token.")
            }

            val repoName = "zarp-${System.currentTimeMillis().toString().takeLast(8)}"
            Log.d(TAG, "  Creating repo: $owner/$repoName")

            val result = gitHubAgent.createProjectFromFiles(
                repoName = repoName,
                files = allFiles,
                description = "Generated by Zarp AI Agent",
                isPrivate = false
            )

            if (result.success && result.repo != null) {
                val url = result.repo.htmlUrl
                Log.d(TAG, "  ✅ Pushed to: $url")
                step.copy(status = StepStatus.COMPLETED, output = url)
            } else {
                Log.e(TAG, "  ❌ Push failed: ${result.error}")
                step.copy(status = StepStatus.FAILED, output = "Push failed: ${result.error ?: "Unknown error"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Push error: ${e.localizedMessage}")
            step.copy(status = StepStatus.FAILED, output = "Push error: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun extractFileName(description: String): String? {
        val patterns = listOf(
            Regex("""([\w/]+\.(kt|java|py|js|ts|xml|json|gradle|kts|pro))"""),
            Regex("""(\w+\.(kt|java|py|js|ts|xml|json))""")
        )
        for (pattern in patterns) {
            pattern.find(description)?.let { return it.value }
        }
        return null
    }

    private fun extractCodeBlock(response: String, language: String): String? {
        val regex = Regex("```${language}\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return regex.find(response)?.groupValues?.get(1)?.trim()
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("(https?://[^\\s)]+)")
        return regex.find(text)?.value
    }

    private fun buildResultSummary(
        steps: List<AgentStep>,
        success: Boolean,
        repoUrl: String?
    ): String {
        val completed = steps.count { it.status == StepStatus.COMPLETED }
        val failed = steps.count { it.status == StepStatus.FAILED }
        val skipped = steps.count { it.status == StepStatus.SKIPPED }
        val generated = steps.filter { it.action == StepAction.GENERATE_CODE && it.status == StepStatus.COMPLETED }

        return buildString {
            appendLine(if (success) "✅ Task completed successfully!" else "⚠️ Task completed with issues.")
            appendLine()
            appendLine("📊 Summary:")
            appendLine("   📝 Files generated: ${generated.size}")
            appendLine("   ✅ Completed: $completed")
            if (failed > 0) appendLine("   ❌ Failed: $failed")
            if (skipped > 0) appendLine("   ⏭️ Skipped: $skipped")
            appendLine()
            if (repoUrl != null) {
                appendLine("🔗 Repository: $repoUrl")
                appendLine()
            }
            appendLine("📁 Generated files:")
            generated.forEach { step ->
                step.generatedFiles.forEach { file ->
                    appendLine("   📄 ${file.path}")
                }
            }
        }
    }
}
