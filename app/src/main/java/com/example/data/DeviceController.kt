package com.example.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DeviceController(private val context: Context) {

    companion object {
        private const val TAG = "DeviceCtrl"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 600L
        private const val UI_TIMEOUT_MS = 5000L
        private const val SCREEN_READ_MAX_DEPTH = 15
    }

    data class DeviceAction(
        val type: ActionType,
        val target: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val text: String? = null,
        val packageName: String? = null,
        val description: String = ""
    )

    enum class ActionType {
        TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
        TYPE_TEXT, OPEN_APP, BACK, HOME, RECENTS,
        SCREENSHOT, SCROLL_UP, SCROLL_DOWN, FIND_AND_TAP
    }

    data class ActionResult(
        val success: Boolean,
        val message: String,
        val screenshot: Bitmap? = null,
        val screenContent: String? = null
    )

    private var accessibilityService: ZarpAccessibilityService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setAccessibilityService(service: ZarpAccessibilityService?) {
        accessibilityService = service
        Log.d(TAG, "♿ Accessibility ${if (service != null) "connected" else "disconnected"}")
    }

    private val isServiceAvailable: Boolean get() = accessibilityService != null

    // ═══════════════════════════════════════════
    // Unified Execute with Retry
    // ═══════════════════════════════════════════

    suspend fun executeAction(
        action: DeviceAction,
        retryCount: Int = MAX_RETRIES
    ): ActionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🎮 ${action.type}: ${action.description}")

        if (!isServiceAvailable) {
            return@withContext ActionResult(false, "Accessibility service not connected. Enable it in Settings → Accessibility → Zarp.")
        }

        var lastResult = ActionResult(false, "Not executed")
        repeat(retryCount) { attempt ->
            lastResult = executeOnce(action)
            if (lastResult.success) return@withContext lastResult
            if (attempt < retryCount - 1) {
                Log.d(TAG, "  Retry ${attempt + 1}/$retryCount")
                delay(RETRY_DELAY_MS)
            }
        }
        Log.w(TAG, "  Failed after $retryCount attempts: ${lastResult.message}")
        lastResult
    }

    suspend fun executeSequence(
        actions: List<DeviceAction>,
        retryCount: Int = MAX_RETRIES
    ): List<ActionResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ActionResult>()
        var consecutiveFailures = 0

        for ((index, action) in actions.withIndex()) {
            Log.d(TAG, "▶️ ${index + 1}/${actions.size}: ${action.description}")
            val result = executeAction(action, retryCount)
            results.add(result)

            if (result.success) {
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    Log.w(TAG, "⚠️ Too many consecutive failures, stopping sequence")
                    break
                }
            }
            delay(400)
        }
        results
    }

    private suspend fun executeOnce(action: DeviceAction): ActionResult = withContext(Dispatchers.IO) {
        when (action.type) {
            ActionType.TAP -> executeTap(action)
            ActionType.LONG_PRESS -> executeLongPress(action)
            ActionType.SWIPE_UP -> executeSwipe(540, 1500, 540, 800, 300)
            ActionType.SWIPE_DOWN -> executeSwipe(540, 800, 540, 1500, 300)
            ActionType.SWIPE_LEFT -> executeSwipe(900, 1200, 500, 1200, 300)
            ActionType.SWIPE_RIGHT -> executeSwipe(200, 1200, 600, 1200, 300)
            ActionType.TYPE_TEXT -> executeTypeText(action.text ?: "")
            ActionType.OPEN_APP -> executeOpenApp(action.packageName ?: "")
            ActionType.BACK -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
            ActionType.HOME -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
            ActionType.RECENTS -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
            ActionType.SCREENSHOT -> executeScreenshot()
            ActionType.SCROLL_UP -> executeScroll(true)
            ActionType.SCROLL_DOWN -> executeScroll(false)
            ActionType.FIND_AND_TAP -> executeFindAndTap(action.target ?: "")
        }
    }

    // ═══════════════════════════════════════════
    // Action Implementations
    // ═══════════════════════════════════════════

    private suspend fun executeTap(action: DeviceAction): ActionResult {
        return if (action.target != null) {
            executeFindAndTap(action.target)
        } else if (action.x != null && action.y != null) {
            val service = accessibilityService ?: return ActionResult(false, "No service")
            val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
            val node = findNodeAtCoordinates(root, action.x, action.y)
            val result = if (node != null && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else false
            root.recycle()
            if (result) ActionResult(true, "Tapped (${action.x},${action.y})") else ActionResult(false, "Nothing clickable at coordinates")
        } else ActionResult(false, "No target or coordinates")
    }

    private suspend fun executeLongPress(action: DeviceAction): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val node = if (action.target != null) findNodeByText(root, action.target) else findNodeAtCoordinates(root, action.x ?: 540, action.y ?: 960)
        val result = if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } else false
        root.recycle()
        if (result) ActionResult(true, "Long pressed") else ActionResult(false, "Long press failed")
    }

    private suspend fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val result = service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                    android.graphics.Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) },
                    0, duration.toLong()
                )).build(), null, null
        )
        return if (result) ActionResult(true, "Swiped") else ActionResult(false, "Swipe failed")
    }

    private suspend fun executeTypeText(text: String): ActionResult {
        if (text.isBlank()) return ActionResult(false, "No text")
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val result = if (focused != null) {
            val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            // Fallback: set clipboard + paste
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("zarp", text))
            false // Paste requires more complex gesture
        }
        root.recycle()
        return if (result) ActionResult(true, "Typed text") else ActionResult(false, "Type failed — tap an input field first")
    }

    private fun executeOpenApp(packageName: String): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                ActionResult(true, "Opened $packageName")
            } else {
                // Try monkey command
                try {
                    Runtime.getRuntime().exec("monkey -p $packageName 1")
                    ActionResult(true, "Opened via monkey")
                } catch (e: Exception) {
                    ActionResult(false, "App not found: $packageName")
                }
            }
        } catch (e: Exception) {
            ActionResult(false, "Open failed: ${e.localizedMessage}")
        }
    }

    private fun executeGlobalAction(action: Int, label: String): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val result = service.performGlobalAction(action)
        return if (result) ActionResult(true, label) else ActionResult(false, "$label failed")
    }

    private fun executeScreenshot(): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        return try {
            service.takeScreenshot(
                android.os.Handler(Looper.getMainLooper())
            ) { screenshot ->
                if (screenshot != null) {
                    Log.d(TAG, "📸 Screenshot captured")
                }
            }
            ActionResult(true, "Screenshot requested")
        } catch (e: Exception) {
            ActionResult(false, "Screenshot failed: ${e.localizedMessage}")
        }
    }

    private suspend fun executeScroll(up: Boolean): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val action = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val scrollable = findScrollableNode(root)
        val result = scrollable?.performAction(action) ?: root.performAction(action)
        root.recycle()
        return if (result) ActionResult(true, "Scrolled") else ActionResult(false, "Scroll failed")
    }

    private suspend fun executeFindAndTap(target: String): ActionResult {
        if (target.isBlank()) return ActionResult(false, "No target")
        val service = accessibilityService ?: return ActionResult(false, "No service")
        val root = getRootNode() ?: return ActionResult(false, "Cannot access screen")
        val node = findNodeByText(root, target)
        val result = if (node != null && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else if (node != null) {
            // Try parent if node itself isn't clickable
            var parent = node.parent
            var clicked = false
            while (parent != null && !clicked) {
                clicked = parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent = parent.parent
            }
            clicked
        } else false
        root.recycle()
        return if (result) ActionResult(true, "Tapped '$target'") else ActionResult(false, "Could not find '$target'")
    }

    // ═══════════════════════════════════════════
    // Screen Reading
    // ═══════════════════════════════════════════

    suspend fun readScreen(): String? = withContext(Dispatchers.IO) {
        val root = getRootNode() ?: return@withContext null
        val content = extractNodeText(root)
        root.recycle()
        content.take(5000)
    }

    private fun extractNodeText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > SCREEN_READ_MAX_DEPTH) return ""
        val sb = StringBuilder()
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val prefix = when {
            isEditable -> "[INPUT] "
            isClickable -> "[BTN] "
            else -> ""
        }
        if (!text.isNullOrBlank()) sb.appendLine("${"  ".repeat(depth)}$prefix$text")
        if (!desc.isNullOrBlank() && desc != text) sb.appendLine("${"  ".repeat(depth)}($desc)")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { sb.append(extractNodeText(it, depth + 1)) }
        }
        return sb.toString()
    }

    // ═══════════════════════════════════════════
    // Node Helpers
    // ═══════════════════════════════════════════

    private fun getRootNode(): AccessibilityNodeInfo? {
        val service = accessibilityService ?: return null
        var root: AccessibilityNodeInfo? = null
        var waited = 0L
        while (root == null && waited < UI_TIMEOUT_MS) {
            root = service.rootInActiveWindow
            if (root == null) {
                try { Thread.sleep(200) } catch (e: InterruptedException) { break }
                waited += 200
            }
        }
        return root
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()?.trim() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
            if (nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findNodeAtCoordinates(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.contains(x, y) && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun cleanup() {
        accessibilityService = null
        Log.d(TAG, "🧹 Cleaned up")
    }
}
