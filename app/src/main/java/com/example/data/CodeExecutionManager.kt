package com.example.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.tool
import com.google.ai.client.generativeai.type.codeExecution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CodeExecutionManager(private val apiKey: String) {

    companion object {
        private const val TAG = "CodeExec"
        private const val EXEC_MODEL = "models/gemini-2.5-flash"
        private const val MAX_FIX_ATTEMPTS = 3
        private const val PASS_SCORE = 8
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class ExecutionResult(
        val success: Boolean,
        val output: String,
        val error: String? = null,
        val executionTimeMs: Long = 0,
        val hasCharts: Boolean = false,
        val chartData: String? = null
    )

    data class CodeReview(
        val score: Int,
        val issues: List<String>,
        val suggestions: List<String>,
        val passesReview: Boolean
    )

    data class AutoFixAttempt(
        val attempt: Int,
        val score: Int,
        val issues: List<String>
    )

    data class AutoFixResult(
        val success: Boolean,
        val finalCode: String,
        val score: Int,
        val attempts: List<AutoFixAttempt>
    )

    // ═══════════════════════════════════════════
    // Execute Python Code in Gemini Sandbox
    // ═══════════════════════════════════════════

    suspend fun executePython(
        code: String,
        input: String? = null,
        modelName: String = EXEC_MODEL
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    maxOutputTokens = 4096
                },
                tools = listOf(
                    tool {
                        codeExecution = codeExecution()
                    }
                )
            )

            val prompt = buildString {
                appendLine("Execute the following Python code and return ONLY the output.")
                if (!input.isNullOrBlank()) {
                    appendLine("Use this as input:")
                    appendLine(input)
                }
                appendLine()
                appendLine("```python")
                appendLine(code)
                appendLine("```")
            }

            Log.d(TAG, "Executing Python (${code.take(60).replace("\n", " ")}...)")
            val response = model.generateContent(content { text(prompt) })

            val executionTime = System.currentTimeMillis() - startTime
            val result = parseExecutionResponse(response.text ?: "", executionTime)

            if (result.success) {
                Log.d(TAG, "Execution complete (${executionTime}ms)")
            } else {
                Log.w(TAG, "Execution failed (${executionTime}ms): ${result.error?.take(100)}")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Execution error", e)
            ExecutionResult(
                success = false,
                output = "",
                error = e.localizedMessage ?: "Unknown execution error",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    // ═══════════════════════════════════════════
    // Execute with Multiple Test Cases
    // ═══════════════════════════════════════════

    suspend fun executePythonWithTests(
        code: String,
        testCases: List<String>,
        modelName: String = EXEC_MODEL
    ): List<ExecutionResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Running ${testCases.size} test cases...")
        testCases.mapIndexed { index, testCase ->
            Log.d(TAG, "  Test case ${index + 1}/${testCases.size}")
            executePython(code, testCase, modelName)
        }
    }

    // ═══════════════════════════════════════════
    // Review Code Quality (Returns 1-10 Score)
    // ═══════════════════════════════════════════

    suspend fun reviewCode(
        code: String,
        language: String = "python",
        modelName: String = EXEC_MODEL
    ): CodeReview = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    maxOutputTokens = 2048
                }
            )

            val prompt = """
                Review this $language code thoroughly. Return ONLY a valid JSON object with this exact structure:

                {
                  "score": <1-10 integer>,
                  "issues": ["specific issue 1", "specific issue 2"],
                  "suggestions": ["improvement 1", "improvement 2"]
                }

                Code to review:
                ```$language
                $code
                ```

                Check for: syntax errors, runtime exceptions, null safety, performance bottlenecks,
                code style violations, security vulnerabilities, missing imports, best practices.

                Return ONLY the JSON object. No other text, no markdown, no explanations.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val raw = response.text?.trim().orEmpty()
            val jsonText = extractJson(raw)

            if (jsonText.isNullOrBlank()) {
                Log.w(TAG, "reviewCode: no JSON found in response")
                return@withContext CodeReview(
                    score = 0,
                    issues = listOf("Could not parse review response"),
                    suggestions = emptyList(),
                    passesReview = false
                )
            }

            val obj = JSONObject(jsonText)
            val score = obj.optInt("score", 0)
            val issues = obj.optJSONArray("issues")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            val suggestions = obj.optJSONArray("suggestions")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            CodeReview(
                score = score,
                issues = issues,
                suggestions = suggestions,
                passesReview = score >= PASS_SCORE
            )
        } catch (e: Exception) {
            Log.e(TAG, "reviewCode error", e)
            CodeReview(
                score = 0,
                issues = listOf("Review failed: ${e.localizedMessage ?: "unknown error"}"),
                suggestions = emptyList(),
                passesReview = false
            )
        }
    }

    // ═══════════════════════════════════════════
    // Auto-Fix Loop: review -> fix -> re-review
    // ═══════════════════════════════════════════

    suspend fun autoFixCode(
        code: String,
        language: String = "python",
        modelName: String = EXEC_MODEL,
        maxAttempts: Int = MAX_FIX_ATTEMPTS
    ): AutoFixResult = withContext(Dispatchers.IO) {
        var currentCode = code
        val attempts = mutableListOf<AutoFixAttempt>()

        repeat(maxAttempts) { i ->
            val attemptNumber = i + 1
            val review = reviewCode(currentCode, language, modelName)
            attempts.add(AutoFixAttempt(attemptNumber, review.score, review.issues))

            if (review.passesReview) {
                Log.d(TAG, "autoFixCode: passed on attempt $attemptNumber (score=${review.score})")
                return@withContext AutoFixResult(
                    success = true,
                    finalCode = currentCode,
                    score = review.score,
                    attempts = attempts
                )
            }

            if (review.issues.isEmpty()) {
                Log.w(TAG, "autoFixCode: no issues reported but score below threshold, stopping")
                return@withContext AutoFixResult(
                    success = false,
                    finalCode = currentCode,
                    score = review.score,
                    attempts = attempts
                )
            }

            Log.d(TAG, "autoFixCode: attempt $attemptNumber failed (score=${review.score}), requesting fix")
            currentCode = fixCode(currentCode, review.issues, language, modelName) ?: currentCode
        }

        val finalReview = reviewCode(currentCode, language, modelName)
        AutoFixResult(
            success = finalReview.passesReview,
            finalCode = currentCode,
            score = finalReview.score,
            attempts = attempts
        )
    }

    private suspend fun fixCode(
        currentCode: String,
        issues: List<String>,
        language: String,
        modelName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    maxOutputTokens = 4096
                }
            )

            val issuesBlock = issues.joinToString("\n") { "- $it" }

            val prompt = """
                Fix ALL of the following issues in this $language code:

                $issuesBlock

                Original code:
                ```$language
                $currentCode
                ```

                Return ONLY the complete fixed code wrapped in a single ```$language code block.
                Include ALL imports. No explanations, no extra text.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val raw = response.text.orEmpty()
            extractCodeFromMarkdown(raw) ?: raw.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "fixCode error", e)
            null
        }
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun parseExecutionResponse(text: String, executionTimeMs: Long): ExecutionResult {
        if (text.isBlank()) {
            return ExecutionResult(
                success = false,
                output = "",
                error = "Empty response from model",
                executionTimeMs = executionTimeMs
            )
        }

        val lowerText = text.lowercase()
        val looksLikeError = lowerText.contains("traceback") ||
            lowerText.contains("error:") ||
            lowerText.contains("exception:")

        val hasCharts = lowerText.contains("matplotlib") ||
            lowerText.contains("plt.show") ||
            lowerText.contains("base64") && lowerText.contains("png")

        return if (looksLikeError) {
            ExecutionResult(
                success = false,
                output = text,
                error = text,
                executionTimeMs = executionTimeMs,
                hasCharts = hasCharts
            )
        } else {
            ExecutionResult(
                success = true,
                output = text.trim(),
                executionTimeMs = executionTimeMs,
                hasCharts = hasCharts
            )
        }
    }

    /** Pulls the first fenced code block's contents out of a markdown response, if present. */
    private fun extractCodeFromMarkdown(text: String): String? {
        val fenceRegex = Regex("```[a-zA-Z]*\\n([\\s\\S]*?)```")
        val match = fenceRegex.find(text) ?: return null
        return match.groupValues[1].trim().ifBlank { null }
    }

    /** Pulls a top-level JSON object out of a response that may contain extra text/markdown fences. */
    private fun extractJson(text: String): String? {
        val cleaned = text
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return cleaned.substring(start, end + 1)
    }
}
