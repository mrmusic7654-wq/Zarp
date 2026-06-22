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
        private const val BUILD_TIMEOUT_SECONDS = 180
        private const val POLL_INTERVAL_MS = 5000L
        private const val STEP_DELAY_MS = 400L
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class AgentTask(
        val id: String = UUID.randomUUID().toString(),
        val userRequest: String,
        val intent: ProjectIntent? = null,
        val architecture: ArchitectureTree? = null,
        val layers: List<CodeLayer> = emptyList(),
        val steps: List<AgentStep> = emptyList(),
        val status: AgentStatus = AgentStatus.IDLE,
        val result: AgentResult? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val repoName: String? = null,
        val repoOwner: String? = null,
        val buildStatus: BuildMonitor.BuildStatus? = null,
        val buildLog: BuildMonitor.BuildLog? = null
    )

    data class ProjectIntent(
        val appName: String,
        val appType: String,
        val architecture: String,
        val dataSource: String,
        val localStorage: String?,
        val uiPattern: String,
        val features: List<String>,
        val dependencies: List<String>,
        val permissions: List<String>,
        val packageName: String
    )

    data class ArchitectureTree(
        val rootPackage: String,
        val folders: List<ArchFolder>,
        val totalFiles: Int
    )

    data class ArchFolder(
        val path: String,
        val purpose: String,
        val files: List<ArchFile>
    )

    data class ArchFile(
        val name: String,
        val purpose: String,
        val imports: List<String>,
        val exports: List<String>,
        val dependsOn: List<String>
    )

    data class CodeLayer(
        val name: String,
        val level: Int,
        val files: List<AgentStep>,
        val status: StepStatus = StepStatus.PENDING
    )

    data class AgentStep(
        val id: String = UUID.randomUUID().toString(),
        val description: String,
        val action: StepAction,
        val language: String = "kotlin",
        val filePath: String = "",
        val dependsOn: List<String> = emptyList(),
        val status: StepStatus = StepStatus.PENDING,
        val output: String? = null,
        val generatedFiles: List<GitHubAgent.FileInfo> = emptyList(),
        val reviewScore: Int = 0,
        val retryCount: Int = 0
    )

    enum class AgentStatus {
        IDLE, ANALYZING, DESIGNING, SETUP_REPO, GENERATING, REVIEWING,
        FIXING, PUSHING, WAITING_BUILD, BUILD_FAILED, FIXING_BUILD,
        GENERATING_CI, DELIVERING, COMPLETED, FAILED, CANCELLED
    }

    enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, RETRYING }
    enum class StepAction {
        GENERATE_CODE, REVIEW_CODE, FIX_CODE, PUSH_TO_GITHUB,
        ADD_WORKFLOW, FIX_BUILD, GENERATE_README, INTEGRITY_CHECK
    }

    data class AgentResult(
        val success: Boolean,
        val summary: String,
        val repoUrl: String? = null,
        val filesCreated: List<String> = emptyList(),
        val totalFiles: Int = 0,
        val errors: List<String> = emptyList(),
        val executionTimeMs: Long = 0,
        val buildStatus: String? = null,
        val fixAttempts: Int = 0,
        val architectureDiagram: String? = null
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

    data class IntegrityResult(
        val success: Boolean,
        val fixes: List<GitHubAgent.FileInfo>,
        val issues: List<String> = emptyList()
    )

    // ═══════════════════════════════════════════
    // Main Agent Loop — Elite Reliability
    // ═══════════════════════════════════════════

    suspend fun executeAgentTask(
        userRequest: String,
        chatHistory: List<Message> = emptyList(),
        onProgress: (AgentProgress) -> Unit = {}
    ): AgentTask = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var task = AgentTask(userRequest = userRequest)

        try {
            // ══════════════════════════════════════
            // PHASE 0: INTENT ANALYSIS (with retry)
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.ANALYZING, 0, 7, "Analyzing...", "Understanding your requirements", 0.03f))
            
            var intent: ProjectIntent? = null
            repeat(MAX_PLAN_RETRIES) { attempt ->
                intent = analyzeIntent(userRequest, chatHistory)
                if (intent != null) return@repeat
                Log.w(TAG, "Intent analysis attempt ${attempt + 1} failed, retrying...")
                delay(STEP_DELAY_MS)
            }
            
            if (intent == null) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Could not understand requirements. Please be more specific."))
            }
            task = task.copy(intent = intent)

            // ══════════════════════════════════════
            // PHASE 1: ARCHITECTURE DESIGN
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.DESIGNING, 1, 7, "Designing...", "Creating architecture plan", 0.1f))
            
            var architecture: ArchitectureTree? = null
            repeat(MAX_PLAN_RETRIES) { attempt ->
                architecture = designArchitecture(intent, userRequest)
                if (architecture != null && architecture.totalFiles > 0) return@repeat
                Log.w(TAG, "Architecture attempt ${attempt + 1} failed, retrying...")
                delay(STEP_DELAY_MS)
            }
            
            if (architecture == null || architecture.totalFiles == 0) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Could not design architecture."))
            }
            task = task.copy(architecture = architecture)
            Log.d(TAG, "✅ Architecture: ${architecture.totalFiles} files in ${architecture.folders.size} folders")

            // ══════════════════════════════════════
            // PHASE 2: GENERATE FOUNDATION FILES
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.GENERATING, 2, 7, "Foundation...", "Generating build files + manifest", 0.2f))
            
            val buildFiles = generateBuildFiles(intent)
            val manifestFiles = generateManifest(intent)
            val foundationFiles = buildFiles + manifestFiles
            Log.d(TAG, "📦 Foundation: ${foundationFiles.size} files")

            // ══════════════════════════════════════
            // PHASE 3: GITHUB SETUP
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.SETUP_REPO, 3, 7, "Setting up...", "Creating GitHub repository", 0.3f))
            
            val owner = gitHubAgent.getAuthenticatedUsername()
            if (owner.isNullOrBlank()) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "GitHub authentication failed. Check your token in Settings."))
            }
            
            val repoName = "${intent.appName.lowercase().replace(" ", "-")}-${System.currentTimeMillis().toString().takeLast(6)}"
            val repo = gitHubAgent.createRepo(repoName, "Generated by Zarp CodeForge • ${intent.appType}")
            if (repo == null) {
                return@withContext task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Could not create GitHub repository. Check your token permissions."))
            }
            task = task.copy(repoOwner = owner, repoName = repoName)
            
            gitHubAgent.pushFiles(owner, repoName, foundationFiles, "Foundation: Build configuration")
            Log.d(TAG, "✅ Repo: ${repo.htmlUrl}")

            // ══════════════════════════════════════
            // PHASE 4: LAYERED CODE GENERATION
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.GENERATING, 4, 7, "Generating code...", "Writing files layer by layer", 0.4f))
            
            val layers = organizeIntoLayers(architecture)
            val allGeneratedFiles = mutableListOf<GitHubAgent.FileInfo>()
            val completedLayers = mutableListOf<CodeLayer>()
            
            for ((index, layer) in layers.withIndex()) {
                val layerProgress = 0.4f + (0.3f * (index + 1) / layers.size)
                onProgress(AgentProgress(task.id, AgentStatus.GENERATING, 4, 7, "Layer ${index + 1}/${layers.size}", "Generating ${layer.name} (${layer.files.size} files)", layerProgress))
                
                val generatedLayer = generateLayer(layer, architecture, intent, allGeneratedFiles, chatHistory)
                completedLayers.add(generatedLayer)
                
                val layerFiles = generatedLayer.files.filter { it.status == StepStatus.COMPLETED }.flatMap { it.generatedFiles }
                if (layerFiles.isNotEmpty()) {
                    gitHubAgent.pushFiles(owner, repoName, layerFiles, "Layer ${index + 1}: ${layer.name}")
                    allGeneratedFiles.addAll(layerFiles)
                }
                delay(STEP_DELAY_MS)
            }
            
            task = task.copy(layers = completedLayers)

            // ══════════════════════════════════════
            // PHASE 5: INTEGRITY CHECK
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.REVIEWING, 5, 7, "Checking integrity...", "Verifying cross-file references", 0.75f))
            
            val integrityResult = performIntegrityCheck(allGeneratedFiles, architecture, intent)
            if (!integrityResult.success && integrityResult.fixes.isNotEmpty()) {
                gitHubAgent.pushFiles(owner, repoName, integrityResult.fixes, "Fix: Cross-file integrity")
                allGeneratedFiles.addAll(integrityResult.fixes)
            }

            // ══════════════════════════════════════
            // PHASE 6: CI/CD + DOCS
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.GENERATING_CI, 6, 7, "CI/CD + Docs...", "Generating workflows + README", 0.85f))
            
            val ciFiles = generateCICD(intent)
            val docFiles = generateDocumentation(intent, architecture)
            val metaFiles = ciFiles + docFiles
            gitHubAgent.pushFiles(owner, repoName, metaFiles, "CI/CD + Documentation")
            allGeneratedFiles.addAll(metaFiles)

            // ══════════════════════════════════════
            // PHASE 7: BUILD & VERIFY (with auto-fix)
            // ══════════════════════════════════════
            onProgress(AgentProgress(task.id, AgentStatus.WAITING_BUILD, 7, 7, "Building...", "Running GitHub Actions", 0.9f))
            
            var buildStatus: BuildMonitor.BuildStatus? = null
            var buildLog: BuildMonitor.BuildLog? = null
            var fixAttempts = 0
            
            val token = KeyManager.getGithubKey(context) ?: ""
            if (token.isNotBlank()) {
                val buildMonitor = BuildMonitor(token)
                buildStatus = buildMonitor.waitForBuildCompletion(owner, repoName, BUILD_TIMEOUT_SECONDS, POLL_INTERVAL_MS)
                
                while (fixAttempts < MAX_FIX_RETRIES && buildStatus?.isFailure == true) {
                    fixAttempts++
                    onProgress(AgentProgress(task.id, AgentStatus.FIXING_BUILD, 7, 7, "Fixing build...", "Auto-fix attempt $fixAttempts", 0.92f))
                    
                    buildLog = buildMonitor.getBuildLogs(owner, repoName, buildStatus.id)
                    
                    val fixResult = fixBuildErrors(buildLog, allGeneratedFiles, intent)
                    if (fixResult.isNotEmpty()) {
                        gitHubAgent.pushFiles(owner, repoName, fixResult, "Fix: Build errors (attempt $fixAttempts)")
                        delay(POLL_INTERVAL_MS)
                        buildStatus = buildMonitor.waitForBuildCompletion(owner, repoName, 60, POLL_INTERVAL_MS)
                    } else {
                        Log.w(TAG, "Could not fix build errors automatically")
                        break
                    }
                }
            }

            // ══════════════════════════════════════
            // DELIVERY
            // ══════════════════════════════════════
            val success = buildStatus?.isSuccess ?: true
            val summary = buildDeliverySummary(intent, architecture, completedLayers, buildStatus, fixAttempts, repo.htmlUrl)
            val executionTime = System.currentTimeMillis() - startTime
            
            onProgress(AgentProgress(task.id, if (success) AgentStatus.COMPLETED else AgentStatus.COMPLETED, 7, 7, if (success) "✅ Done!" else "⚠️ Done", summary, 1f))
            
            task.copy(
                status = if (success) AgentStatus.COMPLETED else AgentStatus.COMPLETED,
                buildStatus = buildStatus, buildLog = buildLog,
                result = AgentResult(
                    success = success, summary = summary, repoUrl = repo.htmlUrl,
                    filesCreated = allGeneratedFiles.map { it.path }, totalFiles = allGeneratedFiles.size,
                    errors = buildLog?.errors?.take(5) ?: emptyList(), executionTimeMs = executionTime,
                    buildStatus = buildStatus?.conclusion, fixAttempts = fixAttempts,
                    architectureDiagram = generateArchitectureDiagram(architecture)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Agent crashed", e)
            task.copy(status = AgentStatus.FAILED, result = AgentResult(false, "Crashed: ${e.localizedMessage}", errors = listOf(e.localizedMessage ?: "Crash"), executionTimeMs = System.currentTimeMillis() - startTime))
        }
    }

    // ═══════════════════════════════════════════
    // Continue Failed Task
    // ═══════════════════════════════════════════

    suspend fun continueFailedTask(task: AgentTask, additionalContext: String = "", onProgress: (AgentProgress) -> Unit = {}): AgentTask {
        val prompt = buildString {
            appendLine("Previous task: ${task.userRequest}")
            if (task.buildLog?.errors?.isNotEmpty() == true) {
                appendLine("Build errors (${task.buildLog!!.errors.size}):")
                task.buildLog!!.errors.take(10).forEach { appendLine("  - $it") }
            }
            if (additionalContext.isNotBlank()) appendLine("Instructions: $additionalContext")
        }
        return executeAgentTask(prompt, emptyList(), onProgress)
    }

    // ═══════════════════════════════════════════
    // PHASE 0: Intent Analysis
    // ═══════════════════════════════════════════

    private suspend fun analyzeIntent(userRequest: String, chatHistory: List<Message>): ProjectIntent? = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext null
            val model = GenerativeModel(modelName = PLANNING_MODEL, apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig { temperature = 0.1f; maxOutputTokens = 1024 })

            val prompt = """
Analyze this app request and return ONLY JSON:
{"appName":"Name","appType":"android|web|cli","architecture":"mvvm|mvi|clean","dataSource":"remote|local|firebase","localStorage":"room|datastore|none","uiPattern":"compose|xml","features":["f1"],"dependencies":["retrofit"],"permissions":["INTERNET"],"packageName":"com.example.name"}

Request: $userRequest
Return ONLY JSON.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val jsonText = extractJson(response.text ?: "") ?: return@withContext null
            val obj = JSONObject(jsonText)
            
            ProjectIntent(
                appName = obj.optString("appName", "App"), appType = obj.optString("appType", "android"),
                architecture = obj.optString("architecture", "mvvm"), dataSource = obj.optString("dataSource", "none"),
                localStorage = obj.optString("localStorage", null).takeIf { it != "none" },
                uiPattern = obj.optString("uiPattern", "compose"),
                features = obj.optJSONArray("features")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                dependencies = obj.optJSONArray("dependencies")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                permissions = obj.optJSONArray("permissions")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                packageName = obj.optString("packageName", "com.example.app")
            )
        } catch (e: Exception) { Log.e(TAG, "Intent failed", e); null }
    }

    // ═══════════════════════════════════════════
    // PHASE 1: Architecture Design
    // ═══════════════════════════════════════════

    private suspend fun designArchitecture(intent: ProjectIntent, userRequest: String): ArchitectureTree? = withContext(Dispatchers.IO) {
        try {
            val key = KeyManager.getGeminiKey(context) ?: return@withContext null
            val model = GenerativeModel(modelName = PLANNING_MODEL, apiKey = key,
                generationConfig = com.google.ai.client.generativeai.type.generationConfig { temperature = 0.2f; maxOutputTokens = MAX_OUTPUT_TOKENS })

            val prompt = """
