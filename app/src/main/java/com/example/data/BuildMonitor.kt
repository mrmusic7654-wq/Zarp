package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        .build()

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class BuildStatus(
        val id: Long,
        val status: String,
        val conclusion: String?,
        val htmlUrl: String,
        val createdAt: String,
        val headBranch: String,
        val commitSha: String,
        val runNumber: Int
    ) {
        val isRunning: Boolean get() = status == "in_progress" || status == "queued" || status == "waiting"
        val isSuccess: Boolean get() = conclusion == "success"
        val isFailure: Boolean get() = conclusion == "failure"
        val isComplete: Boolean get() = conclusion != null
    }

    data class BuildLog(
        val runId: Long,
        val logText: String,
        val errors: List<String>,
        val warnings: List<String>
    )

    // ═══════════════════════════════════════════
    // Get Latest Build Status
    // ═══════════════════════════════════════════

    suspend fun getLatestBuildStatus(owner: String, repo: String): BuildStatus? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$owner/$repo/actions/runs?per_page=1"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to get build status: ${response.code}")
                return@withContext null
            }

            val json = JSONObject(body)
            val runs = json.optJSONArray("workflow_runs")
            if (runs == null || runs.length() == 0) {
                Log.d(TAG, "📭 No workflow runs found for $owner/$repo")
                return@withContext null
            }

            val run = runs.getJSONObject(0)
            BuildStatus(
                id = run.getLong("id"),
                status = run.optString("status", "unknown"),
                conclusion = run.optString("conclusion", null),
                htmlUrl = run.optString("html_url", ""),
                createdAt = run.optString("created_at", ""),
                headBranch = run.optString("head_branch", "main"),
                commitSha = run.optString("head_sha", ""),
                runNumber = run.optInt("run_number", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Build status error", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    // Get Build Logs (Errors)
    // ═══════════════════════════════════════════

    suspend fun getBuildLogs(owner: String, repo: String, runId: Long): BuildLog = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$owner/$repo/actions/runs/$runId/logs"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext BuildLog(runId, "", emptyList(), emptyList())

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to get build logs: ${response.code}")
                return@withContext BuildLog(runId, "", emptyList(), emptyList())
            }

            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            body.lines().forEach { line ->
                when {
                    line.contains("error:", ignoreCase = true) ||
                    line.contains("FAILED", ignoreCase = true) ||
                    line.contains("Exception", ignoreCase = true) ||
                    line.contains("Unresolved reference", ignoreCase = true) ||
                    line.contains("cannot find symbol", ignoreCase = true) -> {
                        errors.add(line.trim())
                    }
                    line.contains("warning:", ignoreCase = true) ||
                    line.contains("WARNING", ignoreCase = true) ||
                    line.contains("deprecated", ignoreCase = true) -> {
                        warnings.add(line.trim())
                    }
                }
            }

            Log.d(TAG, "📋 Build log: ${errors.size} errors, ${warnings.size} warnings")
            BuildLog(
                runId = runId,
                logText = body.take(8000),
                errors = errors.take(20),
                warnings = warnings.take(10)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Build logs error", e)
            BuildLog(runId, "", emptyList(), emptyList())
        }
    }

    // ═══════════════════════════════════════════
    // Wait for Build Completion
    // ═══════════════════════════════════════════

    suspend fun waitForBuildCompletion(
        owner: String,
        repo: String,
        maxWaitSeconds: Int = 120,
        pollIntervalMs: Long = 5000
    ): BuildStatus? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val maxWaitMs = maxWaitSeconds * 1000L

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val status = getLatestBuildStatus(owner, repo)
            if (status != null && status.isComplete) {
                Log.d(TAG, "🏁 Build completed: ${status.conclusion}")
                return@withContext status
            }
            Log.d(TAG, "⏳ Build still running... waiting ${pollIntervalMs}ms")
            kotlinx.coroutines.delay(pollIntervalMs)
        }

        Log.w(TAG, "⏰ Build timed out after ${maxWaitSeconds}s")
        getLatestBuildStatus(owner, repo)
    }

    // ═══════════════════════════════════════════
    // Check if Workflow File Exists
    // ═══════════════════════════════════════════

    suspend fun hasWorkflowFile(owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$owner/$repo/contents/.github/workflows"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════
    // Poll for Build + Get Logs on Failure
    // ═══════════════════════════════════════════

    suspend fun monitorBuild(
        owner: String,
        repo: String,
        onStatus: (BuildStatus) -> Unit = {},
        onComplete: (BuildStatus, BuildLog?) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Monitoring build for $owner/$repo")

        // Wait for build to start
        kotlinx.coroutines.delay(3000)

        val status = waitForBuildCompletion(owner, repo, maxWaitSeconds = 180)
        if (status == null) {
            Log.w(TAG, "⚠️ Could not get build status")
            return@withContext
        }

        onStatus(status)

        if (status.isFailure) {
            Log.d(TAG, "❌ Build failed, fetching logs...")
            kotlinx.coroutines.delay(2000)
            val logs = getBuildLogs(owner, repo, status.id)
            onComplete(status, logs)
        } else if (status.isSuccess) {
            Log.d(TAG, "✅ Build passed!")
            onComplete(status, null)
        }
    }

    // ═══════════════════════════════════════════
    // Request Builder
    // ═══════════════════════════════════════════

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $githubToken")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZarpAI/2.0")
            .get()
            .build()
    }
}
