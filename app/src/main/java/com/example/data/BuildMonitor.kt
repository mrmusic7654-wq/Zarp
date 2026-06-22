package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BuildMonitor(private val githubToken: String) {

    companion object {
        private const val TAG = "BuildMonitor"
        private const val API_BASE = "https://api.github.com"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ZarpAI/3.0")
                .build()
            chain.proceed(request)
        }
        .build()

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class BuildStatus(
        val id: Long,
        val name: String,
        val status: String,
        val conclusion: String?,
        val htmlUrl: String,
        val createdAt: String,
        val updatedAt: String,
        val headBranch: String,
        val commitSha: String,
        val commitMessage: String?,
        val runNumber: Int,
        val event: String,
        val workflowName: String?,
        val actor: String?
    ) {
        val isRunning get() = status == "in_progress" || status == "queued" || status == "waiting" || status == "pending"
        val isSuccess get() = conclusion == "success"
        val isFailure get() = conclusion == "failure" || conclusion == "cancelled" || conclusion == "timed_out"
        val isComplete get() = conclusion != null
        val durationMinutes: Long get() {
            return try {
                val created = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(createdAt)?.time ?: 0
                val updated = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(updatedAt)?.time ?: 0
                (updated - created) / 60000
            } catch (e: Exception) { 0 }
        }
    }

    data class BuildLog(
        val runId: Long,
        val logText: String,
        val errors: List<String>,
        val warnings: List<String>,
        val totalLines: Int = 0
    )

    data class BuildSummary(
        val totalRuns: Int,
        val successRate: Float,
        val averageDurationMinutes: Long,
        val recentRuns: List<BuildStatus>
    )

    data class ArtifactInfo(
        val id: Long,
        val name: String,
        val url: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val createdAt: String
    )

    // ═══════════════════════════════════════════
    // Build Status
    // ═══════════════════════════════════════════

    suspend fun getLatestBuildStatus(owner: String, repo: String, workflowName: String? = null): BuildStatus? =
        withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append("$API_BASE/repos/$owner/$repo/actions/runs?per_page=5")
                    if (workflowName != null) append("&workflow=$workflowName")
                }
                val response = executeGet(url)
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) { Log.e(TAG, "Status failed: ${response.code}"); return@withContext null }
                val runs = JSONObject(body).optJSONArray("workflow_runs")
                if (runs == null || runs.length() == 0) { Log.d(TAG, "No runs for $owner/$repo"); return@withContext null }
                parseBuildStatus(runs.getJSONObject(0))
            } catch (e: Exception) { Log.e(TAG, "Status error", e); null }
        }

    suspend fun getBuildStatusById(owner: String, repo: String, runId: Long): BuildStatus? =
        withContext(Dispatchers.IO) {
            try {
                val response = executeGet("$API_BASE/repos/$owner/$repo/actions/runs/$runId")
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                parseBuildStatus(JSONObject(body))
            } catch (e: Exception) { null }
        }

    suspend fun getBuildSummary(owner: String, repo: String): BuildSummary = withContext(Dispatchers.IO) {
        val runs = getRecentBuilds(owner, repo, 30)
        val completed = runs.filter { it.isComplete }
        val successCount = completed.count { it.isSuccess }
        BuildSummary(runs.size, if (completed.isNotEmpty()) successCount.toFloat() / completed.size else 0f, if (completed.isNotEmpty()) completed.map { it.durationMinutes }.average().toLong() else 0, runs.take(5))
    }

    suspend fun getRecentBuilds(owner: String, repo: String, count: Int = 10): List<BuildStatus> =
        withContext(Dispatchers.IO) {
            try {
                val response = executeGet("$API_BASE/repos/$owner/$repo/actions/runs?per_page=$count")
                val body = response.body?.string() ?: return@withContext emptyList()
                val runs = JSONObject(body).optJSONArray("workflow_runs") ?: return@withContext emptyList()
                (0 until runs.length()).map { i -> parseBuildStatus(runs.getJSONObject(i)) }
            } catch (e: Exception) { emptyList() }
        }

    // ═══════════════════════════════════════════
    // Build Logs
    // ═══════════════════════════════════════════

    suspend fun getBuildLogs(owner: String, repo: String, runId: Long): BuildLog = withContext(Dispatchers.IO) {
        try {
            val response = executeGet("$API_BASE/repos/$owner/$repo/actions/runs/$runId/logs")
            val body = response.body?.string() ?: return@withContext BuildLog(runId, "", emptyList(), emptyList())
            if (!response.isSuccessful) return@withContext BuildLog(runId, "", emptyList(), emptyList())

            val lines = body.lines()
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            lines.forEach { line ->
                when {
                    line.contains("error:", true) || line.contains("FAILED") || line.contains("BUILD FAILED") ||
                    line.contains("Exception") || line.contains("Unresolved reference") || line.contains("cannot find symbol") ||
                    line.contains("Execution failed") || line.contains("Compilation error") -> errors.add(line.trim())
                    line.contains("warning:", true) || line.contains("WARNING") || line.contains("deprecated") || line.contains("Note:") -> warnings.add(line.trim())
                }
            }
            Log.d(TAG, "📋 ${errors.size} errors, ${warnings.size} warnings")
            BuildLog(runId, body.take(10000), errors.take(30), warnings.take(15), lines.size)
        } catch (e: Exception) { Log.e(TAG, "Logs error", e); BuildLog(runId, "", emptyList(), emptyList()) }
    }

    // ═══════════════════════════════════════════
    // Artifacts
    // ═══════════════════════════════════════════

    suspend fun getArtifacts(owner: String, repo: String, runId: Long? = null): List<ArtifactInfo> =
        withContext(Dispatchers.IO) {
            try {
                val url = if (runId != null) "$API_BASE/repos/$owner/$repo/actions/runs/$runId/artifacts"
                else "$API_BASE/repos/$owner/$repo/actions/artifacts"
                val response = executeGet(url)
                val body = response.body?.string() ?: return@withContext emptyList()
                val artifacts = JSONObject(body).optJSONArray("artifacts") ?: return@withContext emptyList()
                (0 until artifacts.length()).map { i ->
                    val obj = artifacts.getJSONObject(i)
                    ArtifactInfo(obj.getLong("id"), obj.getString("name"), obj.getString("url"), "${obj.getString("url")}/zip", obj.getLong("size_in_bytes"), obj.optString("created_at"))
                }
            } catch (e: Exception) { emptyList() }
        }

    // ═══════════════════════════════════════════
    // Wait & Monitor
    // ═══════════════════════════════════════════

    suspend fun waitForBuildCompletion(
        owner: String, repo: String, maxWaitSeconds: Int = 180, pollIntervalMs: Long = 5000
    ): BuildStatus? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val maxWaitMs = maxWaitSeconds * 1000L
        var lastStatus: BuildStatus? = null
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val status = getLatestBuildStatus(owner, repo)
            if (status != null) { lastStatus = status; if (status.isComplete) return@withContext status }
            delay(pollIntervalMs)
        }
        Log.w(TAG, "⏰ Build timed out after ${maxWaitSeconds}s")
        lastStatus
    }

    suspend fun monitorBuild(
        owner: String, repo: String,
        onStatus: (BuildStatus) -> Unit = {},
        onComplete: (BuildStatus, BuildLog?) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        delay(3000)
        val status = waitForBuildCompletion(owner, repo, 180)
        if (status == null) return@withContext
        onStatus(status)
        if (status.isFailure) { delay(2000); val logs = getBuildLogs(owner, repo, status.id); onComplete(status, logs) }
        else if (status.isSuccess) onComplete(status, null)
    }

    // ═══════════════════════════════════════════
    // Workflow Management
    // ═══════════════════════════════════════════

    suspend fun triggerWorkflow(owner: String, repo: String, workflowId: String, ref: String = "main"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply { put("ref", ref) }
                val response = executePost("$API_BASE/repos/$owner/$repo/actions/workflows/$workflowId/dispatches", json.toString())
                response.isSuccessful
            } catch (e: Exception) { false }
        }

    suspend fun cancelWorkflow(owner: String, repo: String, runId: Long): Boolean = withContext(Dispatchers.IO) {
        try { executePost("$API_BASE/repos/$owner/$repo/actions/runs/$runId/cancel", null).isSuccessful } catch (e: Exception) { false }
    }

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long): Boolean = withContext(Dispatchers.IO) {
        try { executePost("$API_BASE/repos/$owner/$repo/actions/runs/$runId/rerun", null).isSuccessful } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun executeGet(url: String) = client.newCall(Request.Builder().url(url).get().build()).execute()

    private fun executePost(url: String, body: String?) = client.newCall(
        Request.Builder().url(url).post(
            body?.toRequestBody("application/json".toMediaType())
            ?: "".toRequestBody("application/json".toMediaType())
        ).build()
    ).execute()

    private fun parseBuildStatus(obj: JSONObject): BuildStatus = BuildStatus(
        obj.getLong("id"), obj.optString("name", obj.optString("display_title", "build")),
        obj.optString("status", "unknown"), obj.optString("conclusion", null),
        obj.optString("html_url", ""), obj.optString("created_at", ""),
        obj.optString("updated_at", ""), obj.optString("head_branch", "main"),
        obj.optString("head_sha", ""), obj.optString("head_commit", null)?.let { try { JSONObject(it).optString("message") } catch (e: Exception) { null } },
        obj.optInt("run_number", 0), obj.optString("event", ""),
        obj.optString("workflow_name", null), obj.optString("actor", null)?.let { try { if (it is String) it else JSONObject(it.toString()).optString("login") } catch (e: Exception) { null } }
    )
}