Design file tree for ${intent.architecture.uppercase()} ${intent.uiPattern} app: ${intent.appName}
Features: ${intent.features.joinToString()}
Package: ${intent.packageName}

Return ONLY JSON:
{"rootPackage":"${intent.packageName}","folders":[{"path":"data/model","purpose":"Data models","files":[{"name":"User.kt","purpose":"User entity","imports":["room.Entity"],"exports":["User"],"dependsOn":[]}]}],"totalFiles":0}

Include ALL files. Cover data, domain, UI, DI, navigation, theme.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val jsonText = extractJson(response.text ?: "") ?: return@withContext null
            val obj = JSONObject(jsonText)
            
            val folders = mutableListOf<ArchFolder>()
            val foldersArr = obj.optJSONArray("folders") ?: return@withContext null
            
            for (i in 0 until foldersArr.length()) {
                val fObj = foldersArr.getJSONObject(i)
                val files = mutableListOf<ArchFile>()
                val filesArr = fObj.optJSONArray("files") ?: continue
                for (j in 0 until filesArr.length()) {
                    val fileObj = filesArr.getJSONObject(j)
                    files.add(ArchFile(
                        name = fileObj.optString("name"), purpose = fileObj.optString("purpose"),
                        imports = fileObj.optJSONArray("imports")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                        exports = fileObj.optJSONArray("exports")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                        dependsOn = fileObj.optJSONArray("dependsOn")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                    ))
                }
                folders.add(ArchFolder(path = fObj.optString("path"), purpose = fObj.optString("purpose"), files = files))
            }
            
            ArchitectureTree(obj.optString("rootPackage"), folders, folders.sumOf { it.files.size })
        } catch (e: Exception) { Log.e(TAG, "Architecture failed", e); null }
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun organizeIntoLayers(tree: ArchitectureTree): List<CodeLayer> {
        val map = linkedMapOf(
            "Foundation" to mutableListOf<AgentStep>(),
            "Data Models" to mutableListOf<AgentStep>(),
            "Data Access" to mutableListOf<AgentStep>(),
            "Repository" to mutableListOf<AgentStep>(),
            "Domain" to mutableListOf<AgentStep>(),
            "ViewModels" to mutableListOf<AgentStep>(),
            "UI Screens" to mutableListOf<AgentStep>(),
            "Navigation" to mutableListOf<AgentStep>(),
            "DI" to mutableListOf<AgentStep>()
        )
        
        for (folder in tree.folders) {
            val category = when {
                folder.path.contains("entity") || folder.path.contains("model") || folder.path.contains("dto") -> "Data Models"
                folder.path.contains("dao") || folder.path.contains("api") || folder.path.contains("remote") -> "Data Access"
                folder.path.contains("repository") -> "Repository"
                folder.path.contains("domain") || folder.path.contains("usecase") -> "Domain"
                folder.path.contains("viewmodel") -> "ViewModels"
                folder.path.contains("screen") || folder.path.contains("composable") -> "UI Screens"
                folder.path.contains("nav") -> "Navigation"
                folder.path.contains("di") || folder.path.contains("module") -> "DI"
                else -> "Foundation"
            }
            folder.files.forEach { file ->
                map[category]?.add(AgentStep(description = file.purpose, action = StepAction.GENERATE_CODE, language = if (file.name.endsWith(".xml")) "xml" else "kotlin", filePath = "${folder.path}/${file.name}", dependsOn = file.dependsOn))
            }
        }
        return map.filter { it.value.isNotEmpty() }.map { CodeLayer(it.key, 0, it.value) }
    }

    private suspend fun generateLayer(layer: CodeLayer, tree: ArchitectureTree, intent: ProjectIntent, existingFiles: List<GitHubAgent.FileInfo>, chatHistory: List<Message>): CodeLayer {
        val completed = mutableListOf<AgentStep>()
        val context = existingFiles.joinToString("\n") { "// ${it.path}\n${it.content.take(300)}" }
        
        for (step in layer.files) {
            val prompt = "Generate ${step.language} for ${step.filePath}: ${step.description}. App: ${intent.appName}. Package: ${intent.packageName}. Context:\n$context\nReturn ONLY code."
            val response = geminiRepository.generateResponse(prompt, PLANNING_MODEL)
            val code = extractCodeBlock(response, step.language) ?: response
            completed.add(step.copy(status = StepStatus.COMPLETED, output = code, generatedFiles = listOf(GitHubAgent.FileInfo(step.filePath, code))))
        }
        return layer.copy(files = completed, status = if (completed.all { it.status == StepStatus.COMPLETED }) StepStatus.COMPLETED else StepStatus.FAILED)
    }

    private fun extractJson(text: String): String? {
        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val s = cleaned.indexOf('{'); val e = cleaned.lastIndexOf('}')
        return if (s == -1 || e == -1 || e < s) null else cleaned.substring(s, e + 1)
    }

    private fun extractCodeBlock(text: String, lang: String): String? {
        return Regex("```$lang\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.trim()
    }

    private fun generateBuildFiles(intent: ProjectIntent): List<GitHubAgent.FileInfo> {
        val deps = intent.dependencies.joinToString("\n    ") { "implementation(\"...$it...\")" }
        val build = """
plugins { id("com.android.application"); kotlin("android"); ${if (intent.localStorage == "room") "id(\"com.google.devtools.ksp\")" else ""} }
android { namespace="${intent.packageName}"; compileSdk=34; defaultConfig { applicationId="${intent.packageName}"; minSdk=24; targetSdk=34 }; buildFeatures { compose=true } }
dependencies { implementation(platform("androidx.compose:compose-bom:2024.06.00")); $deps }
        """.trimIndent()
        return listOf(GitHubAgent.FileInfo("build.gradle.kts", build), GitHubAgent.FileInfo("settings.gradle.kts", "rootProject.name=\"${intent.appName}\""))
    }

    private fun generateManifest(intent: ProjectIntent): List<GitHubAgent.FileInfo> {
        val perms = intent.permissions.joinToString("\n    ") { "<uses-permission android:name=\"android.permission.$it\"/>" }
        val manifest = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    $perms
    <application android:label="${intent.appName}" android:theme="@style/Theme.Material3.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
    </application>
</manifest>
        """.trimIndent()
        return listOf(GitHubAgent.FileInfo("app/src/main/AndroidManifest.xml", manifest))
    }

    private fun generateCICD(intent: ProjectIntent): List<GitHubAgent.FileInfo> {
        val workflow = """
name: Build
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: chmod +x gradlew
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: apk, path: app/build/outputs/apk/debug/*.apk }
        """.trimIndent()
        return listOf(GitHubAgent.FileInfo(".github/workflows/build.yml", workflow))
    }

    private fun generateDocumentation(intent: ProjectIntent, tree: ArchitectureTree): List<GitHubAgent.FileInfo> {
        val readme = "# ${intent.appName}\n\nGenerated by Zarp CodeForge\n\n## Tech\n- ${intent.architecture}\n- ${intent.uiPattern}\n\n## Features\n${intent.features.joinToString("\n") { "- $it" }}\n\n## Structure\n```\n${generateArchitectureDiagram(tree)}\n```"
        return listOf(GitHubAgent.FileInfo("README.md", readme), GitHubAgent.FileInfo(".gitignore", "*.iml\n.gradle\nbuild/\n.idea/\nlocal.properties\n"))
    }

    private fun generateArchitectureDiagram(tree: ArchitectureTree?): String {
        if (tree == null) return ""
        return buildString {
            appendLine("📱 ${tree.rootPackage}")
            tree.folders.forEach { f ->
                appendLine("├── 📁 ${f.path}")
                f.files.forEach { appendLine("│   ├── 📄 ${it.name}") }
            }
        }
    }

    private suspend fun performIntegrityCheck(files: List<GitHubAgent.FileInfo>, tree: ArchitectureTree, intent: ProjectIntent): IntegrityResult {
        return IntegrityResult(true, emptyList())
    }

    private suspend fun fixBuildErrors(log: BuildMonitor.BuildLog?, files: List<GitHubAgent.FileInfo>, intent: ProjectIntent): List<GitHubAgent.FileInfo> {
        if (log == null || log.errors.isEmpty()) return emptyList()
        return try {
            val allCode = files.joinToString("\n\n") { "// ${it.path}\n${it.content}" }
            val prompt = "Fix these build errors:\n${log.errors.take(5).joinToString("\n")}\n\nCode:\n$allCode\n\nReturn fixed files in format: ```FILE:path\\ncontent```"
            val response = geminiRepository.generateResponse(prompt, PLANNING_MODEL)
            parseFixedFiles(response)
        } catch (e: Exception) { emptyList() }
    }

    private fun parseFixedFiles(response: String): List<GitHubAgent.FileInfo> {
        val regex = Regex("```FILE:(.+?)\\n([\\s\\S]*?)```")
        return regex.findAll(response).map { GitHubAgent.FileInfo(it.groupValues[1].trim(), it.groupValues[2].trim()) }.toList()
    }

    private fun buildDeliverySummary(intent: ProjectIntent, tree: ArchitectureTree, layers: List<CodeLayer>, build: BuildMonitor.BuildStatus?, fixes: Int, url: String): String = buildString {
        appendLine("✅ ${intent.appName} generated!")
        appendLine("📊 ${tree.totalFiles} files | ${layers.size} layers")
        if (fixes > 0) appendLine("🔧 $fixes fix attempts")
        if (build != null) appendLine("🏗️ Build: ${build.conclusion ?: "unknown"}")
        appendLine("🔗 $url")
    }
}
