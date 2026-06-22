package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class GitHubAgent(private val githubToken: String) {

    companion object {
        private const val TAG = "GitHubAgent"
        private const val API_BASE = "https://api.github.com"
        private const val DEFAULT_BRANCH = "main"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class RepoInfo(
        val name: String,
        val fullName: String,
        val url: String,
        val cloneUrl: String,
        val htmlUrl: String,
        val defaultBranch: String
    )

    data class FileInfo(
        val path: String,
        val content: String,
        val sha: String? = null,
        val url: String? = null
    )

    data class PushResult(
        val success: Boolean,
        val repo: RepoInfo? = null,
        val filesCreated: List<String> = emptyList(),
        val filesUpdated: List<String> = emptyList(),
        val commitUrl: String? = null,
        val error: String? = null
    )

    data class PRInfo(
        val number: Int,
        val title: String,
        val url: String,
        val state: String
    )

    // ═══════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════

    /**
     * Returns the authenticated user's GitHub username.
     * Uses the stored token to call GET /user.
     */
    suspend fun getAuthenticatedUsername(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔑 Fetching authenticated user...")
            val request = buildRequest("GET", "$API_BASE/user")
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Auth check failed: ${response.code}")
                return@withContext null
            }

            val json = JSONObject(body ?: return@withContext null)
            val login = json.optString("login", null)
            Log.d(TAG, "✅ Authenticated as: $login")
            login
        } catch (e: Exception) {
            Log.e(TAG, "❌ Auth error", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    // Create Repository
    // ═══════════════════════════════════════════

    suspend fun createRepo(
        name: String,
        description: String = "Created by Zarp AI",
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("private", isPrivate)
                put("auto_init", autoInit)
            }

            Log.d(TAG, "📁 Creating repo: $name")
            val request = buildRequest("POST", "$API_BASE/user/repos", json.toString())
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Create repo failed: ${response.code} — ${body.take(200)}")
                return@withContext null
            }

            val repo = JSONObject(body)
            val result = RepoInfo(
                name = repo.getString("name"),
                fullName = repo.getString("full_name"),
                url = repo.getString("url"),
                cloneUrl = repo.getString("clone_url"),
                htmlUrl = repo.getString("html_url"),
                defaultBranch = repo.optString("default_branch", DEFAULT_BRANCH)
            )
            Log.d(TAG, "✅ Repo created: ${result.htmlUrl}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Create repo error", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    // Push Multiple Files
    // ═══════════════════════════════════════════

    suspend fun pushFiles(
        owner: String,
        repo: String,
        files: List<FileInfo>,
        commitMessage: String = "Zarp AI: Initial commit",
        branch: String = DEFAULT_BRANCH
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            val created = mutableListOf<String>()
            val updated = mutableListOf<String>()

            for (file in files) {
                Log.d(TAG, "📄 Pushing: ${file.path}")
                val pushedFile = pushSingleFile(owner, repo, file, commitMessage, branch)

                if (pushedFile.sha != null) {
                    if (file.sha == null) {
                        created.add(file.path)
                        Log.d(TAG, "  ✅ Created: ${file.path}")
                    } else {
                        updated.add(file.path)
                        Log.d(TAG, "  ✅ Updated: ${file.path}")
                    }
                } else {
                    Log.w(TAG, "  ⚠️ Failed: ${file.path}")
                }
            }

            val repoInfo = getRepo(owner, repo)

            PushResult(
                success = created.isNotEmpty() || updated.isNotEmpty(),
                repo = repoInfo,
                filesCreated = created,
                filesUpdated = updated,
                commitUrl = repoInfo?.htmlUrl?.let { "$it/commit/$branch" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Push files error", e)
            PushResult(success = false, error = e.localizedMessage)
        }
    }

    private suspend fun pushSingleFile(
        owner: String,
        repo: String,
        file: FileInfo,
        commitMessage: String,
        branch: String
    ): FileInfo = withContext(Dispatchers.IO) {
        try {
            val existingSha = getFileSha(owner, repo, file.path, branch)

            val json = JSONObject().apply {
                put("message", commitMessage)
                put("content", Base64.getEncoder().encodeToString(file.content.toByteArray()))
                put("branch", branch)
                if (existingSha != null) put("sha", existingSha)
            }

            val url = "$API_BASE/repos/$owner/$repo/contents/${file.path}"
            val request = buildRequest("PUT", url, json.toString())
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext file

            if (response.isSuccessful) {
                val result = JSONObject(body)
                val content = result.optJSONObject("content")
                file.copy(sha = content?.optString("sha"), url = content?.optString("html_url"))
            } else {
                Log.e(TAG, "  ❌ Push failed (${response.code}): ${body.take(200)}")
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Push single file error", e)
            file
        }
    }

    private suspend fun getFileSha(owner: String, repo: String, path: String, branch: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/repos/$owner/$repo/contents/$path?ref=$branch"
                val request = buildRequest("GET", url)
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (response.isSuccessful && body != null) JSONObject(body).optString("sha", null) else null
            } catch (e: Exception) { null }
        }

    // ═══════════════════════════════════════════
    // Get Repository Info
    // ═══════════════════════════════════════════

    suspend fun getRepo(owner: String, repo: String): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/repos/$owner/$repo"
            val request = buildRequest("GET", url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(body)
            RepoInfo(
                name = json.getString("name"),
                fullName = json.getString("full_name"),
                url = json.getString("url"),
                cloneUrl = json.getString("clone_url"),
                htmlUrl = json.getString("html_url"),
                defaultBranch = json.optString("default_branch", DEFAULT_BRANCH)
            )
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // Create Pull Request
    // ═══════════════════════════════════════════

    suspend fun createPR(
        owner: String, repo: String, title: String, head: String,
        base: String = DEFAULT_BRANCH, body: String = "Created by Zarp AI"
    ): PRInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("title", title); put("head", head); put("base", base); put("body", body)
            }
            val url = "$API_BASE/repos/$owner/$repo/pulls"
            val request = buildRequest("POST", url, json.toString())
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val pr = JSONObject(respBody)
            PRInfo(number = pr.getInt("number"), title = pr.getString("title"), url = pr.getString("html_url"), state = pr.getString("state"))
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // Create Branch
    // ═══════════════════════════════════════════

    suspend fun createBranch(owner: String, repo: String, branchName: String, baseBranch: String = DEFAULT_BRANCH): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val refUrl = "$API_BASE/repos/$owner/$repo/git/ref/heads/$baseBranch"
                val refReq = buildRequest("GET", refUrl)
                val refResp = client.newCall(refReq).execute()
                val refBody = refResp.body?.string() ?: return@withContext false
                if (!refResp.isSuccessful) return@withContext false
                val baseSha = JSONObject(refBody).getJSONObject("object").getString("sha")
                val json = JSONObject().apply { put("ref", "refs/heads/$branchName"); put("sha", baseSha) }
                val url = "$API_BASE/repos/$owner/$repo/git/refs"
                val request = buildRequest("POST", url, json.toString())
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }

    // ═══════════════════════════════════════════
    // List User Repos
    // ═══════════════════════════════════════════

    suspend fun listRepos(): List<RepoInfo> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("GET", "$API_BASE/user/repos?per_page=50")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val json = arr.getJSONObject(i)
                RepoInfo(name = json.getString("name"), fullName = json.getString("full_name"), url = json.getString("url"), cloneUrl = json.getString("clone_url"), htmlUrl = json.getString("html_url"), defaultBranch = json.optString("default_branch", DEFAULT_BRANCH))
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════════════
    // Read File from Repo
    // ═══════════════════════════════════════════

    suspend fun readFile(owner: String, repo: String, path: String, branch: String = DEFAULT_BRANCH): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/repos/$owner/$repo/contents/$path?ref=$branch"
                val request = buildRequest("GET", url)
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(body)
                val encoded = json.optString("content", "")
                if (encoded.isBlank()) return@withContext null
                Base64.getDecoder().decode(encoded.replace("\n", "")).toString(Charsets.UTF_8)
            } catch (e: Exception) { null }
        }

    // ═══════════════════════════════════════════
    // Delete File
    // ═══════════════════════════════════════════

    suspend fun deleteFile(owner: String, repo: String, path: String, branch: String = DEFAULT_BRANCH, message: String = "Delete via Zarp AI"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val sha = getFileSha(owner, repo, path, branch) ?: return@withContext false
                val json = JSONObject().apply { put("message", message); put("sha", sha); put("branch", branch) }
                val url = "$API_BASE/repos/$owner/$repo/contents/$path"
                val request = buildRequest("DELETE", url, json.toString())
                client.newCall(request).execute().isSuccessful
            } catch (e: Exception) { false }
        }

    // ═══════════════════════════════════════════
    // Complete Workflow: Create Repo + Push + PR
    // ═══════════════════════════════════════════

    suspend fun createProjectFromFiles(
        repoName: String,
        files: List<FileInfo>,
        description: String = "Created by Zarp AI",
        isPrivate: Boolean = false,
        createPR: Boolean = false
    ): PushResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 Creating project: $repoName with ${files.size} files")

        val repo = createRepo(repoName, description, isPrivate)
        if (repo == null) {
            return@withContext PushResult(success = false, error = "Failed to create repository")
        }

        val owner = repo.fullName.split("/").first()
        val repoOnly = repo.name

        val result = pushFiles(owner, repoOnly, files, "Zarp AI: Initial project setup")

        if (result.success && createPR) {
            val branchName = "zarp-${System.currentTimeMillis()}"
            createBranch(owner, repoOnly, branchName)
            createPR(owner, repoOnly, "Zarp AI: Generated project", branchName)
        }

        Log.d(TAG, if (result.success) "✅ Project ready: ${repo.htmlUrl}" else "❌ Project failed")
        result
    }

    // ═══════════════════════════════════════════
    // Request Builder
    // ═══════════════════════════════════════════

    private fun buildRequest(method: String, url: String, body: String? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $githubToken")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZarpAI/2.0")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body!!.toRequestBody("application/json".toMediaType()))
            "PUT" -> builder.put(body!!.toRequestBody("application/json".toMediaType()))
            "DELETE" -> {
                if (body != null) builder.delete(body.toRequestBody("application/json".toMediaType()))
                else builder.delete()
            }
        }

        return builder.build()
    }
}
