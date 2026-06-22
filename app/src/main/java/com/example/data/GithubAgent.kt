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
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
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

    data class RepoInfo(
        val id: Long = 0,
        val name: String,
        val fullName: String,
        val url: String,
        val cloneUrl: String,
        val htmlUrl: String,
        val defaultBranch: String,
        val description: String? = null,
        val isPrivate: Boolean = false,
        val createdAt: String? = null,
        val starsCount: Int = 0
    )

    data class FileInfo(
        val path: String,
        val content: String,
        val sha: String? = null,
        val url: String? = null,
        val size: Int = 0
    ) {
        val extension: String get() = path.substringAfterLast('.', "")
        val fileName: String get() = path.substringAfterLast('/')
        val directory: String get() = path.substringBeforeLast('/')
    }

    data class PushResult(
        val success: Boolean,
        val repo: RepoInfo? = null,
        val filesCreated: List<String> = emptyList(),
        val filesUpdated: List<String> = emptyList(),
        val commitSha: String? = null,
        val commitUrl: String? = null,
        val error: String? = null
    )

    data class PRInfo(
        val number: Int,
        val title: String,
        val url: String,
        val state: String,
        val draft: Boolean = false,
        val createdAt: String? = null
    )

    data class BranchInfo(
        val name: String,
        val sha: String,
        val isProtected: Boolean = false
    )

    data class ReleaseInfo(
        val id: Long,
        val tagName: String,
        val name: String,
        val url: String,
        val assets: List<AssetInfo> = emptyList()
    )

    data class AssetInfo(
        val id: Long,
        val name: String,
        val url: String,
        val downloadUrl: String,
        val size: Long
    )

    data class TemplateInfo(
        val name: String,
        val fullName: String,
        val description: String,
        val url: String
    )

    // ═══════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════

    suspend fun getAuthenticatedUsername(): String? = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/user")
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) { Log.e(TAG, "Auth failed: ${response.code}"); return@withContext null }
            JSONObject(body).optString("login", null)
        } catch (e: Exception) { Log.e(TAG, "Auth error", e); null }
    }

    suspend fun getUserInfo(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/user")
            if (!response.isSuccessful) return@withContext null
            JSONObject(response.body?.string() ?: return@withContext null)
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // Repository Operations
    // ═══════════════════════════════════════════

    suspend fun createRepo(
        name: String,
        description: String = "Created by Zarp AI",
        isPrivate: Boolean = false,
        autoInit: Boolean = true,
        hasIssues: Boolean = true,
        hasWiki: Boolean = false,
        hasProjects: Boolean = false
    ): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("private", isPrivate)
                put("auto_init", autoInit)
                put("has_issues", hasIssues)
                put("has_wiki", hasWiki)
                put("has_projects", hasProjects)
            }
            Log.d(TAG, "📁 Creating repo: $name")
            val response = executeRequest("POST", "$API_BASE/user/repos", json.toString())
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) { Log.e(TAG, "Create repo failed: ${response.code} — ${body.take(200)}"); return@withContext null }
            parseRepoInfo(JSONObject(body))
        } catch (e: Exception) { Log.e(TAG, "Create repo error", e); null }
    }

    suspend fun createRepoFromTemplate(
        templateOwner: String,
        templateRepo: String,
        newName: String,
        description: String = "Generated from template by Zarp AI",
        isPrivate: Boolean = false
    ): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("owner", getAuthenticatedUsername() ?: return@withContext null)
                put("name", newName)
                put("description", description)
                put("private", isPrivate)
            }
            val url = "$API_BASE/repos/$templateOwner/$templateRepo/generate"
            Log.d(TAG, "📁 Creating from template: $templateOwner/$templateRepo → $newName")
            val response = executeRequest("POST", url, json.toString())
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) { Log.e(TAG, "Template clone failed: ${response.code}"); return@withContext null }
            parseRepoInfo(JSONObject(body))
        } catch (e: Exception) { Log.e(TAG, "Template error", e); null }
    }

    suspend fun getRepo(owner: String, repo: String): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/repos/$owner/$repo")
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            parseRepoInfo(JSONObject(body))
        } catch (e: Exception) { null }
    }

    suspend fun listRepos(perPage: Int = 50, page: Int = 1): List<RepoInfo> = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/user/repos?per_page=$perPage&page=$page&sort=updated")
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i -> parseRepoInfo(arr.getJSONObject(i)) }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteRepo(owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("DELETE", "$API_BASE/repos/$owner/$repo")
            response.isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun listTemplates(): List<TemplateInfo> = withContext(Dispatchers.IO) {
        try {
            val user = getAuthenticatedUsername() ?: return@withContext emptyList()
            val repos = listRepos(100)
            repos.filter { it.name.startsWith("zarp-") || it.name.contains("template") }.map {
                TemplateInfo(it.name, it.fullName, it.description ?: "", it.htmlUrl)
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════════════
    // File Operations
    // ═══════════════════════════════════════════

    suspend fun pushFiles(
        owner: String,
        repo: String,
        files: List<FileInfo>,
        commitMessage: String = "Zarp AI: Code update",
        branch: String = DEFAULT_BRANCH
    ): PushResult = withContext(Dispatchers.IO) {
        try {
            val created = mutableListOf<String>()
            val updated = mutableListOf<String>()
            var commitSha: String? = null

            for (file in files) {
                Log.d(TAG, "📄 Pushing: ${file.path}")
                val pushedFile = pushSingleFile(owner, repo, file, commitMessage, branch)
                if (pushedFile.sha != null) {
                    if (file.sha == null) created.add(file.path) else updated.add(file.path)
                    commitSha = pushedFile.sha
                }
            }

            val repoInfo = getRepo(owner, repo)
            PushResult(
                success = created.isNotEmpty() || updated.isNotEmpty(),
                repo = repoInfo,
                filesCreated = created,
                filesUpdated = updated,
                commitSha = commitSha,
                commitUrl = repoInfo?.htmlUrl?.let { "$it/commit/$branch" }
            )
        } catch (e: Exception) { Log.e(TAG, "Push files error", e); PushResult(false, error = e.localizedMessage) }
    }

    private suspend fun pushSingleFile(
        owner: String, repo: String, file: FileInfo, commitMessage: String, branch: String
    ): FileInfo = withContext(Dispatchers.IO) {
        try {
            val existingSha = getFileSha(owner, repo, file.path, branch)
            val json = JSONObject().apply {
                put("message", commitMessage)
                put("content", Base64.getEncoder().encodeToString(file.content.toByteArray()))
                put("branch", branch)
                if (existingSha != null) put("sha", existingSha)
            }
            val response = executeRequest("PUT", "$API_BASE/repos/$owner/$repo/contents/${file.path}", json.toString())
            val body = response.body?.string() ?: return@withContext file
            if (response.isSuccessful) {
                val result = JSONObject(body)
                val content = result.optJSONObject("content")
                file.copy(sha = content?.optString("sha"), url = content?.optString("html_url"))
            } else { Log.e(TAG, "Push failed (${response.code}): ${body.take(200)}"); file }
        } catch (e: Exception) { Log.e(TAG, "Push single error", e); file }
    }

    suspend fun readFile(owner: String, repo: String, path: String, branch: String = DEFAULT_BRANCH): String? =
        withContext(Dispatchers.IO) {
            try {
                val response = executeRequest("GET", "$API_BASE/repos/$owner/$repo/contents/$path?ref=$branch")
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val encoded = JSONObject(body).optString("content", "")
                if (encoded.isBlank()) return@withContext null
                Base64.getDecoder().decode(encoded.replace("\n", "")).toString(Charsets.UTF_8)
            } catch (e: Exception) { null }
        }

    suspend fun listFiles(owner: String, repo: String, path: String = "", branch: String = DEFAULT_BRANCH): List<FileInfo> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/repos/$owner/$repo/contents/$path?ref=$branch"
                val response = executeRequest("GET", url)
                val body = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    FileInfo(path = obj.getString("path"), content = "", sha = obj.optString("sha"), size = obj.optInt("size"))
                }
            } catch (e: Exception) { emptyList() }
        }

    suspend fun deleteFile(owner: String, repo: String, path: String, branch: String = DEFAULT_BRANCH, message: String = "Delete via Zarp"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val sha = getFileSha(owner, repo, path, branch) ?: return@withContext false
                val json = JSONObject().apply { put("message", message); put("sha", sha); put("branch", branch) }
                executeRequest("DELETE", "$API_BASE/repos/$owner/$repo/contents/$path", json.toString()).isSuccessful
            } catch (e: Exception) { false }
        }

    private suspend fun getFileSha(owner: String, repo: String, path: String, branch: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val response = executeRequest("GET", "$API_BASE/repos/$owner/$repo/contents/$path?ref=$branch")
                val body = response.body?.string()
                if (response.isSuccessful && body != null) JSONObject(body).optString("sha", null) else null
            } catch (e: Exception) { null }
        }

    // ═══════════════════════════════════════════
    // Branch Operations
    // ═══════════════════════════════════════════

    suspend fun createBranch(owner: String, repo: String, branchName: String, baseBranch: String = DEFAULT_BRANCH): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val refUrl = "$API_BASE/repos/$owner/$repo/git/ref/heads/$baseBranch"
                val refResponse = executeRequest("GET", refUrl)
                val refBody = refResponse.body?.string() ?: return@withContext false
                if (!refResponse.isSuccessful) return@withContext false
                val baseSha = JSONObject(refBody).getJSONObject("object").getString("sha")
                val json = JSONObject().apply { put("ref", "refs/heads/$branchName"); put("sha", baseSha) }
                executeRequest("POST", "$API_BASE/repos/$owner/$repo/git/refs", json.toString()).isSuccessful
            } catch (e: Exception) { false }
        }

    suspend fun listBranches(owner: String, repo: String): List<BranchInfo> = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/repos/$owner/$repo/branches")
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BranchInfo(obj.getString("name"), obj.getJSONObject("commit").getString("sha"), obj.optBoolean("protected"))
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════════════
    // Pull Request Operations
    // ═══════════════════════════════════════════

    suspend fun createPR(
        owner: String, repo: String, title: String, head: String,
        base: String = DEFAULT_BRANCH, body: String = "Created by Zarp AI", draft: Boolean = false
    ): PRInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("title", title); put("head", head); put("base", base); put("body", body); put("draft", draft)
            }
            val response = executeRequest("POST", "$API_BASE/repos/$owner/$repo/pulls", json.toString())
            val respBody = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val pr = JSONObject(respBody)
            PRInfo(pr.getInt("number"), pr.getString("title"), pr.getString("html_url"), pr.getString("state"), pr.optBoolean("draft"), pr.optString("created_at"))
        } catch (e: Exception) { null }
    }

    suspend fun listPRs(owner: String, repo: String, state: String = "open"): List<PRInfo> = withContext(Dispatchers.IO) {
        try {
            val response = executeRequest("GET", "$API_BASE/repos/$owner/$repo/pulls?state=$state")
            val body = response.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PRInfo(obj.getInt("number"), obj.getString("title"), obj.getString("html_url"), obj.getString("state"))
            }
        } catch (e: Exception) { emptyList() }
    }

    // ═══════════════════════════════════════════
    // Release Operations
    // ═══════════════════════════════════════════

    suspend fun createRelease(
        owner: String, repo: String, tagName: String, name: String, body: String = "", draft: Boolean = false
    ): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("tag_name", tagName); put("name", name); put("body", body); put("draft", draft)
            }
            val response = executeRequest("POST", "$API_BASE/repos/$owner/$repo/releases", json.toString())
            val respBody = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val release = JSONObject(respBody)
            ReleaseInfo(release.getLong("id"), release.getString("tag_name"), release.getString("name"), release.getString("html_url"))
        } catch (e: Exception) { null }
    }

    // ═══════════════════════════════════════════
    // Complete Workflows
    // ═══════════════════════════════════════════

    suspend fun createProjectFromFiles(
        repoName: String, files: List<FileInfo>, description: String = "Created by Zarp AI",
        isPrivate: Boolean = false, createPR: Boolean = false
    ): PushResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 Creating project: $repoName with ${files.size} files")
        val repo = createRepo(repoName, description, isPrivate) ?: return@withContext PushResult(false, error = "Failed to create repository")
        Log.d(TAG, "📁 Repo: ${repo.htmlUrl}")
        val owner = repo.fullName.split("/").first()
        val result = pushFiles(owner, repo.name, files, "Zarp AI: Initial project setup")
        if (result.success && createPR) {
            val branchName = "zarp-${System.currentTimeMillis()}"
            createBranch(owner, repo.name, branchName)
            createPR(owner, repo.name, "Zarp AI: Generated project", branchName)
        }
        Log.d(TAG, if (result.success) "✅ Project ready: ${repo.htmlUrl}" else "❌ Project failed")
        result
    }

    suspend fun createProjectFromTemplate(
        templateOwner: String, templateRepo: String, newName: String,
        customizations: List<FileInfo> = emptyList(), description: String = "Generated from template by Zarp AI"
    ): PushResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 Creating from template: $templateOwner/$templateRepo → $newName")
        val repo = createRepoFromTemplate(templateOwner, templateRepo, newName, description)
            ?: return@withContext PushResult(false, error = "Failed to create from template")
        val owner = repo.fullName.split("/").first()
        if (customizations.isNotEmpty()) {
            pushFiles(owner, repo.name, customizations, "Zarp AI: Customizations")
        }
        PushResult(true, repo, customizations.map { it.path }, commitUrl = repo.htmlUrl)
    }

    // ═══════════════════════════════════════════
    // Core HTTP
    // ═══════════════════════════════════════════

    private suspend fun executeRequest(method: String, url: String, body: String? = null): Response =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body!!.toRequestBody("application/json".toMediaType()))
                "PUT" -> builder.put(body!!.toRequestBody("application/json".toMediaType()))
                "DELETE" -> if (body != null) builder.delete(body.toRequestBody("application/json".toMediaType())) else builder.delete()
            }
            client.newCall(builder.build()).execute()
        }

    private fun parseRepoInfo(obj: JSONObject): RepoInfo = RepoInfo(
        id = obj.optLong("id"), name = obj.getString("name"), fullName = obj.getString("full_name"),
        url = obj.getString("url"), cloneUrl = obj.getString("clone_url"), htmlUrl = obj.getString("html_url"),
        defaultBranch = obj.optString("default_branch", DEFAULT_BRANCH), description = obj.optString("description", null),
        isPrivate = obj.optBoolean("private"), createdAt = obj.optString("created_at", null),
        starsCount = obj.optInt("stargazers_count")
    )
}
