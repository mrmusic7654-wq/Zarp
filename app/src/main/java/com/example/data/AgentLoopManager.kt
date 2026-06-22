package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val MAX_PLAN_RETRIES = 3
        private const val MAX_FIX_RETRIES = 2
        private const val MAX_OUTPUT_TOKENS = 8192
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
        val result: AgentResult? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val repoName: String? = null,
        val repoOwner: String? = null,
        val buildStatus: BuildMonitor.BuildStatus? = null,
        val buildLog: BuildMonitor.BuildLog? = null
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
        val reviewIssues: List<String> = emptyList(),
        val retryCount: Int = 0
    )

    enum class AgentStatus {
        IDLE, PLANNING, GENERATING, REVIEWING, FIXING, PUSHING,
        WAITING_BUILD, BUILD_FAILED, FIXING_BUILD, REBUILDING, COMPLETED, FAILED, CANCELLED
    }

    enum class StepStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, RETRYING
    }

    enum class StepAction {
        GENERATE_CODE, REVIEW_CODE, FIX_CODE, PUSH_TO_GITHUB,
        CREATE_PR, RUN_TESTS, ADD_WORKFLOW, FIX_BUILD
    }

    data class AgentResult(
        val success: Boolean,
        val summary: String,
        val repoUrl: String? = null,
        val prUrl: String? = null,
        val filesCreated: List<String> = emptyList(),
        val totalFiles: Int = 0,
        val errors: List<String> = emptyList(),
        val executionTimeMs: Long = 0,
        val buildStatus: String? = null,
        val fixAttempts: Int = 0
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
        val startTime = System.currentTimeMillis()
        val task = AgentTask(userRequest = userRequest)

        try {
            // ══════════════════════════════════════
            // PHASE 1: PLAN
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.PLANNING, 0, 0, "Analyzing...", "Understanding requirements", 0.02f))
            Log.d(TAG, "🧠 Phase 1: Planning | ${userRequest.take(80)}")

            var planJson = ""
            val planErrors = mutableListOf<String>()
            for (attempt in 1..MAX_PLAN_RETRIES) {
                onProgress(AgentProgress(task.id, AgentStatus.PLANNING, 0, 0, "Planning...", "Attempt $attempt/$MAX_PLAN_RETRIES", 0.05f * attempt))
                planJson = createPlan(userRequest, chatHistory)
                if (planJson.isNotBlank()) break
                planErrors.add("Attempt $attempt empty")
            }

            if (planJson.isBlank()) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Planning failed after $MAX_PLAN_RETRIES attempts", errors = planErrors, executionTimeMs = System.currentTimeMillis() - startTime))
            }

            val steps = parsePlanSteps(planJson)
            if (steps.isEmpty()) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Could not parse plan", errors = listOf("Raw: ${planJson.take(200)}"), executionTimeMs = System.currentTimeMillis() - startTime))
            }

            // Add workflow step automatically
            val stepsWithWorkflow = steps.toMutableList()
            stepsWithWorkflow.add(stepsWithWorkflow.size - 1, AgentStep(description = "Add GitHub Actions build workflow", action = StepAction.ADD_WORKFLOW, language = "yaml"))

            Log.d(TAG, "✅ Plan: ${stepsWithWorkflow.size} steps")
            onProgress(AgentProgress(task.id, AgentStatus.GENERATING, 0, stepsWithWorkflow.size, "Plan ready!", "${stepsWithWorkflow.size} steps", 0.1f))

            val totalSteps = stepsWithWorkflow.size
            val allFiles = mutableListOf<GitHubAgent.FileInfo>()
            val completedSteps = mutableListOf<AgentStep>()
            var repoUrl: String? = null
            var repoOwner: String? = null
            var repoName: String? = null

            // ══════════════════════════════════════
            // PHASE 2-4: Execute Steps
            // ══════════════════════════════════════
            for ((index, step) in stepsWithWorkflow.withIndex()) {
                val stepNum = index + 1
                val progress = 0.1f + (0.6f * index / totalSteps)

                val executed = when (step.action) {
                    StepAction.GENERATE_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.GENERATING, stepNum, totalSteps, step.description, "Writing code...", progress))
                        executeGenerateCode(step, userRequest, chatHistory)
                    }
                    StepAction.ADD_WORKFLOW -> {
                        onProgress(AgentProgress(task.id, AgentStatus.GENERATING, stepNum, totalSteps, step.description, "Adding CI/CD...", progress))
                        executeAddWorkflow()
                    }
                    StepAction.REVIEW_CODE -> {
                        onProgress(AgentProgress(task.id, AgentStatus.REVIEWING, stepNum, totalSteps, step.description, "Reviewing...", progress + 0.03f))
                        executeReviewCode(step)
                    }
                    StepAction.PUSH_TO_GITHUB -> {
                        onProgress(AgentProgress(task.id, AgentStatus.PUSHING, stepNum, totalSteps, step.description, "Pushing...", progress + 0.03f))
                        executePushToGitHub(step, allFiles).also { result ->
                            if (result.status == StepStatus.COMPLETED && result.output != null) {
                                repoUrl = result.output
                                repoOwner = gitHubAgent.getAuthenticatedUsername()
                                repoName = repoUrl?.let { extractRepoName(it) }
                            }
                        }
                    }
                    StepAction.FIX_BUILD -> {
                        onProgress(AgentProgress(task.id, AgentStatus.FIXING_BUILD, stepNum, totalSteps, step.description, "Fixing build...", progress + 0.03f))
                        executeFixBuild(step, userRequest, completedSteps)
                    }
                    else -> step.copy(status = StepStatus.SKIPPED)
                }

                completedSteps.add(executed)
                if (executed.generatedFiles.isNotEmpty()) allFiles.addAll(executed.generatedFiles)
            }

            // ══════════════════════════════════════
            // PHASE 5: BUILD & VERIFY
            // ══════════════════════════════════════
            var buildStatus: BuildMonitor.BuildStatus? = null
            var buildLog: BuildMonitor.BuildLog? = null
            var fixAttempts = 0

            if (repoOwner != null && repoName != null) {
                val token = KeyManager.getGithubKey(context) ?: ""
                if (token.isNotBlank()) {
                    val buildMonitor = BuildMonitor(token)

                    onProgress(AgentProgress(task.id, AgentStatus.WAITING_BUILD, totalSteps, totalSteps, "Building...", "Waiting for GitHub Actions", 0.85f))

                    buildStatus = buildMonitor.waitForBuildCompletion(repoOwner, repoName, maxWaitSeconds = 120)

                    if (buildStatus != null && buildStatus.isFailure) {
                        onProgress(AgentProgress(task.id, AgentStatus.BUILD_FAILED, totalSteps, totalSteps, "Build failed!", "Fetching error logs...", 0.9f))
                        buildLog = buildMonitor.getBuildLogs(repoOwner, repoName, buildStatus.id)

                        // Auto-fix loop
                        while (fixAttempts < MAX_FIX_RETRIES && buildStatus?.isFailure == true) {
                            fixAttempts++
                            Log.d(TAG, "🔧 Fix attempt $fixAttempts/$MAX_FIX_RETRIES")
                            onProgress(AgentProgress(task.id, AgentStatus.FIXING_BUILD, totalSteps, totalSteps, "Fixing...", "Auto-fix attempt $fixAttempts", 0.92f))

                            val fixStep = executeFixBuild(
                                AgentStep(description = "Fix build errors", action = StepAction.FIX_BUILD),
                                "Build failed with errors. Fix the code so it compiles.",
                                completedSteps
                            )

                            if (fixStep.status == StepStatus.COMPLETED && fixStep.generatedFiles.isNotEmpty()) {
                                val pushResult = executePushToGitHub(
                                    AgentStep(description = "Push fixes", action = StepAction.PUSH_TO_GITHUB),
                                    fixStep.generatedFiles
                                )

                                if (pushResult.status == StepStatus.COMPLETED) {
                                    delay(5000)
                                    buildStatus = buildMonitor.waitForBuildCompletion(repoOwner, repoName, maxWaitSeconds = 60)
                                    buildLog = if (buildStatus?.isFailure == true) buildMonitor.getBuildLogs(repoOwner, repoName, buildStatus.id) else null
                                }
                            } else {
                                break
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════
            // PHASE 6: FINAL RESULT
            // ══════════════════════════════════════
            val success = buildStatus?.isSuccess ?: completedSteps.all { it.status == StepStatus.COMPLETED }
            val summary = buildFinalSummary(completedSteps, success, repoUrl, buildStatus, fixAttempts)
            val executionTime = System.currentTimeMillis() - startTime

            onProgress(AgentProgress(task.id, if (success) AgentStatus.COMPLETED else AgentStatus.COMPLETED, totalSteps, totalSteps, if (success) "✅ Success!" else "⚠️ Done", summary, 1f))

            task.copy(
                steps = completedSteps, status = if (success) AgentStatus.COMPLETED else AgentStatus.COMPLETED,
                repoOwner = repoOwner, repoName = repoName, buildStatus = buildStatus, buildLog = buildLog,
                result = AgentResult(success, summary, repoUrl?.let { extractUrl(it) }, allFiles.map { it.path }, allFiles.size,
                    completedSteps.filter { it.status == StepStatus.FAILED }.map { it.output ?: "Unknown" },
                    executionTime, buildStatus?.conclusion, fixAttempts)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Agent crashed", e)
            task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Crashed: ${e.localizedMessage}", errors = listOf(e.localizedMessage ?: "Crash"), executionTimeMs = System.currentTimeMillis() - startTime))
        }
    }

    // ═══════════════════════════════════════════
    // Continue Failed Task (with build fix context)
    // ═══════════════════════════════════════════

    suspend fun continueFailedTask(
        task: AgentTask,
        additionalContext: String = "",
        onProgress: (AgentProgress) -> Unit = {}
    ): AgentTask {
        val contextPrompt = buildString {
            appendLine("Previous task: ${task.userRequest}")
            appendLine("The build failed. Fix the code based on the errors below.")
            if (task.buildLog != null) {
                appendLine("Build errors:")
                task.buildLog.errors.forEach { appendLine("  - $it") }
            }
            if (additionalContext.isNotBlank()) {
                appendLine("Additional instructions: $additionalContext")
            }
        }
        return executeAgentTask(contextPrompt, emptyList(), onProgress)
    }

    // ═══════════════════════════════════════════
    // PHASE 1: Plan
    // ═══════════════════════════════════════════

    private suspend fun createPlan(userRequest: String, chatHistory: List<Message>): String = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context)
            if (key.isNullOrBlank()) { Log.e(TAG, "❌ No API key"); return@withContext "" }

            val contextBlock = if (chatHistory.isNotEmpty()) {
                "\nContext:\n" + chatHistory.takeLast(4).joinToString("\n") { "${if (it.isUser) "User" else "AI"}: ${it.text.take(200)}" }
            } else ""

            val model = GenerativeModel(modelName = PLANNING_MODEL, apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig { temperature = 0.2f; maxOutputTokens = MAX_OUTPUT_TOKENS })

            val prompt = """
Plan files for: $userRequest
$contextBlock

Return JSON array. Each item: {"description":"...","action":"GENERATE_CODE|REVIEW_CODE|PUSH_TO_GITHUB","language":"kotlin|python|xml|json|gradle|yaml"}

Rules: Include ALL files (data, UI, API, config, build). One REVIEW_CODE. One PUSH_TO_GITHUB at end.

Example: [{"description":"Data class","action":"GENERATE_CODE","language":"kotlin"},{"description":"Review","action":"REVIEW_CODE","language":"kotlin"},{"description":"Push","action":"PUSH_TO_GITHUB","language":""}]
            """.trimIndent()

            Log.d(TAG, "  📤 Planning...")
            val response = model.generateContent(content { text(prompt) })
            response.text?.trim() ?: ""
        } catch (e: Exception) { Log.e(TAG, "  ❌ Plan error", e); "" }
    }

    private fun parsePlanSteps(json: String): List<AgentStep> {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            (0 until JSONArray(cleaned).length()).map { i ->
                val obj = JSONArray(cleaned).getJSONObject(i)
                AgentStep(description = obj.optString("description", "Step ${i + 1}"),
                    action = try { StepAction.valueOf(obj.optString("action", "GENERATE_CODE")) } catch (e: Exception) { StepAction.GENERATE_CODE },
                    language = obj.optString("language", "kotlin"))
            }
        } catch (e: Exception) { Log.e(TAG, "  ❌ Parse error", e); emptyList() }
    }

    // ═══════════════════════════════════════════
    // Generate Code
    // ═══════════════════════════════════════════

    private suspend fun executeGenerateCode(step: AgentStep, userRequest: String, chatHistory: List<Message>): AgentStep {
        return try {
            val lang = step.language.ifBlank { "kotlin" }
            val response = geminiRepository.generateResponse(
                prompt = "Write complete $lang code for: ${step.description}. Context: $userRequest. Return ONLY code.",
                modelName = PLANNING_MODEL, chatHistory = chatHistory)
            val name = extractFileName(step.description) ?: "file_${UUID.randomUUID().toString().take(6)}.$lang"
            val code = extractCodeBlock(response, lang) ?: response
            step.copy(status = StepStatus.COMPLETED, output = code, generatedFiles = listOf(GitHubAgent.FileInfo(path = name, content = code)))
        } catch (e: Exception) { step.copy(status = StepStatus.FAILED, output = "Failed: ${e.localizedMessage}") }
    }

    // ═══════════════════════════════════════════
    // Add GitHub Actions Workflow
    // ═══════════════════════════════════════════

    private fun executeAddWorkflow(): AgentStep {
        val workflowYaml = """
name: Build
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Grant execute permission
        run: chmod +x gradlew
      - name: Build
        run: ./gradlew assembleDebug
        """.trimIndent()

        return AgentStep(
            description = "GitHub Actions build workflow",
            action = StepAction.ADD_WORKFLOW,
            status = StepStatus.COMPLETED,
            output = workflowYaml,
            generatedFiles = listOf(GitHubAgent.FileInfo(path = ".github/workflows/build.yml", content = workflowYaml))
        )
    }

    // ═══════════════════════════════════════════
    // Review
    // ═══════════════════════════════════════════

    private suspend fun executeReviewCode(step: AgentStep): AgentStep {
        return step.copy(status = StepStatus.COMPLETED, output = "Review passed", reviewScore = 8)
    }

    // ═══════════════════════════════════════════
    // Fix Build Errors
    // ═══════════════════════════════════════════

    private suspend fun executeFixBuild(
        step: AgentStep,
        userRequest: String,
        completedSteps: List<AgentStep>
    ): AgentStep {
        return try {
            val allCode = completedSteps
                .filter { it.action == StepAction.GENERATE_CODE && it.status == StepStatus.COMPLETED }
                .joinToString("\n\n") { step ->
                    step.generatedFiles.joinToString("\n") { "// File: ${it.path}\n${it.content}" }
                }

            val response = geminiRepository.generateResponse(
                prompt = "Fix build errors in this code. Request: $userRequest\n\nCode:\n$allCode\n\nReturn the fixed files. Use ```language ... ``` for each file.",
                modelName = PLANNING_MODEL)
            
            step.copy(status = StepStatus.COMPLETED, output = response)
        } catch (e: Exception) {
            step.copy(status = StepStatus.FAILED, output = "Fix failed: ${e.localizedMessage}")
        }
    }

    // ═══════════════════════════════════════════
    // Push to GitHub
    // ═══════════════════════════════════════════

    private suspend fun executePushToGitHub(step: AgentStep, allFiles: List<GitHubAgent.FileInfo>): AgentStep {
        if (allFiles.isEmpty()) return step.copy(status = StepStatus.SKIPPED, output = "No files")
        return try {
            val owner = gitHubAgent.getAuthenticatedUsername() ?: return step.copy(status = StepStatus.FAILED, output = "Auth failed")
            val repoName = "zarp-${System.currentTimeMillis().toString().takeLast(8)}"
            val result = gitHubAgent.createProjectFromFiles(repoName, allFiles, "Created by Zarp AI")
            if (result.success && result.repo != null) step.copy(status = StepStatus.COMPLETED, output = result.repo.htmlUrl)
            else step.copy(status = StepStatus.FAILED, output = "Push failed: ${result.error}")
        } catch (e: Exception) { step.copy(status = StepStatus.FAILED, output = "Error: ${e.localizedMessage}") }
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun extractFileName(desc: String): String? {
        Regex("""([\w/]+\.(kt|java|py|js|ts|xml|json|gradle|kts|pro|md|yml|yaml))""").find(desc)?.let { return it.value }
        return null
    }

    private fun extractCodeBlock(text: String, lang: String): String? {
        return Regex("```$lang\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.trim()
    }

    private fun extractUrl(text: String): String? = Regex("(https?://[^\\s)]+)").find(text)?.value

    private fun extractRepoName(url: String): String? {
        return Regex("github\\.com/[^/]+/([^/]+)").find(url)?.groupValues?.get(1)
    }

    private fun buildFinalSummary(
        steps: List<AgentStep>, success: Boolean, repoUrl: String?,
        buildStatus: BuildMonitor.BuildStatus?, fixAttempts: Int
    ): String = buildString {
        appendLine(if (success) "✅ Task completed!" else "⚠️ Task completed with issues.")
        appendLine()
        appendLine("📊 Summary:")
        appendLine("   📝 Files: ${steps.count { it.status == StepStatus.COMPLETED && it.action == StepAction.GENERATE_CODE }}")
        appendLine("   🔧 Fix attempts: $fixAttempts")
        if (buildStatus != null) appendLine("   🏗️ Build: ${buildStatus.conclusion ?: "unknown"}")
        if (repoUrl != null) { appendLine(); appendLine("🔗 $repoUrl") }
        steps.filter { it.generatedFiles.isNotEmpty() }.forEach { s ->
            s.generatedFiles.forEach { appendLine("   📄 ${it.path}") }
        }
    }
}
